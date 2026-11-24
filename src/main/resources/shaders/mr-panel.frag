#version 450

layout(push_constant) uniform PanelPushConstants {
    mat4 modelViewProjection;
    vec4 accentColor;
} pushConstants;

layout(location = 0) in vec2 panelCoordinate;
layout(location = 0) out vec4 outputColor;

void main() {
    vec2 normalizedCoordinate = abs(panelCoordinate / vec2(0.28, 0.18));
    float edge = max(normalizedCoordinate.x, normalizedCoordinate.y);
    float grid = step(0.96, abs(fract(panelCoordinate.x * 12.0) - 0.5) * 2.0)
            + step(0.96, abs(fract(panelCoordinate.y * 12.0) - 0.5) * 2.0);
    if (edge > 0.91 || grid > 0.5) {
        outputColor = vec4(pushConstants.accentColor.rgb, 0.94);
    } else {
        outputColor = vec4(0.018, 0.055, 0.09, 0.78);
    }
}
