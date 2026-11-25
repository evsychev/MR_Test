package org.example;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.XrSwapchainImageVulkanKHR;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceLayers;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
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
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkDevice;

final class VulkanPanelRenderer implements AutoCloseable {
    private static final int TEXTURE_FORMAT = VK10.VK_FORMAT_R8G8B8A8_UNORM;

    private final VkPhysicalDevice physicalDevice;
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
    private final long descriptorSetLayout;
    private final long descriptorPool;
    private final long descriptorSet;
    private final long textureImage;
    private final long textureMemory;
    private final long textureImageView;
    private final long textureSampler;
    private final long stagingBuffer;
    private final long stagingMemory;
    private final long stagingBufferBytes;
    private final ByteBuffer mappedStagingBuffer;
    private final long[] imageViews;
    private final long[] framebuffers;
    private final VkCommandBuffer commandBuffer;
    private boolean textureInitialized;

    VulkanPanelRenderer(VkPhysicalDevice physicalDevice, VkDevice device, VkQueue queue, long commandPool,
                        XrSwapchainImageVulkanKHR.Buffer swapchainImages,
                        int viewCount, int width, int height, long swapchainFormat) {
        this.physicalDevice = physicalDevice;
        this.device = device;
        this.queue = queue;
        this.commandPool = commandPool;
        this.swapchainImages = swapchainImages;
        this.viewCount = viewCount;
        this.width = width;
        this.height = height;
        stagingBufferBytes = (long) width * height * Integer.BYTES;

        ByteBuffer stackMemory = MemoryUtil.memAlloc(512 * 1024);
        try {
            try (MemoryStack stack = MemoryStack.create(stackMemory).push()) {
                descriptorSetLayout = createDescriptorSetLayout(stack);
                long[] textureResources = createTextureImage(stack);
                textureImage = textureResources[0];
                textureMemory = textureResources[1];
                textureImageView = createTextureImageView(stack, textureImage);
                textureSampler = createTextureSampler(stack);
                long[] stagingResources = createStagingBuffer(stack);
                stagingBuffer = stagingResources[0];
                stagingMemory = stagingResources[1];
                PointerBuffer mappedPointer = stack.mallocPointer(1);
                check(VK10.vkMapMemory(device, stagingMemory, 0, stagingBufferBytes, 0, mappedPointer),
                        "map panel staging buffer");
                mappedStagingBuffer = MemoryUtil.memByteBuffer(mappedPointer.get(0), (int) stagingBufferBytes);
                long[] descriptorResources = createDescriptorSet(stack);
                descriptorPool = descriptorResources[0];
                descriptorSet = descriptorResources[1];
                updateDescriptorSet(stack);

                renderPass = createRenderPass(stack, (int) swapchainFormat);
                pipelineLayout = createPipelineLayout(stack);
                graphicsPipeline = createGraphicsPipeline(stack);
                imageViews = createSwapchainImageViews(stack, (int) swapchainFormat);
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

    void render(int imageIndex, TechnicalObjectTreeUI treeUi, AnchoredWindow window) {
        treeUi.markWindowStateChanged(window.minimized());
        boolean textureDirty = treeUi.refreshPixels();
        if (textureDirty) {
            mappedStagingBuffer.clear();
            mappedStagingBuffer.put(treeUi.rgbaPixels()).flip();
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            check(VK10.vkResetCommandBuffer(commandBuffer, 0), "reset panel command buffer");
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default().flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            check(VK10.vkBeginCommandBuffer(commandBuffer, beginInfo), "begin panel command buffer");

            if (textureDirty || !textureInitialized) {
                uploadTexture(stack);
                textureInitialized = true;
            }
            transitionSwapchainImage(imageIndex, stack);
            drawPanel(imageIndex, stack);

            check(VK10.vkEndCommandBuffer(commandBuffer), "end panel command buffer");
            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack).sType$Default()
                    .pCommandBuffers(stack.pointers(commandBuffer.address()));
            check(VK10.vkQueueSubmit(queue, submitInfo, VK10.VK_NULL_HANDLE), "submit panel frame");
            check(VK10.vkQueueWaitIdle(queue), "wait for panel frame");
        }
    }

    private void uploadTexture(MemoryStack stack) {
        VkImageMemoryBarrier.Buffer toTransfer = VkImageMemoryBarrier.calloc(1, stack)
                .sType$Default()
                .srcAccessMask(textureInitialized ? VK10.VK_ACCESS_SHADER_READ_BIT : 0)
                .dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                .oldLayout(textureInitialized ? VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                        : VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                .newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .image(textureImage)
                .subresourceRange(VkImageSubresourceRange.calloc(stack)
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1)
                        .baseArrayLayer(0).layerCount(1));
        VK10.vkCmdPipelineBarrier(commandBuffer,
                textureInitialized ? VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT : VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, toTransfer);

        VkBufferImageCopy.Buffer copy = VkBufferImageCopy.calloc(1, stack)
                .bufferOffset(0).bufferRowLength(0).bufferImageHeight(0)
                .imageSubresource(VkImageSubresourceLayers.calloc(stack)
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0)
                        .baseArrayLayer(0).layerCount(1))
                .imageOffset(org.lwjgl.vulkan.VkOffset3D.calloc(stack).set(0, 0, 0))
                .imageExtent(org.lwjgl.vulkan.VkExtent3D.calloc(stack).set(width, height, 1));
        VK10.vkCmdCopyBufferToImage(commandBuffer, stagingBuffer, textureImage,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, copy);

        VkImageMemoryBarrier.Buffer toShader = VkImageMemoryBarrier.calloc(1, stack)
                .sType$Default().srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                .oldLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .image(textureImage)
                .subresourceRange(VkImageSubresourceRange.calloc(stack)
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1)
                        .baseArrayLayer(0).layerCount(1));
        VK10.vkCmdPipelineBarrier(commandBuffer, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, null, null, toShader);
    }

    private void transitionSwapchainImage(int imageIndex, MemoryStack stack) {
        long image = swapchainImages.get(imageIndex).image();
        VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack)
                .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1)
                .baseArrayLayer(0).layerCount(1);
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

    private void drawPanel(int imageIndex, MemoryStack stack) {
        VkClearValue.Buffer clearValues = VkClearValue.calloc(1, stack);
        clearValues.get(0).color().float32(0, 0.0f).float32(1, 0.0f)
                .float32(2, 0.0f).float32(3, 0.0f);
        VkRenderPassBeginInfo renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType$Default().renderPass(renderPass)
                .framebuffer(framebuffers[imageIndex * viewCount])
                .renderArea(VkRect2D.calloc(stack)
                        .offset(org.lwjgl.vulkan.VkOffset2D.calloc(stack).set(0, 0))
                        .extent(org.lwjgl.vulkan.VkExtent2D.calloc(stack).set(width, height)))
                .pClearValues(clearValues);
        VK10.vkCmdBeginRenderPass(commandBuffer, renderPassBeginInfo, VK10.VK_SUBPASS_CONTENTS_INLINE);
        VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);
        VK10.vkCmdSetViewport(commandBuffer, 0, VkViewport.calloc(1, stack)
                .x(0.0f).y(0.0f).width(width).height(height).minDepth(0.0f).maxDepth(1.0f));
        VK10.vkCmdSetScissor(commandBuffer, 0, VkRect2D.calloc(1, stack)
                .offset(org.lwjgl.vulkan.VkOffset2D.calloc(stack).set(0, 0))
                .extent(org.lwjgl.vulkan.VkExtent2D.calloc(stack).set(width, height)));
        VK10.vkCmdBindDescriptorSets(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
                pipelineLayout, 0, stack.longs(descriptorSet), null);
        VK10.vkCmdDraw(commandBuffer, 6, 1, 0, 0);
        VK10.vkCmdEndRenderPass(commandBuffer);
    }

    private long createDescriptorSetLayout(MemoryStack stack) {
        VkDescriptorSetLayoutBinding.Buffer binding = VkDescriptorSetLayoutBinding.calloc(1, stack)
                .binding(0).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
        var createInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType$Default().pBindings(binding);
        LongBuffer handle = stack.mallocLong(1);
        check(VK10.vkCreateDescriptorSetLayout(device, createInfo, null, handle),
                "create panel descriptor set layout");
        return handle.get(0);
    }

    private long[] createTextureImage(MemoryStack stack) {
        var createInfo = VkImageCreateInfo.calloc(stack)
                .sType$Default().imageType(VK10.VK_IMAGE_TYPE_2D).format(TEXTURE_FORMAT)
                .extent(org.lwjgl.vulkan.VkExtent3D.calloc(stack).set(width, height, 1))
                .mipLevels(1).arrayLayers(1).samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                .tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
                .usage(VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT)
                .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
        LongBuffer imageHandle = stack.mallocLong(1);
        check(VK10.vkCreateImage(device, createInfo, null, imageHandle), "create panel texture image");

        VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
        VK10.vkGetImageMemoryRequirements(device, imageHandle.get(0), requirements);
        int memoryType = findMemoryType(stack, requirements.memoryTypeBits(), VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        VkMemoryAllocateInfo allocationInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType$Default().allocationSize(requirements.size()).memoryTypeIndex(memoryType);
        LongBuffer memoryHandle = stack.mallocLong(1);
        check(VK10.vkAllocateMemory(device, allocationInfo, null, memoryHandle), "allocate panel texture memory");
        check(VK10.vkBindImageMemory(device, imageHandle.get(0), memoryHandle.get(0), 0),
                "bind panel texture memory");
        return new long[]{imageHandle.get(0), memoryHandle.get(0)};
    }

    private long createTextureImageView(MemoryStack stack, long image) {
        var createInfo = VkImageViewCreateInfo.calloc(stack).sType$Default()
                .image(image).viewType(VK10.VK_IMAGE_VIEW_TYPE_2D).format(TEXTURE_FORMAT)
                .components(org.lwjgl.vulkan.VkComponentMapping.calloc(stack)
                        .r(VK10.VK_COMPONENT_SWIZZLE_IDENTITY).g(VK10.VK_COMPONENT_SWIZZLE_IDENTITY)
                        .b(VK10.VK_COMPONENT_SWIZZLE_IDENTITY).a(VK10.VK_COMPONENT_SWIZZLE_IDENTITY))
                .subresourceRange(VkImageSubresourceRange.calloc(stack)
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1)
                        .baseArrayLayer(0).layerCount(1));
        LongBuffer handle = stack.mallocLong(1);
        check(VK10.vkCreateImageView(device, createInfo, null, handle), "create panel texture image view");
        return handle.get(0);
    }

    private long createTextureSampler(MemoryStack stack) {
        VkSamplerCreateInfo createInfo = VkSamplerCreateInfo.calloc(stack)
                .sType$Default().magFilter(VK10.VK_FILTER_LINEAR).minFilter(VK10.VK_FILTER_LINEAR)
                .mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST)
                .addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .mipLodBias(0.0f).anisotropyEnable(false).maxAnisotropy(1.0f)
                .compareEnable(false).compareOp(VK10.VK_COMPARE_OP_ALWAYS)
                .minLod(0.0f).maxLod(0.0f)
                .borderColor(VK10.VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE).unnormalizedCoordinates(false);
        LongBuffer handle = stack.mallocLong(1);
        check(VK10.vkCreateSampler(device, createInfo, null, handle), "create panel texture sampler");
        return handle.get(0);
    }

    private long[] createStagingBuffer(MemoryStack stack) {
        VkBufferCreateInfo createInfo = VkBufferCreateInfo.calloc(stack)
                .sType$Default().size(stagingBufferBytes).usage(VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
                .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
        LongBuffer bufferHandle = stack.mallocLong(1);
        check(VK10.vkCreateBuffer(device, createInfo, null, bufferHandle), "create panel staging buffer");

        VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
        VK10.vkGetBufferMemoryRequirements(device, bufferHandle.get(0), requirements);
        int memoryType = findMemoryType(stack, requirements.memoryTypeBits(),
                VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        VkMemoryAllocateInfo allocationInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType$Default().allocationSize(requirements.size()).memoryTypeIndex(memoryType);
        LongBuffer memoryHandle = stack.mallocLong(1);
        check(VK10.vkAllocateMemory(device, allocationInfo, null, memoryHandle), "allocate panel staging memory");
        check(VK10.vkBindBufferMemory(device, bufferHandle.get(0), memoryHandle.get(0), 0),
                "bind panel staging memory");
        return new long[]{bufferHandle.get(0), memoryHandle.get(0)};
    }

    private int findMemoryType(MemoryStack stack, int allowedTypes, int requiredFlags) {
        VkPhysicalDeviceMemoryProperties properties = VkPhysicalDeviceMemoryProperties.calloc(stack);
        VK10.vkGetPhysicalDeviceMemoryProperties(physicalDevice, properties);
        for (int index = 0; index < properties.memoryTypeCount(); index++) {
            int typeFlag = 1 << index;
            if ((allowedTypes & typeFlag) != 0
                    && (properties.memoryTypes(index).propertyFlags() & requiredFlags) == requiredFlags) {
                return index;
            }
        }
        throw new IllegalStateException("No Vulkan memory type supports flags " + requiredFlags);
    }

    private long[] createDescriptorSet(MemoryStack stack) {
        VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1, stack)
                .type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1);
        VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType$Default().maxSets(1).pPoolSizes(poolSize);
        LongBuffer poolHandle = stack.mallocLong(1);
        check(VK10.vkCreateDescriptorPool(device, poolInfo, null, poolHandle), "create panel descriptor pool");

        VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType$Default().descriptorPool(poolHandle.get(0))
                .pSetLayouts(stack.longs(descriptorSetLayout));
        LongBuffer setHandle = stack.mallocLong(1);
        check(VK10.vkAllocateDescriptorSets(device, allocateInfo, setHandle), "allocate panel descriptor set");
        return new long[]{poolHandle.get(0), setHandle.get(0)};
    }

    private void updateDescriptorSet(MemoryStack stack) {
        VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                .sampler(textureSampler).imageView(textureImageView)
                .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        var write = org.lwjgl.vulkan.VkWriteDescriptorSet.calloc(1, stack)
                .sType$Default().dstSet(descriptorSet).dstBinding(0).dstArrayElement(0)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1).pImageInfo(imageInfo);
        VK10.vkUpdateDescriptorSets(device, write, null);
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
        VkPipelineLayoutCreateInfo createInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType$Default().pSetLayouts(stack.longs(descriptorSetLayout));
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
                    VkPipelineColorBlendAttachmentState.calloc(1, stack).blendEnable(false)
                            .colorWriteMask(VK10.VK_COLOR_COMPONENT_R_BIT | VK10.VK_COLOR_COMPONENT_G_BIT
                                    | VK10.VK_COLOR_COMPONENT_B_BIT | VK10.VK_COLOR_COMPONENT_A_BIT);
            VkPipelineColorBlendStateCreateInfo colorBlend = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType$Default().logicOpEnable(false).pAttachments(blendAttachment);
            int[] dynamicStateValues = {VK10.VK_DYNAMIC_STATE_VIEWPORT, VK10.VK_DYNAMIC_STATE_SCISSOR};
            var dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType$Default().pDynamicStates(stack.ints(dynamicStateValues));
            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            pipelineInfo.get(0).sType$Default().pStages(stages).pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly).pViewportState(viewportState)
                    .pRasterizationState(rasterization).pMultisampleState(multisample)
                    .pColorBlendState(colorBlend).pDynamicState(dynamicState)
                    .layout(pipelineLayout).renderPass(renderPass).subpass(0)
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

    private long[] createSwapchainImageViews(MemoryStack stack, int format) {
        int imageCount = swapchainImages.capacity();
        long[] handles = new long[imageCount * viewCount];
        for (int imageIndex = 0; imageIndex < imageCount; imageIndex++) {
            for (int viewIndex = 0; viewIndex < viewCount; viewIndex++) {
                VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack)
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1)
                        .baseArrayLayer(viewIndex).layerCount(1);
                var createInfo = VkImageViewCreateInfo.calloc(stack).sType$Default()
                        .image(swapchainImages.get(imageIndex).image()).viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
                        .format(format)
                        .components(org.lwjgl.vulkan.VkComponentMapping.calloc(stack)
                                .r(VK10.VK_COMPONENT_SWIZZLE_IDENTITY).g(VK10.VK_COMPONENT_SWIZZLE_IDENTITY)
                                .b(VK10.VK_COMPONENT_SWIZZLE_IDENTITY).a(VK10.VK_COMPONENT_SWIZZLE_IDENTITY))
                        .subresourceRange(range);
                LongBuffer handle = stack.mallocLong(1);
                check(VK10.vkCreateImageView(device, createInfo, null, handle), "create swapchain image view");
                handles[imageIndex * viewCount + viewIndex] = handle.get(0);
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
            check(VK10.vkCreateFramebuffer(device, createInfo, null, handle), "create panel framebuffer");
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
        VK10.vkDestroyDescriptorPool(device, descriptorPool, null);
        VK10.vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
        VK10.vkDestroySampler(device, textureSampler, null);
        VK10.vkDestroyImageView(device, textureImageView, null);
        VK10.vkDestroyImage(device, textureImage, null);
        VK10.vkFreeMemory(device, textureMemory, null);
        VK10.vkUnmapMemory(device, stagingMemory);
        VK10.vkDestroyBuffer(device, stagingBuffer, null);
        VK10.vkFreeMemory(device, stagingMemory, null);
        VK10.vkDestroyRenderPass(device, renderPass, null);
        VK10.vkFreeCommandBuffers(device, commandPool, commandBuffer);
    }

    private static void check(int result, String operation) {
        if (result != VK10.VK_SUCCESS) {
            throw new IllegalStateException(operation + " failed with Vulkan result code " + result);
        }
    }
}
