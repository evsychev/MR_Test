package org.example;

import java.nio.LongBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.XrAction;
import org.lwjgl.openxr.XrActionCreateInfo;
import org.lwjgl.openxr.XrActionSet;
import org.lwjgl.openxr.XrActionSetCreateInfo;
import org.lwjgl.openxr.XrActionSpaceCreateInfo;
import org.lwjgl.openxr.XrActionStateBoolean;
import org.lwjgl.openxr.XrActionStateGetInfo;
import org.lwjgl.openxr.XrActionStatePose;
import org.lwjgl.openxr.XrActionsSyncInfo;
import org.lwjgl.openxr.XrActiveActionSet;
import org.lwjgl.openxr.XrInteractionProfileSuggestedBinding;
import org.lwjgl.openxr.XrSessionActionSetsAttachInfo;
import org.lwjgl.openxr.XrSpace;
import org.lwjgl.openxr.XrSpaceLocation;
import org.lwjgl.system.MemoryStack;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.openxr.XR10.*;

final class SpatialInputHandler implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpatialInputHandler.class);

    private final org.lwjgl.openxr.XrInstance instance;
    private final org.lwjgl.openxr.XrSession session;
    private final XrActionSet actionSet;
    private final XrAction selectAction;
    private final XrAction poseAction;
    private final long leftHandPath;
    private final long rightHandPath;
    private final long leftSelectPath;
    private final long rightSelectPath;
    private final long leftPosePath;
    private final long rightPosePath;
    private final XrSpace leftPoseSpace;
    private final XrSpace rightPoseSpace;
    private boolean leftSelectPressed;
    private boolean rightSelectPressed;
    private boolean leftPoseWasActive;
    private boolean rightPoseWasActive;
    private boolean leftPoseValid;
    private boolean rightPoseValid;
    private final Vector3f leftPosition = new Vector3f();
    private final Vector3f rightPosition = new Vector3f();

    SpatialInputHandler(org.lwjgl.openxr.XrInstance instance, org.lwjgl.openxr.XrSession session) {
        this.instance = instance;
        this.session = session;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            leftHandPath = path(stack, "/user/hand/left");
            rightHandPath = path(stack, "/user/hand/right");
            leftSelectPath = path(stack, "/user/hand/left/input/select/click");
            rightSelectPath = path(stack, "/user/hand/right/input/select/click");
            leftPosePath = path(stack, "/user/hand/left/input/grip/pose");
            rightPosePath = path(stack, "/user/hand/right/input/grip/pose");
            long simpleControllerProfile = path(stack, "/interaction_profiles/khr/simple_controller");

            PointerBuffer actionSetHandle = stack.mallocPointer(1);
            XrActionSetCreateInfo actionSetInfo = XrActionSetCreateInfo.calloc(stack)
                    .type$Default()
                    .actionSetName(stack.UTF8("mr_actions"))
                    .localizedActionSetName(stack.UTF8("MR Actions"))
                    .priority(0);
            check(xrCreateActionSet(instance, actionSetInfo, actionSetHandle), "create XR action set");
            actionSet = new XrActionSet(actionSetHandle.get(0), instance);

            LongBuffer handPaths = stack.longs(leftHandPath, rightHandPath);
            selectAction = createAction(stack, "select", "Select", XR_ACTION_TYPE_BOOLEAN_INPUT, handPaths);
            poseAction = createAction(stack, "grip_pose", "Grip Pose", XR_ACTION_TYPE_POSE_INPUT, handPaths);

            var bindings = org.lwjgl.openxr.XrActionSuggestedBinding.calloc(4, stack);
            bindings.get(0).set(selectAction, leftSelectPath);
            bindings.get(1).set(selectAction, rightSelectPath);
            bindings.get(2).set(poseAction, leftPosePath);
            bindings.get(3).set(poseAction, rightPosePath);
            XrInteractionProfileSuggestedBinding bindingInfo =
                    XrInteractionProfileSuggestedBinding.calloc(stack)
                            .type$Default()
                            .interactionProfile(simpleControllerProfile)
                            .suggestedBindings(bindings);
            int bindingResult = xrSuggestInteractionProfileBindings(instance, bindingInfo);
            if (bindingResult != XR_SUCCESS) {
                LOGGER.warn("The runtime did not accept simple-controller bindings: {}", bindingResult);
            }

            XrSessionActionSetsAttachInfo attachInfo = XrSessionActionSetsAttachInfo.calloc(stack)
                    .type$Default()
                    .actionSets(stack.pointers(actionSet.address()));
            check(xrAttachSessionActionSets(session, attachInfo), "attach XR action set");

            leftPoseSpace = createPoseSpace(stack, leftHandPath);
            rightPoseSpace = createPoseSpace(stack, rightHandPath);
        }
        LOGGER.info("Spatial input actions attached (left/right select and grip pose)");
    }

    void update(long predictedDisplayTime, XrSpace appSpace, MemoryStack stack) {
        XrActiveActionSet.Buffer activeActionSets = XrActiveActionSet.calloc(1, stack)
                .actionSet(actionSet).subactionPath(XR_NULL_PATH);
        XrActionsSyncInfo syncInfo = XrActionsSyncInfo.calloc(stack)
                .type$Default().countActiveActionSets(1).activeActionSets(activeActionSets);
        int syncResult = xrSyncActions(session, syncInfo);
        if (syncResult == XR_SESSION_NOT_FOCUSED) {
            leftSelectPressed = false;
            rightSelectPressed = false;
            leftPoseValid = false;
            rightPoseValid = false;
            return;
        }
        check(syncResult, "synchronize XR actions");

        leftSelectPressed = updateHand("left", leftHandPath, leftPoseSpace, leftSelectPressed,
                predictedDisplayTime, appSpace, stack);
        rightSelectPressed = updateHand("right", rightHandPath, rightPoseSpace, rightSelectPressed,
                predictedDisplayTime, appSpace, stack);
    }

    boolean isSelectPressed() {
        return leftSelectPressed || rightSelectPressed;
    }

    boolean leftPoseValid() {
        return leftPoseValid;
    }

    boolean rightPoseValid() {
        return rightPoseValid;
    }

    Vector3f leftPosition() {
        return leftPosition;
    }

    Vector3f rightPosition() {
        return rightPosition;
    }

    private boolean updateHand(String handName, long handPath, XrSpace poseSpace, boolean wasPressed,
                               long predictedDisplayTime, XrSpace appSpace, MemoryStack stack) {
        XrActionStateGetInfo selectInfo = XrActionStateGetInfo.calloc(stack)
                .type$Default().action(selectAction).subactionPath(handPath);
        XrActionStateBoolean selectState = XrActionStateBoolean.calloc(stack).type$Default();
        check(xrGetActionStateBoolean(session, selectInfo, selectState), "read " + handName + " select action");
        boolean pressed = selectState.isActive() && selectState.currentState();
        if (pressed && !wasPressed) {
            LOGGER.info("{} controller select pressed", capitalize(handName));
        } else if (!pressed && wasPressed) {
            LOGGER.info("{} controller select released", capitalize(handName));
        }

        XrActionStateGetInfo poseInfo = XrActionStateGetInfo.calloc(stack)
                .type$Default().action(poseAction).subactionPath(handPath);
        XrActionStatePose poseState = XrActionStatePose.calloc(stack).type$Default();
        check(xrGetActionStatePose(session, poseInfo, poseState), "read " + handName + " grip pose action");
        boolean poseActive = poseState.isActive();
        if (handName.equals("left")) {
            leftPoseValid = false;
        } else {
            rightPoseValid = false;
        }
        if (poseActive) {
            XrSpaceLocation location = XrSpaceLocation.calloc(stack).type$Default();
            int result = xrLocateSpace(poseSpace, appSpace, predictedDisplayTime, location);
            long requiredFlags = XR_SPACE_LOCATION_POSITION_VALID_BIT | XR_SPACE_LOCATION_ORIENTATION_VALID_BIT;
            if (result == XR_SUCCESS && (location.locationFlags() & requiredFlags) == requiredFlags) {
                var position = location.pose().position$();
                if (handName.equals("left")) {
                    leftPosition.set(position.x(), position.y(), position.z());
                    leftPoseValid = true;
                } else {
                    rightPosition.set(position.x(), position.y(), position.z());
                    rightPoseValid = true;
                }
                if (pressed) {
                    LOGGER.debug("{} grip at ({}, {}, {})", handName,
                            position.x(), position.y(), position.z());
                }
            }
        } else if ((handName.equals("left") && leftPoseWasActive)
                || (handName.equals("right") && rightPoseWasActive)) {
            LOGGER.debug("{} controller pose is no longer active", capitalize(handName));
        }
        if (handName.equals("left")) {
            leftPoseWasActive = poseActive;
        } else {
            rightPoseWasActive = poseActive;
        }
        return pressed;
    }

    private XrAction createAction(MemoryStack stack, String name, String localizedName,
                                  int actionType, LongBuffer subactionPaths) {
        XrActionCreateInfo actionInfo = XrActionCreateInfo.calloc(stack)
                .type$Default()
                .actionName(stack.UTF8(name))
                .actionType(actionType)
                .countSubactionPaths(subactionPaths.remaining())
                .subactionPaths(subactionPaths)
                .localizedActionName(stack.UTF8(localizedName));
        PointerBuffer actionHandle = stack.mallocPointer(1);
        check(xrCreateAction(actionSet, actionInfo, actionHandle), "create " + name + " XR action");
        return new XrAction(actionHandle.get(0), actionSet);
    }

    private XrSpace createPoseSpace(MemoryStack stack, long handPath) {
        var pose = org.lwjgl.openxr.XrPosef.calloc(stack)
                .orientation(org.lwjgl.openxr.XrQuaternionf.calloc(stack).w(1.0f));
        XrActionSpaceCreateInfo spaceInfo = XrActionSpaceCreateInfo.calloc(stack)
                .type$Default().action(poseAction).subactionPath(handPath).poseInActionSpace(pose);
        PointerBuffer spaceHandle = stack.mallocPointer(1);
        check(xrCreateActionSpace(session, spaceInfo, spaceHandle), "create controller action space");
        return new XrSpace(spaceHandle.get(0), session);
    }

    private long path(MemoryStack stack, String pathString) {
        LongBuffer path = stack.mallocLong(1);
        check(xrStringToPath(instance, stack.UTF8(pathString), path), "resolve XR path " + pathString);
        return path.get(0);
    }

    @Override
    public void close() {
        int result = xrDestroySpace(leftPoseSpace);
        if (result != XR_SUCCESS) LOGGER.warn("Could not destroy left action space: {}", result);
        result = xrDestroySpace(rightPoseSpace);
        if (result != XR_SUCCESS) LOGGER.warn("Could not destroy right action space: {}", result);
        result = xrDestroyAction(poseAction);
        if (result != XR_SUCCESS) LOGGER.warn("Could not destroy grip pose action: {}", result);
        result = xrDestroyAction(selectAction);
        if (result != XR_SUCCESS) LOGGER.warn("Could not destroy select action: {}", result);
        result = xrDestroyActionSet(actionSet);
        if (result != XR_SUCCESS) LOGGER.warn("Could not destroy XR action set: {}", result);
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static void check(int result, String operation) {
        if (result < XR_SUCCESS) {
            throw new IllegalStateException(operation + " failed with OpenXR result code " + result);
        }
    }
}
