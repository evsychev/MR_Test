package org.example.xr;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.openxr.FBSpatialEntity;
import org.lwjgl.openxr.FBSpatialEntityQuery;
import org.lwjgl.openxr.METASpatialEntityMesh;
import org.lwjgl.openxr.XR10;
import org.lwjgl.openxr.XrEventDataBuffer;
import org.lwjgl.openxr.XrEventDataSpaceQueryCompleteFB;
import org.lwjgl.openxr.XrEventDataSpaceQueryResultsAvailableFB;
import org.lwjgl.openxr.XrEventDataSpaceSetStatusCompleteFB;
import org.lwjgl.openxr.XrSession;
import org.lwjgl.openxr.XrSpace;
import org.lwjgl.openxr.XrSpaceComponentStatusFB;
import org.lwjgl.openxr.XrSpaceComponentStatusSetInfoFB;
import org.lwjgl.openxr.XrSpaceFilterInfoBaseHeaderFB;
import org.lwjgl.openxr.XrSpaceLocation;
import org.lwjgl.openxr.XrSpaceQueryInfoBaseHeaderFB;
import org.lwjgl.openxr.XrSpaceQueryInfoFB;
import org.lwjgl.openxr.XrSpaceQueryResultFB;
import org.lwjgl.openxr.XrSpaceQueryResultsFB;
import org.lwjgl.openxr.XrSpaceStorageLocationFilterInfoFB;
import org.lwjgl.openxr.XrSpaceTriangleMeshGetInfoMETA;
import org.lwjgl.openxr.XrSpaceTriangleMeshMETA;
import org.lwjgl.openxr.XrVector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.openxr.XR10.XR_ERROR_SIZE_INSUFFICIENT;
import static org.lwjgl.openxr.XR10.XR_SUCCESS;

final class SpatialMeshService implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpatialMeshService.class);
    private static final int MAX_QUERY_RESULTS = 128;
    private static final long QUERY_TIMEOUT_NANOS = 10_000_000_000L;

    private final XrSession session;
    private final Map<Long, XrSpace> pendingMeshRequests = new HashMap<>();
    private final List<XrSpace> ownedSpaces = new ArrayList<>();
    private final List<MeshSnapshot> meshes = new ArrayList<>();
    private boolean queryStarted;
    private boolean leftControllerNearMesh;
    private boolean rightControllerNearMesh;

    SpatialMeshService(XrSession session) {
        this.session = session;
    }

    void startQuery(MemoryStack stack) {
        if (queryStarted) {
            return;
        }
        queryStarted = true;

        XrSpaceStorageLocationFilterInfoFB localStorageFilter =
                XrSpaceStorageLocationFilterInfoFB.calloc(stack)
                        .type$Default()
                        .location(FBSpatialEntityQuery.XR_SPACE_STORAGE_LOCATION_LOCAL_FB);
        XrSpaceQueryInfoFB queryInfo = XrSpaceQueryInfoFB.calloc(stack)
                .type$Default()
                .queryAction(FBSpatialEntityQuery.XR_SPACE_QUERY_ACTION_LOAD_FB)
                .maxResultCount(MAX_QUERY_RESULTS)
                .timeout(QUERY_TIMEOUT_NANOS)
                .filter(XrSpaceFilterInfoBaseHeaderFB.create(localStorageFilter.address()));
        LongBuffer requestId = stack.mallocLong(1);
        check(FBSpatialEntityQuery.xrQuerySpacesFB(session,
                        XrSpaceQueryInfoBaseHeaderFB.create(queryInfo), requestId),
                "start local spatial entity query");
        LOGGER.info("Started local spatial mesh query, request {}", requestId.get(0));
    }

    void processEvent(XrEventDataBuffer event) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (event.type() == FBSpatialEntityQuery.XR_TYPE_EVENT_DATA_SPACE_QUERY_RESULTS_AVAILABLE_FB) {
                XrEventDataSpaceQueryResultsAvailableFB available =
                        XrEventDataSpaceQueryResultsAvailableFB.create(event.address());
                retrieveResults(available.requestId(), stack);
            } else if (event.type() == FBSpatialEntityQuery.XR_TYPE_EVENT_DATA_SPACE_QUERY_COMPLETE_FB) {
                XrEventDataSpaceQueryCompleteFB complete =
                        XrEventDataSpaceQueryCompleteFB.create(event.address());
                if (complete.result() == XR_SUCCESS) {
                    LOGGER.info("Local spatial entity query {} completed", complete.requestId());
                } else {
                    LOGGER.warn("Local spatial entity query {} completed with result {}",
                            complete.requestId(), complete.result());
                }
            } else if (event.type() == FBSpatialEntity.XR_TYPE_EVENT_DATA_SPACE_SET_STATUS_COMPLETE_FB) {
                XrEventDataSpaceSetStatusCompleteFB complete =
                        XrEventDataSpaceSetStatusCompleteFB.create(event.address());
                completeMeshRequest(complete, stack);
            }
        }
    }

    private void retrieveResults(long requestId, MemoryStack stack) {
        XrSpaceQueryResultsFB queryResults = XrSpaceQueryResultsFB.calloc(stack).type$Default();
        int result = FBSpatialEntityQuery.xrRetrieveSpaceQueryResultsFB(session, requestId, queryResults);
        if (result != XR_SUCCESS && result != XR_ERROR_SIZE_INSUFFICIENT) {
            LOGGER.warn("Could not read spatial query result count for request {}: {}", requestId, result);
            return;
        }

        int resultCount = queryResults.resultCountOutput();
        if (resultCount == 0) {
            LOGGER.info("Spatial query {} returned no local entities; select a simulator room with scene data", requestId);
            return;
        }

        XrSpaceQueryResultFB.Buffer results = XrSpaceQueryResultFB.calloc(resultCount, stack);
        queryResults.resultCapacityInput(resultCount).results(results);
        result = FBSpatialEntityQuery.xrRetrieveSpaceQueryResultsFB(session, requestId, queryResults);
        if (result != XR_SUCCESS) {
            LOGGER.warn("Could not retrieve {} spatial query results for request {}: {}", resultCount, requestId, result);
            return;
        }

        for (int index = 0; index < queryResults.resultCountOutput(); index++) {
            XrSpace space = new XrSpace(results.get(index).space(), session);
            ownedSpaces.add(space);
            try (MemoryStack resultStack = MemoryStack.stackPush()) {
                requestTriangleMesh(space, resultStack);
            }
        }
        LOGGER.info("Loaded {} local spatial entities", queryResults.resultCountOutput());
    }

    private void requestTriangleMesh(XrSpace space, MemoryStack stack) {
        IntBuffer componentCount = stack.ints(0);
        int result = FBSpatialEntity.xrEnumerateSpaceSupportedComponentsFB(space, componentCount, null);
        if (result != XR_SUCCESS || componentCount.get(0) == 0) {
            LOGGER.debug("Spatial entity {} exposes no queryable components", space.address());
            return;
        }

        IntBuffer components = stack.mallocInt(componentCount.get(0));
        result = FBSpatialEntity.xrEnumerateSpaceSupportedComponentsFB(space, componentCount, components);
        if (result != XR_SUCCESS) {
            LOGGER.warn("Could not enumerate components for spatial entity {}: {}", space.address(), result);
            return;
        }

        boolean triangleMeshSupported = false;
        for (int index = 0; index < componentCount.get(0); index++) {
            if (components.get(index) == METASpatialEntityMesh.XR_SPACE_COMPONENT_TYPE_TRIANGLE_MESH_META) {
                triangleMeshSupported = true;
                break;
            }
        }
        if (!triangleMeshSupported) {
            LOGGER.debug("Spatial entity {} does not provide a triangle mesh", space.address());
            return;
        }

        XrSpaceComponentStatusFB status = XrSpaceComponentStatusFB.calloc(stack).type$Default();
        result = FBSpatialEntity.xrGetSpaceComponentStatusFB(space,
                METASpatialEntityMesh.XR_SPACE_COMPONENT_TYPE_TRIANGLE_MESH_META, status);
        if (result == XR_SUCCESS && status.enabled()) {
            readTriangleMesh(space, stack);
            return;
        }
        if (result != XR_SUCCESS && result != FBSpatialEntity.XR_ERROR_SPACE_COMPONENT_NOT_ENABLED_FB
                && result != FBSpatialEntity.XR_ERROR_SPACE_COMPONENT_STATUS_PENDING_FB) {
            LOGGER.warn("Could not read triangle mesh component status for entity {}: {}", space.address(), result);
            return;
        }

        XrSpaceComponentStatusSetInfoFB statusInfo = XrSpaceComponentStatusSetInfoFB.calloc(stack)
                .type$Default()
                .componentType(METASpatialEntityMesh.XR_SPACE_COMPONENT_TYPE_TRIANGLE_MESH_META)
                .enabled(true)
                .timeout(QUERY_TIMEOUT_NANOS);
        LongBuffer requestId = stack.mallocLong(1);
        result = FBSpatialEntity.xrSetSpaceComponentStatusFB(space, statusInfo, requestId);
        if (result == FBSpatialEntity.XR_ERROR_SPACE_COMPONENT_STATUS_ALREADY_SET_FB) {
            readTriangleMesh(space, stack);
        } else if (result == XR_SUCCESS) {
            pendingMeshRequests.put(requestId.get(0), space);
            LOGGER.debug("Requested triangle mesh for spatial entity {}", space.address());
        } else {
            LOGGER.warn("Could not enable triangle mesh for spatial entity {}: {}", space.address(), result);
        }
    }

    private void completeMeshRequest(XrEventDataSpaceSetStatusCompleteFB event, MemoryStack stack) {
        XrSpace space = pendingMeshRequests.remove(event.requestId());
        if (space == null || event.componentType() != METASpatialEntityMesh.XR_SPACE_COMPONENT_TYPE_TRIANGLE_MESH_META) {
            return;
        }
        if (event.result() == XR_SUCCESS && event.enabled()) {
            readTriangleMesh(space, stack);
        } else {
            LOGGER.warn("Could not enable spatial mesh for entity {}: {}", space.address(), event.result());
        }
    }

    private void readTriangleMesh(XrSpace space, MemoryStack stack) {
        XrSpaceTriangleMeshGetInfoMETA getInfo = XrSpaceTriangleMeshGetInfoMETA.calloc(stack).type$Default();
        XrSpaceTriangleMeshMETA mesh = XrSpaceTriangleMeshMETA.calloc(stack).type$Default();
        int result = METASpatialEntityMesh.xrGetSpaceTriangleMeshMETA(space, getInfo, mesh);
        if (result != XR_ERROR_SIZE_INSUFFICIENT && result != XR_SUCCESS) {
            LOGGER.warn("Could not query spatial mesh sizes for entity {}: {}", space.address(), result);
            return;
        }

        int vertexCount = mesh.vertexCountOutput();
        int indexCount = mesh.indexCountOutput();
        if (vertexCount == 0 || indexCount == 0) {
            LOGGER.info("Spatial entity {} has an empty triangle mesh", space.address());
            return;
        }

        XrVector3f.Buffer vertices = XrVector3f.calloc(vertexCount);
        IntBuffer indices = MemoryUtil.memAllocInt(indexCount);
        try {
            MemoryUtil.memPutInt(mesh.address() + XrSpaceTriangleMeshMETA.VERTEXCAPACITYINPUT, vertexCount);
            MemoryUtil.memPutAddress(mesh.address() + XrSpaceTriangleMeshMETA.VERTICES, vertices.address());
            MemoryUtil.memPutInt(mesh.address() + XrSpaceTriangleMeshMETA.INDEXCAPACITYINPUT, indexCount);
            MemoryUtil.memPutAddress(mesh.address() + XrSpaceTriangleMeshMETA.INDICES, MemoryUtil.memAddress(indices));
            result = METASpatialEntityMesh.xrGetSpaceTriangleMeshMETA(space, getInfo, mesh);
            if (result != XR_SUCCESS) {
                LOGGER.warn("Could not retrieve triangle mesh for entity {}: {}", space.address(), result);
                return;
            }

            int actualVertexCount = mesh.vertexCountOutput();
            int actualIndexCount = mesh.indexCountOutput();
            float[] positions = new float[Math.multiplyExact(actualVertexCount, 3)];
            int[] triangleIndices = new int[actualIndexCount];
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            for (int index = 0; index < actualVertexCount; index++) {
                XrVector3f vertex = vertices.get(index);
                positions[index * 3] = vertex.x();
                positions[index * 3 + 1] = vertex.y();
                positions[index * 3 + 2] = vertex.z();
                minX = Math.min(minX, vertex.x());
                minY = Math.min(minY, vertex.y());
                minZ = Math.min(minZ, vertex.z());
                maxX = Math.max(maxX, vertex.x());
                maxY = Math.max(maxY, vertex.y());
                maxZ = Math.max(maxZ, vertex.z());
            }
            for (int index = 0; index < actualIndexCount; index++) {
                triangleIndices[index] = indices.get(index);
            }
            meshes.add(new MeshSnapshot(space, positions, triangleIndices,
                    minX, minY, minZ, maxX, maxY, maxZ));
            LOGGER.info("Spatial triangle mesh loaded: {} vertices, {} indices, local bounds [{}, {}, {}] to [{}, {}, {}]",
                    actualVertexCount, actualIndexCount, minX, minY, minZ, maxX, maxY, maxZ);
        } finally {
            vertices.free();
            MemoryUtil.memFree(indices);
        }
    }

    List<MeshSnapshot> meshes() {
        return List.copyOf(meshes);
    }

    boolean updateControllerProximity(Vector3f leftPosition, boolean leftActive,
                                      Vector3f rightPosition, boolean rightActive,
                                      XrSpace appSpace, long predictedDisplayTime, MemoryStack stack) {
        boolean leftNear = leftActive && isPointNearMesh(leftPosition, appSpace, predictedDisplayTime, stack);
        boolean rightNear = rightActive && isPointNearMesh(rightPosition, appSpace, predictedDisplayTime, stack);
        if (leftNear != leftControllerNearMesh) {
            LOGGER.info("Left controller {} scanned spatial mesh bounds",
                    leftNear ? "entered" : "left");
        }
        if (rightNear != rightControllerNearMesh) {
            LOGGER.info("Right controller {} scanned spatial mesh bounds",
                    rightNear ? "entered" : "left");
        }
        leftControllerNearMesh = leftNear;
        rightControllerNearMesh = rightNear;
        return leftNear || rightNear;
    }

    private boolean isPointNearMesh(Vector3f pointInAppSpace, XrSpace appSpace,
                                    long predictedDisplayTime, MemoryStack stack) {
        for (MeshSnapshot mesh : meshes) {
            XrSpaceLocation location = XrSpaceLocation.calloc(stack).type$Default();
            int result = XR10.xrLocateSpace(
                    mesh.space(), appSpace, predictedDisplayTime, location);
            long requiredFlags = XR10.XR_SPACE_LOCATION_POSITION_VALID_BIT
                    | XR10.XR_SPACE_LOCATION_ORIENTATION_VALID_BIT;
            if (result != XR_SUCCESS || (location.locationFlags() & requiredFlags) != requiredFlags) {
                continue;
            }
            var pose = location.pose();
            var position = pose.position$();
            var orientation = pose.orientation();
            Quaternionf inverseOrientation = new Quaternionf(
                    orientation.x(), orientation.y(), orientation.z(), orientation.w()).conjugate();
            Vector3f localPoint = new Vector3f(pointInAppSpace)
                    .sub(position.x(), position.y(), position.z())
                    .rotate(inverseOrientation);
            float margin = 0.08f;
            if (localPoint.x >= mesh.minX() - margin && localPoint.x <= mesh.maxX() + margin
                    && localPoint.y >= mesh.minY() - margin && localPoint.y <= mesh.maxY() + margin
                    && localPoint.z >= mesh.minZ() - margin && localPoint.z <= mesh.maxZ() + margin) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
        for (XrSpace space : ownedSpaces) {
            int result = XR10.xrDestroySpace(space);
            if (result != XR_SUCCESS) {
                LOGGER.warn("Could not destroy spatial entity space {}: {}", space.address(), result);
            }
        }
        ownedSpaces.clear();
        pendingMeshRequests.clear();
        meshes.clear();
    }

    private static void check(int result, String operation) {
        if (result < XR_SUCCESS) {
            throw new IllegalStateException(operation + " failed with OpenXR result code " + result);
        }
    }

    record MeshSnapshot(XrSpace space, float[] positions, int[] indices,
                        float minX, float minY, float minZ,
                        float maxX, float maxY, float maxZ) {
    }
}
