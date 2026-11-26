package org.example.xr;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Set;

import org.example.profile.UserProfile;
import org.example.render.VulkanPanelRenderer;
import org.example.state.ProfileStateStore;
import org.example.ui.AnchoredWindow;
import org.example.ui.TechnicalObjectTreeUI;

import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.XrEventDataBuffer;
import org.lwjgl.openxr.XrEventDataSessionStateChanged;
import org.lwjgl.openxr.XrGraphicsBindingVulkan2KHR;
import org.lwjgl.openxr.XrGraphicsBindingVulkanKHR;
import org.lwjgl.openxr.XrInstance;
import org.lwjgl.openxr.XrPosef;
import org.lwjgl.openxr.XrQuaternionf;
import org.lwjgl.openxr.XrReferenceSpaceCreateInfo;
import org.lwjgl.openxr.XrSession;
import org.lwjgl.openxr.XrSessionBeginInfo;
import org.lwjgl.openxr.XrSessionCreateInfo;
import org.lwjgl.openxr.XrSpace;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.example.xr.OpenXrSupport.*;
import static org.lwjgl.openxr.XR10.*;

final class OpenXrSessionRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenXrSessionRuntime.class);

    private OpenXrSessionRuntime() {
    }

    static void runSessionLifecycle(XrInstance instance, long systemId, Set<String> extensions,
                                            boolean vulkanEnable2Available, boolean vulkanEnableAvailable,
                                            boolean spatialMeshAvailable) {
        ByteBuffer stackMemory = MemoryUtil.memAlloc(1024 * 1024);
        try {
            try (MemoryStack stack = MemoryStack.create(stackMemory).push();
                 SessionResources resources = new SessionResources()) {
                int environmentBlendMode = selectEnvironmentBlendMode(
                        instance, systemId, extensions.contains("XR_FB_passthrough"), stack);
                var graphics = XrVulkanDevice.create(instance, systemId, vulkanEnable2Available,
                        vulkanEnableAvailable, stack, resources);
                XrGraphicsBindingVulkan2KHR binding2 = null;
                XrGraphicsBindingVulkanKHR binding = null;
                if (vulkanEnable2Available) {
                    binding2 = XrGraphicsBindingVulkan2KHR.calloc(stack)
                            .type$Default().instance(graphics.instance()).physicalDevice(graphics.physicalDevice()).device(graphics.device())
                            .queueFamilyIndex(graphics.queueFamily()).queueIndex(0);
                } else {
                    binding = XrGraphicsBindingVulkanKHR.calloc(stack)
                            .type$Default().instance(graphics.instance()).physicalDevice(graphics.physicalDevice()).device(graphics.device())
                            .queueFamilyIndex(graphics.queueFamily()).queueIndex(0);
                }
                XrSessionCreateInfo createInfo = XrSessionCreateInfo.calloc(stack)
                        .type$Default()
                        .systemId(systemId);
                createInfo.next(vulkanEnable2Available ? binding2 : binding);
                PointerBuffer sessionHandle = stack.mallocPointer(1);
                check(xrCreateSession(instance, createInfo, sessionHandle), "create OpenXR session");
                var session = new XrSession(sessionHandle.get(0), instance);
                resources.defer(() -> check(xrDestroySession(session), "destroy OpenXR session"));
                SpatialInputHandler inputHandler = new SpatialInputHandler(instance, session);
                resources.defer(inputHandler::close);
                PointerBuffer spaceHandle = stack.mallocPointer(1);
                var spaceInfo = XrReferenceSpaceCreateInfo.calloc(stack)
                        .type$Default().referenceSpaceType(XR_REFERENCE_SPACE_TYPE_LOCAL)
                        .poseInReferenceSpace(XrPosef.calloc(stack).orientation(
                                XrQuaternionf.calloc(stack).w(1.0f)));
                check(xrCreateReferenceSpace(session, spaceInfo, spaceHandle), "create local reference space");
                var appSpace = new XrSpace(spaceHandle.get(0), session);
                resources.defer(() -> check(xrDestroySpace(appSpace), "destroy reference space"));

                var panel = PanelSwapchain.create(instance, session, systemId, stack, resources);
                var passthroughLayer = extensions.contains("XR_FB_passthrough")
                        ? FbPassthrough.start(session, stack, resources) : null;
                VulkanPanelRenderer renderer = new VulkanPanelRenderer(graphics.physicalDevice(), graphics.device(), graphics.queue(),
                        graphics.commandPool(), panel.images(), panel.viewCount(), panel.width(), panel.height(), panel.format());
                resources.defer(renderer::close);
                LOGGER.info("XR Vulkan panel renderer initialized");
                AnchoredWindow anchoredWindow = new AnchoredWindow();
                var profile = UserProfile.loadMock();
                var store = ProfileStateStore.open(profile.id(), Path.of(System.getProperty("user.home"), ".mr-test", "state.db"));
                resources.defer(store::close);
                TechnicalObjectTreeUI treeUi = new TechnicalObjectTreeUI(panel.width(), panel.height(), anchoredWindow, profile, store);
                resources.defer(treeUi::close);

                boolean running = false;
                boolean exiting = false;
                SpatialMeshService spatialMeshService = spatialMeshAvailable
                        ? new SpatialMeshService(session) : null;
                if (spatialMeshService != null) resources.defer(spatialMeshService::close);
                try {
                    XrEventDataBuffer event = XrEventDataBuffer.calloc(stack);
                    while (!exiting) {
                        event.type(XR_TYPE_EVENT_DATA_BUFFER).next(0L);
                        int result = xrPollEvent(instance, event);
                        while (result != XR_EVENT_UNAVAILABLE) {
                            check(result, "poll OpenXR events");
                            if (event.type() == XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED) {
                                XrEventDataSessionStateChanged changed = XrEventDataSessionStateChanged.create(event.address());
                                int state = changed.state();
                                LOGGER.info("OpenXR session state: {}", state);
                                if (state == XR_SESSION_STATE_READY && !running) {
                                    check(xrBeginSession(session, XrSessionBeginInfo.calloc(stack).type$Default()
                                                    .primaryViewConfigurationType(XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO)),
                                            "begin OpenXR session");
                                    running = true;
                                    LOGGER.info("XR session begun; frame submission is active");
                                    if (spatialMeshService != null) {
                                        spatialMeshService.startQuery(stack);
                                    }
                                } else if (state == XR_SESSION_STATE_STOPPING && running) {
                                    check(xrEndSession(session), "end OpenXR session");
                                    running = false;
                                } else if (state == XR_SESSION_STATE_EXITING || state == XR_SESSION_STATE_LOSS_PENDING) {
                                    exiting = true;
                                }
                            }
                            if (spatialMeshService != null) {
                                spatialMeshService.processEvent(event);
                            }
                            event.type(XR_TYPE_EVENT_DATA_BUFFER).next(0L);
                            result = xrPollEvent(instance, event);
                        }
                        if (running) {
                            OpenXrFrameLoop.runFrame(session, appSpace, panel,
                                    renderer, passthroughLayer, inputHandler, anchoredWindow, treeUi,
                                    environmentBlendMode);
                        } else {
                            Thread.sleep(10);
                        }
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    LOGGER.info("OpenXR frame loop interrupted");
                } finally {
                    if (running) {
                        xrEndSession(session);
                    }
                }
            }
        } finally {
            MemoryUtil.memFree(stackMemory);
        }
    }
}
