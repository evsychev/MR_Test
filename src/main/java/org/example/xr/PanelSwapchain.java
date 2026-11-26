package org.example.xr;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.XR10;
import org.lwjgl.openxr.XrInstance;
import org.lwjgl.openxr.XrSession;
import org.lwjgl.openxr.XrSwapchain;
import org.lwjgl.openxr.XrSwapchainCreateInfo;
import org.lwjgl.openxr.XrSwapchainImageVulkanKHR;
import org.lwjgl.openxr.XrViewConfigurationView;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.example.xr.OpenXrSupport.check;
import static org.lwjgl.openxr.XR10.*;

record PanelSwapchain(XrSwapchain handle, XrSwapchainImageVulkanKHR.Buffer images,
                      int viewCount, int width, int height, long format) {
    private static final Logger LOGGER = LoggerFactory.getLogger(PanelSwapchain.class);

    static PanelSwapchain create(XrInstance instance, XrSession session, long systemId,
                                 MemoryStack stack, SessionResources resources) {
        IntBuffer viewCountBuffer = stack.ints(0);
        check(xrEnumerateViewConfigurationViews(instance, systemId,
                XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, viewCountBuffer, null), "count stereo views");
        int viewCount = viewCountBuffer.get(0);
        var configViews = XrViewConfigurationView.calloc(viewCount, stack);
        configViews.forEach(XrViewConfigurationView::type$Default);
        check(xrEnumerateViewConfigurationViews(instance, systemId,
                XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, viewCountBuffer, configViews), "read stereo views");

        IntBuffer formatCount = stack.ints(0);
        check(xrEnumerateSwapchainFormats(session, formatCount, null), "count swapchain formats");
        LongBuffer formats = stack.mallocLong(formatCount.get(0));
        check(xrEnumerateSwapchainFormats(session, formatCount, formats), "read swapchain formats");
        long swapchainFormat = formats.get(0);
        for (int index = 0; index < formats.capacity(); index++) {
            if (formats.get(index) == VK10.VK_FORMAT_R8G8B8A8_SRGB
                    || formats.get(index) == VK10.VK_FORMAT_B8G8R8A8_SRGB) {
                swapchainFormat = formats.get(index);
                break;
            }
        }
        int width = 0;
        int height = 0;
        for (int index = 0; index < viewCount; index++) {
            width = Math.max(width, configViews.get(index).recommendedImageRectWidth());
            height = Math.max(height, configViews.get(index).recommendedImageRectHeight());
        }
        var swapchainInfo = XrSwapchainCreateInfo.calloc(stack)
                .type$Default().usageFlags(XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT)
                .format(swapchainFormat).sampleCount(1).width(width).height(height)
                .faceCount(1).arraySize(viewCount).mipCount(1);
        PointerBuffer swapchainHandle = stack.mallocPointer(1);
        check(xrCreateSwapchain(session, swapchainInfo, swapchainHandle), "create stereo swapchain");
        var swapchain = new XrSwapchain(swapchainHandle.get(0), session);
        resources.defer(() -> check(xrDestroySwapchain(swapchain), "destroy swapchain"));
        IntBuffer swapchainImageCount = stack.ints(0);
        check(xrEnumerateSwapchainImages(swapchain, swapchainImageCount, null), "count Vulkan swapchain images");
        XrSwapchainImageVulkanKHR.Buffer swapchainImages = XrSwapchainImageVulkanKHR.calloc(swapchainImageCount.get(0), stack);
        swapchainImages.forEach(XrSwapchainImageVulkanKHR::type$Default);
        check(XR10.nxrEnumerateSwapchainImages(swapchain,
                swapchainImageCount.get(0), MemoryUtil.memAddress(swapchainImageCount),
                swapchainImages.address()), "read Vulkan swapchain images");
        LOGGER.info("XR swapchain ready: format={}, size={}x{}, views={}, images={}",
                swapchainFormat, width, height, viewCount, swapchainImageCount.get(0));
        return new PanelSwapchain(swapchain, swapchainImages, viewCount, width, height, swapchainFormat);
    }
}
