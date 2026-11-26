package org.example;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;

final class SearchField {
    private static final Color ACCENT = new Color(91, 166, 245, 240);

    void draw(Graphics2D graphics, PanelUiLayout layout, String text, boolean editing, boolean hovered) {
        Shape previousClip = graphics.getClip();
        graphics.clip(layout.searchField);
        var field = layout.searchField;
        graphics.setColor(hovered ? new Color(88, 110, 132, 150) : new Color(79, 86, 94, 104));
        graphics.fillRoundRect(field.x, field.y, field.width, field.height, 22, 22);
        graphics.setColor(hovered ? ACCENT : new Color(190, 200, 211, 115));
        graphics.setStroke(new BasicStroke(hovered ? 3.0f : 2.0f));
        graphics.drawRoundRect(field.x, field.y, field.width, field.height, 22, 22);
        graphics.drawOval(field.x + 25, field.y + 22, 31, 31);
        graphics.drawLine(field.x + 51, field.y + 50, field.x + 66, field.y + 65);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 28));
        graphics.setColor(text.isEmpty() ? new Color(202, 209, 217, 145) : new Color(235, 240, 245, 242));
        String value = text.isEmpty() ? "Search objects..." : text;
        graphics.drawString(fitText(graphics, value, field.width - 110), field.x + 82, field.y + 53);
        if (editing) {
            graphics.setColor(ACCENT);
            int cursor = Math.min(field.x + field.width - 10,
                    field.x + 82 + graphics.getFontMetrics().stringWidth(text) + 4);
            graphics.fillRoundRect(cursor, field.y + 30, 3, 28, 3, 3);
        }
        graphics.setClip(previousClip);
    }

    private static String fitText(Graphics2D graphics, String value, int width) {
        var metrics = graphics.getFontMetrics();
        if (metrics.stringWidth(value) <= width) {
            return value;
        }
        String clipped = value;
        while (!clipped.isEmpty() && metrics.stringWidth(clipped + "…") > width) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        return clipped + "…";
    }
}
