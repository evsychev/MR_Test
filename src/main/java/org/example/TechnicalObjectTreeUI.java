package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class TechnicalObjectTreeUI implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TechnicalObjectTreeUI.class);
    private static final int ROW_HEIGHT = 74;
    private static final int TREE_TOP = 360;
    private static final int LEFT = 48;
    private static final int INDENT = 42;
    private static final int KEYBOARD_HEIGHT = 390;

    private static final Color FOREGROUND = new Color(235, 240, 245, 242);
    private static final Color SECONDARY = new Color(181, 190, 201, 225);
    private static final Color ACCENT = new Color(91, 166, 245, 240);

    private final int pixelWidth;
    private final int pixelHeight;
    private final int logicalWidth;
    private final List<TechnicalObjectTreeDataSource.TreeNode> roots;
    private final BufferedImage image;
    private final ByteBuffer rgbaPixels;
    private final Set<String> expanded = new HashSet<>();
    private final List<TreeRow> visibleRows = new ArrayList<>();
    private final List<KeyboardKey> keyboardKeys = new ArrayList<>();
    private final Vector2f leftHit = new Vector2f();
    private final Vector2f rightHit = new Vector2f();

    private String selectedId;
    private String hoveredId = "";
    private String searchText = "";
    private boolean keyboardVisible;
    private boolean previousLeftSelect;
    private boolean previousRightSelect;
    private int activeGestureHand = -1;
    private boolean dirty = true;
    private boolean closed;

    TechnicalObjectTreeUI(int pixelWidth, int pixelHeight) {
        TechnicalObjectTreeDataSource.TreeData treeData = TechnicalObjectTreeDataSource.loadTree();
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
        roots = treeData.roots();
        expanded.addAll(treeData.expandedIds());
        selectedId = treeData.selectedId();
        logicalWidth = Math.max(1, Math.round(pixelHeight
                * AnchoredWindow.DEFAULT_WIDTH_METERS / AnchoredWindow.EXPANDED_HEIGHT_METERS));
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

        String nextHoveredId = "";
        boolean leftClick = leftSelect && !previousLeftSelect;
        boolean rightClick = rightSelect && !previousRightSelect;
        // Meta XR Simulator can mirror the selected mouse click into both controller action sets.
        // Prefer the explicitly selected right pointer to avoid applying a tree toggle twice.
        if (leftSelect && rightSelect) {
            leftClick = false;
        }
        if (activeGestureHand >= 0) {
            boolean held = activeGestureHand == 0 ? leftSelect : rightSelect;
            boolean poseValid = activeGestureHand == 0 ? leftPoseValid : rightPoseValid;
            Vector3f position = activeGestureHand == 0 ? leftPosition : rightPosition;
            Quaternionf orientation = activeGestureHand == 0 ? leftOrientation : rightOrientation;
            Vector2f point = activeGestureHand == 0 ? leftHit : rightHit;
            if (held && poseValid && window.intersectRayPlane(position, orientation, point)) {
                window.updateGesture(point.x, point.y);
            } else if (!held) {
                window.endGesture();
                activeGestureHand = -1;
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
        return Math.round(meters / window.widthMeters() * logicalWidth);
    }

    private int toLogicalY(float meters, AnchoredWindow window) {
        return Math.round(meters / window.heightMeters() * pixelHeight);
    }

    private boolean beginGestureAt(int hand, int x, int y, Vector2f panelPoint, AnchoredWindow window) {
        if (window.minimized()) {
            return false;
        }
        int edge = 52;
        boolean left = x <= edge;
        boolean right = x >= logicalWidth - edge;
        boolean top = y <= edge;
        boolean bottom = y >= pixelHeight - edge;
        if ((left || right) && (top || bottom)) {
            window.beginResize(panelPoint.x, panelPoint.y, right ? 1 : -1, bottom ? 1 : -1);
            activeGestureHand = hand;
            return true;
        }
        if (y < 105 && x >= 145 && x <= logicalWidth - 145) {
            window.beginMove(panelPoint.x, panelPoint.y);
            activeGestureHand = hand;
            return true;
        }
        return false;
    }

    private void activateAt(int x, int y, AnchoredWindow window) {
        if (window.minimized()) {
            if (x < 130) {
                window.toggleMinimized();
            } else if (x > logicalWidth - 130) {
                window.closeWindow();
                closed = true;
            }
            dirty = true;
            return;
        }

        if (y < 105) {
            if (x < 140) {
                window.toggleMinimized();
                keyboardVisible = false;
                dirty = true;
            } else if (x > logicalWidth - 140) {
                window.closeWindow();
                closed = true;
            }
            return;
        }

        if (inside(x, y, 38, 236, logicalWidth - 76, 84)) {
            keyboardVisible = true;
            dirty = true;
            return;
        }

        if (keyboardVisible && y >= pixelHeight - KEYBOARD_HEIGHT) {
            activateKeyboardKey(x, y);
            dirty = true;
            return;
        }

        int rowIndex = (y - TREE_TOP) / ROW_HEIGHT;
        if (rowIndex < 0 || rowIndex >= visibleRows.size()) {
            return;
        }
        TreeRow row = visibleRows.get(rowIndex);
        int rowTop = TREE_TOP + rowIndex * ROW_HEIGHT;
        int rowStart = LEFT + row.depth * INDENT;
        if (row.hasChildren && x <= rowStart + 36 && y <= rowTop + ROW_HEIGHT) {
            if (!expanded.add(row.node.id)) {
                expanded.remove(row.node.id);
            }
        } else {
            selectedId = row.node.id;
        }
        dirty = true;
    }

    private void activateKeyboardKey(int x, int y) {
        for (KeyboardKey key : keyboardKeys) {
            if (inside(x, y, key.x, key.y, key.width, key.height)) {
                switch (key.action) {
                    case "BACKSPACE" -> {
                        if (!searchText.isEmpty()) {
                            searchText = searchText.substring(0, searchText.length() - 1);
                        }
                    }
                    case "SPACE" -> searchText += " ";
                    case "CLEAR" -> searchText = "";
                    case "DONE" -> keyboardVisible = false;
                    default -> searchText += key.action.toLowerCase(Locale.ROOT);
                }
                return;
            }
        }
    }

    private String targetAt(int x, int y) {
        if (y < 105) {
            return x < 140 ? "minimize" : x > logicalWidth - 140 ? "close" : "toolbar";
        }
        if (inside(x, y, 38, 236, logicalWidth - 76, 84)) {
            return "search";
        }
        if (keyboardVisible && y >= pixelHeight - KEYBOARD_HEIGHT) {
            for (KeyboardKey key : keyboardKeys) {
                if (inside(x, y, key.x, key.y, key.width, key.height)) {
                    return "key-" + key.action;
                }
            }
            return "keyboard";
        }
        int rowIndex = (y - TREE_TOP) / ROW_HEIGHT;
        return rowIndex >= 0 && rowIndex < visibleRows.size()
                ? "row-" + visibleRows.get(rowIndex).node.id : "";
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

            if (lastMinimized) {
                drawMinimized(graphics);
            } else {
                drawWindow(graphics);
            }
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
        int panelRadius = 30;
        graphics.setColor(new Color(36, 39, 43, 148));
        graphics.fillRoundRect(8, 8, logicalWidth - 16, pixelHeight - 16, panelRadius, panelRadius);

        graphics.setColor(new Color(235, 240, 245, 175));
        graphics.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        drawCornerMarkers(graphics);

        graphics.setColor(new Color(230, 235, 242, 180));
        graphics.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(58, 56, 96, 56);
        drawCloseIcon(graphics, logicalWidth - 78, 56, false);

        graphics.setColor(new Color(228, 234, 241, 240));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        drawNetworkIcon(graphics, 62, 166);
        graphics.drawString("Technical Objects", 112, 178);

        drawSearchField(graphics);
        drawTree(graphics);
        if (keyboardVisible) {
            drawKeyboard(graphics);
        }
    }

    private void drawMinimized(Graphics2D graphics) {
        graphics.setColor(new Color(36, 39, 43, 190));
        graphics.fillRoundRect(8, 8, logicalWidth - 16, pixelHeight - 16, 30, 30);
        graphics.setColor(new Color(230, 235, 242, 220));
        int centerY = pixelHeight / 2;
        int lineHeight = Math.round(5.0f * AnchoredWindow.EXPANDED_HEIGHT_METERS
                / AnchoredWindow.MINIMIZED_HEIGHT_METERS);
        graphics.fillRoundRect(58, centerY - lineHeight / 2, 44, lineHeight, lineHeight, lineHeight);
        drawMinimizedCloseIcon(graphics, logicalWidth - 78, centerY);
    }

    private void drawMinimizedCloseIcon(Graphics2D graphics, int centerX, int centerY) {
        graphics.setColor(new Color(235, 240, 245, 225));
        graphics.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int halfWidth = 14;
        int halfHeight = Math.round(halfWidth * AnchoredWindow.DEFAULT_WIDTH_METERS
                / logicalWidth * pixelHeight / AnchoredWindow.MINIMIZED_HEIGHT_METERS);
        graphics.drawLine(centerX - halfWidth, centerY - halfHeight,
                centerX + halfWidth, centerY + halfHeight);
        graphics.drawLine(centerX + halfWidth, centerY - halfHeight,
                centerX - halfWidth, centerY + halfHeight);
    }

    private void drawCornerMarkers(Graphics2D graphics) {
        int inset = 20;
        int length = 25;
        int right = logicalWidth - inset;
        int bottom = pixelHeight - inset;

        graphics.drawLine(inset, inset + length, inset, inset);
        graphics.drawLine(inset, inset, inset + length, inset);
        graphics.drawLine(right - length, inset, right, inset);
        graphics.drawLine(right, inset, right, inset + length);
        graphics.drawLine(inset, bottom - length, inset, bottom);
        graphics.drawLine(inset, bottom, inset + length, bottom);
        graphics.drawLine(right - length, bottom, right, bottom);
        graphics.drawLine(right, bottom - length, right, bottom);
    }

    private void drawCloseIcon(Graphics2D graphics, int centerX, int centerY, boolean hovered) {
        graphics.setColor(hovered ? ACCENT : new Color(235, 240, 245, 225));
        graphics.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(centerX - 14, centerY - 14, centerX + 14, centerY + 14);
        graphics.drawLine(centerX + 14, centerY - 14, centerX - 14, centerY + 14);
    }

    private void drawSearchField(Graphics2D graphics) {
        int x = 38;
        int y = 236;
        int width = logicalWidth - 76;
        int height = 84;
        graphics.setColor(new Color(79, 86, 94, 104));
        graphics.fillRoundRect(x, y, width, height, 22, 22);
        graphics.setColor(new Color(190, 200, 211, 115));
        graphics.setStroke(new BasicStroke(2.0f));
        graphics.drawRoundRect(x, y, width, height, 22, 22);
        graphics.drawOval(x + 25, y + 22, 31, 31);
        graphics.drawLine(x + 51, y + 50, x + 66, y + 65);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 28));
        graphics.setColor(searchText.isEmpty() ? new Color(202, 209, 217, 145) : FOREGROUND);
        graphics.drawString(searchText.isEmpty() ? "Search objects..." : searchText, x + 82, y + 53);
        if (keyboardVisible) {
            graphics.setColor(ACCENT);
            graphics.fillRoundRect(x + 80 + graphics.getFontMetrics().stringWidth(searchText) + 4,
                    y + 30, 3, 28, 3, 3);
        }
    }

    private void drawTree(Graphics2D graphics) {
        visibleRows.clear();
        String query = searchText.strip().toLowerCase(Locale.ROOT);
        for (TechnicalObjectTreeDataSource.TreeNode root : roots) {
            appendVisibleRows(root, 0, query);
        }

        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 27));
        for (int rowIndex = 0; rowIndex < visibleRows.size(); rowIndex++) {
            TreeRow row = visibleRows.get(rowIndex);
            int top = TREE_TOP + rowIndex * ROW_HEIGHT;
            int centerY = top + ROW_HEIGHT / 2;
            String rowId = "row-" + row.node.id;
            if (row.node.id.equals(selectedId)) {
                graphics.setColor(new Color(57, 132, 224, 145));
                graphics.fillRoundRect(LEFT + row.depth * INDENT - 7, top + 3,
                        logicalWidth - LEFT - row.depth * INDENT - 27, ROW_HEIGHT - 6, 18, 18);
            } else if (rowId.equals(hoveredId)) {
                graphics.setColor(new Color(185, 208, 232, 38));
                graphics.fillRoundRect(LEFT + row.depth * INDENT - 7, top + 3,
                        logicalWidth - LEFT - row.depth * INDENT - 27, ROW_HEIGHT - 6, 18, 18);
            }

            drawTreeGuides(graphics, rowIndex, row);
            int rowStart = LEFT + row.depth * INDENT;
            if (row.hasChildren) {
                drawChevron(graphics, rowStart + 10, centerY, row.expanded, rowId.equals(hoveredId));
            }
            drawNodeIcon(graphics, row.node.kind, rowStart + 48, centerY);
            graphics.setColor(row.node.id.equals(selectedId) ? Color.WHITE : FOREGROUND);
            graphics.drawString(row.node.label, rowStart + 88, centerY + 9);
        }
    }

    private void appendVisibleRows(TechnicalObjectTreeDataSource.TreeNode node, int depth, String query) {
        boolean descendantMatches = containsMatch(node, query);
        if (!descendantMatches) {
            return;
        }
        boolean isExpanded = query.isEmpty() ? expanded.contains(node.id) : true;
        visibleRows.add(new TreeRow(node, depth, !node.children.isEmpty(), isExpanded));
        if (isExpanded) {
            for (TechnicalObjectTreeDataSource.TreeNode child : node.children) {
                appendVisibleRows(child, depth + 1, query);
            }
        }
    }

    private boolean containsMatch(TechnicalObjectTreeDataSource.TreeNode node, String query) {
        if (query.isEmpty() || node.label.toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        for (TechnicalObjectTreeDataSource.TreeNode child : node.children) {
            if (containsMatch(child, query)) {
                return true;
            }
        }
        return false;
    }

    private void drawTreeGuides(Graphics2D graphics, int rowIndex, TreeRow row) {
        graphics.setColor(new Color(190, 202, 215, 54));
        graphics.setStroke(new BasicStroke(1.4f));
        int centerY = TREE_TOP + rowIndex * ROW_HEIGHT + ROW_HEIGHT / 2;
        if (row.depth > 0) {
            int guideX = LEFT + (row.depth - 1) * INDENT + 18;
            graphics.drawLine(guideX, centerY - ROW_HEIGHT / 2, guideX, centerY);
            graphics.drawLine(guideX, centerY, guideX + 14, centerY);
        }
    }

    private void drawChevron(Graphics2D graphics, int x, int centerY, boolean isExpanded, boolean hovered) {
        graphics.setColor(hovered ? ACCENT : FOREGROUND);
        graphics.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (isExpanded) {
            graphics.drawLine(x - 8, centerY - 4, x, centerY + 4);
            graphics.drawLine(x, centerY + 4, x + 8, centerY - 4);
        } else {
            graphics.drawLine(x - 4, centerY - 8, x + 4, centerY);
            graphics.drawLine(x + 4, centerY, x - 4, centerY + 8);
        }
    }

    private void drawNetworkIcon(Graphics2D graphics, int x, int centerY) {
        graphics.setColor(new Color(230, 237, 244, 230));
        graphics.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawRect(x + 10, centerY - 19, 12, 12);
        graphics.drawRect(x - 16, centerY + 8, 12, 12);
        graphics.drawRect(x + 10, centerY + 8, 12, 12);
        graphics.drawRect(x + 36, centerY + 8, 12, 12);
        graphics.drawLine(x + 16, centerY - 7, x + 16, centerY + 1);
        graphics.drawLine(x - 10, centerY + 1, x + 42, centerY + 1);
        graphics.drawLine(x - 10, centerY + 1, x - 10, centerY + 8);
        graphics.drawLine(x + 42, centerY + 1, x + 42, centerY + 8);
        graphics.drawLine(x + 16, centerY + 1, x + 16, centerY + 8);
    }

    private void drawNodeIcon(Graphics2D graphics, TechnicalObjectTreeDataSource.IconKind kind, int centerX, int centerY) {
        graphics.setColor(new Color(224, 232, 240, 225));
        graphics.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int left = centerX - 15;
        int top = centerY - 15;
        switch (kind) {
            case FACTORY -> {
                graphics.fillRect(left, centerY - 1, 30, 16);
                graphics.fillRect(left + 4, centerY - 13, 6, 12);
                graphics.fillRect(left + 14, centerY - 8, 6, 7);
                graphics.fillRect(left + 24, centerY - 12, 5, 11);
                graphics.setColor(new Color(41, 45, 50, 220));
                graphics.fillRect(left + 5, centerY + 7, 4, 5);
                graphics.fillRect(left + 14, centerY + 7, 4, 5);
                graphics.fillRect(left + 23, centerY + 7, 4, 5);
            }
            case BUILDING -> {
                graphics.drawRect(left + 2, top + 2, 26, 27);
                graphics.drawRect(left + 7, top + 7, 5, 5);
                graphics.drawRect(left + 18, top + 7, 5, 5);
                graphics.drawRect(left + 7, top + 17, 5, 5);
                graphics.drawRect(left + 18, top + 17, 5, 5);
            }
            case EQUIPMENT -> {
                graphics.drawOval(left + 5, top + 5, 20, 20);
                graphics.drawOval(left + 11, top + 11, 8, 8);
                for (int index = 0; index < 8; index++) {
                    double angle = Math.PI * index / 4.0;
                    int x1 = centerX + (int) Math.round(Math.cos(angle) * 11);
                    int y1 = centerY + (int) Math.round(Math.sin(angle) * 11);
                    int x2 = centerX + (int) Math.round(Math.cos(angle) * 15);
                    int y2 = centerY + (int) Math.round(Math.sin(angle) * 15);
                    graphics.drawLine(x1, y1, x2, y2);
                }
            }
            case MACHINE -> {
                graphics.drawLine(centerX, top, left + 29, centerY - 7);
                graphics.drawLine(left + 29, centerY - 7, centerX, centerY + 2);
                graphics.drawLine(centerX, centerY + 2, left, centerY - 7);
                graphics.drawLine(left, centerY - 7, centerX, top);
                graphics.drawLine(left, centerY - 7, left, centerY + 10);
                graphics.drawLine(left, centerY + 10, centerX, centerY + 19);
                graphics.drawLine(centerX, centerY + 19, left + 29, centerY + 10);
                graphics.drawLine(left + 29, centerY + 10, left + 29, centerY - 7);
                graphics.drawLine(centerX, top, centerX, centerY + 19);
            }
            case THERMAL -> {
                graphics.drawRoundRect(centerX - 5, top, 10, 22, 8, 8);
                graphics.drawOval(centerX - 8, centerY + 5, 16, 16);
                graphics.drawLine(centerX, top + 5, centerX, centerY + 10);
            }
            case POWER -> {
                int[] xs = {centerX + 2, centerX - 9, centerX - 1, centerX - 5,
                        centerX + 10, centerX + 2, centerX + 7};
                int[] ys = {top - 1, centerY + 1, centerY + 1, centerY + 15,
                        centerY - 3, centerY - 3, top - 1};
                graphics.fillPolygon(xs, ys, xs.length);
            }
            case WAREHOUSE -> {
                graphics.drawLine(left - 1, centerY - 2, centerX, top - 1);
                graphics.drawLine(centerX, top - 1, left + 31, centerY - 2);
                graphics.drawRect(left + 3, centerY - 2, 24, 18);
                graphics.drawRect(centerX - 5, centerY + 5, 10, 11);
            }
            case SERVER -> {
                for (int offset = -12; offset <= 8; offset += 10) {
                    graphics.drawRoundRect(left, centerY + offset, 30, 8, 3, 3);
                    graphics.fillOval(left + 4, centerY + offset + 3, 3, 3);
                }
            }
            case GRID -> {
                graphics.drawRect(left, top, 11, 11);
                graphics.drawRect(centerX + 3, top, 11, 11);
                graphics.drawRect(left, centerY + 3, 11, 11);
                graphics.drawRect(centerX + 3, centerY + 3, 11, 11);
            }
        }
    }

    private void drawKeyboard(Graphics2D graphics) {
        int panelY = pixelHeight - KEYBOARD_HEIGHT;
        graphics.setColor(new Color(29, 32, 36, 232));
        graphics.fillRoundRect(20, panelY, logicalWidth - 40, KEYBOARD_HEIGHT - 20, 22, 22);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 23));
        graphics.setColor(new Color(220, 228, 237, 215));
        graphics.drawString("Search", 42, panelY + 34);

        keyboardKeys.clear();
        String[] rows = {"QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"};
        int keyHeight = 58;
        int gap = 8;
        int contentWidth = logicalWidth - 80;
        int y = panelY + 48;
        for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
            String row = rows[rowIndex];
            int keyWidth = Math.min(80, (contentWidth - gap * (row.length() - 1)) / row.length());
            int rowWidth = row.length() * keyWidth + (row.length() - 1) * gap;
            int x = (logicalWidth - rowWidth) / 2;
            if (rowIndex == 1) {
                x += keyWidth / 2;
            }
            if (rowIndex == 2) {
                x += keyWidth / 2;
            }
            for (int keyIndex = 0; keyIndex < row.length(); keyIndex++) {
                addKeyboardKey(graphics, row.substring(keyIndex, keyIndex + 1),
                        x + keyIndex * (keyWidth + gap), y + rowIndex * (keyHeight + 9),
                        keyWidth, keyHeight);
            }
        }
        int actionY = y + rows.length * (keyHeight + 9) - 4;
        int actionGap = 10;
        int spaceWidth = 360;
        int smallWidth = (contentWidth - spaceWidth - actionGap * 2) / 2;
        int x = (logicalWidth - (spaceWidth + smallWidth * 2 + actionGap * 2)) / 2;
        addKeyboardKey(graphics, "SPACE", x, actionY, spaceWidth, 58);
        addKeyboardKey(graphics, "CLEAR", x + spaceWidth + actionGap, actionY, smallWidth, 58);
        addKeyboardKey(graphics, "DONE", x + spaceWidth + actionGap + smallWidth + actionGap,
                actionY, smallWidth, 58);
        addKeyboardKey(graphics, "BACKSPACE", logicalWidth - 140, y + 2 * (keyHeight + 9),
                74, keyHeight);
    }

    private void addKeyboardKey(Graphics2D graphics, String action, int x, int y, int width, int height) {
        KeyboardKey key = new KeyboardKey(action, x, y, width, height);
        keyboardKeys.add(key);
        boolean isHovered = ("key-" + action).equals(hoveredId);
        graphics.setColor(isHovered ? new Color(73, 139, 213, 220) : new Color(74, 80, 88, 215));
        graphics.fillRoundRect(x, y, width, height, 12, 12);
        graphics.setColor(new Color(230, 236, 243, 200));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, action.length() > 1 ? 18 : 25));
        String label = action.equals("BACKSPACE") ? "⌫" : action;
        int labelWidth = graphics.getFontMetrics().stringWidth(label);
        graphics.drawString(label, x + (width - labelWidth) / 2,
                y + (height + graphics.getFontMetrics().getAscent()) / 2 - 4);
    }

    private static boolean inside(int x, int y, int left, int top, int width, int height) {
        return x >= left && x <= left + width && y >= top && y <= top + height;
    }

    @Override
    public void close() {
        MemoryUtil.memFree(rgbaPixels);
    }

    private record TreeRow(TechnicalObjectTreeDataSource.TreeNode node, int depth, boolean hasChildren, boolean expanded) {
    }

    private record KeyboardKey(String action, int x, int y, int width, int height) {
    }
}
