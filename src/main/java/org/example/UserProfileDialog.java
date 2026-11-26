package org.example;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.List;

final class UserProfileDialog {
    private static final Color TEXT = new Color(235, 240, 245, 242);
    private static final Color MUTED = new Color(181, 190, 201, 225);

    void draw(Graphics2D graphics, PanelUiLayout layout, UserProfile profile, String hoveredTarget) {
        graphics.setColor(new Color(7, 10, 13, 155));
        graphics.fillRect(0, 0, layout.logicalWidth, layout.pixelHeight);
        int left = 50;
        int top = 130;
        int width = layout.logicalWidth - 100;
        int height = layout.pixelHeight - 260;
        graphics.setColor(new Color(34, 38, 43, 248));
        graphics.fillRoundRect(left, top, width, height, 28, 28);
        graphics.setColor(new Color(188, 199, 210, 155));
        graphics.setStroke(new BasicStroke(2.0f));
        graphics.drawRoundRect(left, top, width, height, 28, 28);

        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        graphics.setColor(TEXT);
        graphics.drawString("User profile", left + 34, top + 55);
        drawClose(graphics, layout.logicalWidth - 88, top + 48,
                "profile-close".equals(hoveredTarget));

        List<String[]> rows = List.of(
                new String[]{"Name", profile.value("fio")},
                new String[]{"Username", profile.value("username")},
                new String[]{"Position", profile.value("post")},
                new String[]{"Email", profile.value("email")},
                new String[]{"Organization", profile.value("ost")},
                new String[]{"Role", profile.value("roles")}
        );
        int y = top + 118;
        for (String[] row : rows) {
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
            graphics.setColor(MUTED);
            graphics.drawString(row[0], left + 36, y);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 21));
            graphics.setColor(TEXT);
            graphics.drawString(fitText(graphics, row[1], width - 310), left + 250, y);
            y += 70;
        }
    }

    private static void drawClose(Graphics2D graphics, int x, int y, boolean hovered) {
        graphics.setColor(hovered ? new Color(91, 166, 245, 240) : TEXT);
        graphics.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(x - 14, y - 14, x + 14, y + 14);
        graphics.drawLine(x + 14, y - 14, x - 14, y + 14);
    }

    private static String fitText(Graphics2D graphics, String value, int width) {
        String result = value;
        while (!result.isEmpty() && graphics.getFontMetrics().stringWidth(result + "…") > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result.equals(value) ? result : result + "…";
    }
}
