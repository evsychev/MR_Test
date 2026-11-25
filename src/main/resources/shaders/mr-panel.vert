#version 450

layout(push_constant) uniform PanelPushConstants {
    mat4 modelViewProjection;
    vec4 accentColor;
    vec4 windowState;
} pushConstants;

layout(location = 0) out vec2 panelCoordinate;

const vec2 PANEL_VERTICES[6] = vec2[6](
    vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(1.0, 1.0),
    vec2(0.0, 0.0), vec2(1.0, 1.0), vec2(0.0, 1.0)
);

void main() {
    vec2 vertex = PANEL_VERTICES[gl_VertexIndex];
    panelCoordinate = vec2(vertex.x, 1.0 - vertex.y);
    vec2 clipPosition = vec2(-1.0 + vertex.x * 2.0,
            -1.0 + vertex.y * 2.0);
    gl_Position = vec4(clipPosition, 0.5, 1.0);
}
