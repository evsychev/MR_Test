package org.example;

import java.util.Set;

record UserAppState(float widthMeters, float heightMeters, float offsetX, float offsetY,
                    float offsetZ, boolean minimized, String selectedNodeId,
                    Set<String> expandedNodeIds, int treeScrollOffset) {
    static UserAppState defaults() {
        return new UserAppState(AnchoredWindow.DEFAULT_WIDTH_METERS,
                AnchoredWindow.EXPANDED_HEIGHT_METERS, 0, 0, 0, false, "", Set.of(), 0);
    }
}
