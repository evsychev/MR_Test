package org.example;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.XrSwapchainImageVulkanKHR;
import org.lwjgl.openxr.XrView;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkDevice;

final class VulkanPanelRenderer implements AutoCloseable {
    private static final int PUSH_CONSTANT_FLOATS = 24;

    private final VkDevice device;
    private final VkQueue queue;
    private final long commandPool;
    private final XrSwapchainImageVulkanKHR.Buffer swapchainImages;
    private final int viewCount;
    private final int width;
    private final int height;
    private final long renderPass;
    private final long pipelineLayout;
    private final long graphicsPipeline;
    private final long[] imageViews;
    private final long[] framebuffers;
    private final VkCommandBuffer commandBuffer;
    private final boolean transparentBackground;

    VulkanPanelRenderer(VkDevice device, VkQueue queue, long commandPool,
                        XrSwapchainImageVulkanKHR.Buffer swapchainImages,
                        int viewCount, int width, int height, long swapchainFormat,
                        boolean transparentBackground) {
        this.device = device;
        this.queue = queue;
        this.commandPool = commandPool;
        this.swapchainImages = swapchainImages;
        this.viewCount = viewCount;
        this.width = width;
        this.height = height;
        this.transparentBackground = transparentBackground;
        ByteBuffer stackMemory = MemoryUtil.memAlloc(256 * 1024);
        try {
            try (MemoryStack stack = MemoryStack.create(stackMemory).push()) {
                renderPass = createRenderPass(stack, (int) swapchainFormat);
                pipelineLayout = createPipelineLayout(stack);
                graphicsPipeline = createGraphicsPipeline(stack);
                imageViews = createImageViews(stack, (int) swapchainFormat);
                framebuffers = createFramebuffers(stack);
                PointerBuffer commandBufferHandle = stack.mallocPointer(1);
                VkCommandBufferAllocateInfo allocationInfo = VkCommandBufferAllocateInfo.calloc(stack)
                        .sType$Default().commandPool(commandPool)
                        .level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY).commandBufferCount(1);
                check(VK10.vkAllocateCommandBuffers(device, allocationInfo, commandBufferHandle),
                        "allocate panel command buffer");
                commandBuffer = new VkCommandBuffer(commandBufferHandle.get(0), device);
            }
        } finally {
            MemoryUtil.memFree(stackMemory);
        }
    }

    void render(int imageIndex, XrView.Buffer views, AnchoredWindow window) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            check(VK10.vkResetCommandBuffer(commandBuffer, 0), "reset panel command buffer");
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default().flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            check(VK10.vkBeginCommandBuffer(commandBuffer, beginInfo), "begin panel command buffer");

            for (int eyeIndex = 0; eyeIndex < viewCount; eyeIndex++) {
                transitionSwapchainImage(imageIndex, eyeIndex, stack);
                drawEye(imageIndex, eyeIndex, window, stack);
            }

            check(VK10.vkEndCommandBuffer(commandBuffer), "end panel command buffer");
            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack).sType$Default()
                    .pCommandBuffers(stack.pointers(commandBuffer.address()));
            check(VK10.vkQueueSubmit(queue, submitInfo, VK10.VK_NULL_HANDLE), "submit panel frame");
            check(VK10.vkQueueWaitIdle(queue), "wait for panel frame");
        }
    }

    private void transitionSwapchainImage(int imageIndex, int eyeIndex, MemoryStack stack) {
        long image = swapchainImages.get(imageIndex).image();
        VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack)
                .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1)
                .baseArrayLayer(eyeIndex).layerCount(1);
        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
                .sType$Default().srcAccessMask(0).dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .oldLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED).newLayout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .image(image).subresourceRange(range);
        VK10.vkCmdPipelineBarrier(commandBuffer,
                VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                0, null, null, barrier);
    }

    private void drawEye(int imageIndex, int eyeIndex, AnchoredWindow window, MemoryStack stack) {
        VkClearValue.Buffer clearValues = VkClearValue.calloc(1, stack);
        clearValues.get(0).color().float32(0, 0.0f).float32(1, 0.0f)
                .float32(2, 0.0f).float32(3, transparentBackground ? 0.0f : 1.0f);
        VkRenderPassBeginInfo renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType$Default().renderPass(renderPass)
                .framebuffer(framebuffers[imageIndex * viewCount + eyeIndex])
                .renderArea(VkRect2D.calloc(stack)
                        .offset(org.lwjgl.vulkan.VkOffset2D.calloc(stack).set(0, 0))
                        .extent(org.lwjgl.vulkan.VkExtent2D.calloc(stack).set(width, height)))
                .pClearValues(clearValues);
        VK10.vkCmdBeginRenderPass(commandBuffer, renderPassBeginInfo, VK10.VK_SUBPASS_CONTENTS_INLINE);
        VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
                graphicsPipeline);
        VK10.vkCmdSetViewport(commandBuffer, 0, VkViewport.calloc(1, stack)
                .x(0.0f).y(0.0f).width(width).height(height).minDepth(0.0f).maxDepth(1.0f));
        VK10.vkCmdSetScissor(commandBuffer, 0, VkRect2D.calloc(1, stack)
                .offset(org.lwjgl.vulkan.VkOffset2D.calloc(stack).set(0, 0))
                .extent(org.lwjgl.vulkan.VkExtent2D.calloc(stack).set(width, height)));
        FloatBuffer pushConstants = stack.mallocFloat(PUSH_CONSTANT_FLOATS);
        new Matrix4f().identity().get(pushConstants);
        if (window.buttonHovered()) {
            pushConstants.put(0.25f).put(0.95f).put(0.72f).put(1.0f);
        } else {
            pushConstants.put(0.12f).put(0.68f).put(1.0f).put(1.0f);
        }
        pushConstants.put(AnchoredWindow.WIDTH_METERS)
                .put(window.heightMeters())
                .put(window.minimized() ? 1.0f : 0.0f)
                .put(window.buttonHovered() ? 1.0f : 0.0f);
        pushConstants.flip();
        VK10.vkCmdPushConstants(commandBuffer, pipelineLayout,
                VK10.VK_SHADER_STAGE_VERTEX_BIT | VK10.VK_SHADER_STAGE_FRAGMENT_BIT,
                0, pushConstants);
        VK10.vkCmdDraw(commandBuffer, 6, 1, 0, 0);
        VK10.vkCmdEndRenderPass(commandBuffer);
    }

    private long createRenderPass(MemoryStack stack, int format) {
        VkAttachmentDescription.Buffer attachment = VkAttachmentDescription.calloc(1, stack)
                .format(format).samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .finalLayout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        VkAttachmentReference.Buffer colorReference = VkAttachmentReference.calloc(1, stack)
                .attachment(0).layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
                .pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(1).pColorAttachments(colorReference);
        VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack)
                .srcSubpass(VK10.VK_SUBPASS_EXTERNAL).dstSubpass(0)
                .srcStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .srcAccessMask(0).dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
        VkRenderPassCreateInfo createInfo = VkRenderPassCreateInfo.calloc(stack)
                .sType$Default().pAttachments(attachment).pSubpasses(subpass).pDependencies(dependency);
        LongBuffer handle = stack.mallocLong(1);
        check(VK10.vkCreateRenderPass(device, createInfo, null, handle), "create panel render pass");
        return handle.get(0);
    }

    private long createPipelineLayout(MemoryStack stack) {
        VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack)
                .stageFlags(VK10.VK_SHADER_STAGE_VERTEX_BIT | VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
                .offset(0).size(PUSH_CONSTANT_FLOATS * Float.BYTES);
        VkPipelineLayoutCreateInfo createInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType$Default().pPushConstantRanges(range);
        LongBuffer handle = stack.mallocLong(1);
        check(VK10.vkCreatePipelineLayout(device, createInfo, null, handle), "create panel pipeline layout");
        return handle.get(0);
    }

    private long createGraphicsPipeline(MemoryStack stack) {
        long vertexShader = createShaderModule(stack, "/shaders/mr-panel.vert.spv");
        long fragmentShader = createShaderModule(stack, "/shaders/mr-panel.frag.spv");
        try {
            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0).sType$Default().stage(VK10.VK_SHADER_STAGE_VERTEX_BIT)
                    .module(vertexShader).pName(stack.UTF8("main"));
            stages.get(1).sType$Default().stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragmentShader).pName(stack.UTF8("main"));
            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType$Default();
            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType$Default().topology(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST).primitiveRestartEnable(false);
            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType$Default().viewportCount(1).scissorCount(1);
            VkPipelineRasterizationStateCreateInfo rasterization = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType$Default().depthClampEnable(false).rasterizerDiscardEnable(false)
                    .polygonMode(VK10.VK_POLYGON_MODE_FILL).cullMode(VK10.VK_CULL_MODE_NONE)
                    .frontFace(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE).depthBiasEnable(false).lineWidth(1.0f);
            VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType$Default().rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT).sampleShadingEnable(false);
            VkPipelineColorBlendAttachmentState.Buffer blendAttachment =
                    VkPipelineColorBlendAttachmentState.calloc(1, stack)
                            .blendEnable(false)
                            .srcColorBlendFactor(VK10.VK_BLEND_FACTOR_SRC_ALPHA)
                            .dstColorBlendFactor(VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                            .colorBlendOp(VK10.VK_BLEND_OP_ADD)
                            .srcAlphaBlendFactor(VK10.VK_BLEND_FACTOR_ONE)
                            .dstAlphaBlendFactor(VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                            .alphaBlendOp(VK10.VK_BLEND_OP_ADD)
                            .colorWriteMask(VK10.VK_COLOR_COMPONENT_R_BIT | VK10.VK_COLOR_COMPONENT_G_BIT
                                    | VK10.VK_COLOR_COMPONENT_B_BIT | VK10.VK_COLOR_COMPONENT_A_BIT);
            VkPipelineColorBlendStateCreateInfo colorBlend = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType$Default().logicOpEnable(false).pAttachments(blendAttachment);
            IntBuffer dynamicStates = stack.ints(VK10.VK_DYNAMIC_STATE_VIEWPORT, VK10.VK_DYNAMIC_STATE_SCISSOR);
            VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType$Default().pDynamicStates(dynamicStates);
            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            pipelineInfo.get(0).sType$Default().pStages(stages).pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly).pViewportState(viewportState)
                    .pRasterizationState(rasterization).pMultisampleState(multisample)
                    .pColorBlendState(colorBlend).pDynamicState(dynamicState)
                    .layout(pipelineLayout)
                    .renderPass(renderPass).subpass(0)
                    .basePipelineHandle(VK10.VK_NULL_HANDLE).basePipelineIndex(-1);
            LongBuffer handle = stack.mallocLong(1);
            check(VK10.vkCreateGraphicsPipelines(device, VK10.VK_NULL_HANDLE, pipelineInfo, null, handle),
                    "create panel graphics pipeline");
            return handle.get(0);
        } finally {
            VK10.vkDestroyShaderModule(device, vertexShader, null);
            VK10.vkDestroyShaderModule(device, fragmentShader, null);
        }
    }

    private long createShaderModule(MemoryStack stack, String resourcePath) {
        byte[] bytes;
        try (var input = VulkanPanelRenderer.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Shader resource is missing: " + resourcePath);
            }
            bytes = input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read shader resource: " + resourcePath, exception);
        }
        ByteBuffer code = stack.malloc(bytes.length).put(bytes).flip();
        var createInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
        LongBuffer handle = stack.mallocLong(1);
        check(VK10.vkCreateShaderModule(device, createInfo, null, handle), "create shader module " + resourcePath);
        return handle.get(0);
    }

    private long[] createImageViews(MemoryStack stack, int format) {
        int imageCount = swapchainImages.capacity();
        long[] handles = new long[imageCount * viewCount];
        for (int imageIndex = 0; imageIndex < imageCount; imageIndex++) {
            for (int eyeIndex = 0; eyeIndex < viewCount; eyeIndex++) {
                VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack)
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1)
                        .baseArrayLayer(eyeIndex).layerCount(1);
                var createInfo = VkImageViewCreateInfo.calloc(stack).sType$Default()
                        .image(swapchainImages.get(imageIndex).image()).viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
                        .format(format)
                        .components(org.lwjgl.vulkan.VkComponentMapping.calloc(stack)
                                .r(VK10.VK_COMPONENT_SWIZZLE_IDENTITY).g(VK10.VK_COMPONENT_SWIZZLE_IDENTITY)
                                .b(VK10.VK_COMPONENT_SWIZZLE_IDENTITY).a(VK10.VK_COMPONENT_SWIZZLE_IDENTITY))
                        .subresourceRange(range);
                LongBuffer handle = stack.mallocLong(1);
                check(VK10.vkCreateImageView(device, createInfo, null, handle), "create swapchain image view");
                handles[imageIndex * viewCount + eyeIndex] = handle.get(0);
            }
        }
        return handles;
    }

    private long[] createFramebuffers(MemoryStack stack) {
        long[] handles = new long[imageViews.length];
        for (int index = 0; index < imageViews.length; index++) {
            VkFramebufferCreateInfo createInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType$Default().renderPass(renderPass)
                    .pAttachments(stack.longs(imageViews[index])).width(width).height(height).layers(1);
            LongBuffer handle = stack.mallocLong(1);
            check(VK10.vkCreateFramebuffer(device, createInfo, null, handle), "create eye framebuffer");
            handles[index] = handle.get(0);
        }
        return handles;
    }

    @Override
    public void close() {
        VK10.vkQueueWaitIdle(queue);
        for (long framebuffer : framebuffers) {
            VK10.vkDestroyFramebuffer(device, framebuffer, null);
        }
        for (long imageView : imageViews) {
            VK10.vkDestroyImageView(device, imageView, null);
        }
        VK10.vkDestroyPipeline(device, graphicsPipeline, null);
        VK10.vkDestroyPipelineLayout(device, pipelineLayout, null);
        VK10.vkDestroyRenderPass(device, renderPass, null);
        VK10.vkFreeCommandBuffers(device, commandPool, commandBuffer);
    }

    private static void check(int result, String operation) {
        if (result != VK10.VK_SUCCESS) {
            throw new IllegalStateException(operation + " failed with Vulkan result code " + result);
        }
    }
}
