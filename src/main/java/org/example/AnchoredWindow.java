package org.example;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.openxr.XrView;

final class AnchoredWindow {
    static final float WIDTH_METERS = 0.56f;
    static final float EXPANDED_HEIGHT_METERS = 0.38f;
    static final float MINIMIZED_HEIGHT_METERS = 0.12f;

    private static final float ANCHOR_DISTANCE_METERS = 2.0f;
    private static final float BUTTON_MIN_X_METERS = WIDTH_METERS - 0.10f;
    private static final float BUTTON_MAX_X_METERS = WIDTH_METERS - 0.02f;
    private static final float BUTTON_TOP_METERS = -0.012f;
    private static final float BUTTON_BOTTOM_METERS = -0.085f;
    private static final float RAY_LENGTH_METERS = 4.0f;

    private final Vector3f anchorPosition = new Vector3f();
    private final Quaternionf anchorOrientation = new Quaternionf();
    private boolean initialized;
    private boolean minimized;
    private boolean buttonHovered;
    private boolean previousLeftSelect;
    private boolean previousRightSelect;

    void updateFromViews(XrView.Buffer views) {
        if (views.remaining() == 0) {
            return;
        }

        var firstPose = views.get(0).pose();
        var firstPosition = firstPose.position$();
        var firstOrientation = firstPose.orientation();
        Vector3f headPosition = new Vector3f(firstPosition.x(), firstPosition.y(), firstPosition.z());
        anchorOrientation.set(firstOrientation.x(), firstOrientation.y(), firstOrientation.z(), firstOrientation.w())
                .normalize();

        if (views.remaining() > 1) {
            var secondPosition = views.get(1).pose().position$();
            headPosition.add(secondPosition.x(), secondPosition.y(), secondPosition.z()).mul(0.5f);
        }

        Vector3f topLeftOffset = new Vector3f(-0.60f, 0.35f, -ANCHOR_DISTANCE_METERS)
                .rotate(anchorOrientation);
        anchorPosition.set(headPosition).add(topLeftOffset);
        initialized = true;
    }

    void updateInteraction(boolean leftSelect, Vector3f leftPosition, Quaternionf leftOrientation,
                           boolean leftPoseValid, boolean rightSelect, Vector3f rightPosition,
                           Quaternionf rightOrientation, boolean rightPoseValid) {
        if (!initialized) {
            previousLeftSelect = leftSelect;
            previousRightSelect = rightSelect;
            return;
        }

        boolean leftButtonHit = leftPoseValid && rayHitsToggleButton(leftPosition, leftOrientation);
        boolean rightButtonHit = rightPoseValid && rayHitsToggleButton(rightPosition, rightOrientation);
        buttonHovered = leftButtonHit || rightButtonHit;

        boolean leftClick = leftSelect && !previousLeftSelect && leftButtonHit;
        boolean rightClick = rightSelect && !previousRightSelect && rightButtonHit;
        if (leftClick || rightClick) {
            minimized = !minimized;
        }

        previousLeftSelect = leftSelect;
        previousRightSelect = rightSelect;
    }

    Vector3f quadCenterPosition() {
        return new Vector3f(anchorPosition)
                .add(new Vector3f(WIDTH_METERS * 0.5f, -heightMeters() * 0.5f, 0.0f)
                        .rotate(anchorOrientation));
    }

    Vector3f anchorPosition() {
        return anchorPosition;
    }

    Quaternionf anchorOrientation() {
        return anchorOrientation;
    }

    float heightMeters() {
        return minimized ? MINIMIZED_HEIGHT_METERS : EXPANDED_HEIGHT_METERS;
    }

    boolean minimized() {
        return minimized;
    }

    boolean buttonHovered() {
        return buttonHovered;
    }

    private boolean rayHitsToggleButton(Vector3f rayOrigin, Quaternionf rayOrientation) {
        Quaternionf inverseAnchorOrientation = new Quaternionf(anchorOrientation).conjugate();
        Vector3f localOrigin = new Vector3f(rayOrigin).sub(anchorPosition).rotate(inverseAnchorOrientation);
        Vector3f rayDirection = new Vector3f(0.0f, 0.0f, -1.0f)
                .rotate(rayOrientation)
                .rotate(inverseAnchorOrientation);

        if (Math.abs(rayDirection.z) < 0.0001f) {
            return false;
        }

        float distance = -localOrigin.z / rayDirection.z;
        if (distance < 0.0f || distance > RAY_LENGTH_METERS) {
            return false;
        }

        float localX = localOrigin.x + rayDirection.x * distance;
        float localY = localOrigin.y + rayDirection.y * distance;
        return localX >= BUTTON_MIN_X_METERS && localX <= BUTTON_MAX_X_METERS
                && localY <= BUTTON_TOP_METERS && localY >= BUTTON_BOTTOM_METERS;
    }
}
