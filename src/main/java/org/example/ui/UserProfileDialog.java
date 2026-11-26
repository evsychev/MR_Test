package org.example.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.List;

import org.example.profile.UserProfile;

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
                new String[]{"Name", profile.name()},
                new String[]{"Username", profile.username()},
                new String[]{"Position", profile.position()},
                new String[]{"Email", profile.email()},
                new String[]{"Organization", profile.organization()},
                new String[]{"Role", String.join(", ", profile.roles())}
        );
        int y = top + 118;
        for (String[] row : rows) {
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
            graphics.setColor(MUTED);
            graphics.drawString(row[0], left + 36, y);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 21));
            graphics.setColor(TEXT);
            graphics.drawString(PanelText.fit(graphics, row[1], width - 310), left + 250, y);
            y += 70;
        }

        var button = layout.profileReset;
        graphics.setColor("profile-reset".equals(hoveredTarget)
                ? new Color(61, 110, 165) : new Color(60, 67, 76));
        graphics.fillRoundRect(button.x, button.y, button.width, button.height, 14, 14);
        graphics.setColor(TEXT);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 21));
        String label = "Reset settings";
        var metrics = graphics.getFontMetrics();
        graphics.drawString(label, button.x + (button.width - metrics.stringWidth(label)) / 2,
                button.y + (button.height - metrics.getHeight()) / 2 + metrics.getAscent());
    }

    private static void drawClose(Graphics2D graphics, int x, int y, boolean hovered) {
        graphics.setColor(hovered ? new Color(91, 166, 245, 240) : TEXT);
        graphics.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(x - 14, y - 14, x + 14, y + 14);
        graphics.drawLine(x + 14, y - 14, x - 14, y + 14);
    }
}
