package org.example.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.List;

import org.example.tree.IconKind;
import org.example.tree.TreeRow;

final class TechnicalObjectTreeView {
    private static final int LEFT = PanelUiLayout.TREE_LEFT;
    private static final int INDENT = PanelUiLayout.TREE_INDENT;
    private static final Color FOREGROUND = new Color(235, 240, 245, 242);
    private static final Color ACCENT = new Color(91, 166, 245, 240);

    void draw(Graphics2D graphics, PanelUiLayout layout,
              List<TreeRow> rows, String selectedId, String hoveredId, int firstRow) {
        Shape previousClip = graphics.getClip();
        graphics.clip(layout.treeViewport);
        try {
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 27));
            int visibleCount = Math.max(1, (layout.treeViewport.height + PanelUiLayout.TREE_ROW_HEIGHT - 1)
                    / PanelUiLayout.TREE_ROW_HEIGHT);
            int lastRow = Math.min(rows.size(), firstRow + visibleCount + 1);
            for (int index = firstRow; index < lastRow; index++) {
                TreeRow row = rows.get(index);
                int top = layout.treeViewport.y + (index - firstRow) * PanelUiLayout.TREE_ROW_HEIGHT;
                int centerY = top + PanelUiLayout.TREE_ROW_HEIGHT / 2;
                String rowId = "row-" + row.node().id;
                boolean rowHovered = rowId.equals(hoveredId)
                        || ("expand-" + row.node().id).equals(hoveredId);
                int rowStart = LEFT + row.depth() * INDENT;
                int rowWidth = Math.max(0, layout.scrollTrack.x - rowStart - 18);
                if (row.node().id.equals(selectedId)) {
                    graphics.setColor(rowHovered
                            ? new Color(78, 157, 248, 190) : new Color(57, 132, 224, 145));
                    graphics.fillRoundRect(Math.max(layout.treeViewport.x, rowStart - 7), top + 3,
                            Math.max(0, rowWidth), PanelUiLayout.TREE_ROW_HEIGHT - 6, 18, 18);
                } else if (rowHovered) {
                    graphics.setColor(new Color(185, 208, 232, 38));
                    graphics.fillRoundRect(Math.max(layout.treeViewport.x, rowStart - 7), top + 3,
                            Math.max(0, rowWidth), PanelUiLayout.TREE_ROW_HEIGHT - 6, 18, 18);
                }
                drawGuides(graphics, top, row);
                if (row.hasChildren()) {
                    drawChevron(graphics, rowStart + 10, centerY, row.expanded(),
                            ("expand-" + row.node().id).equals(hoveredId));
                }
                drawNodeIcon(graphics, row.node().kind, rowStart + 48, centerY,
                        rowHovered);
                graphics.setColor(row.node().id.equals(selectedId) || rowHovered
                        ? Color.WHITE : FOREGROUND);
                int textX = rowStart + 88;
                graphics.drawString(PanelText.fit(graphics, row.node().label, layout.scrollTrack.x - textX - 12),
                        textX, centerY + 9);
            }
        } finally {
            graphics.setClip(previousClip);
        }
        drawScrollbar(graphics, layout, rows.size(), firstRow, hoveredId);
    }

    private void drawScrollbar(Graphics2D graphics, PanelUiLayout layout, int rowCount,
                               int firstRow, String hoveredId) {
        var scrollbar = layout.scrollbar(rowCount);
        if (scrollbar.maxOffset() == 0) {
            return;
        }
        var track = layout.scrollTrack;
        boolean hovered = "tree-scroll".equals(hoveredId);
        int visibleTrackX = track.x + (track.width - 28) / 2;
        graphics.setColor(hovered ? new Color(64, 76, 88, 220) : new Color(12, 15, 18, 110));
        graphics.fillRoundRect(visibleTrackX, track.y, 28, track.height, 12, 12);
        int thumbHeight = scrollbar.thumbHeight();
        int thumbY = scrollbar.thumbTop(firstRow);
        graphics.setColor(hovered ? new Color(115, 184, 246, 245) : new Color(190, 205, 220, 190));
        graphics.fillRoundRect(visibleTrackX + 2, thumbY, 24, thumbHeight, 10, 10);
    }

    private void drawGuides(Graphics2D graphics, int top, TreeRow row) {
        graphics.setColor(new Color(190, 202, 215, 54));
        graphics.setStroke(new BasicStroke(1.4f));
        int centerY = top + PanelUiLayout.TREE_ROW_HEIGHT / 2;
        if (row.depth() > 0) {
            int guideX = LEFT + (row.depth() - 1) * INDENT + 18;
            graphics.drawLine(guideX, centerY - PanelUiLayout.TREE_ROW_HEIGHT / 2, guideX, centerY);
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

    private void drawNodeIcon(Graphics2D graphics, IconKind kind,
                              int centerX, int centerY, boolean hovered) {
        graphics.setColor(hovered ? new Color(125, 190, 250, 245) : new Color(224, 232, 240, 225));
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
                    graphics.drawLine(centerX + (int) Math.round(Math.cos(angle) * 11),
                            centerY + (int) Math.round(Math.sin(angle) * 11),
                            centerX + (int) Math.round(Math.cos(angle) * 15),
                            centerY + (int) Math.round(Math.sin(angle) * 15));
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
            case POWER -> graphics.fillPolygon(new int[]{centerX + 2, centerX - 9, centerX - 1,
                            centerX - 5, centerX + 10, centerX + 2, centerX + 7},
                    new int[]{top - 1, centerY + 1, centerY + 1, centerY + 15,
                            centerY - 3, centerY - 3, top - 1}, 7);
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

}
