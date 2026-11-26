package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class TechnicalObjectTreeUI implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TechnicalObjectTreeUI.class);
    private static final int ROW_HEIGHT = PanelUiLayout.TREE_ROW_HEIGHT;
    private static final int LEFT = PanelUiLayout.TREE_LEFT;
    private static final int INDENT = PanelUiLayout.TREE_INDENT;
    private static final long DOUBLE_CLICK_INTERVAL_NANOS = 450_000_000L;
    private final int pixelWidth;
    private final int pixelHeight;
    private final PanelUiLayout layout;
    private final int logicalWidth;
    private final List<TechnicalObjectTreeDataSource.TreeNode> roots;
    private final BufferedImage image;
    private final ByteBuffer rgbaPixels;
    private final Set<String> expanded = new HashSet<>();
    private final List<TechnicalObjectTreeView.TreeRow> visibleRows = new ArrayList<>();
    private final SearchKeyboard searchKeyboard = new SearchKeyboard();
    private final TechnicalObjectTreeView treeView = new TechnicalObjectTreeView();
    private final PanelChrome chrome = new PanelChrome();
    private final SearchField searchField = new SearchField();
    private final PanelStatusBar statusBar = new PanelStatusBar();
    private final TechnicalObjectDetailsDialog detailsDialog = new TechnicalObjectDetailsDialog();
    private final UserProfileDialog userProfileDialog = new UserProfileDialog();
    private final UserProfile userProfile = UserProfile.loadMock();
    private final UserStateRepository stateRepository;
    private final Vector2f activeScrollHit = new Vector2f();
    private final Vector2f leftHit = new Vector2f();
    private final Vector2f rightHit = new Vector2f();

    private String selectedId;
    private String hoveredId = "";
    private TechnicalObjectTreeDataSource.TreeNode detailsNode;
    private boolean userProfileOpen;
    private String lastClickedNodeId;
    private long lastNodeClickNanos;
    private int detailsPage;
    private int treeScrollOffset;
    private int activeScrollHand = -1;
    private float scrollGrabOffsetY;
    private boolean previousLeftSelect;
    private boolean previousRightSelect;
    private int activeGestureHand = -1;
    private boolean dirty = true;
    private boolean closed;
    private boolean stateStorageAvailable;

    TechnicalObjectTreeUI(int pixelWidth, int pixelHeight) {
        TechnicalObjectTreeDataSource.TreeData treeData = TechnicalObjectTreeDataSource.loadTree();
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
        layout = new PanelUiLayout(pixelWidth, pixelHeight);
        roots = treeData.roots();
        expanded.addAll(treeData.expandedIds());
        selectedId = treeData.selectedId();
        logicalWidth = layout.logicalWidth;
        UserStateRepository openedRepository = null;
        UserAppState savedState = UserAppState.defaults();
        try {
            String userId = userProfile.value("id");
            if (!userId.isBlank()) {
                openedRepository = UserStateRepository.open();
                savedState = openedRepository.load(userId);
                stateStorageAvailable = true;
            } else {
                LOGGER.warn("Profile has no stable user ID; application state will not be persisted");
            }
        } catch (Exception exception) {
            LOGGER.error("Could not initialize per-user state storage; using default state", exception);
            if (openedRepository != null) {
                try { openedRepository.close(); } catch (Exception ignored) { }
                openedRepository = null;
            }
        }
        stateRepository = openedRepository;
        loadedWindowState = savedState;
        if (stateStorageAvailable) {
            selectedId = savedState.selectedNodeId().isBlank() ? selectedId : savedState.selectedNodeId();
            expanded.addAll(savedState.expandedNodeIds());
            treeScrollOffset = savedState.treeScrollOffset();
        }
        image = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB_PRE);
        rgbaPixels = MemoryUtil.memAlloc(pixelWidth * pixelHeight * Integer.BYTES);
    }

    boolean closed() {
        return closed;
    }

    boolean refreshPixels() {
        if (!dirty) {
            return false;
        }
        paintImage();
        rgbaPixels.clear();
        int[] argb = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        for (int pixel : argb) {
            rgbaPixels.put((byte) (pixel >>> 16));
            rgbaPixels.put((byte) (pixel >>> 8));
            rgbaPixels.put((byte) pixel);
            rgbaPixels.put((byte) (pixel >>> 24));
        }
        rgbaPixels.flip();
        dirty = false;
        return true;
    }

    ByteBuffer rgbaPixels() {
        return rgbaPixels;
    }

    void updateInteraction(AnchoredWindow window,
                           boolean leftSelect, Vector3f leftPosition, Quaternionf leftOrientation,
                           boolean leftPoseValid,
                           boolean rightSelect, Vector3f rightPosition, Quaternionf rightOrientation,
                           boolean rightPoseValid) {
        if (closed) {
            previousLeftSelect = leftSelect;
            previousRightSelect = rightSelect;
            return;
        }
        if (stateStorageAvailable && !windowStateRestored) {
            window.restore(loadedWindowState);
            lastMinimized = window.minimized();
            windowStateRestored = true;
            dirty = true;
        }

        String nextHoveredId = "";
        boolean leftClick = leftSelect && !previousLeftSelect;
        boolean rightClick = rightSelect && !previousRightSelect;
        // Meta XR Simulator can mirror the selected mouse click into both controller action sets.
        // Prefer the explicitly selected right pointer to avoid applying a tree toggle twice.
        if (leftSelect && rightSelect) {
            leftClick = false;
        }
        if (activeScrollHand >= 0) {
            boolean held = activeScrollHand == 0 ? leftSelect : rightSelect;
            boolean poseValid = activeScrollHand == 0 ? leftPoseValid : rightPoseValid;
            Vector3f position = activeScrollHand == 0 ? leftPosition : rightPosition;
            Quaternionf orientation = activeScrollHand == 0 ? leftOrientation : rightOrientation;
            if (held && poseValid && window.intersectRayPlane(position, orientation, activeScrollHit)) {
                scrollTreeFromThumb(toLogicalY(activeScrollHit.y, window));
            } else if (!held) {
                LOGGER.info("Tree scrollbar drag released at row offset {}", treeScrollOffset);
                activeScrollHand = -1;
                persistState(window);
            }
            hoveredId = "tree-scroll";
            previousLeftSelect = leftSelect;
            previousRightSelect = rightSelect;
            dirty = true;
            return;
        }
        if (activeGestureHand >= 0) {
            boolean held = activeGestureHand == 0 ? leftSelect : rightSelect;
            boolean poseValid = activeGestureHand == 0 ? leftPoseValid : rightPoseValid;
            Vector3f position = activeGestureHand == 0 ? leftPosition : rightPosition;
            Quaternionf orientation = activeGestureHand == 0 ? leftOrientation : rightOrientation;
            if (held && poseValid) {
                window.updateGestureFromRay(position, orientation);
            } else if (!held) {
                window.endGesture();
                activeGestureHand = -1;
                persistState(window);
            }
            previousLeftSelect = leftSelect;
            previousRightSelect = rightSelect;
            dirty = true;
            return;
        }

        if (leftPoseValid && window.intersectRay(leftPosition, leftOrientation, leftHit)) {
            int x = toLogicalX(leftHit.x, window);
            int y = toLogicalY(leftHit.y, window);
            nextHoveredId = targetAt(x, y);
            if (leftClick) {
                if (!beginGestureAt(0, x, y, leftHit, window)) {
                    LOGGER.info("Left select at panel pixel ({}, {}), target={}", x, y, targetAt(x, y));
                    activateAt(x, y, window);
                }
            }
        }
        if (rightPoseValid && window.intersectRay(rightPosition, rightOrientation, rightHit)) {
            int x = toLogicalX(rightHit.x, window);
            int y = toLogicalY(rightHit.y, window);
            String rightHoveredId = targetAt(x, y);
            if (!rightHoveredId.isEmpty()) {
                nextHoveredId = rightHoveredId;
            }
            if (rightClick) {
                if (!beginGestureAt(1, x, y, rightHit, window)) {
                    LOGGER.info("Right select at panel pixel ({}, {}), target={}", x, y, rightHoveredId);
                    activateAt(x, y, window);
                }
            }
        }
        if (!nextHoveredId.equals(hoveredId)) {
            hoveredId = nextHoveredId;
            dirty = true;
        }
        previousLeftSelect = leftSelect;
        previousRightSelect = rightSelect;
    }

    private int toLogicalX(float meters, AnchoredWindow window) {
        return layout.toLogicalX(meters, window.widthMeters());
    }

    private int toLogicalY(float meters, AnchoredWindow window) {
        return layout.toPixelY(meters, window.heightMeters());
    }

    private boolean beginGestureAt(int hand, int x, int y, Vector2f panelPoint, AnchoredWindow window) {
        if (detailsNode != null || userProfileOpen) {
            return false;
        }
        // The on-panel keyboard is modal and occupies the lower resize and scrollbar hit zones.
        if (!window.minimized() && searchKeyboard.occupies(y, pixelHeight)) {
            return false;
        }
        if (hasTreeOverflow() && layout.scrollTrack.contains(x, y)) {
            beginScrollGesture(hand, y);
            LOGGER.info("Tree scrollbar drag started by {} hand at panel y={} (row offset {})",
                    hand == 0 ? "left" : "right", y, treeScrollOffset);
            hoveredId = "tree-scroll";
            dirty = true;
            return true;
        }
        if (window.minimized()) {
            if (!layout.isMinimizeButtonX(x) && !layout.isCloseButtonX(x)) {
                window.beginMove(panelPoint.x, panelPoint.y);
                activeGestureHand = hand;
                return true;
            }
            return false;
        }
        if (layout.isResizeCorner(x, y)) {
            boolean right = layout.isRightResizeCorner(x);
            boolean bottom = layout.isBottomResizeCorner(y);
            window.beginResize(panelPoint.x, panelPoint.y, right ? 1 : -1, bottom ? 1 : -1);
            activeGestureHand = hand;
            LOGGER.info("Resize gesture started at {}-{} corner, panel point ({}, {})",
                    bottom ? "bottom" : "top", right ? "right" : "left", panelPoint.x, panelPoint.y);
            return true;
        }
        if (layout.isToolbarButton(x, y)) {
            return false;
        }
        if (layout.isToolbarDrag(x, y)) {
            window.beginMove(panelPoint.x, panelPoint.y);
            activeGestureHand = hand;
            LOGGER.info("Move gesture started at panel point ({}, {})", panelPoint.x, panelPoint.y);
            return true;
        }
        return false;
    }

    private boolean isResizeHandleHit(int x, int y) {
        return layout.isResizeCorner(x, y);
    }

    private boolean isExpandedHeaderButtonHit(int x, int y) {
        return layout.isToolbarButton(x, y);
    }

    private void activateAt(int x, int y, AnchoredWindow window) {
        if (window.minimized()) {
            if (userProfileOpen) {
                if (x >= logicalWidth - 124 && x <= logicalWidth - 52 && y >= 130 && y <= 202) {
                    userProfileOpen = false;
                }
            } else if (layout.isUserButton(x, y)) {
                userProfileOpen = true;
                dirty = true;
                return;
            } else if (layout.isMinimizeButtonX(x)) {
                window.toggleMinimized();
                detailsNode = null;
            } else if (layout.isCloseButtonX(x)) {
                window.closeWindow();
                closed = true;
            }
            dirty = true;
            persistState(window);
            return;
        }

        if (detailsNode != null) {
            if (x > logicalWidth - 150 && y < 195) {
                detailsNode = null;
            } else if (y >= pixelHeight - 145 && x < logicalWidth / 2 && detailsPage > 0) {
                detailsPage--;
            } else if (y >= pixelHeight - 145 && x >= logicalWidth / 2
                    && detailsPage + 1 < detailsDialog.pageCount(detailsNode)) {
                detailsPage++;
            }
            dirty = true;
            persistState(window);
            return;
        }

        if (userProfileOpen) {
            if (x >= logicalWidth - 124 && x <= logicalWidth - 52 && y >= 130 && y <= 202) {
                userProfileOpen = false;
                dirty = true;
            }
            return;
        }

        if (layout.isUserButton(x, y)) {
            userProfileOpen = true;
            LOGGER.info("Opened user profile dialog for username={}", userProfile.value("username"));
            dirty = true;
            return;
        }

        if (isExpandedHeaderButtonHit(x, y)) {
            if (x < logicalWidth / 2) {
                window.toggleMinimized();
                searchKeyboard.hide();
                detailsNode = null;
                dirty = true;
                persistState(window);
            } else {
                window.closeWindow();
                closed = true;
            }
            return;
        }

        if (layout.searchField.contains(x, y)) {
            searchKeyboard.show();
            dirty = true;
            return;
        }

        if (searchKeyboard.occupies(y, pixelHeight)) {
            searchKeyboard.activate(x, y);
            dirty = true;
            return;
        }

        if (hasTreeOverflow() && layout.scrollTrack.contains(x, y)) {
            scrollTreeAt(y);
            dirty = true;
            return;
        }

        if (!layout.treeViewport.contains(x, y)) {
            return;
        }
        int rowIndex = treeScrollOffset + (y - layout.treeViewport.y) / ROW_HEIGHT;
        if (rowIndex < 0 || rowIndex >= visibleRows.size()) {
            return;
        }
        TechnicalObjectTreeView.TreeRow row = visibleRows.get(rowIndex);
        if (isExpandHit(row, x)) {
            if (!expanded.add(row.node().id)) {
                expanded.remove(row.node().id);
            }
            lastClickedNodeId = null;
            LOGGER.info("Tree node {} {} by single click", row.node().id,
                    expanded.contains(row.node().id) ? "expanded" : "collapsed");
        } else {
            selectedId = row.node().id;
            long now = System.nanoTime();
            if (row.node().id.equals(lastClickedNodeId)
                    && now - lastNodeClickNanos <= DOUBLE_CLICK_INTERVAL_NANOS) {
                detailsNode = row.node();
                detailsPage = 0;
                searchKeyboard.hide();
                lastClickedNodeId = null;
                LOGGER.info("Opened technical object details for rtoId={} toCode={}",
                        row.node().id, row.node().fields.getOrDefault("toCode", ""));
            } else {
                lastClickedNodeId = row.node().id;
                lastNodeClickNanos = now;
            }
        }
        dirty = true;
        persistState(window);
    }

    private String targetAt(int x, int y) {
        if (userProfileOpen) {
            return x >= logicalWidth - 124 && x <= logicalWidth - 52 && y >= 130 && y <= 202
                    ? "profile-close" : "user-profile-dialog";
        }
        if (detailsNode != null) {
            if (x > logicalWidth - 150 && y < 195) {
                return "details-close";
            }
            if (y >= pixelHeight - 145) {
                return x < logicalWidth / 2 ? "details-previous" : "details-next";
            }
            return "details";
        }
        if (!lastMinimized && searchKeyboard.occupies(y, pixelHeight)) {
            return searchKeyboard.targetAt(x, y);
        }
        if (layout.isUserButton(x, y)) {
            return "user-profile";
        }
        if (lastMinimized) {
            if (layout.isUserButton(x, y)) {
                return "user-profile";
            }
            return layout.isMinimizeButtonX(x) ? "minimize"
                    : layout.isCloseButtonX(x) ? "close" : "move";
        } else if (isExpandedHeaderButtonHit(x, y)) {
            return x < logicalWidth / 2 ? "minimize" : "close";
        }
        if (!lastMinimized && isResizeHandleHit(x, y)) {
            boolean right = layout.isRightResizeCorner(x);
            boolean bottom = layout.isBottomResizeCorner(y);
            return "resize-" + (bottom ? "bottom-" : "top-") + (right ? "right" : "left");
        }
        if (!lastMinimized && layout.isToolbarDrag(x, y)) {
            return "move";
        }
        if (layout.searchField.contains(x, y)) {
            return "search";
        }
        if (!lastMinimized && hasTreeOverflow() && layout.scrollTrack.contains(x, y)) {
            return "tree-scroll";
        }
        if (!layout.treeViewport.contains(x, y)) {
            return "";
        }
        int rowIndex = treeScrollOffset + (y - layout.treeViewport.y) / ROW_HEIGHT;
        if (rowIndex < 0 || rowIndex >= visibleRows.size()) {
            return "";
        }
        TechnicalObjectTreeView.TreeRow row = visibleRows.get(rowIndex);
        return isExpandHit(row, x) ? "expand-" + row.node().id : "row-" + row.node().id;
    }

    private static boolean isExpandHit(TechnicalObjectTreeView.TreeRow row, int x) {
        int rowStart = LEFT + row.depth() * INDENT;
        return row.hasChildren() && x <= rowStart + 82;
    }

    private void scrollTreeAt(int y) {
        int pageSize = visibleRowCountInView();
        int maxOffset = Math.max(0, visibleRows.size() - pageSize);
        if (maxOffset == 0) {
            treeScrollOffset = 0;
            return;
        }
        float fraction = Math.max(0.0f, Math.min(1.0f,
                (y - layout.scrollTrack.y) / (float) layout.scrollTrack.height));
        treeScrollOffset = Math.round(fraction * maxOffset);
    }

    private void beginScrollGesture(int hand, int pointerY) {
        int pageSize = visibleRowCountInView();
        int maxOffset = Math.max(0, visibleRows.size() - pageSize);
        int thumbHeight = Math.max(54, layout.scrollTrack.height * pageSize / visibleRows.size());
        thumbHeight = Math.min(layout.scrollTrack.height, thumbHeight);
        int thumbY = layout.scrollTrack.y + (layout.scrollTrack.height - thumbHeight)
                * treeScrollOffset / Math.max(1, maxOffset);
        if (pointerY >= thumbY && pointerY <= thumbY + thumbHeight) {
            scrollGrabOffsetY = pointerY - thumbY;
        } else {
            scrollGrabOffsetY = thumbHeight / 2.0f;
        }
        activeScrollHand = hand;
        scrollTreeFromThumb(pointerY);
    }

    private void scrollTreeFromThumb(int pointerY) {
        int pageSize = visibleRowCountInView();
        int maxOffset = Math.max(0, visibleRows.size() - pageSize);
        int thumbHeight = Math.max(54, layout.scrollTrack.height * pageSize / visibleRows.size());
        thumbHeight = Math.min(layout.scrollTrack.height, thumbHeight);
        int maxThumbY = layout.scrollTrack.y + layout.scrollTrack.height - thumbHeight;
        int thumbTop = Math.max(layout.scrollTrack.y,
                Math.min(maxThumbY, Math.round(pointerY - scrollGrabOffsetY)));
        float fraction = (thumbTop - layout.scrollTrack.y)
                / (float) Math.max(1, maxThumbY - layout.scrollTrack.y);
        treeScrollOffset = Math.round(fraction * maxOffset);
    }

    private int visibleRowCountInView() {
        return Math.max(1, layout.treeViewport.height / ROW_HEIGHT);
    }

    private UserAppState loadedWindowState = UserAppState.defaults();
    private boolean windowStateRestored;

    private void persistState(AnchoredWindow window) {
        if (!stateStorageAvailable || stateRepository == null) return;
        try {
            stateRepository.save(userProfile.value("id"),
                    window.snapshot(selectedId, expanded, treeScrollOffset));
        } catch (Exception exception) {
            stateStorageAvailable = false;
            LOGGER.error("Could not save application state for the active user", exception);
        }
    }

    private boolean hasTreeOverflow() {
        return visibleRows.size() > visibleRowCountInView();
    }

    private String selectedNodeName() {
        for (TechnicalObjectTreeView.TreeRow row : visibleRows) {
            if (row.node().id.equals(selectedId)) {
                return row.node().label;
            }
        }
        return "";
    }

    private void paintImage() {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(0, 0, pixelWidth, pixelHeight);
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.scale(pixelWidth / (double) logicalWidth, 1.0);
            Shape originalClip = graphics.getClip();
            graphics.clip(layout.roundedPanelClip());

            if (lastMinimized) {
                drawMinimized(graphics);
            } else {
                drawWindow(graphics);
            }
            if (userProfileOpen) {
                userProfileDialog.draw(graphics, layout, userProfile, hoveredId);
            }
            graphics.setClip(originalClip);
        } finally {
            graphics.dispose();
        }
    }

    private boolean lastMinimized;

    void markWindowStateChanged(boolean minimized) {
        if (lastMinimized != minimized) {
            lastMinimized = minimized;
            dirty = true;
        }
    }

    private void drawWindow(Graphics2D graphics) {
        chrome.drawExpanded(graphics, layout, hoveredId);

        searchField.draw(graphics, layout, searchKeyboard.text(), searchKeyboard.visible(),
                "search".equals(hoveredId));
        drawTree(graphics);
        if (detailsNode != null) {
            detailsDialog.draw(graphics, layout, detailsNode, detailsPage, hoveredId);
        }
        statusBar.draw(graphics, layout, visibleRowCountInView(), treeScrollOffset,
                visibleRows.size(), selectedNodeName());
        if (searchKeyboard.visible()) {
            searchKeyboard.draw(graphics, logicalWidth, pixelHeight, hoveredId);
        }
    }

    private void drawMinimized(Graphics2D graphics) {
        chrome.drawMinimized(graphics, layout, hoveredId);
    }

    private void drawTree(Graphics2D graphics) {
        visibleRows.clear();
        String query = searchKeyboard.text().strip().toLowerCase(Locale.ROOT);
        for (TechnicalObjectTreeDataSource.TreeNode root : roots) {
            appendVisibleRows(root, 0, query);
        }
        int pageSize = visibleRowCountInView();
        treeScrollOffset = Math.max(0, Math.min(treeScrollOffset, Math.max(0, visibleRows.size() - pageSize)));
        treeView.draw(graphics, layout, visibleRows, selectedId, hoveredId, treeScrollOffset);
    }

    private void appendVisibleRows(TechnicalObjectTreeDataSource.TreeNode node, int depth, String query) {
        boolean descendantMatches = containsMatch(node, query);
        if (!descendantMatches) {
            return;
        }
        boolean isExpanded = query.isEmpty() ? expanded.contains(node.id) : true;
        visibleRows.add(new TechnicalObjectTreeView.TreeRow(node, depth, !node.children.isEmpty(), isExpanded));
        if (isExpanded) {
            for (TechnicalObjectTreeDataSource.TreeNode child : node.children) {
                appendVisibleRows(child, depth + 1, query);
            }
        }
    }

    private boolean containsMatch(TechnicalObjectTreeDataSource.TreeNode node, String query) {
        if (query.isEmpty() || node.searchText().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        for (TechnicalObjectTreeDataSource.TreeNode child : node.children) {
            if (containsMatch(child, query)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
        MemoryUtil.memFree(rgbaPixels);
        if (stateRepository != null) {
            try {
                stateRepository.close();
            } catch (Exception exception) {
                LOGGER.warn("Could not close user state database cleanly", exception);
            }
        }
    }



}
