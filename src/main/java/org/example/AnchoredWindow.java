package org.example;

import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.openxr.XrView;

final class AnchoredWindow {
    static final float DEFAULT_WIDTH_METERS = 0.78f;
    static final float EXPANDED_HEIGHT_METERS = 1.40f;
    static final float MINIMIZED_HEIGHT_METERS = 0.08f;
    private static final float MIN_WIDTH_METERS = 0.48f;
    private static final float MAX_WIDTH_METERS = 1.50f;
    private static final float MIN_HEIGHT_METERS = 0.72f;
    private static final float MAX_HEIGHT_METERS = 2.40f;

    private static final float ANCHOR_DISTANCE_METERS = 1.55f;
    private static final float ANCHOR_LEFT_OFFSET_METERS = -0.85f;
    private static final float ANCHOR_TOP_OFFSET_METERS = 0.92f;
    private static final float RAY_LENGTH_METERS = 4.0f;

    private final Vector3f anchorPosition = new Vector3f();
    private final Quaternionf anchorOrientation = new Quaternionf();
    private final Vector3f userOffset = new Vector3f();
    private boolean initialized;
    private boolean minimized;
    private boolean closed;
    private float widthMeters = DEFAULT_WIDTH_METERS;
    private float expandedHeightMeters = EXPANDED_HEIGHT_METERS;
    private Gesture gesture = Gesture.NONE;
    private float previousPointerX;
    private float previousPointerY;
    private int resizeHorizontalSign;
    private int resizeVerticalSign;

    private enum Gesture { NONE, MOVE, RESIZE }

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
        anchorPosition.set(headPosition).add(topLeftOffset).add(new Vector3f(userOffset).rotate(anchorOrientation));
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
        if (x < 0.0f || x > widthMeters() || yFromTop < 0.0f || yFromTop > heightMeters()) {
            return false;
        }

        panelPoint.set(x, yFromTop);
        return true;
    }

    boolean intersectRayPlane(Vector3f rayOrigin, Quaternionf rayOrientation, Vector2f panelPoint) {
        if (!initialized || closed) {
            return false;
        }
        Quaternionf inverseAnchorOrientation = new Quaternionf(anchorOrientation).conjugate();
        Vector3f localOrigin = new Vector3f(rayOrigin).sub(anchorPosition).rotate(inverseAnchorOrientation);
        Vector3f rayDirection = new Vector3f(0.0f, 0.0f, -1.0f)
                .rotate(rayOrientation).rotate(inverseAnchorOrientation);
        if (Math.abs(rayDirection.z) < 0.0001f) {
            return false;
        }
        float distance = -localOrigin.z / rayDirection.z;
        if (distance < 0.0f || distance > RAY_LENGTH_METERS) {
            return false;
        }
        panelPoint.set(localOrigin.x + rayDirection.x * distance,
                -(localOrigin.y + rayDirection.y * distance));
        return true;
    }

    void beginMove(float panelX, float panelY) {
        gesture = Gesture.MOVE;
        previousPointerX = panelX;
        previousPointerY = panelY;
    }

    void beginResize(float panelX, float panelY, int horizontalSign, int verticalSign) {
        gesture = Gesture.RESIZE;
        previousPointerX = panelX;
        previousPointerY = panelY;
        resizeHorizontalSign = horizontalSign;
        resizeVerticalSign = verticalSign;
    }

    void updateGesture(float panelX, float panelY) {
        if (gesture == Gesture.NONE) {
            return;
        }
        float deltaX = panelX - previousPointerX;
        float deltaY = panelY - previousPointerY;
        if (gesture == Gesture.MOVE) {
            userOffset.add(deltaX, -deltaY, 0.0f);
            anchorPosition.add(new Vector3f(deltaX, -deltaY, 0.0f).rotate(anchorOrientation));
        } else {
            float previousWidth = widthMeters;
            widthMeters = clamp(widthMeters + resizeHorizontalSign * deltaX, MIN_WIDTH_METERS, MAX_WIDTH_METERS);
            float widthDelta = widthMeters - previousWidth;
            if (!minimized) {
                float previousHeight = expandedHeightMeters;
                float resizedHeight = clamp(previousHeight + resizeVerticalSign * deltaY,
                        MIN_HEIGHT_METERS, MAX_HEIGHT_METERS);
                float heightDelta = resizedHeight - previousHeight;
                if (resizeVerticalSign < 0) {
                    anchorPosition.add(new Vector3f(0.0f, heightDelta, 0.0f).rotate(anchorOrientation));
                    userOffset.add(0.0f, heightDelta, 0.0f);
                }
                expandedHeightMeters = resizedHeight;
            }
            if (resizeHorizontalSign < 0) {
                float anchorDeltaX = -widthDelta;
                anchorPosition.add(new Vector3f(anchorDeltaX, 0.0f, 0.0f).rotate(anchorOrientation));
                userOffset.add(anchorDeltaX, 0.0f, 0.0f);
            }
        }
        previousPointerX = panelX;
        previousPointerY = panelY;
    }

    void endGesture() {
        gesture = Gesture.NONE;
    }

    boolean gestureActive() {
        return gesture != Gesture.NONE;
    }

    Vector3f quadCenterPosition() {
        return new Vector3f(anchorPosition)
                .add(new Vector3f(widthMeters() * 0.5f, -heightMeters() * 0.5f, 0.0f)
                        .rotate(anchorOrientation));
    }

    Vector3f anchorPosition() {
        return anchorPosition;
    }

    Quaternionf anchorOrientation() {
        return anchorOrientation;
    }

    float heightMeters() {
        return minimized ? MINIMIZED_HEIGHT_METERS : expandedHeightMeters;
    }

    float widthMeters() {
        return widthMeters;
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

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
