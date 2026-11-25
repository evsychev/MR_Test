# Modern MR Java

A Java 25 and LWJGL OpenXR mixed reality test application.

## Platform support

The application targets the vendor-neutral OpenXR ecosystem and must not require
Meta hardware, runtimes, SDKs, or vendor extensions. OpenXR core is the baseline;
runtime-specific extensions are optional capabilities discovered at startup and
isolated behind vendor adapters. Passthrough, spatial mesh, and input support
vary by runtime, so the application must report unsupported capabilities and
provide a graceful fallback where possible. Validate cross-device support with
at least one non-Meta OpenXR runtime before claiming portability.

## Requirements

- JDK 25
- Maven 3.9 or later
- An installed OpenXR runtime
- Vulkan loader and a Vulkan device supported by the active OpenXR runtime
- For Meta XR Simulator on macOS, Apple Silicon with Metal available to MoltenVK

## Run the MR application

The OpenXR loader and Vulkan loader locations depend on the machine. On macOS,
set them in the run configuration or pass them to Maven:

```shell
export VULKAN_SDK="/Users/evgenysychev/VulkanSDK/1.4.363.1/macOS"
export VK_ICD_FILENAMES="$VULKAN_SDK/share/vulkan/icd.d/MoltenVK_icd.json"

mvn \
  -Dorg.lwjgl.librarypath="/private/tmp/openxr-sdk-build/src/loader" \
  -Dorg.lwjgl.vulkan.libname="$VULKAN_SDK/lib/libvulkan.1.dylib" \
  clean compile exec:java
```

In IntelliJ, use the same paths in the run configuration. Set
`VK_ICD_FILENAMES` as an environment variable, and add
`--enable-native-access=ALL-UNNAMED` to VM options for Java 25.

When startup succeeds, the application creates a Vulkan-backed OpenXR session,
enters the stereo frame loop, submits a transparent projection layer, and starts
`XR_FB_passthrough` when the runtime advertises it. The renderer displays a
small stereoscopic application window in the upper-left area. It is anchored to
the session's `LOCAL` space using the first valid head pose, so it stays in that
world location instead of following later head movement. Point a controller at
the window's top-right control and press Select to minimize or restore it.

The window only tests controller rays against its own button. It does not use
room geometry for collision or alter the physical scene. This session-local
anchor is not persisted across application sessions.

The application creates left and right simple-controller select and grip-pose
actions. When the active runtime provides `XR_FB_spatial_entity_query` and
`XR_META_spatial_entity_mesh` along with `XR_FB_spatial_entity`, it queries local
spatial entities, enables their triangle-mesh components, and loads their
vertices and indices. Choose a simulator synthetic environment containing
scene data to exercise spatial mesh loading.

Passthrough and spatial mesh are runtime extensions, so other OpenXR runtimes
may omit them. The application reports available capabilities and skips the
optional feature path when its required extensions are absent.

## Verified macOS launch

The application was launched with Meta XR Simulator 207.1, MoltenVK from Vulkan
SDK 1.4.363.1, and Java 25. The runtime reached `FOCUSED`, copied the passthrough
texture, and returned 25 local spatial entities including a triangle mesh. If
the OpenXR loader is installed elsewhere, replace `org.lwjgl.librarypath` with
the directory containing `libopenxr_loader.dylib`.

## Engine components

- `Main`: runtime and system discovery, session lifecycle, frame loop and layer submission.
- `VulkanPanelRenderer`: swapchain image views, stereo render pass and virtual panel.
- `AnchoredWindow`: session-local window pose, minimize/restore state and controller-ray button hit testing.
- `SpatialInputHandler`: action set, controller select/grip actions and pose spaces.
- `SpatialMeshService`: local spatial entity query, triangle mesh loading and controller proximity.

OpenXR lifecycle calls currently run on the application thread. Mesh data is
copied into Java-owned arrays so later rendering or collision work can move to
worker threads without retaining stack-backed native buffers.
