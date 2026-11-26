package org.example.xr;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.LinkedHashSet;
import java.util.Set;

import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.KHRVulkanEnable;
import org.lwjgl.openxr.KHRVulkanEnable2;
import org.lwjgl.openxr.XrGraphicsRequirementsVulkan2KHR;
import org.lwjgl.openxr.XrGraphicsRequirementsVulkanKHR;
import org.lwjgl.openxr.XrInstance;
import org.lwjgl.openxr.XrVulkanDeviceCreateInfoKHR;
import org.lwjgl.openxr.XrVulkanGraphicsDeviceGetInfoKHR;
import org.lwjgl.openxr.XrVulkanInstanceCreateInfoKHR;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRBufferDeviceAddress;
import org.lwjgl.vulkan.KHRPortabilityEnumeration;
import org.lwjgl.vulkan.KHRTimelineSemaphore;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceBufferDeviceAddressFeaturesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceTimelineSemaphoreFeaturesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.example.xr.OpenXrSupport.*;
import static org.lwjgl.openxr.XR10.*;

record XrVulkanDevice(VkInstance instance, VkPhysicalDevice physicalDevice, VkDevice device,
                      int queueFamily, VkQueue queue, long commandPool) {
    private static final Logger LOGGER = LoggerFactory.getLogger(XrVulkanDevice.class);
    private static final String APPLICATION_NAME = "Modern MR Java";
    private static final String ENGINE_NAME = "Modern MR Engine";

    static XrVulkanDevice create(XrInstance instance, long systemId,
                                 boolean vulkanEnable2Available, boolean vulkanEnableAvailable,
                                 MemoryStack stack, SessionResources resources) {
        long requiredVulkanVersion;
        long maximumVulkanVersionSupported;
        if (vulkanEnable2Available) {
            var graphicsRequirements2 = XrGraphicsRequirementsVulkan2KHR.calloc(stack).type$Default();
            check(KHRVulkanEnable2.xrGetVulkanGraphicsRequirements2KHR(
                    instance, systemId, graphicsRequirements2), "get OpenXR Vulkan graphics requirements");
            requiredVulkanVersion = graphicsRequirements2.minApiVersionSupported();
            maximumVulkanVersionSupported = graphicsRequirements2.maxApiVersionSupported();
        } else {
            var graphicsRequirements = XrGraphicsRequirementsVulkanKHR.calloc(stack).type$Default();
            check(KHRVulkanEnable.xrGetVulkanGraphicsRequirementsKHR(
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
            check(KHRVulkanEnable.xrGetVulkanInstanceExtensionsKHR(
                    instance, systemId, extensionBytes, null), "get Vulkan instance extensions");
            java.nio.ByteBuffer extensionBuffer = stack.malloc(extensionBytes.get(0));
            check(KHRVulkanEnable.xrGetVulkanInstanceExtensionsKHR(
                    instance, systemId, extensionBytes, extensionBuffer), "read Vulkan instance extensions");
            addExtensionNames(requiredInstanceExtensionNames,
                    MemoryUtil.memUTF8(extensionBuffer));
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
                        ? KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR
                        : 0);
        PointerBuffer vkInstanceHandle = stack.mallocPointer(1);
        if (vulkanEnable2Available) {
            IntBuffer vkResult = stack.ints(VK10.VK_SUCCESS);
            var xrVkCreateInfo = XrVulkanInstanceCreateInfoKHR.calloc(stack)
                    .type$Default().systemId(systemId)
                    .pfnGetInstanceProcAddr(VK10.vkGetInstanceProcAddr(null, "vkGetInstanceProcAddr"))
                    .vulkanCreateInfo(vkCreateInfo);
            check(KHRVulkanEnable2.xrCreateVulkanInstanceKHR(
                    instance, xrVkCreateInfo, vkInstanceHandle, vkResult), "create runtime-bound Vulkan instance");
            checkVulkan(vkResult.get(0), "create Vulkan instance");
        } else {
            checkVulkan(VK10.vkCreateInstance(vkCreateInfo, null, vkInstanceHandle), "create Vulkan instance");
        }
        VkInstance vkInstance = new VkInstance(vkInstanceHandle.get(0), vkCreateInfo);
        resources.defer(() -> VK10.vkDestroyInstance(vkInstance, null));

        PointerBuffer physicalHandle = stack.mallocPointer(1);
        int graphicsDeviceResult;
        if (vulkanEnable2Available) {
            var graphicsDeviceInfo = XrVulkanGraphicsDeviceGetInfoKHR.calloc(stack)
                    .type$Default().systemId(systemId).vulkanInstance(vkInstance);
            graphicsDeviceResult = KHRVulkanEnable2.xrGetVulkanGraphicsDevice2KHR(
                    instance, graphicsDeviceInfo, physicalHandle);
        } else {
            graphicsDeviceResult = KHRVulkanEnable.xrGetVulkanGraphicsDeviceKHR(
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
            check(KHRVulkanEnable.xrGetVulkanDeviceExtensionsKHR(
                    instance, systemId, deviceExtensionBytes, null), "get Vulkan device extensions");
            java.nio.ByteBuffer deviceExtensionBuffer = stack.malloc(deviceExtensionBytes.get(0));
            check(KHRVulkanEnable.xrGetVulkanDeviceExtensionsKHR(
                    instance, systemId, deviceExtensionBytes, deviceExtensionBuffer), "read Vulkan device extensions");
            addExtensionNames(requiredDeviceExtensionNames,
                    MemoryUtil.memUTF8(deviceExtensionBuffer));
        }
        Set<String> availableDeviceExtensionNames = enumerateDeviceExtensionNames(stack, physicalDevice);
        boolean bufferDeviceAddressAvailable = availableDeviceExtensionNames.contains(
                KHRBufferDeviceAddress.VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME);
        if (bufferDeviceAddressAvailable && !useVulkan12Features) {
            requiredDeviceExtensionNames.add(
                    KHRBufferDeviceAddress.VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME);
        }
        boolean timelineSemaphoreAvailable = availableDeviceExtensionNames.contains(
                KHRTimelineSemaphore.VK_KHR_TIMELINE_SEMAPHORE_EXTENSION_NAME);
        if (timelineSemaphoreAvailable && !useVulkan12Features) {
            requiredDeviceExtensionNames.add(
                    KHRTimelineSemaphore.VK_KHR_TIMELINE_SEMAPHORE_EXTENSION_NAME);
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
            var vulkan12Features = VkPhysicalDeviceVulkan12Features.calloc(stack)
                    .sType$Default()
                    .bufferDeviceAddress(bufferDeviceAddressAvailable)
                    .timelineSemaphore(timelineSemaphoreAvailable);
            deviceCreateInfo.pNext(vulkan12Features);
            deviceCreateInfo.pEnabledFeatures(null);
            LOGGER.info("Enabled Vulkan buffer device address for the OpenXR runtime");
            LOGGER.info("Enabled Vulkan timeline semaphores for the OpenXR runtime");
        } else {
            VkPhysicalDeviceBufferDeviceAddressFeaturesKHR bufferAddressFeatures = null;
            VkPhysicalDeviceTimelineSemaphoreFeaturesKHR timelineFeatures = null;
            if (bufferDeviceAddressAvailable) {
                bufferAddressFeatures = VkPhysicalDeviceBufferDeviceAddressFeaturesKHR
                        .calloc(stack).sType$Default().bufferDeviceAddress(true);
            }
            if (timelineSemaphoreAvailable) {
                timelineFeatures = VkPhysicalDeviceTimelineSemaphoreFeaturesKHR
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
            var xrVkDeviceCreateInfo = XrVulkanDeviceCreateInfoKHR.calloc(stack)
                    .type$Default().systemId(systemId)
                    .pfnGetInstanceProcAddr(VK10.vkGetInstanceProcAddr(null, "vkGetInstanceProcAddr"))
                    .vulkanCreateInfo(deviceCreateInfo).vulkanPhysicalDevice(physicalDevice);
            check(KHRVulkanEnable2.xrCreateVulkanDeviceKHR(
                    instance, xrVkDeviceCreateInfo, deviceHandle, vkResult), "create runtime-bound Vulkan device");
            checkVulkan(vkResult.get(0), "create Vulkan device");
        } else {
            checkVulkan(VK10.vkCreateDevice(physicalDevice, deviceCreateInfo, null, deviceHandle), "create Vulkan device");
        }
        VkDevice device = new VkDevice(deviceHandle.get(0), physicalDevice, deviceCreateInfo);
        resources.defer(() -> VK10.vkDestroyDevice(device, null));
        PointerBuffer queueHandle = stack.mallocPointer(1);
        VK10.vkGetDeviceQueue(device, graphicsQueue, 0, queueHandle);
        VkQueue graphicsQueueHandle = new VkQueue(queueHandle.get(0), device);

        LongBuffer commandPoolHandle = stack.mallocLong(1);
        VkCommandPoolCreateInfo commandPoolInfo = VkCommandPoolCreateInfo.calloc(stack)
                .sType$Default().queueFamilyIndex(graphicsQueue)
                .flags(VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
        checkVulkan(VK10.vkCreateCommandPool(device, commandPoolInfo, null, commandPoolHandle), "create Vulkan command pool");
        long commandPool = commandPoolHandle.get(0);
        resources.defer(() -> VK10.vkDestroyCommandPool(device, commandPool, null));
        return new XrVulkanDevice(vkInstance, physicalDevice, device, graphicsQueue, graphicsQueueHandle, commandPool);
    }
}
