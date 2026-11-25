package org.example;

import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.openxr.XrView;

final class AnchoredWindow {
    static final float WIDTH_METERS = 0.39f;
    static final float EXPANDED_HEIGHT_METERS = 0.70f;
    static final float MINIMIZED_HEIGHT_METERS = 0.12f;

    private static final float ANCHOR_DISTANCE_METERS = 1.55f;
    private static final float ANCHOR_LEFT_OFFSET_METERS = -0.85f;
    private static final float ANCHOR_TOP_OFFSET_METERS = 0.92f;
    private static final float RAY_LENGTH_METERS = 4.0f;

    private final Vector3f anchorPosition = new Vector3f();
    private final Quaternionf anchorOrientation = new Quaternionf();
    private boolean initialized;
    private boolean minimized;
    private boolean closed;

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

        Vector3f topLeftOffset = new Vector3f(ANCHOR_LEFT_OFFSET_METERS,
                ANCHOR_TOP_OFFSET_METERS, -ANCHOR_DISTANCE_METERS).rotate(anchorOrientation);
        anchorPosition.set(headPosition).add(topLeftOffset);
        initialized = true;
    }

    boolean intersectRay(Vector3f rayOrigin, Quaternionf rayOrientation, Vector2f panelPoint) {
        if (!initialized || closed) {
            return false;
        }

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

        float x = localOrigin.x + rayDirection.x * distance;
        float yFromTop = -(localOrigin.y + rayDirection.y * distance);
        if (x < 0.0f || x > WIDTH_METERS || yFromTop < 0.0f || yFromTop > heightMeters()) {
            return false;
        }

        panelPoint.set(x, yFromTop);
        return true;
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

    void toggleMinimized() {
        minimized = !minimized;
    }

    boolean closed() {
        return closed;
    }

    void closeWindow() {
        closed = true;
    }
}
