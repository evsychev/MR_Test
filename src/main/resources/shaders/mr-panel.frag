#version 450

layout(push_constant) uniform PanelPushConstants {
    mat4 modelViewProjection;
    vec4 accentColor;
    vec4 windowState;
} pushConstants;

layout(location = 0) in vec2 panelCoordinate;
layout(location = 0) out vec4 outputColor;

vec4 premultipliedColor(vec3 color, float alpha) {
    return vec4(color * alpha, alpha);
}

void main() {
    vec2 uv = panelCoordinate;
    float width = pushConstants.windowState.x;
    float height = pushConstants.windowState.y;
    float minimized = pushConstants.windowState.z;
    float hovered = pushConstants.windowState.w;

    float headerHeight = min(0.105 / height, 1.0);
    if (uv.y < headerHeight) {
        outputColor = premultipliedColor(vec3(0.035, 0.037, 0.041), 0.58);
    } else {
        outputColor = premultipliedColor(vec3(0.025, 0.027, 0.030), 0.36);
    }

    float buttonLeft = 1.0 - 0.095 / width;
    float buttonRight = 1.0 - 0.018 / width;
    float buttonTop = 0.012 / height;
    float buttonBottom = min(0.085 / height, headerHeight - 0.008);
    bool inWindowButton = uv.x >= buttonLeft && uv.x <= buttonRight
            && uv.y >= buttonTop && uv.y <= buttonBottom;
    if (inWindowButton) {
        outputColor = premultipliedColor(hovered > 0.5
                ? vec3(0.12, 0.42, 0.49)
                : vec3(0.055, 0.20, 0.28), 0.90);

        vec2 buttonSize = vec2(buttonRight - buttonLeft, buttonBottom - buttonTop);
        vec2 buttonCoordinate = (uv - vec2(buttonLeft, buttonTop)) / buttonSize;
        if (minimized > 0.5) {
            float outline = max(abs(buttonCoordinate.x - 0.5), abs(buttonCoordinate.y - 0.5));
            if (outline > 0.24 && outline < 0.34) {
                outputColor = premultipliedColor(vec3(0.82, 0.94, 1.0), 0.96);
            }
        } else if (buttonCoordinate.y > 0.44 && buttonCoordinate.y < 0.57
                && buttonCoordinate.x > 0.26 && buttonCoordinate.x < 0.74) {
            outputColor = premultipliedColor(vec3(0.82, 0.94, 1.0), 0.96);
        }
        return;
    }

    float titleMarkY = headerHeight * 0.5;
    bool titleMark = uv.x > 0.07 && uv.x < 0.49
            && (abs(uv.y - titleMarkY) < 0.012
            || (uv.x < 0.14 && abs(uv.y - titleMarkY) < 0.025));
    if (titleMark) {
        outputColor = premultipliedColor(vec3(0.82, 0.92, 0.98), 0.96);
        return;
    }

    if (minimized > 0.5) {
        return;
    }

    float contentTop = headerHeight + 0.035;
    for (int row = 0; row < 4; row++) {
        float rowTop = contentTop + float(row) * 0.17;
        float rowBottom = rowTop + 0.13;
        if (uv.y >= rowTop && uv.y <= rowBottom && uv.x > 0.07 && uv.x < 0.93) {
            outputColor = premultipliedColor(vec3(0.045, 0.050, 0.055), 0.40);

            bool icon = uv.x > 0.11 && uv.x < 0.23
                    && uv.y > rowTop + 0.025 && uv.y < rowTop + 0.105;
            if (icon) {
                outputColor = premultipliedColor(pushConstants.accentColor.rgb, 0.72);
            } else {
                bool titleLine = uv.x > 0.29 && uv.x < 0.83
                        && uv.y > rowTop + 0.035 && uv.y < rowTop + 0.058;
                bool detailLine = uv.x > 0.29 && uv.x < 0.68
                        && uv.y > rowTop + 0.075 && uv.y < rowTop + 0.089;
                if (titleLine) {
                    outputColor = premultipliedColor(vec3(0.86, 0.93, 0.98), 0.88);
                } else if (detailLine) {
                    outputColor = premultipliedColor(vec3(0.58, 0.70, 0.78), 0.70);
                }
            }
            return;
        }
    }

    if (uv.y > 0.93) {
        outputColor = premultipliedColor(vec3(0.025, 0.027, 0.030), 0.54);
        if (uv.x > 0.19 && uv.x < 0.27 || uv.x > 0.46 && uv.x < 0.54
                || uv.x > 0.73 && uv.x < 0.81) {
            outputColor = premultipliedColor(pushConstants.accentColor.rgb, 0.86);
        }
    }
}
