package org.example.xr;

import java.nio.IntBuffer;

import org.example.render.VulkanPanelRenderer;
import org.example.ui.AnchoredWindow;
import org.example.ui.TechnicalObjectTreeUI;

import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.XR10;
import org.lwjgl.openxr.XrCompositionLayerPassthroughFB;
import org.lwjgl.openxr.XrCompositionLayerQuad;
import org.lwjgl.openxr.XrExtent2Df;
import org.lwjgl.openxr.XrExtent2Di;
import org.lwjgl.openxr.XrFrameBeginInfo;
import org.lwjgl.openxr.XrFrameEndInfo;
import org.lwjgl.openxr.XrFrameState;
import org.lwjgl.openxr.XrFrameWaitInfo;
import org.lwjgl.openxr.XrOffset2Di;
import org.lwjgl.openxr.XrPassthroughLayerFB;
import org.lwjgl.openxr.XrPosef;
import org.lwjgl.openxr.XrQuaternionf;
import org.lwjgl.openxr.XrRect2Di;
import org.lwjgl.openxr.XrSession;
import org.lwjgl.openxr.XrSpace;
import org.lwjgl.openxr.XrSwapchainImageAcquireInfo;
import org.lwjgl.openxr.XrSwapchainImageReleaseInfo;
import org.lwjgl.openxr.XrSwapchainImageWaitInfo;
import org.lwjgl.openxr.XrSwapchainSubImage;
import org.lwjgl.openxr.XrVector3f;
import org.lwjgl.openxr.XrView;
import org.lwjgl.openxr.XrViewLocateInfo;
import org.lwjgl.openxr.XrViewState;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.example.xr.OpenXrSupport.*;
import static org.lwjgl.openxr.XR10.*;

final class OpenXrFrameLoop {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenXrFrameLoop.class);
    private static long frameCounter;

    private OpenXrFrameLoop() {
    }

    static void runFrame(XrSession session, XrSpace appSpace,
                         PanelSwapchain panel,
                         VulkanPanelRenderer renderer,
                         XrPassthroughLayerFB passthroughLayer,
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
                var locateInfo = XrViewLocateInfo.calloc(stack)
                        .type$Default().viewConfigurationType(XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO)
                        .displayTime(frameState.predictedDisplayTime()).space(appSpace);
                var viewState = XrViewState.calloc(stack).type$Default();
                var views = XrView.calloc(panel.viewCount(), stack);
                views.forEach(XrView::type$Default);
                IntBuffer locatedCount = stack.ints(0);
                check(xrLocateViews(session, locateInfo, viewState, locatedCount, views), "locate stereo views");
                locatedViewCount = locatedCount.get(0);
                views.limit(locatedViewCount);
                anchoredWindow.updateFromViews(views);
                treeUi.updateInteraction(
                        inputHandler.leftSelectPressed(), inputHandler.leftPosition(),
                        inputHandler.leftOrientation(), inputHandler.leftPoseValid(),
                        inputHandler.rightSelectPressed(), inputHandler.rightPosition(),
                        inputHandler.rightOrientation(), inputHandler.rightPoseValid());

                if (!anchoredWindow.closed()) {
                    IntBuffer imageIndex = stack.ints(0);
                    check(xrAcquireSwapchainImage(panel.handle(),
                            XrSwapchainImageAcquireInfo.calloc(stack).type$Default(), imageIndex),
                            "acquire swapchain image");
                    check(xrWaitSwapchainImage(panel.handle(),
                            XrSwapchainImageWaitInfo.calloc(stack).type$Default()
                                    .timeout(XR_INFINITE_DURATION)), "wait for swapchain image");
                    acquiredImageIndex = imageIndex.get(0);
                    try {
                        treeUi.markWindowStateChanged(anchoredWindow.minimized());
                        renderer.render(acquiredImageIndex, treeUi.pollTextureUpdate());
                        rendered = true;
                    } finally {
                        check(xrReleaseSwapchainImage(panel.handle(),
                                XrSwapchainImageReleaseInfo.calloc(stack).type$Default()),
                                "release swapchain image");
                    }
                }

                if (passthroughLayer != null) {
                    var passthroughComposition = XrCompositionLayerPassthroughFB.calloc(stack)
                            .type$Default().layerHandle(passthroughLayer)
                            .flags(XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT);
                    layers.put(passthroughComposition.address());
                }
                if (!anchoredWindow.closed()) {
                    var quadSubImage = XrSwapchainSubImage.calloc(stack)
                            .swapchain(panel.handle()).imageArrayIndex(0)
                            .imageRect(XrRect2Di.calloc(stack)
                                    .offset(XrOffset2Di.calloc(stack).set(0, 0))
                                    .extent(XrExtent2Di.calloc(stack).set(panel.width(), panel.height())));
                    var centerPosition = anchoredWindow.quadCenterPosition();
                    var orientation = anchoredWindow.anchorOrientation();
                    var quadPose = XrPosef.calloc(stack)
                            .orientation(XrQuaternionf.calloc(stack)
                                    .x(orientation.x).y(orientation.y).z(orientation.z).w(orientation.w))
                            .position$(XrVector3f.calloc(stack)
                                    .x(centerPosition.x).y(centerPosition.y).z(centerPosition.z));
                    var quadLayer = XrCompositionLayerQuad.calloc(stack)
                            .type$Default()
                            .layerFlags(XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT)
                            .space(appSpace)
                            .eyeVisibility(XR10.XR_EYE_VISIBILITY_BOTH)
                            .subImage(quadSubImage)
                            .pose(quadPose)
                            .size(XrExtent2Df.calloc(stack)
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
