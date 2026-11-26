package org.example;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;

final class PanelStatusBar {
    void draw(Graphics2D graphics, PanelUiLayout layout, int visibleRows, int scrollOffset,
              int totalRows, String selectedName) {
        Shape previousClip = graphics.getClip();
        graphics.clip(layout.statusBar);
        graphics.setColor(new Color(23, 26, 30, 190));
        graphics.fillRoundRect(layout.statusBar.x, layout.statusBar.y,
                layout.statusBar.width, layout.statusBar.height, 14, 14);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        graphics.setColor(new Color(210, 218, 226, 215));
        String status = selectedName == null || selectedName.isBlank()
                ? "Objects: " + totalRows
                : "Selected: " + selectedName + "   •   Rows "
                + Math.min(totalRows, scrollOffset + 1) + "–"
                + Math.min(totalRows, scrollOffset + visibleRows) + " of " + totalRows;
        graphics.drawString(fitText(graphics, status, layout.statusBar.width - 24),
                layout.statusBar.x + 12, layout.statusBar.y + 25);
        graphics.setClip(previousClip);
    }

    private static String fitText(Graphics2D graphics, String text, int width) {
        var metrics = graphics.getFontMetrics();
        String result = text;
        while (!result.isEmpty() && metrics.stringWidth(result) > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result.equals(text) ? result : result + "…";
    }
}
