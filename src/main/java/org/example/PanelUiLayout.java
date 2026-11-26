package org.example;

import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;

final class PanelUiLayout {
    static final int CORNER_RESIZE_SIZE = 150;
    static final int TREE_ROW_HEIGHT = 74;
    static final int TREE_LEFT = 48;
    static final int TREE_INDENT = 42;
    private static final int TOOLBAR_BUTTON_WIDTH = 86;
    private static final int TOOLBAR_BUTTON_HEIGHT = 76;
    private static final int TOOLBAR_BUTTON_GAP = 12;

    final int logicalWidth;
    final int pixelWidth;
    final int pixelHeight;
    final Rectangle panel;
    final Rectangle toolbar;
    final Rectangle minimizeButton;
    final Rectangle closeButton;
    final Rectangle searchField;
    final Rectangle treeViewport;
    final Rectangle scrollTrack;
    final Rectangle statusBar;

    PanelUiLayout(int pixelWidth, int pixelHeight) {
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
        logicalWidth = Math.max(1, Math.round(pixelHeight
                * AnchoredWindow.DEFAULT_WIDTH_METERS / AnchoredWindow.EXPANDED_HEIGHT_METERS));
        panel = new Rectangle(8, 8, logicalWidth - 16, pixelHeight - 16);
        toolbar = new Rectangle(24, 24, logicalWidth - 48, 204);
        int buttonX = CORNER_RESIZE_SIZE + TOOLBAR_BUTTON_GAP;
        minimizeButton = new Rectangle(buttonX, 18, TOOLBAR_BUTTON_WIDTH, TOOLBAR_BUTTON_HEIGHT);
        closeButton = new Rectangle(logicalWidth - buttonX - TOOLBAR_BUTTON_WIDTH,
                18, TOOLBAR_BUTTON_WIDTH, TOOLBAR_BUTTON_HEIGHT);
        searchField = new Rectangle(38, 236, logicalWidth - 76, 84);
        statusBar = new Rectangle(32, pixelHeight - 64, logicalWidth - 64, 38);
        int treeTop = searchField.y + searchField.height + 24;
        int treeBottom = statusBar.y - 14;
        treeViewport = new Rectangle(38, treeTop, logicalWidth - 76, Math.max(1, treeBottom - treeTop));
        // Keep a forgiving pointer target around the narrow visual scrollbar.
        scrollTrack = new Rectangle(treeViewport.x + treeViewport.width - 88,
                treeViewport.y, 88, treeViewport.height);
    }

    RoundRectangle2D.Float roundedPanelClip() {
        return new RoundRectangle2D.Float(panel.x, panel.y, panel.width, panel.height, 30, 30);
    }

    boolean isResizeCorner(int x, int y) {
        boolean horizontalEdge = x >= 0 && x <= CORNER_RESIZE_SIZE
                || x >= logicalWidth - CORNER_RESIZE_SIZE && x <= logicalWidth;
        boolean verticalEdge = y >= 0 && y <= CORNER_RESIZE_SIZE
                || y >= pixelHeight - CORNER_RESIZE_SIZE && y <= pixelHeight;
        if (!horizontalEdge || !verticalEdge) {
            return false;
        }
        return !minimizeButton.contains(x, y) && !closeButton.contains(x, y);
    }

    boolean isToolbarButton(int x, int y) {
        return minimizeButton.contains(x, y) || closeButton.contains(x, y);
    }

    boolean isMinimizeButtonX(int x) {
        return x >= minimizeButton.x && x <= minimizeButton.x + minimizeButton.width;
    }

    boolean isCloseButtonX(int x) {
        return x >= closeButton.x && x <= closeButton.x + closeButton.width;
    }

    boolean isToolbarDrag(int x, int y) {
        return toolbar.contains(x, y) && y <= 225
                && x > CORNER_RESIZE_SIZE && x < logicalWidth - CORNER_RESIZE_SIZE
                && !isToolbarButton(x, y);
    }

    boolean isRightResizeCorner(int x) {
        return x >= logicalWidth - CORNER_RESIZE_SIZE;
    }

    boolean isBottomResizeCorner(int y) {
        return y >= pixelHeight - CORNER_RESIZE_SIZE;
    }

    int toLogicalX(float panelMetersX, float panelWidthMeters) {
        return Math.round(panelMetersX / panelWidthMeters * logicalWidth);
    }

    int toPixelY(float panelMetersY, float panelHeightMeters) {
        return Math.round(panelMetersY / panelHeightMeters * pixelHeight);
    }
}
