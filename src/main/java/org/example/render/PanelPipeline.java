package org.example.render;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
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
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;

import static org.example.render.VulkanPanelRenderer.check;

final class PanelPipeline {
    private static VkPipelineShaderStageCreateInfo main;

    private PanelPipeline() {}

    static long createRenderPass(VkDevice device, MemoryStack stack, int format) {
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

    static long createPipelineLayout(VkDevice device, MemoryStack stack, long descriptorSetLayout) {
        VkPipelineLayoutCreateInfo createInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType$Default().pSetLayouts(stack.longs(descriptorSetLayout));
        LongBuffer handle = stack.mallocLong(1);
        check(VK10.vkCreatePipelineLayout(device, createInfo, null, handle), "create panel pipeline layout");
        return handle.get(0);
    }

    static long createGraphicsPipeline(VkDevice device, MemoryStack stack, long pipelineLayout, long renderPass) {
        long vertexShader = createShaderModule(device, stack, "/shaders/mr-panel.vert.spv");
        long fragmentShader = createShaderModule(device, stack, "/shaders/mr-panel.frag.spv");
        try {
            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            main;
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

    private static long createShaderModule(VkDevice device, MemoryStack stack, String resourcePath) {
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
}
