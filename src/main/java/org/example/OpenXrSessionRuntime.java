package org.example;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.LinkedHashSet;
import java.util.Set;

import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.XrEventDataBuffer;
import org.lwjgl.openxr.XrEventDataSessionStateChanged;
import org.lwjgl.openxr.XrSessionCreateInfo;
import org.lwjgl.openxr.XrSessionBeginInfo;
import org.lwjgl.openxr.XrGraphicsBindingVulkanKHR;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.openxr.XrSwapchainImageVulkanKHR;

import static org.lwjgl.openxr.XR10.*;

import static org.example.OpenXrSupport.*;
final class OpenXrSessionRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenXrSessionRuntime.class);
    private static final String APPLICATION_NAME = "Modern MR Java";
    private static final String ENGINE_NAME = "Modern MR Engine";

    private OpenXrSessionRuntime() {
    }

    static void runSessionLifecycle(org.lwjgl.openxr.XrInstance instance, long systemId, Set<String> extensions,
                                            boolean vulkanEnable2Available, boolean vulkanEnableAvailable,
                                            boolean spatialMeshAvailable) {
        ByteBuffer stackMemory = MemoryUtil.memAlloc(1024 * 1024);
        try {
            try (MemoryStack stack = MemoryStack.create(stackMemory).push()) {
                int environmentBlendMode = selectEnvironmentBlendMode(
                        instance, systemId, extensions.contains("XR_FB_passthrough"), stack);
                long requiredVulkanVersion;
                long maximumVulkanVersionSupported;
                if (vulkanEnable2Available) {
                    var graphicsRequirements2 = org.lwjgl.openxr.XrGraphicsRequirementsVulkan2KHR.calloc(stack).type$Default();
                    check(org.lwjgl.openxr.KHRVulkanEnable2.xrGetVulkanGraphicsRequirements2KHR(
                            instance, systemId, graphicsRequirements2), "get OpenXR Vulkan graphics requirements");
                    requiredVulkanVersion = graphicsRequirements2.minApiVersionSupported();
                    maximumVulkanVersionSupported = graphicsRequirements2.maxApiVersionSupported();
                } else {
                    var graphicsRequirements = org.lwjgl.openxr.XrGraphicsRequirementsVulkanKHR.calloc(stack).type$Default();
                    check(org.lwjgl.openxr.KHRVulkanEnable.xrGetVulkanGraphicsRequirementsKHR(
                            instance, systemId, graphicsRequirements), "get OpenXR Vulkan graphics requirements");
                    requiredVulkanVersion = graphicsRequirements.minApiVersionSupported();
                    maximumVulkanVersionSupported = graphicsRequirements.maxApiVersionSupported();
            }
            int vulkan12Version = VK10.VK_MAKE_VERSION(1, 2, 0);
            int maximumVulkanApiVersion = VK10.VK_MAKE_VERSION(
                    XR_VERSION_MAJOR(maximumVulkanVersionSupported),
                    XR_VERSION_MINOR(maximumVulkanVersionSupported),
                    XR_VERSION_PATCH(maximumVulkanVersionSupported));
            boolean useVulkan12Features = Integer.compareUnsigned(maximumVulkanApiVersion, vulkan12Version) >= 0;
            int applicationVulkanVersion = useVulkan12Features
                    ? vulkan12Version
                    : VK10.VK_MAKE_VERSION(XR_VERSION_MAJOR(requiredVulkanVersion),
                    XR_VERSION_MINOR(requiredVulkanVersion), XR_VERSION_PATCH(requiredVulkanVersion));
            LOGGER.info("OpenXR Vulkan API range: {}.{} to {}.{}; requesting {}.{}",
                    XR_VERSION_MAJOR(requiredVulkanVersion), XR_VERSION_MINOR(requiredVulkanVersion),
                    XR_VERSION_MAJOR(maximumVulkanVersionSupported), XR_VERSION_MINOR(maximumVulkanVersionSupported),
                    useVulkan12Features ? 1 : XR_VERSION_MAJOR(requiredVulkanVersion),
                    useVulkan12Features ? 2 : XR_VERSION_MINOR(requiredVulkanVersion));
            Set<String> requiredInstanceExtensionNames = new LinkedHashSet<>();
            if (vulkanEnableAvailable) {
                IntBuffer extensionBytes = stack.ints(0);
                check(org.lwjgl.openxr.KHRVulkanEnable.xrGetVulkanInstanceExtensionsKHR(
                        instance, systemId, extensionBytes, null), "get Vulkan instance extensions");
                java.nio.ByteBuffer extensionBuffer = stack.malloc(extensionBytes.get(0));
                check(org.lwjgl.openxr.KHRVulkanEnable.xrGetVulkanInstanceExtensionsKHR(
                        instance, systemId, extensionBytes, extensionBuffer), "read Vulkan instance extensions");
                addExtensionNames(requiredInstanceExtensionNames,
                        org.lwjgl.system.MemoryUtil.memUTF8(extensionBuffer));
            }
            Set<String> availableInstanceExtensionNames = enumerateInstanceExtensionNames(stack);
            boolean portabilityEnumerationAvailable = availableInstanceExtensionNames
                    .contains("VK_KHR_portability_enumeration");
            if (portabilityEnumerationAvailable) {
                requiredInstanceExtensionNames.add("VK_KHR_portability_enumeration");
            }
            PointerBuffer instanceExtensions = toPointerBuffer(stack, requiredInstanceExtensionNames);

            VkApplicationInfo vkAppInfo = VkApplicationInfo.calloc(stack)
                    .sType$Default().pApplicationName(stack.UTF8(APPLICATION_NAME))
                    .pEngineName(stack.UTF8(ENGINE_NAME))
                    .apiVersion(applicationVulkanVersion);
            VkInstanceCreateInfo vkCreateInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType$Default().pApplicationInfo(vkAppInfo).ppEnabledExtensionNames(instanceExtensions)
                    .flags(portabilityEnumerationAvailable
                            ? org.lwjgl.vulkan.KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR
                            : 0);
            PointerBuffer vkInstanceHandle = stack.mallocPointer(1);
            if (vulkanEnable2Available) {
                IntBuffer vkResult = stack.ints(VK10.VK_SUCCESS);
                var xrVkCreateInfo = org.lwjgl.openxr.XrVulkanInstanceCreateInfoKHR.calloc(stack)
                        .type$Default().systemId(systemId)
                        .pfnGetInstanceProcAddr(VK10.vkGetInstanceProcAddr(null, "vkGetInstanceProcAddr"))
                        .vulkanCreateInfo(vkCreateInfo);
                check(org.lwjgl.openxr.KHRVulkanEnable2.xrCreateVulkanInstanceKHR(
                        instance, xrVkCreateInfo, vkInstanceHandle, vkResult), "create runtime-bound Vulkan instance");
                checkVulkan(vkResult.get(0), "create Vulkan instance");
            } else {
                checkVulkan(VK10.vkCreateInstance(vkCreateInfo, null, vkInstanceHandle), "create Vulkan instance");
            }
            VkInstance vkInstance = new VkInstance(vkInstanceHandle.get(0), vkCreateInfo);

            PointerBuffer physicalHandle = stack.mallocPointer(1);
            int graphicsDeviceResult;
            if (vulkanEnable2Available) {
                var graphicsDeviceInfo = org.lwjgl.openxr.XrVulkanGraphicsDeviceGetInfoKHR.calloc(stack)
                        .type$Default().systemId(systemId).vulkanInstance(vkInstance);
                graphicsDeviceResult = org.lwjgl.openxr.KHRVulkanEnable2.xrGetVulkanGraphicsDevice2KHR(
                        instance, graphicsDeviceInfo, physicalHandle);
            } else {
                graphicsDeviceResult = org.lwjgl.openxr.KHRVulkanEnable.xrGetVulkanGraphicsDeviceKHR(
                        instance, systemId, vkInstance, physicalHandle);
            }
            check(graphicsDeviceResult, "select OpenXR Vulkan device");
            VkPhysicalDevice physicalDevice = new VkPhysicalDevice(physicalHandle.get(0), vkInstance);
            IntBuffer queueCount = stack.ints(0);
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueCount, null);
            VkQueueFamilyProperties.Buffer queues = VkQueueFamilyProperties.calloc(queueCount.get(0), stack);
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueCount, queues);
            int graphicsQueue = -1;
            for (int index = 0; index < queues.capacity(); index++) {
                if ((queues.get(index).queueFlags() & VK10.VK_QUEUE_GRAPHICS_BIT) != 0) {
                    graphicsQueue = index;
                    break;
                }
            }
            if (graphicsQueue < 0) throw new OpenXrException("OpenXR Vulkan device has no graphics queue");

            Set<String> requiredDeviceExtensionNames = new LinkedHashSet<>();
            if (vulkanEnableAvailable) {
                IntBuffer deviceExtensionBytes = stack.ints(0);
                check(org.lwjgl.openxr.KHRVulkanEnable.xrGetVulkanDeviceExtensionsKHR(
                        instance, systemId, deviceExtensionBytes, null), "get Vulkan device extensions");
                java.nio.ByteBuffer deviceExtensionBuffer = stack.malloc(deviceExtensionBytes.get(0));
                check(org.lwjgl.openxr.KHRVulkanEnable.xrGetVulkanDeviceExtensionsKHR(
                        instance, systemId, deviceExtensionBytes, deviceExtensionBuffer), "read Vulkan device extensions");
                addExtensionNames(requiredDeviceExtensionNames,
                        org.lwjgl.system.MemoryUtil.memUTF8(deviceExtensionBuffer));
            }
            Set<String> availableDeviceExtensionNames = enumerateDeviceExtensionNames(stack, physicalDevice);
            boolean bufferDeviceAddressAvailable = availableDeviceExtensionNames.contains(
                    org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME);
            if (bufferDeviceAddressAvailable && !useVulkan12Features) {
                requiredDeviceExtensionNames.add(
                        org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME);
            }
            boolean timelineSemaphoreAvailable = availableDeviceExtensionNames.contains(
                    org.lwjgl.vulkan.KHRTimelineSemaphore.VK_KHR_TIMELINE_SEMAPHORE_EXTENSION_NAME);
            if (timelineSemaphoreAvailable && !useVulkan12Features) {
                requiredDeviceExtensionNames.add(
                        org.lwjgl.vulkan.KHRTimelineSemaphore.VK_KHR_TIMELINE_SEMAPHORE_EXTENSION_NAME);
            }
            if (availableDeviceExtensionNames.contains("VK_KHR_portability_subset")) {
                requiredDeviceExtensionNames.add("VK_KHR_portability_subset");
            }
            LOGGER.info("Runtime-required Vulkan device extensions: {}", requiredDeviceExtensionNames);
            PointerBuffer deviceExtensions = toPointerBuffer(stack, requiredDeviceExtensionNames);
            VkDeviceQueueCreateInfo.Buffer queueCreateInfo = VkDeviceQueueCreateInfo.calloc(1, stack);
            queueCreateInfo.get(0).sType$Default().queueFamilyIndex(graphicsQueue).pQueuePriorities(stack.floats(1.0f));
            VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType$Default().pQueueCreateInfos(queueCreateInfo).ppEnabledExtensionNames(deviceExtensions)
                    .pEnabledFeatures(VkPhysicalDeviceFeatures.calloc(stack));
            if (useVulkan12Features) {
                var vulkan12Features = org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features.calloc(stack)
                        .sType$Default()
                        .bufferDeviceAddress(bufferDeviceAddressAvailable)
                        .timelineSemaphore(timelineSemaphoreAvailable);
                deviceCreateInfo.pNext(vulkan12Features);
                deviceCreateInfo.pEnabledFeatures(null);
                LOGGER.info("Enabled Vulkan buffer device address for the OpenXR runtime");
                LOGGER.info("Enabled Vulkan timeline semaphores for the OpenXR runtime");
            } else {
                org.lwjgl.vulkan.VkPhysicalDeviceBufferDeviceAddressFeaturesKHR bufferAddressFeatures = null;
                org.lwjgl.vulkan.VkPhysicalDeviceTimelineSemaphoreFeaturesKHR timelineFeatures = null;
                if (bufferDeviceAddressAvailable) {
                    bufferAddressFeatures = org.lwjgl.vulkan.VkPhysicalDeviceBufferDeviceAddressFeaturesKHR
                            .calloc(stack).sType$Default().bufferDeviceAddress(true);
                }
                if (timelineSemaphoreAvailable) {
                    timelineFeatures = org.lwjgl.vulkan.VkPhysicalDeviceTimelineSemaphoreFeaturesKHR
                            .calloc(stack).sType$Default().timelineSemaphore(true);
                    if (bufferAddressFeatures != null) timelineFeatures.pNext(bufferAddressFeatures.address());
                    deviceCreateInfo.pNext(timelineFeatures);
                } else if (bufferAddressFeatures != null) {
                    deviceCreateInfo.pNext(bufferAddressFeatures);
                }
                if (bufferAddressFeatures != null) {
                    LOGGER.info("Enabled Vulkan buffer device address for the OpenXR runtime");
                }
                if (timelineFeatures != null) {
                    LOGGER.info("Enabled Vulkan timeline semaphores for the OpenXR runtime");
                }
            }
            PointerBuffer deviceHandle = stack.mallocPointer(1);
            if (vulkanEnable2Available) {
                IntBuffer vkResult = stack.ints(VK10.VK_SUCCESS);
                var xrVkDeviceCreateInfo = org.lwjgl.openxr.XrVulkanDeviceCreateInfoKHR.calloc(stack)
                        .type$Default().systemId(systemId)
                        .pfnGetInstanceProcAddr(VK10.vkGetInstanceProcAddr(null, "vkGetInstanceProcAddr"))
                        .vulkanCreateInfo(deviceCreateInfo).vulkanPhysicalDevice(physicalDevice);
                check(org.lwjgl.openxr.KHRVulkanEnable2.xrCreateVulkanDeviceKHR(
                        instance, xrVkDeviceCreateInfo, deviceHandle, vkResult), "create runtime-bound Vulkan device");
                checkVulkan(vkResult.get(0), "create Vulkan device");
            } else {
                checkVulkan(VK10.vkCreateDevice(physicalDevice, deviceCreateInfo, null, deviceHandle), "create Vulkan device");
            }
            org.lwjgl.vulkan.VkDevice device = new org.lwjgl.vulkan.VkDevice(deviceHandle.get(0), physicalDevice, deviceCreateInfo);
            PointerBuffer queueHandle = stack.mallocPointer(1);
            VK10.vkGetDeviceQueue(device, graphicsQueue, 0, queueHandle);
            VkQueue graphicsQueueHandle = new VkQueue(queueHandle.get(0), device);

            LongBuffer commandPoolHandle = stack.mallocLong(1);
            VkCommandPoolCreateInfo commandPoolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default().queueFamilyIndex(graphicsQueue)
                    .flags(VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
            checkVulkan(VK10.vkCreateCommandPool(device, commandPoolInfo, null, commandPoolHandle), "create Vulkan command pool");
            long commandPool = commandPoolHandle.get(0);

            org.lwjgl.openxr.XrGraphicsBindingVulkan2KHR binding2 = null;
            XrGraphicsBindingVulkanKHR binding = null;
            if (vulkanEnable2Available) {
                binding2 = org.lwjgl.openxr.XrGraphicsBindingVulkan2KHR.calloc(stack)
                        .type$Default().instance(vkInstance).physicalDevice(physicalDevice).device(device)
                        .queueFamilyIndex(graphicsQueue).queueIndex(0);
            } else {
                binding = XrGraphicsBindingVulkanKHR.calloc(stack)
                        .type$Default().instance(vkInstance).physicalDevice(physicalDevice).device(device)
                        .queueFamilyIndex(graphicsQueue).queueIndex(0);
            }
            XrSessionCreateInfo createInfo = XrSessionCreateInfo.calloc(stack)
                    .type$Default()
                    .systemId(systemId);
            createInfo.next(vulkanEnable2Available ? binding2 : binding);
            PointerBuffer sessionHandle = stack.mallocPointer(1);
            check(xrCreateSession(instance, createInfo, sessionHandle), "create OpenXR session");
            var session = new org.lwjgl.openxr.XrSession(sessionHandle.get(0), instance);
            SpatialInputHandler inputHandler = new SpatialInputHandler(instance, session);
            PointerBuffer spaceHandle = stack.mallocPointer(1);
            var spaceInfo = org.lwjgl.openxr.XrReferenceSpaceCreateInfo.calloc(stack)
                    .type$Default().referenceSpaceType(XR_REFERENCE_SPACE_TYPE_LOCAL)
                    .poseInReferenceSpace(org.lwjgl.openxr.XrPosef.calloc(stack).orientation(
                            org.lwjgl.openxr.XrQuaternionf.calloc(stack).w(1.0f)));
            check(xrCreateReferenceSpace(session, spaceInfo, spaceHandle), "create local reference space");
            var appSpace = new org.lwjgl.openxr.XrSpace(spaceHandle.get(0), session);

            IntBuffer viewCountBuffer = stack.ints(0);
            check(xrEnumerateViewConfigurationViews(instance, systemId,
                    XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, viewCountBuffer, null), "count stereo views");
            int viewCount = viewCountBuffer.get(0);
            var configViews = org.lwjgl.openxr.XrViewConfigurationView.calloc(viewCount, stack);
            configViews.forEach(org.lwjgl.openxr.XrViewConfigurationView::type$Default);
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
            var swapchainInfo = org.lwjgl.openxr.XrSwapchainCreateInfo.calloc(stack)
                    .type$Default().usageFlags(XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT)
                    .format(swapchainFormat).sampleCount(1).width(width).height(height)
                    .faceCount(1).arraySize(viewCount).mipCount(1);
            PointerBuffer swapchainHandle = stack.mallocPointer(1);
            check(xrCreateSwapchain(session, swapchainInfo, swapchainHandle), "create stereo swapchain");
            var swapchain = new org.lwjgl.openxr.XrSwapchain(swapchainHandle.get(0), session);
            IntBuffer swapchainImageCount = stack.ints(0);
            check(xrEnumerateSwapchainImages(swapchain, swapchainImageCount, null), "count Vulkan swapchain images");
            XrSwapchainImageVulkanKHR.Buffer swapchainImages = XrSwapchainImageVulkanKHR.calloc(swapchainImageCount.get(0), stack);
            swapchainImages.forEach(XrSwapchainImageVulkanKHR::type$Default);
            check(org.lwjgl.openxr.XR10.nxrEnumerateSwapchainImages(swapchain,
                    swapchainImageCount.get(0), org.lwjgl.system.MemoryUtil.memAddress(swapchainImageCount),
                    swapchainImages.address()), "read Vulkan swapchain images");
            LOGGER.info("XR swapchain ready: format={}, size={}x{}, views={}, images={}",
                    swapchainFormat, width, height, viewCount, swapchainImageCount.get(0));

            org.lwjgl.openxr.XrPassthroughFB passthrough = null;
            org.lwjgl.openxr.XrPassthroughLayerFB passthroughLayer = null;
            if (extensions.contains("XR_FB_passthrough")) {
                PointerBuffer passthroughHandle = stack.mallocPointer(1);
                var passthroughInfo = org.lwjgl.openxr.XrPassthroughCreateInfoFB.calloc(stack)
                        .type$Default().flags(org.lwjgl.openxr.FBPassthrough.XR_PASSTHROUGH_IS_RUNNING_AT_CREATION_BIT_FB);
                check(org.lwjgl.openxr.FBPassthrough.xrCreatePassthroughFB(session, passthroughInfo, passthroughHandle),
                        "create passthrough feature");
                passthrough = new org.lwjgl.openxr.XrPassthroughFB(passthroughHandle.get(0), session);
                check(org.lwjgl.openxr.FBPassthrough.xrPassthroughStartFB(passthrough), "start passthrough");
                PointerBuffer passthroughLayerHandle = stack.mallocPointer(1);
                var passthroughLayerInfo = org.lwjgl.openxr.XrPassthroughLayerCreateInfoFB.calloc(stack)
                        .type$Default().passthrough(passthrough)
                        .purpose(org.lwjgl.openxr.FBPassthrough.XR_PASSTHROUGH_LAYER_PURPOSE_RECONSTRUCTION_FB);
                check(org.lwjgl.openxr.FBPassthrough.xrCreatePassthroughLayerFB(
                        session, passthroughLayerInfo, passthroughLayerHandle), "create passthrough layer");
                passthroughLayer = new org.lwjgl.openxr.XrPassthroughLayerFB(passthroughLayerHandle.get(0), session);
                check(org.lwjgl.openxr.FBPassthrough.xrPassthroughLayerResumeFB(passthroughLayer), "resume passthrough layer");
                LOGGER.info("XR passthrough feature and reconstruction layer are active");
            } else {
                LOGGER.warn("XR_FB_passthrough was not enabled; rendering without passthrough");
            }
            VulkanPanelRenderer renderer = new VulkanPanelRenderer(physicalDevice, device, graphicsQueueHandle,
                    commandPool, swapchainImages, viewCount, width, height, swapchainFormat);
            LOGGER.info("XR Vulkan panel renderer initialized");
            AnchoredWindow anchoredWindow = new AnchoredWindow();
            TechnicalObjectTreeUI treeUi = new TechnicalObjectTreeUI(width, height);

            boolean running = false;
            boolean exiting = false;
            SpatialMeshService spatialMeshService = spatialMeshAvailable
                    ? new SpatialMeshService(session) : null;
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
                        OpenXrFrameLoop.runFrame(session, appSpace, swapchain, swapchainImages, viewCount, width, height,
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
            renderer.close();
            treeUi.close();
            check(xrDestroySwapchain(swapchain), "destroy stereo swapchain");
            check(xrDestroySpace(appSpace), "destroy local reference space");
            if (spatialMeshService != null) {
                spatialMeshService.close();
            }
            inputHandler.close();
            if (passthroughLayer != null) {
                check(org.lwjgl.openxr.FBPassthrough.xrDestroyPassthroughLayerFB(passthroughLayer), "destroy passthrough layer");
                check(org.lwjgl.openxr.FBPassthrough.xrDestroyPassthroughFB(passthrough), "destroy passthrough feature");
            }
            check(xrDestroySession(session), "destroy OpenXR session");
            VK10.vkDestroyCommandPool(device, commandPool, null);
            VK10.vkDestroyDevice(device, null);
            VK10.vkDestroyInstance(vkInstance, null);
            }
        } finally {
            MemoryUtil.memFree(stackMemory);
        }
    }


}
