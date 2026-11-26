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
enters the stereo frame loop, submits a head-relative quad layer, and starts
`XR_FB_passthrough` when the runtime advertises it. The renderer displays a
small stereoscopic application window in the upper-left area. The window follows
the user's head with an adjustable offset saved for the active profile.
Use Select on its header to move it, on the corner markers to resize it, and on
the header controls to minimize, restore, or close it.

The object tree reads `src/main/resources/content/technical-objects.csv` in the
source feed's 24-column format, with fields separated by `|~|`. The technical
root whose `rtoId` is `00000000-0001-0001-0001-000000000001` is hidden. Parent
links use `rtoParent`; records whose parent is absent from the supplied data are
shown as top-level items. Double-click a tree item to open its paged details
window.

Controller rays interact with the panel in its own coordinate system. Window size,
head-relative offset, minimized state, tree expansion, selection and scroll offset
are restored from the active profile’s local settings.

The application creates left and right OpenXR actions for controller selection
and pointer poses. It binds the Khronos simple-controller profile and Oculus
Touch profile; the latter uses trigger value and aim pose. When the active
runtime provides `XR_FB_spatial_entity_query` and
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

## Code layout

The Maven and IntelliJ entry point is `org.example.Main`. Packages under `org.example` own the following responsibilities.

| Package | Responsibility |
| --- | --- |
| `xr` | Runtime discovery, Vulkan device negotiation, session lifecycle, input actions, spatial meshes, passthrough and composition |
| `render` | Vulkan pipeline, swapchain framebuffers and panel texture uploads |
| `ui` | Head-relative window geometry, pointer routing, panel controls and Java2D drawing |
| `tree` | CSV format, object hierarchy, expansion and filtered rows |
| `profile` | User identity and profile fields from the bundled JSON |
| `state` | Profile-scoped SQLite settings in `~/.mr-test/state.db` |
| `logging` | Standard stream redirection to SLF4J/Logback |

Run `mvn clean verify` with JDK 25 to compile, exercise the panel and storage regressions,
and package the application. Tests use temporary SQLite databases and headless panel rendering.

## Interface language

The interface supports English and Russian. Russian is the default regardless of the system locale. Select Русский or English in User profile to apply and save a personal preference. Reset settings restores the startup language. To set the startup language in an IntelliJ run configuration, add `-Dmr.language=ru` or `-Dmr.language=en` to the VM options. The on-screen keyboard has its own EN/RU input switch. Object names and profile values are displayed as supplied by the data source.
