package org.example.xr;

import java.nio.IntBuffer;
import java.util.LinkedHashSet;
import java.util.Set;

import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.XrInstance;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.openxr.XR10.*;

final class OpenXrSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenXrSupport.class);

    private OpenXrSupport() {
    }

    static int selectEnvironmentBlendMode(XrInstance instance, long systemId,
                                                  boolean passthroughRequested, MemoryStack stack) {
        IntBuffer modeCount = stack.ints(0);
        check(xrEnumerateEnvironmentBlendModes(instance, systemId,
                XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, modeCount, null), "count environment blend modes");
        IntBuffer modes = stack.mallocInt(modeCount.get(0));
        check(xrEnumerateEnvironmentBlendModes(instance, systemId,
                XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, modeCount, modes), "read environment blend modes");
        int preferredMode = XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
        for (int index = 0; index < modes.capacity(); index++) {
            if (modes.get(index) == preferredMode) {
                LOGGER.info("Selected OPAQUE environment blend mode (passthrough requested={})",
                        passthroughRequested);
                return preferredMode;
            }
        }
        if (passthroughRequested) {
            for (int index = 0; index < modes.capacity(); index++) {
                if (modes.get(index) == XR_ENVIRONMENT_BLEND_MODE_ALPHA_BLEND) {
                    LOGGER.warn("Runtime lacks OPAQUE blend mode; falling back to ALPHA_BLEND with passthrough");
                    return XR_ENVIRONMENT_BLEND_MODE_ALPHA_BLEND;
                }
            }
            throw new OpenXrException("The runtime advertises passthrough but supports neither OPAQUE nor ALPHA_BLEND");
        }
        if (modes.capacity() == 0) {
            throw new OpenXrException("The runtime exposes no environment blend mode");
        }
        LOGGER.warn("Opaque blend mode is unavailable; using runtime blend mode {}", modes.get(0));
        return modes.get(0);
    }

    static Set<String> enumerateInstanceExtensionNames(MemoryStack stack) {
        IntBuffer extensionCount = stack.ints(0);
        checkVulkan(VK10.vkEnumerateInstanceExtensionProperties((String) null, extensionCount, null),
                "count Vulkan instance extensions");
        var properties = VkExtensionProperties.calloc(extensionCount.get(0), stack);
        checkVulkan(VK10.vkEnumerateInstanceExtensionProperties((String) null, extensionCount, properties),
                "enumerate Vulkan instance extensions");
        Set<String> names = new LinkedHashSet<>();
        for (VkExtensionProperties property : properties) {
            names.add(property.extensionNameString());
        }
        return names;
    }

    static Set<String> enumerateDeviceExtensionNames(MemoryStack stack, VkPhysicalDevice physicalDevice) {
        IntBuffer extensionCount = stack.ints(0);
        checkVulkan(VK10.vkEnumerateDeviceExtensionProperties(physicalDevice, (String) null, extensionCount, null),
                "count Vulkan device extensions");
        var properties = VkExtensionProperties.calloc(extensionCount.get(0), stack);
        checkVulkan(VK10.vkEnumerateDeviceExtensionProperties(physicalDevice, (String) null, extensionCount, properties),
                "enumerate Vulkan device extensions");
        Set<String> names = new LinkedHashSet<>();
        for (VkExtensionProperties property : properties) {
            names.add(property.extensionNameString());
        }
        return names;
    }

    static void addExtensionNames(Set<String> destination, String extensionList) {
        for (String extension : extensionList.replace('\0', ' ').strip().split("\\s+")) {
            if (!extension.isBlank()) {
                destination.add(extension);
            }
        }
    }

    static PointerBuffer toPointerBuffer(MemoryStack stack, Set<String> extensionNames) {
        PointerBuffer buffer = stack.mallocPointer(extensionNames.size());
        for (String extensionName : extensionNames) {
            buffer.put(stack.UTF8(extensionName));
        }
        return buffer.flip();
    }

    static void check(int result, String operation) {
        if (result >= XR_SUCCESS) {
            return;
        }
        if (result == XR_ERROR_RUNTIME_UNAVAILABLE) {
            throw new OpenXrException(operation + ": no active OpenXR runtime is available");
        }
        if (result == XR_ERROR_RUNTIME_FAILURE && operation.equals("select OpenXR Vulkan device")) {
            throw new OpenXrException(operation + " failed with XR_ERROR_RUNTIME_FAILURE (-2). "
                    + "The active runtime could not provide its Vulkan device; inspect its log for loader, driver, or Metal errors.");
        }
        if (result == XR_ERROR_VALIDATION_FAILURE && operation.contains("OpenXR")) {
            throw new OpenXrException(operation + " failed with XR_ERROR_VALIDATION_FAILURE (-1). "
                    + "Check the active runtime's Vulkan initialization and driver log; on macOS, MoltenVK needs Metal.");
        }
        throw new OpenXrException(operation + " failed with result code " + result);
    }

    static void checkVulkan(int result, String operation) {
        if (result != VK10.VK_SUCCESS) {
            throw new OpenXrException(operation + " failed with Vulkan result code " + result);
        }
    }

    static final class OpenXrException extends RuntimeException {
        OpenXrException(String message) {
            super(message);
        }
    }
}
