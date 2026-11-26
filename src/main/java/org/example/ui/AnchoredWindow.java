package org.example.ui;

import org.example.state.UserAppState;

import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.openxr.XrView;

public final class AnchoredWindow {
    static final float DEFAULT_WIDTH_METERS = UserAppState.DEFAULT_WIDTH_METERS;
    static final float EXPANDED_HEIGHT_METERS = UserAppState.DEFAULT_HEIGHT_METERS;
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
    private final Vector3f gesturePlanePosition = new Vector3f();
    private final Quaternionf gesturePlaneOrientation = new Quaternionf();
    private final Vector3f gestureStartWorldHit = new Vector3f();
    private final Vector3f gestureStartUserOffset = new Vector3f();
    private float gestureStartWidth;
    private float gestureStartHeight;
    private int resizeHorizontalSign;
    private int resizeVerticalSign;

    private final Quaternionf inverseAnchor = new Quaternionf();
    private final Vector3f localOrigin = new Vector3f();
    private final Vector3f localDirection = new Vector3f();

    private enum Gesture { NONE, MOVE, RESIZE }

    public void updateFromViews(XrView.Buffer views) {
        if (views.remaining() == 0) {
            return;
        }

        var firstPose = views.get(0).pose();
        var firstPosition = firstPose.position$();
        var firstOrientation = firstPose.orientation();
        anchorPosition.set(firstPosition.x(), firstPosition.y(), firstPosition.z());
        anchorOrientation.set(firstOrientation.x(), firstOrientation.y(), firstOrientation.z(), firstOrientation.w())
                .normalize();

        if (views.remaining() > 1) {
            var secondPosition = views.get(1).pose().position$();
            anchorPosition.add(secondPosition.x(), secondPosition.y(), secondPosition.z()).mul(0.5f);
        }

        inverseAnchor.set(anchorOrientation).conjugate();
        localOrigin.set(ANCHOR_LEFT_OFFSET_METERS, ANCHOR_TOP_OFFSET_METERS, -ANCHOR_DISTANCE_METERS)
                .add(userOffset).rotate(anchorOrientation);
        anchorPosition.add(localOrigin);
        initialized = true;
    }

    void restore(UserAppState state) {
        widthMeters = clamp(state.widthMeters(), MIN_WIDTH_METERS, MAX_WIDTH_METERS);
        expandedHeightMeters = clamp(state.heightMeters(), MIN_HEIGHT_METERS, MAX_HEIGHT_METERS);
        userOffset.set(state.offsetX(), state.offsetY(), state.offsetZ());
        minimized = state.minimized();
    }

    UserAppState snapshot(String selectedId, java.util.Set<String> expandedIds, int scrollOffset) {
        return new UserAppState(widthMeters, expandedHeightMeters, userOffset.x, userOffset.y, userOffset.z,
                minimized, selectedId, java.util.Set.copyOf(expandedIds), scrollOffset);
    }

    boolean intersectRay(Vector3f rayOrigin, Quaternionf rayOrientation, Vector2f panelPoint) {
        return intersectRayPlane(rayOrigin, rayOrientation, panelPoint)
                && panelPoint.x >= 0 && panelPoint.x <= widthMeters()
                && panelPoint.y >= 0 && panelPoint.y <= heightMeters();
    }

    boolean intersectRayPlane(Vector3f rayOrigin, Quaternionf rayOrientation, Vector2f panelPoint) {
        if (!initialized || closed) {
            return false;
        }
        localOrigin.set(rayOrigin).sub(anchorPosition).rotate(inverseAnchor);
        localDirection.set(0, 0, -1).rotate(rayOrientation).rotate(inverseAnchor);
        if (Math.abs(localDirection.z) < 0.0001f) {
            return false;
        }
        float distance = -localOrigin.z / localDirection.z;
        if (distance < 0.0f || distance > RAY_LENGTH_METERS) {
            return false;
        }
        panelPoint.set(localOrigin.x + localDirection.x * distance,
                -(localOrigin.y + localDirection.y * distance));
        return true;
    }

    void beginMove(float panelX, float panelY) {
        gesture = Gesture.MOVE;
        beginGesturePlane(panelX, panelY);
    }

    void beginResize(float panelX, float panelY, int horizontalSign, int verticalSign) {
        gesture = Gesture.RESIZE;
        beginGesturePlane(panelX, panelY);
        resizeHorizontalSign = horizontalSign;
        resizeVerticalSign = verticalSign;
    }

    private void beginGesturePlane(float panelX, float panelY) {
        gesturePlanePosition.set(anchorPosition);
        gesturePlaneOrientation.set(anchorOrientation);
        gestureStartWidth = widthMeters;
        gestureStartHeight = expandedHeightMeters;
        gestureStartUserOffset.set(userOffset);
        gestureStartWorldHit.set(panelX, -panelY, 0.0f).rotate(gesturePlaneOrientation).add(gesturePlanePosition);
    }

    void updateGestureFromRay(Vector3f rayOrigin, Quaternionf rayOrientation) {
        if (gesture == Gesture.NONE) {
            return;
        }
        Vector3f hit = new Vector3f();
        if (!intersectGesturePlane(rayOrigin, rayOrientation, hit)) {
            return;
        }
        Vector3f movement = hit.sub(gestureStartWorldHit).rotate(new Quaternionf(gesturePlaneOrientation).conjugate());
        float deltaX = movement.x;
        float deltaY = -movement.y;
        if (gesture == Gesture.MOVE) {
            Vector3f desiredUserOffset = new Vector3f(gestureStartUserOffset)
                    .add(new Vector3f(deltaX, -deltaY, 0.0f).rotate(gesturePlaneOrientation)
                            .rotate(new Quaternionf(anchorOrientation).conjugate()));
            Vector3f offsetChange = new Vector3f(desiredUserOffset).sub(userOffset);
            userOffset.set(desiredUserOffset);
            anchorPosition.add(offsetChange.rotate(anchorOrientation));
        } else {
            float previousWidth = widthMeters;
            widthMeters = clamp(gestureStartWidth + resizeHorizontalSign * deltaX,
                    MIN_WIDTH_METERS, MAX_WIDTH_METERS);
            float widthDelta = widthMeters - previousWidth;
            if (!minimized) {
                float previousHeight = expandedHeightMeters;
                float resizedHeight = clamp(gestureStartHeight + resizeVerticalSign * deltaY,
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
    }

    private boolean intersectGesturePlane(Vector3f rayOrigin, Quaternionf rayOrientation, Vector3f hit) {
        Quaternionf inverseOrientation = new Quaternionf(gesturePlaneOrientation).conjugate();
        Vector3f localOrigin = new Vector3f(rayOrigin).sub(gesturePlanePosition).rotate(inverseOrientation);
        Vector3f localDirection = new Vector3f(0.0f, 0.0f, -1.0f)
                .rotate(rayOrientation).rotate(inverseOrientation);
        if (Math.abs(localDirection.z) < 0.0001f) {
            return false;
        }
        float distance = -localOrigin.z / localDirection.z;
        if (distance < 0.0f || distance > RAY_LENGTH_METERS) {
            return false;
        }
        hit.set(localOrigin.x + localDirection.x * distance,
                localOrigin.y + localDirection.y * distance, 0.0f)
                .rotate(gesturePlaneOrientation).add(gesturePlanePosition);
        return true;
    }

    void endGesture() {
        gesture = Gesture.NONE;
    }

    public Vector3f quadCenterPosition() {
        return new Vector3f(anchorPosition)
                .add(new Vector3f(widthMeters() * 0.5f, -heightMeters() * 0.5f, 0.0f)
                        .rotate(anchorOrientation));
    }

    public Vector3f anchorPosition() {
        return anchorPosition;
    }

    public Quaternionf anchorOrientation() {
        return anchorOrientation;
    }

    public float heightMeters() {
        return minimized ? MINIMIZED_HEIGHT_METERS : expandedHeightMeters;
    }

    public float widthMeters() {
        return widthMeters;
    }

    public boolean minimized() {
        return minimized;
    }

    void toggleMinimized() {
        minimized = !minimized;
    }

    public boolean closed() {
        return closed;
    }

    void closeWindow() {
        closed = true;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
