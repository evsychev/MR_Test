package org.example.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

final class PanelChrome {
    private static final Color PANEL = new Color(36, 39, 43, 148);
    private static final Color FOREGROUND = new Color(235, 240, 245, 225);

    void drawExpanded(Graphics2D graphics, PanelUiLayout layout, PanelPage page, String hoveredTarget) {
        graphics.setColor(PANEL);
        graphics.fillRoundRect(layout.panel.x, layout.panel.y,
                layout.panel.width, layout.panel.height, 30, 30);
        drawCornerMarkers(graphics, layout, hoveredTarget.startsWith("resize-"));

        if ("minimize".equals(hoveredTarget)) {
            graphics.setColor(new Color(91, 166, 245, 105));
            graphics.fillRoundRect(layout.minimizeButton.x, layout.minimizeButton.y,
                    layout.minimizeButton.width, layout.minimizeButton.height, 18, 18);
        }
        if ("close".equals(hoveredTarget)) {
            graphics.setColor(new Color(91, 166, 245, 105));
            graphics.fillRoundRect(layout.closeButton.x, layout.closeButton.y,
                    layout.closeButton.width, layout.closeButton.height, 18, 18);
        }
        if ("user-profile".equals(hoveredTarget)) {
            graphics.setColor(new Color(91, 166, 245, 105));
            graphics.fillRoundRect(layout.userButton.x, layout.userButton.y,
                    layout.userButton.width, layout.userButton.height, 18, 18);
        }

        graphics.setColor(new Color(230, 235, 242, 180));
        graphics.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if ("move".equals(hoveredTarget)) {
            graphics.setColor(new Color(91, 166, 245, 75));
            graphics.fillRoundRect(layout.pageHeading.x, layout.pageHeading.y,
                    layout.pageHeading.width, layout.pageHeading.height, 20, 20);
            graphics.setColor(new Color(230, 235, 242, 180));
        }
        int buttonCenterY = layout.minimizeButton.y + layout.minimizeButton.height / 2;
        int minimizeCenterX = layout.minimizeButton.x + layout.minimizeButton.width / 2;
        int closeCenterX = layout.closeButton.x + layout.closeButton.width / 2;
        graphics.drawLine(minimizeCenterX - 19, buttonCenterY,
                minimizeCenterX + 19, buttonCenterY);
        drawCloseIcon(graphics, closeCenterX, buttonCenterY,
                "close".equals(hoveredTarget));
        drawUserIcon(graphics, layout.userButton.x + layout.userButton.width / 2,
                buttonCenterY, "user-profile".equals(hoveredTarget));
        graphics.setColor(new Color(228, 234, 241, 240));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        drawPageArrow(graphics, layout.previousPage, -1, page.step(-1) != page, "previous-page".equals(hoveredTarget));
        drawPageArrow(graphics, layout.nextPage, 1, page.step(1) != page, "next-page".equals(hoveredTarget));
        int iconWidth = 68;
        int iconGap = 20;
        int textWidth = layout.pageHeading.width - iconWidth - iconGap - 12;
        int measuredWidth = graphics.getFontMetrics().stringWidth(page.title());
        if (measuredWidth > textWidth && textWidth > 0) {
            graphics.setFont(graphics.getFont().deriveFont(34f * textWidth / measuredWidth));
        }
        String title = PanelText.fit(graphics, page.title(), textWidth);
        int titleX = (layout.logicalWidth - graphics.getFontMetrics().stringWidth(title) + iconWidth + iconGap) / 2;
        int iconLeft = titleX - iconGap - iconWidth;
        switch (page) {
            case TECHNICAL_OBJECTS -> drawNetworkIcon(graphics, iconLeft + 18, 180);
            case ROUNDS -> drawRouteIcon(graphics, iconLeft, 180);
        }
        graphics.setColor(FOREGROUND);
        graphics.drawString(title, titleX, 192);
    }

    private void drawPageArrow(Graphics2D graphics, java.awt.Rectangle button, int direction,
                               boolean enabled, boolean hovered) {
        if (enabled && hovered) {
            graphics.setColor(new Color(91, 166, 245, 105));
            graphics.fill(button);
        }
        graphics.setColor(enabled ? FOREGROUND : new Color(185, 195, 205, 90));
        graphics.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int x = button.x + button.width / 2;
        int y = button.y + button.height / 2;
        graphics.drawLine(x - direction * 7, y - 12, x + direction * 6, y);
        graphics.drawLine(x + direction * 6, y, x - direction * 7, y + 12);
    }

    void drawMinimized(Graphics2D graphics, PanelUiLayout layout, String hoveredTarget) {
        graphics.setColor(new Color(36, 39, 43, 190));
        graphics.fillRoundRect(layout.panel.x, layout.panel.y,
                layout.panel.width, layout.panel.height, 30, 30);
        int centerY = layout.pixelHeight / 2;
        if ("minimize".equals(hoveredTarget)) {
            graphics.setColor(new Color(91, 166, 245, 230));
        } else {
            graphics.setColor(new Color(230, 235, 242, 220));
        }
        int minimizeCenterX = layout.minimizeButton.x + layout.minimizeButton.width / 2;
        int closeCenterX = layout.closeButton.x + layout.closeButton.width / 2;
        int userCenterX = layout.userButton.x + layout.userButton.width / 2;
        int lineHeight = Math.round(5.0f * AnchoredWindow.EXPANDED_HEIGHT_METERS
                / AnchoredWindow.MINIMIZED_HEIGHT_METERS);
        graphics.fillRoundRect(minimizeCenterX - 22, centerY - lineHeight / 2,
                44, lineHeight, lineHeight, lineHeight);
        drawCloseIcon(graphics, closeCenterX, centerY, "close".equals(hoveredTarget));
        drawUserIcon(graphics, userCenterX, centerY, "user-profile".equals(hoveredTarget));
    }

    private void drawCornerMarkers(Graphics2D graphics, PanelUiLayout layout, boolean hovered) {
        int inset = 18;
        int length = 38;
        int right = layout.logicalWidth - inset;
        int bottom = layout.pixelHeight - inset;
        graphics.setColor(hovered ? new Color(91, 166, 245, 245) : new Color(235, 240, 245, 175));
        graphics.setStroke(new BasicStroke(hovered ? 7.0f : 5.0f,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
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
        graphics.setColor(hovered ? new Color(91, 166, 245, 240) : FOREGROUND);
        graphics.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(centerX - 14, centerY - 14, centerX + 14, centerY + 14);
        graphics.drawLine(centerX + 14, centerY - 14, centerX - 14, centerY + 14);
    }

    private void drawUserIcon(Graphics2D graphics, int centerX, int centerY, boolean hovered) {
        graphics.setColor(hovered ? new Color(91, 166, 245, 240) : FOREGROUND);
        graphics.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawOval(centerX - 9, centerY - 18, 18, 18);
        graphics.drawArc(centerX - 20, centerY + 1, 40, 30, 0, 180);
    }

    private void drawRouteIcon(Graphics2D graphics, int left, int centerY) {
        graphics.setColor(FOREGROUND);
        graphics.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int top = centerY - 15;
        int bottom = centerY + 15;
        graphics.drawOval(left + 2, top - 5, 10, 10);
        graphics.drawLine(left + 12, top, left + 41, top);
        graphics.drawArc(left + 33, top, 16, 15, -90, 180);
        graphics.drawLine(left + 41, centerY, left + 26, centerY);
        graphics.drawArc(left + 18, centerY, 16, 15, 90, 180);
        graphics.drawLine(left + 26, bottom, left + 55, bottom);
        graphics.fillOval(left + 55, bottom - 5, 11, 11);
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
}
