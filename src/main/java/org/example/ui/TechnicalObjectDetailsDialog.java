package org.example.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.example.tree.TechnicalObject;

final class TechnicalObjectDetailsDialog {
    private static final int PAGE_SIZE = 8;
    private static final Color PRIMARY = new Color(235, 240, 245, 242);
    private static final Color SECONDARY = new Color(181, 190, 201, 225);

    int pageCount(TechnicalObject node) {
        return Math.max(1, (node.fields.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    void draw(Graphics2D graphics, PanelUiLayout layout,
              TechnicalObject node, int page, String hoveredTarget) {
        graphics.setColor(new Color(7, 10, 13, 145));
        graphics.fillRect(0, 0, layout.logicalWidth, layout.pixelHeight);
        int left = 28;
        int top = 82;
        int width = layout.logicalWidth - 56;
        int height = layout.pixelHeight - 164;
        graphics.setColor(new Color(31, 35, 40, 246));
        graphics.fillRoundRect(left, top, width, height, 28, 28);
        graphics.setColor(new Color(188, 199, 210, 155));
        graphics.setStroke(new BasicStroke(2.0f));
        graphics.drawRoundRect(left, top, width, height, 28, 28);

        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 29));
        graphics.setColor(PRIMARY);
        graphics.drawString(Messages.text("details.title"), left + 28, top + 56);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        graphics.setColor(SECONDARY);
        graphics.drawString(PanelText.fit(graphics, node.label, width - 180), left + 28, top + 94);
        drawCloseIcon(graphics, layout.logicalWidth - 76, top + 50,
                "details-close".equals(hoveredTarget));

        List<Map.Entry<String, String>> fields = new ArrayList<>(node.fields.entrySet());
        int pages = pageCount(node);
        int start = page * PAGE_SIZE;
        int end = Math.min(fields.size(), start + PAGE_SIZE);
        int rowTop = top + 132;
        int labelX = left + 28;
        int valueX = left + 300;
        int valueWidth = width - 340;
        ShapeClip clip = new ShapeClip(graphics, left + 18, top + 118, width - 36, height - 190);
        try {
            for (int index = start; index < end; index++) {
                Map.Entry<String, String> field = fields.get(index);
                String value = field.getValue().isBlank() ? "—" : field.getValue();
                graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
                List<String> lines = wrapText(graphics, value, valueWidth, 4);
                int rowHeight = Math.max(60, 34 + lines.size() * 23);
                graphics.setColor(new Color(181, 203, 225, 235));
                graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
                graphics.drawString(PanelText.fit(graphics, Messages.text("field." + field.getKey()), valueX - labelX - 18),
                        labelX, rowTop + 28);
                graphics.setColor(PRIMARY);
                graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
                int valueY = rowTop + 24;
                for (String line : lines) {
                    graphics.drawString(line, valueX, valueY);
                    valueY += 23;
                }
                rowTop += rowHeight;
            }
        } finally {
            clip.close();
        }

        int navigationY = top + height - 54;
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        if ("details-previous".equals(hoveredTarget)) {
            graphics.setColor(new Color(91, 166, 245, 75));
            graphics.fillRoundRect(left + 18, navigationY - 26, 145, 36, 12, 12);
        }
        if ("details-next".equals(hoveredTarget)) {
            graphics.setColor(new Color(91, 166, 245, 75));
            graphics.fillRoundRect(layout.logicalWidth - 154, navigationY - 26, 120, 36, 12, 12);
        }
        graphics.setColor(page > 0 ? PRIMARY : new Color(145, 151, 158, 150));
        graphics.drawString(Messages.text("details.previous"), left + 28, navigationY);
        graphics.setColor(SECONDARY);
        graphics.drawString((page + 1) + " / " + pages, layout.logicalWidth / 2 - 28, navigationY);
        graphics.setColor(page + 1 < pages ? PRIMARY : new Color(145, 151, 158, 150));
        graphics.drawString(Messages.text("details.next"), layout.logicalWidth - 130, navigationY);
    }

    private static List<String> wrapText(Graphics2D graphics, String text, int maxWidth, int maxLines) {
        var metrics = graphics.getFontMetrics();
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            line.append(text.charAt(index));
            if (metrics.stringWidth(line.toString()) > maxWidth && line.length() > 1) {
                char last = line.charAt(line.length() - 1);
                line.setLength(line.length() - 1);
                lines.add(line.toString());
                line.setLength(0);
                line.append(last);
                if (lines.size() == maxLines) {
                    lines.set(lines.size() - 1, PanelText.fit(graphics, lines.getLast(), maxWidth - 24) + "…");
                    return lines;
                }
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines.isEmpty() ? List.of("—") : lines;
    }

    private static void drawCloseIcon(Graphics2D graphics, int centerX, int centerY, boolean hovered) {
        graphics.setColor(hovered ? new Color(91, 166, 245, 240) : PRIMARY);
        graphics.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(centerX - 14, centerY - 14, centerX + 14, centerY + 14);
        graphics.drawLine(centerX + 14, centerY - 14, centerX - 14, centerY + 14);
    }

    private static final class ShapeClip implements AutoCloseable {
        private final Graphics2D graphics;
        private final java.awt.Shape previous;

        private ShapeClip(Graphics2D graphics, int x, int y, int width, int height) {
            this.graphics = graphics;
            previous = graphics.getClip();
            graphics.clipRect(x, y, width, height);
        }

        @Override
        public void close() {
            graphics.setClip(previous);
        }
    }
}
