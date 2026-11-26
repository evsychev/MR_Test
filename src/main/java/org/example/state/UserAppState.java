package org.example.state;

import java.util.Set;

public record UserAppState(float widthMeters, float heightMeters, float offsetX, float offsetY,
                    float offsetZ, boolean minimized, String selectedNodeId,
                    Set<String> expandedNodeIds, int treeScrollOffset, String language) {
    public UserAppState(float widthMeters, float heightMeters, float offsetX, float offsetY,
                        float offsetZ, boolean minimized, String selectedNodeId,
                        Set<String> expandedNodeIds, int treeScrollOffset) {
        this(widthMeters, heightMeters, offsetX, offsetY, offsetZ, minimized, selectedNodeId,
                expandedNodeIds, treeScrollOffset, "");
    }

    public UserAppState withLanguage(String value) {
        return new UserAppState(widthMeters, heightMeters, offsetX, offsetY, offsetZ, minimized,
                selectedNodeId, expandedNodeIds, treeScrollOffset, value);
    }

    public static final float DEFAULT_WIDTH_METERS = 0.78f;
    public static final float DEFAULT_HEIGHT_METERS = 1.40f;

    public static UserAppState defaults() {
        return new UserAppState(DEFAULT_WIDTH_METERS,
                DEFAULT_HEIGHT_METERS, 0, 0, 0, false, "", Set.of(), 0);
    }
}
