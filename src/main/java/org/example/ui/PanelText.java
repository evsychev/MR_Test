package org.example.ui;

import java.awt.Graphics2D;

final class PanelText {
    private PanelText() {}

    static String fit(Graphics2D graphics, String text, int width) {
        var metrics = graphics.getFontMetrics();
        if (metrics.stringWidth(text) <= width) return text;
        int available = width - metrics.stringWidth("…");
        if (available < 0) return "";
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (metrics.stringWidth(text.substring(0, mid)) <= available) low = mid;
            else high = mid - 1;
        }
        if (low > 0 && Character.isHighSurrogate(text.charAt(low - 1))) low--;
        return text.substring(0, low) + "…";
    }
}
