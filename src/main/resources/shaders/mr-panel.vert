#version 450

layout(push_constant) uniform PanelPushConstants {
    mat4 modelViewProjection;
    vec4 accentColor;
} pushConstants;

layout(location = 0) out vec2 panelCoordinate;

const vec2 PANEL_VERTICES[6] = vec2[6](
    vec2(-0.28, -0.18), vec2(0.28, -0.18), vec2(0.28, 0.18),
    vec2(-0.28, -0.18), vec2(0.28, 0.18), vec2(-0.28, 0.18)
);

void main() {
    vec2 vertex = PANEL_VERTICES[gl_VertexIndex];
    panelCoordinate = vertex;
    gl_Position = pushConstants.modelViewProjection * vec4(vertex, 0.0, 1.0);
}
