#version 450

layout(location = 0) out vec2 panelCoordinate;

const vec2 PANEL_VERTICES[6] = vec2[6](
    vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(1.0, 1.0),
    vec2(0.0, 0.0), vec2(1.0, 1.0), vec2(0.0, 1.0)
);

void main() {
    vec2 vertex = PANEL_VERTICES[gl_VertexIndex];
    panelCoordinate = vertex;
    gl_Position = vec4(-1.0 + vertex.x * 2.0, -1.0 + vertex.y * 2.0, 0.5, 1.0);
}
