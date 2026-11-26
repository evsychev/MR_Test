package org.example.state;

import java.util.Set;

public record UserAppState(float widthMeters, float heightMeters, float offsetX, float offsetY,
                    float offsetZ, boolean minimized, String selectedNodeId,
                    Set<String> expandedNodeIds, int treeScrollOffset) {
    public static final float DEFAULT_WIDTH_METERS = 0.78f;
    public static final float DEFAULT_HEIGHT_METERS = 1.40f;

    public static UserAppState defaults() {
        return new UserAppState(DEFAULT_WIDTH_METERS,
                DEFAULT_HEIGHT_METERS, 0, 0, 0, false, "", Set.of(), 0);
    }
}
