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
        graphics.drawString(Messages.text("profile.title"), left + 34, top + 55);
        drawClose(graphics, layout.logicalWidth - 88, top + 48,
                "profile-close".equals(hoveredTarget));

        List<String[]> rows = List.of(
                new String[]{Messages.text("profile.name"), profile.name()},
                new String[]{Messages.text("profile.username"), profile.username()},
                new String[]{Messages.text("profile.position"), profile.position()},
                new String[]{Messages.text("profile.email"), profile.email()},
                new String[]{Messages.text("profile.organization"), profile.organization()},
                new String[]{Messages.text("profile.role"), String.join(", ", profile.roles())}
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

        graphics.setColor(TEXT);
        graphics.drawString(Messages.text("profile.language"), layout.russianButton.x, layout.russianButton.y - 16);
        drawLanguage(graphics, layout.russianButton, "ru", hoveredTarget);
        drawLanguage(graphics, layout.englishButton, "en", hoveredTarget);

        var button = layout.profileReset;
        graphics.setColor("profile-reset".equals(hoveredTarget)
                ? new Color(61, 110, 165) : new Color(60, 67, 76));
        graphics.fillRoundRect(button.x, button.y, button.width, button.height, 14, 14);
        graphics.setColor(TEXT);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 21));
        String label = Messages.text("profile.reset");
        var metrics = graphics.getFontMetrics();
        graphics.drawString(label, button.x + (button.width - metrics.stringWidth(label)) / 2,
                button.y + (button.height - metrics.getHeight()) / 2 + metrics.getAscent());
    }

    private static void drawLanguage(Graphics2D graphics, java.awt.Rectangle button, String code, String hovered) {
        boolean selected = Messages.ACTIVE.get().getLanguage().equals(code);
        graphics.setColor(selected ? new Color(48, 105, 162) : new Color(60, 67, 76));
        graphics.fillRoundRect(button.x, button.y, button.width, button.height, 14, 14);
        graphics.setColor(TEXT);
        if (hovered.equals("language-" + code)) {
            graphics.drawRoundRect(button.x, button.y, button.width, button.height, 14, 14);
        }
        String label = Messages.text("language." + code);
        graphics.drawString(label, button.x + (button.width - graphics.getFontMetrics().stringWidth(label)) / 2,
                button.y + 34);
    }

    private static void drawClose(Graphics2D graphics, int x, int y, boolean hovered) {
        graphics.setColor(hovered ? new Color(91, 166, 245, 240) : TEXT);
        graphics.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(x - 14, y - 14, x + 14, y + 14);
        graphics.drawLine(x + 14, y - 14, x - 14, y + 14);
    }
}
