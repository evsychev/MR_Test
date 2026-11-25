package org.example;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.LinkedHashSet;
import java.util.Set;

import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.XrApplicationInfo;
import org.lwjgl.openxr.XrExtensionProperties;
import org.lwjgl.openxr.XrInstanceCreateInfo;
import org.lwjgl.openxr.XrInstanceProperties;
import org.lwjgl.openxr.XrSystemGetInfo;
import org.lwjgl.openxr.XrSystemProperties;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkPhysicalDevice;

import static org.lwjgl.openxr.XR10.*;
import static org.example.OpenXrSupport.*;
public final class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final String APPLICATION_NAME = "Modern MR Java";
    private static final String ENGINE_NAME = "Modern MR Engine";

    private Main() {
    }

    public static void main(String[] args) {
        ConsoleLogRedirector.install();
        try {
            initializeAndPrint();
        } catch (OpenXrException exception) {
            LOGGER.error("OpenXR initialization failed", exception);
            System.exit(1);
        }
    }

    private static void initializeAndPrint() {
        long instanceHandle = 0L;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer extensionCount = stack.ints(0);
            check(xrEnumerateInstanceExtensionProperties((String) null, extensionCount, null),
                    "enumerate OpenXR extensions");

            XrExtensionProperties.Buffer extensionProperties =
                    XrExtensionProperties.calloc(extensionCount.get(0), stack);
            extensionProperties.forEach(XrExtensionProperties::type$Default);
            check(xrEnumerateInstanceExtensionProperties(
                    (String) null, extensionCount, extensionProperties), "read OpenXR extensions");

            Set<String> extensions = new LinkedHashSet<>();
            for (XrExtensionProperties property : extensionProperties) {
                extensions.add(property.extensionNameString());
            }

            XrApplicationInfo applicationInfo = XrApplicationInfo.calloc(stack)
                    .applicationName(stack.UTF8(APPLICATION_NAME))
                    .applicationVersion(1)
                    .engineName(stack.UTF8(ENGINE_NAME))
                    .engineVersion(1)
                    .apiVersion(XR_CURRENT_API_VERSION);

            boolean passthroughAvailable = extensions.contains("XR_FB_passthrough");
            boolean vulkanEnable2Available = extensions.contains("XR_KHR_vulkan_enable2");
            boolean vulkanEnableAvailable = extensions.contains("XR_KHR_vulkan_enable");
            boolean spatialMeshAvailable = extensions.contains("XR_FB_spatial_entity")
                    && extensions.contains("XR_FB_spatial_entity_query")
                    && extensions.contains("XR_META_spatial_entity_mesh");
            if (!vulkanEnable2Available && !vulkanEnableAvailable) {
                throw new OpenXrException("The active OpenXR runtime exposes no supported Vulkan graphics extension");
            }
            PointerBuffer enabledExtensions = stack.mallocPointer((vulkanEnable2Available ? 1 : 0)
                    + (vulkanEnableAvailable ? 1 : 0) + (passthroughAvailable ? 1 : 0)
                    + (spatialMeshAvailable ? 3 : 0));
            if (vulkanEnable2Available) enabledExtensions.put(stack.UTF8("XR_KHR_vulkan_enable2"));
            if (vulkanEnableAvailable) enabledExtensions.put(stack.UTF8("XR_KHR_vulkan_enable"));
            if (passthroughAvailable) enabledExtensions.put(stack.UTF8("XR_FB_passthrough"));
            if (spatialMeshAvailable) {
                enabledExtensions.put(stack.UTF8("XR_FB_spatial_entity"));
                enabledExtensions.put(stack.UTF8("XR_FB_spatial_entity_query"));
                enabledExtensions.put(stack.UTF8("XR_META_spatial_entity_mesh"));
            }
            enabledExtensions.flip();
            XrInstanceCreateInfo createInfo = XrInstanceCreateInfo.calloc(stack)
                    .type$Default()
                    .applicationInfo(applicationInfo)
                    .enabledExtensionNames(enabledExtensions);

            PointerBuffer instanceBuffer = stack.mallocPointer(1);
            check(xrCreateInstance(createInfo, instanceBuffer), "create OpenXR instance");
            instanceHandle = instanceBuffer.get(0);

            var instance = new org.lwjgl.openxr.XrInstance(instanceHandle, createInfo);

            XrInstanceProperties instanceProperties = XrInstanceProperties.calloc(stack).type$Default();
            check(xrGetInstanceProperties(instance, instanceProperties), "read runtime properties");

            XrSystemGetInfo systemInfo = XrSystemGetInfo.calloc(stack)
                    .type$Default()
                    .formFactor(XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY);
            LongBuffer systemId = stack.mallocLong(1);
            check(xrGetSystem(instance, systemInfo, systemId), "find headset system");

            XrSystemProperties systemProperties = XrSystemProperties.calloc(stack).type$Default();
            check(xrGetSystemProperties(instance, systemId.get(0), systemProperties),
                    "read headset properties");

            long version = instanceProperties.runtimeVersion();
            LOGGER.info("OpenXR runtime: {}", instanceProperties.runtimeNameString());
            LOGGER.info("Runtime version: {}.{}.{}",
                    XR_VERSION_MAJOR(version), XR_VERSION_MINOR(version), XR_VERSION_PATCH(version));
            LOGGER.info("Headset system: {}", systemProperties.systemNameString());
            LOGGER.info("Vendor ID: {}", Integer.toUnsignedString(systemProperties.vendorId()));
            LOGGER.info("Passthrough extension available: {}", extensions.contains("XR_FB_passthrough"));
            LOGGER.info("Scene mesh extension available: {}",
                    extensions.contains("XR_MSFT_scene_understanding")
                            || extensions.contains("XR_META_spatial_entity_mesh"));
            LOGGER.info("Vulkan binding selected: {}",
                    vulkanEnable2Available ? "XR_KHR_vulkan_enable2" : "XR_KHR_vulkan_enable");
            LOGGER.info("Spatial mesh query enabled: {}",
                    extensions.contains("XR_FB_spatial_entity")
                            && extensions.contains("XR_FB_spatial_entity_query")
                            && extensions.contains("XR_META_spatial_entity_mesh"));
            LOGGER.info("Available OpenXR extensions: {}", extensions.size());
            try {
                OpenXrSessionRuntime.runSessionLifecycle(instance, systemId.get(0), extensions, vulkanEnable2Available,
                        vulkanEnableAvailable, spatialMeshAvailable);
            } finally {
                check(xrDestroyInstance(instance), "destroy OpenXR instance");
                instanceHandle = 0L;
            }
        } catch (RuntimeException exception) {
            if (instanceHandle != 0L) {
                xrDestroyInstance(new org.lwjgl.openxr.XrInstance(instanceHandle, null));
            }
            throw exception;
        }
    }


}
