#version 450

layout(push_constant) uniform PanelPushConstants {
    mat4 modelViewProjection;
    vec4 accentColor;
    vec4 windowState;
} pushConstants;

layout(location = 0) in vec2 panelCoordinate;
layout(location = 0) out vec4 outputColor;

void main() {
    vec2 uv = panelCoordinate;
    float width = pushConstants.windowState.x;
    float height = pushConstants.windowState.y;
    float minimized = pushConstants.windowState.z;
    float hovered = pushConstants.windowState.w;

    float border = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    if (border < 0.012) {
        outputColor = vec4(pushConstants.accentColor.rgb, 1.0);
        outputColor.a = 1.0;
        return;
    }

    float headerHeight = min(0.075 / height, 1.0);
    if (uv.y < headerHeight) {
        outputColor = vec4(0.035, 0.22, 0.38, 1.0);
    } else {
        outputColor = vec4(0.07, 0.11, 0.15, 1.0);
    }

    float buttonLeft = 1.0 - 0.095 / width;
    float buttonRight = 1.0 - 0.018 / width;
    float buttonTop = 0.012 / height;
    float buttonBottom = min(0.072 / height, headerHeight - 0.003);
    bool inWindowButton = uv.x >= buttonLeft && uv.x <= buttonRight
            && uv.y >= buttonTop && uv.y <= buttonBottom;
    if (inWindowButton) {
        outputColor = hovered > 0.5
                ? vec4(0.12, 0.42, 0.49, 1.0)
                : vec4(0.055, 0.20, 0.28, 1.0);

        vec2 buttonSize = vec2(buttonRight - buttonLeft, buttonBottom - buttonTop);
        vec2 buttonCoordinate = (uv - vec2(buttonLeft, buttonTop)) / buttonSize;
        if (minimized > 0.5) {
            float outline = max(abs(buttonCoordinate.x - 0.5), abs(buttonCoordinate.y - 0.5));
            if (outline > 0.24 && outline < 0.34) {
                outputColor = vec4(0.82, 0.94, 1.0, 1.0);
            }
        } else if (buttonCoordinate.y > 0.44 && buttonCoordinate.y < 0.57
                && buttonCoordinate.x > 0.26 && buttonCoordinate.x < 0.74) {
            outputColor = vec4(0.82, 0.94, 1.0, 1.0);
        }
        outputColor.a = 1.0;
        return;
    }

    if (minimized > 0.5) {
        outputColor.a = 1.0;
        return;
    }

    float titleMarkY = headerHeight * 0.5;
    if (uv.x > 0.045 && uv.x < 0.20 && abs(uv.y - titleMarkY) < 0.012) {
        outputColor = vec4(0.78, 0.90, 0.98, 1.0);
        outputColor.a = 1.0;
        return;
    }

    float contentTop = headerHeight + 0.075;
    bool sidebar = uv.x > 0.04 && uv.x < 0.24 && uv.y > contentTop && uv.y < 0.94;
    bool contentRow = uv.x > 0.30 && uv.x < 0.92 && uv.y > contentTop && uv.y < 0.91
            && (fract((uv.y - contentTop) * 7.0) > 0.65);
    bool contentTile = uv.x > 0.30 && uv.x < 0.54 && uv.y > 0.67 && uv.y < 0.88;

    if (sidebar && fract((uv.y - contentTop) * 8.0) > 0.70) {
        outputColor = vec4(0.26, 0.43, 0.52, 1.0);
    } else if (contentRow) {
        outputColor = vec4(0.31, 0.49, 0.59, 1.0);
    } else if (contentTile) {
        outputColor = vec4(0.12, 0.23, 0.30, 1.0);
    }
    outputColor.a = 1.0;
}
