package org.example.ui;

import java.awt.Graphics2D;
import java.nio.ByteBuffer;
import java.util.List;

import org.example.profile.UserProfile;
import org.example.state.ProfileStateStore;
import org.example.state.UserAppState;
import org.example.tree.TechnicalObject;
import org.example.tree.TechnicalObjectTreeDataSource;
import org.example.tree.TreeRow;
import org.example.tree.TreeRows;

import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TechnicalObjectTreeUI implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TechnicalObjectTreeUI.class);
    private static final int ROW_HEIGHT = PanelUiLayout.TREE_ROW_HEIGHT;
    private static final int LEFT = PanelUiLayout.TREE_LEFT;
    private static final int INDENT = PanelUiLayout.TREE_INDENT;
    private static final long DOUBLE_CLICK_INTERVAL_NANOS = 450_000_000L;
    private final int pixelHeight;
    private final PanelUiLayout layout;
    private final int logicalWidth;
    private final TreeRows treeRows;
    private final PanelTexture texture;
    private List<TreeRow> visibleRows = List.of();
    private final SearchKeyboard searchKeyboard = new SearchKeyboard();
    private final TechnicalObjectTreeView treeView = new TechnicalObjectTreeView();
    private final PanelChrome chrome = new PanelChrome();
    private final SearchField searchField = new SearchField();
    private final PanelStatusBar statusBar = new PanelStatusBar();
    private final TechnicalObjectDetailsDialog detailsDialog = new TechnicalObjectDetailsDialog();
    private final UserProfileDialog userProfileDialog = new UserProfileDialog();
    private final UserProfile userProfile;
    private final AnchoredWindow window;
    private final ProfileStateStore store;
    private final Vector2f activeScrollHit = new Vector2f();
    private final Vector2f leftHit = new Vector2f();
    private final Vector2f rightHit = new Vector2f();

    private PanelPage page = PanelPage.TECHNICAL_OBJECTS;
    private int swipeHand = -1;
    private int pressX;
    private int pressY;
    private boolean tapCandidate;
    private String pressedTarget;
    private final Vector2f swipeHit = new Vector2f();

    PanelPage currentPage() {
        return page;
    }

    private String selectedId;
    private String hoveredId = "";
    private TechnicalObject detailsNode;
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

    public TechnicalObjectTreeUI(int pixelWidth, int pixelHeight, AnchoredWindow window,
                                 UserProfile profile, ProfileStateStore store) {
        TechnicalObjectTreeDataSource.TreeData treeData = TechnicalObjectTreeDataSource.loadTree();
        this.pixelHeight = pixelHeight;
        layout = new PanelUiLayout(pixelWidth, pixelHeight);
        selectedId = treeData.selectedId();
        logicalWidth = layout.logicalWidth;
        this.store = store;
        this.userProfile = profile;
        this.window = window;
        UserAppState saved = store.state();
        window.restore(saved);
        lastMinimized = window.minimized();
        selectedId = saved.selectedNodeId().isBlank() ? selectedId : saved.selectedNodeId();
        treeRows = new TreeRows(treeData.roots(), saved.expandedNodeIds());
        treeScrollOffset = saved.treeScrollOffset();
        texture = new PanelTexture(pixelWidth, pixelHeight);
    }

    public ByteBuffer pollTextureUpdate() {
        if (!dirty) return null;
        var pixels = texture.paint(layout, this::draw);
        dirty = false;
        return pixels;
    }

    private void draw(Graphics2D graphics) {
        if (lastMinimized) chrome.drawMinimized(graphics, layout, hoveredId);
        else drawWindow(graphics);
        if (userProfileOpen) userProfileDialog.draw(graphics, layout, userProfile, hoveredId);
    }

    public void updateInteraction(boolean leftSelect, Vector3f leftPosition, Quaternionf leftOrientation,
                           boolean leftPoseValid,
                           boolean rightSelect, Vector3f rightPosition, Quaternionf rightOrientation,
                           boolean rightPoseValid) {
        if (window.closed()) {
            previousLeftSelect = leftSelect;
            previousRightSelect = rightSelect;
            return;
        }
        String nextHoveredId = "";
        boolean leftClick = leftSelect && !previousLeftSelect;
        boolean rightClick = rightSelect && !previousRightSelect;
        // Meta XR Simulator can mirror the selected mouse click into both controller action sets.
        // Prefer the explicitly selected right pointer to avoid applying a tree toggle twice.
        if (leftSelect && rightSelect) {
            leftClick = false;
        }
        if (swipeHand >= 0) {
            boolean left = swipeHand == 0;
            updateSwipe(left ? leftSelect : rightSelect, left ? leftPoseValid : rightPoseValid,
                    left ? leftPosition : rightPosition, left ? leftOrientation : rightOrientation);
            previousLeftSelect = leftSelect;
            previousRightSelect = rightSelect;
            return;
        }
        if (activeScrollHand >= 0) {
            int previousOffset = treeScrollOffset;
            boolean held = activeScrollHand == 0 ? leftSelect : rightSelect;
            boolean poseValid = activeScrollHand == 0 ? leftPoseValid : rightPoseValid;
            Vector3f position = activeScrollHand == 0 ? leftPosition : rightPosition;
            Quaternionf orientation = activeScrollHand == 0 ? leftOrientation : rightOrientation;
            if (held && poseValid && window.intersectRayPlane(position, orientation, activeScrollHit)) {
                scrollTreeFromThumb(layout.toPixelY(activeScrollHit.y, window.heightMeters()));
            } else if (!held) {
                LOGGER.info("Tree scrollbar drag released at row offset {}", treeScrollOffset);
                activeScrollHand = -1;
                persistState();
            }
            dirty |= previousOffset != treeScrollOffset || !"tree-scroll".equals(hoveredId);
            hoveredId = "tree-scroll";
            previousLeftSelect = leftSelect;
            previousRightSelect = rightSelect;
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
                persistState();
            }
            previousLeftSelect = leftSelect;
            previousRightSelect = rightSelect;
            return;
        }

        if (leftPoseValid && window.intersectRay(leftPosition, leftOrientation, leftHit)) {
            nextHoveredId = pointAt(0, leftHit, leftClick);
        }
        if (rightPoseValid && window.intersectRay(rightPosition, rightOrientation, rightHit)) {
            String rightTarget = pointAt(1, rightHit, rightClick);
            if (!rightTarget.isEmpty()) nextHoveredId = rightTarget;
        }
        if (!nextHoveredId.equals(hoveredId)) {
            hoveredId = nextHoveredId;
            dirty = true;
        }
        previousLeftSelect = leftSelect;
        previousRightSelect = rightSelect;
    }

    private String pointAt(int hand, Vector2f hit, boolean click) {
        int x = layout.toLogicalX(hit.x, window.widthMeters());
        int y = layout.toPixelY(hit.y, window.heightMeters());
        String target = targetAt(x, y);
        if (click) {
            LOGGER.debug("Panel select at ({}, {}), target={}", x, y, target);
            activate(target, hand, hit, x, y);
        }
        return target;
    }

    private void updateSwipe(boolean held, boolean tracked, Vector3f position, Quaternionf orientation) {
        boolean valid = tracked && window.intersectRayPlane(position, orientation, swipeHit);
        if (!valid) {
            tapCandidate = false;
            if (!held) swipeHand = -1;
            lastClickedNodeId = null;
            return;
        }
        int x = layout.toLogicalX(swipeHit.x, window.widthMeters());
        int y = layout.toPixelY(swipeHit.y, window.heightMeters());
        int dx = x - pressX;
        int dy = y - pressY;
        tapCandidate &= Math.max(Math.abs(dx), Math.abs(dy)) <= Math.max(8, logicalWidth / 50);
        if (held) return;
        swipeHand = -1;
        if (Math.abs(dx) >= logicalWidth * 0.10 && Math.abs(dx) >= 1.5 * Math.abs(dy)) {
            navigate(dx < 0 ? 1 : -1);
        } else if (tapCandidate && pressedTarget.equals(targetAt(x, y))
                && (pressedTarget.startsWith("row-") || pressedTarget.startsWith("expand-"))) {
            activateRow(pressX, pressY);
        } else {
            lastClickedNodeId = null;
        }
    }

    private void navigate(int direction) {
        lastClickedNodeId = null;
        PanelPage next = page.step(direction);
        if (next == page) return;
        page = next;
        searchKeyboard.hide();
        hoveredId = "";
        dirty = true;
    }

    private void activate(String target, int hand, Vector2f hit, int x, int y) {
        switch (target) {
            case "previous-page" -> navigate(-1);
            case "next-page" -> navigate(1);
            case "move" -> {
                window.beginMove(hit.x, hit.y);
                activeGestureHand = hand;
            }
            case "resize-top-left", "resize-top-right", "resize-bottom-left", "resize-bottom-right" -> {
                window.beginResize(hit.x, hit.y, layout.isRightResizeCorner(x) ? 1 : -1,
                        layout.isBottomResizeCorner(y) ? 1 : -1);
                activeGestureHand = hand;
            }
            case "tree-scroll" -> beginScrollGesture(hand, y);
            case "minimize" -> {
                window.toggleMinimized();
                searchKeyboard.hide();
                persistState();
            }
            case "close" -> window.closeWindow();
            case "user-profile" -> {
                if (window.minimized()) window.toggleMinimized();
                searchKeyboard.hide();
                userProfileOpen = true;
            }
            case "profile-close" -> userProfileOpen = false;
            case "details-close" -> detailsNode = null;
            case "details-previous" -> detailsPage = Math.max(0, detailsPage - 1);
            case "details-next" -> detailsPage = Math.min(detailsDialog.pageCount(detailsNode) - 1, detailsPage + 1);
            case "search" -> searchKeyboard.show();
            default -> {
                if (target.startsWith("key-")) searchKeyboard.activate(x, y);
                else if (target.equals("page-body") || target.startsWith("row-") || target.startsWith("expand-")) {
                    swipeHand = hand;
                    pressX = x;
                    pressY = y;
                    pressedTarget = target;
                    tapCandidate = true;
                }
                else return;
            }
        }
        dirty = true;
    }

    private void activateRow(int x, int y) {
        int rowIndex = treeScrollOffset + (y - layout.treeViewport.y) / ROW_HEIGHT;
        TreeRow row = visibleRows.get(rowIndex);
        if (isExpandHit(row, x)) {
            boolean expanded = treeRows.toggle(row.node().id);
            lastClickedNodeId = null;
            LOGGER.info("Tree node {} {} by single click", row.node().id,
                    expanded ? "expanded" : "collapsed");
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
        persistState();
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
        if (lastMinimized) {
            if (layout.isUserButton(x, layout.userButton.y)) {
                return "user-profile";
            }
            return layout.isMinimizeButtonX(x) ? "minimize"
                    : layout.isCloseButtonX(x) ? "close" : "move";
        }
        if (layout.isUserButton(x, y)) return "user-profile";
        if (layout.isToolbarButton(x, y)) {
            return x < logicalWidth / 2 ? "minimize" : "close";
        }
        if (page == PanelPage.TECHNICAL_OBJECTS && hasTreeOverflow() && layout.scrollTrack.contains(x, y)) {
            return "tree-scroll";
        }
        if (layout.isResizeCorner(x, y)) {
            boolean right = layout.isRightResizeCorner(x);
            boolean bottom = layout.isBottomResizeCorner(y);
            return "resize-" + (bottom ? "bottom-" : "top-") + (right ? "right" : "left");
        }
        if (layout.previousPage.contains(x, y)) return page.step(-1) == page ? "" : "previous-page";
        if (layout.nextPage.contains(x, y)) return page.step(1) == page ? "" : "next-page";
        if (layout.isToolbarDrag(x, y)) {
            return "move";
        }
        boolean inBody = layout.panel.contains(x, y) && y >= layout.toolbar.y + layout.toolbar.height;
        if (page == PanelPage.ROUNDS) return inBody ? "page-body" : "";
        if (layout.searchField.contains(x, y)) {
            return "search";
        }
        if (!layout.treeViewport.contains(x, y)) {
            return inBody && !layout.statusBar.contains(x, y) ? "page-body" : "";
        }
        int rowIndex = treeScrollOffset + (y - layout.treeViewport.y) / ROW_HEIGHT;
        if (rowIndex < 0 || rowIndex >= visibleRows.size()) {
            return "page-body";
        }
        TreeRow row = visibleRows.get(rowIndex);
        return isExpandHit(row, x) ? "expand-" + row.node().id : "row-" + row.node().id;
    }

    private static boolean isExpandHit(TreeRow row, int x) {
        int rowStart = LEFT + row.depth() * INDENT;
        return row.hasChildren() && x <= rowStart + 82;
    }

    private void beginScrollGesture(int hand, int pointerY) {
        var scrollbar = layout.scrollbar(visibleRows.size());
        int thumbHeight = scrollbar.thumbHeight();
        int thumbY = scrollbar.thumbTop(treeScrollOffset);
        if (pointerY >= thumbY && pointerY <= thumbY + thumbHeight) {
            scrollGrabOffsetY = pointerY - thumbY;
        } else {
            scrollGrabOffsetY = thumbHeight / 2.0f;
        }
        activeScrollHand = hand;
        scrollTreeFromThumb(pointerY);
    }

    private void scrollTreeFromThumb(int pointerY) {
        treeScrollOffset = layout.scrollbar(visibleRows.size()).offsetAt(pointerY - scrollGrabOffsetY);
    }

    private int visibleRowCountInView() {
        return Math.max(1, layout.treeViewport.height / ROW_HEIGHT);
    }

    private void persistState() {
        store.save(window.snapshot(selectedId, treeRows.expandedIds(), treeScrollOffset));
    }

    private boolean hasTreeOverflow() {
        return visibleRows.size() > visibleRowCountInView();
    }

    private String selectedNodeName() {
        for (TreeRow row : visibleRows) {
            if (row.node().id.equals(selectedId)) {
                return row.node().label;
            }
        }
        return "";
    }

    private boolean lastMinimized;

    public void markWindowStateChanged(boolean minimized) {
        if (lastMinimized != minimized) {
            lastMinimized = minimized;
            dirty = true;
        }
    }

    private void drawWindow(Graphics2D graphics) {
        chrome.drawExpanded(graphics, layout, page, hoveredId);
        if (page == PanelPage.ROUNDS) return;

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

    private void drawTree(Graphics2D graphics) {
        visibleRows = treeRows.visible(searchKeyboard.text());
        int pageSize = visibleRowCountInView();
        treeScrollOffset = Math.max(0, Math.min(treeScrollOffset, Math.max(0, visibleRows.size() - pageSize)));
        treeView.draw(graphics, layout, visibleRows, selectedId, hoveredId, treeScrollOffset);
    }

    @Override
    public void close() {
        try {
            persistState();
        } finally {
            texture.close();
        }
    }
}
