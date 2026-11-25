package org.example;

import java.nio.IntBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.XrFrameWaitInfo;
import org.lwjgl.openxr.XrFrameState;
import org.lwjgl.openxr.XrFrameBeginInfo;
import org.lwjgl.openxr.XrFrameEndInfo;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.openxr.XrSwapchainImageVulkanKHR;

import static org.lwjgl.openxr.XR10.*;

import static org.example.OpenXrSupport.*;
final class OpenXrFrameLoop {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenXrFrameLoop.class);
    private static long frameCounter;

    private OpenXrFrameLoop() {
    }

    static void runFrame(org.lwjgl.openxr.XrSession session, org.lwjgl.openxr.XrSpace appSpace,
                                 org.lwjgl.openxr.XrSwapchain swapchain, XrSwapchainImageVulkanKHR.Buffer swapchainImages,
                                 int viewCount, int width, int height, VulkanPanelRenderer renderer,
                                 org.lwjgl.openxr.XrPassthroughLayerFB passthroughLayer,
                                 SpatialInputHandler inputHandler, AnchoredWindow anchoredWindow,
                                 TechnicalObjectTreeUI treeUi,
                                 int environmentBlendMode) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long currentFrame = ++frameCounter;
            XrFrameState frameState = XrFrameState.calloc(stack).type$Default();
            check(xrWaitFrame(session, XrFrameWaitInfo.calloc(stack).type$Default(), frameState), "wait for frame");
            check(xrBeginFrame(session, XrFrameBeginInfo.calloc(stack).type$Default()), "begin frame");
            inputHandler.update(frameState.predictedDisplayTime(), appSpace, stack);
            PointerBuffer layers = stack.mallocPointer(2);
            boolean rendered = false;
            int locatedViewCount = 0;
            int acquiredImageIndex = -1;
            if (frameState.shouldRender()) {
                var locateInfo = org.lwjgl.openxr.XrViewLocateInfo.calloc(stack)
                        .type$Default().viewConfigurationType(XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO)
                        .displayTime(frameState.predictedDisplayTime()).space(appSpace);
                var viewState = org.lwjgl.openxr.XrViewState.calloc(stack).type$Default();
                var views = org.lwjgl.openxr.XrView.calloc(viewCount, stack);
                views.forEach(org.lwjgl.openxr.XrView::type$Default);
                IntBuffer locatedCount = stack.ints(0);
                check(xrLocateViews(session, locateInfo, viewState, locatedCount, views), "locate stereo views");
                locatedViewCount = locatedCount.get(0);
                anchoredWindow.updateFromViews(views);
                treeUi.updateInteraction(anchoredWindow,
                        inputHandler.leftSelectPressed(), inputHandler.leftPosition(),
                        inputHandler.leftOrientation(), inputHandler.leftPoseValid(),
                        inputHandler.rightSelectPressed(), inputHandler.rightPosition(),
                        inputHandler.rightOrientation(), inputHandler.rightPoseValid());

                if (!anchoredWindow.closed()) {
                    IntBuffer imageIndex = stack.ints(0);
                    check(xrAcquireSwapchainImage(swapchain,
                            org.lwjgl.openxr.XrSwapchainImageAcquireInfo.calloc(stack).type$Default(), imageIndex),
                            "acquire swapchain image");
                    check(xrWaitSwapchainImage(swapchain,
                            org.lwjgl.openxr.XrSwapchainImageWaitInfo.calloc(stack).type$Default()
                                    .timeout(XR_INFINITE_DURATION)), "wait for swapchain image");
                    acquiredImageIndex = imageIndex.get(0);
                    renderer.render(acquiredImageIndex, treeUi, anchoredWindow);
                    rendered = true;
                    check(xrReleaseSwapchainImage(swapchain,
                            org.lwjgl.openxr.XrSwapchainImageReleaseInfo.calloc(stack).type$Default()),
                            "release swapchain image");
                }

                if (passthroughLayer != null) {
                    var passthroughComposition = org.lwjgl.openxr.XrCompositionLayerPassthroughFB.calloc(stack)
                            .type$Default().layerHandle(passthroughLayer)
                            .flags(XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT);
                    layers.put(passthroughComposition.address());
                }
                if (!anchoredWindow.closed()) {
                    var quadSubImage = org.lwjgl.openxr.XrSwapchainSubImage.calloc(stack)
                            .swapchain(swapchain).imageArrayIndex(0)
                            .imageRect(org.lwjgl.openxr.XrRect2Di.calloc(stack)
                                    .offset(org.lwjgl.openxr.XrOffset2Di.calloc(stack).set(0, 0))
                                    .extent(org.lwjgl.openxr.XrExtent2Di.calloc(stack).set(width, height)));
                    var centerPosition = anchoredWindow.quadCenterPosition();
                    var orientation = anchoredWindow.anchorOrientation();
                    var quadPose = org.lwjgl.openxr.XrPosef.calloc(stack)
                            .orientation(org.lwjgl.openxr.XrQuaternionf.calloc(stack)
                                    .x(orientation.x).y(orientation.y).z(orientation.z).w(orientation.w))
                            .position$(org.lwjgl.openxr.XrVector3f.calloc(stack)
                                    .x(centerPosition.x).y(centerPosition.y).z(centerPosition.z));
                    var quadLayer = org.lwjgl.openxr.XrCompositionLayerQuad.calloc(stack)
                            .type$Default()
                            .layerFlags(XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT)
                            .space(appSpace)
                            .eyeVisibility(org.lwjgl.openxr.XR10.XR_EYE_VISIBILITY_BOTH)
                            .subImage(quadSubImage)
                            .pose(quadPose)
                            .size(org.lwjgl.openxr.XrExtent2Df.calloc(stack)
                                    .width(anchoredWindow.widthMeters()).height(anchoredWindow.heightMeters()));
                    layers.put(quadLayer.address());
                }
            }
            layers.flip();
            int submittedLayerCount = layers.remaining();
            int endFrameResult = xrEndFrame(session, XrFrameEndInfo.calloc(stack).type$Default()
                    .displayTime(frameState.predictedDisplayTime()).environmentBlendMode(environmentBlendMode)
                    .layers(layers).layerCount(submittedLayerCount));
            check(endFrameResult, "end frame");
            if (currentFrame <= 5 || currentFrame % 300 == 0) {
                var anchor = anchoredWindow.anchorPosition();
                LOGGER.info("XR frame {} submitted: shouldRender={}, rendered={}, views={}, imageIndex={}, " +
                                "layers={}, passthrough={}, blendMode={}, anchor=({}, {}, {})",
                        currentFrame, frameState.shouldRender(), rendered, locatedViewCount, acquiredImageIndex,
                        submittedLayerCount, passthroughLayer != null, environmentBlendMode,
                        anchor.x, anchor.y, anchor.z);
            }
        }
    }

}
