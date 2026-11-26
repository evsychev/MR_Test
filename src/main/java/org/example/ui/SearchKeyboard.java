package org.example.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SearchKeyboard {
    private static final int HEIGHT = 390;
    private final List<Key> keys = new ArrayList<>();
    private String text = "";
    private boolean russianLayout;
    private boolean visible;

    boolean visible() {
        return visible;
    }

    String text() {
        return text;
    }

    void show() {
        visible = true;
    }

    void hide() {
        visible = false;
    }

    boolean occupies(int y, int pixelHeight) {
        return visible && y >= pixelHeight - HEIGHT;
    }

    String targetAt(int x, int y) {
        for (Key key : keys) {
            if (inside(x, y, key.x, key.y, key.width, key.height)) {
                return "key-" + key.action;
            }
        }
        return "keyboard";
    }

    void activate(int x, int y) {
        for (Key key : keys) {
            if (!inside(x, y, key.x, key.y, key.width, key.height)) {
                continue;
            }
            switch (key.action) {
                case "BACKSPACE" -> {
                    if (!text.isEmpty()) {
                        text = text.substring(0, text.length() - 1);
                    }
                }
                case "SPACE" -> text += " ";
                case "CLEAR" -> text = "";
                case "DONE" -> visible = false;
                case "LANGUAGE" -> russianLayout = !russianLayout;
                default -> text += key.action.toLowerCase(Locale.ROOT);
            }
            return;
        }
    }

    void draw(Graphics2D graphics, int logicalWidth, int pixelHeight, String hoveredId) {
        int panelY = pixelHeight - HEIGHT;
        graphics.setColor(new Color(29, 32, 36, 232));
        graphics.fillRoundRect(20, panelY, logicalWidth - 40, HEIGHT - 20, 22, 22);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 23));
        graphics.setColor(new Color(220, 228, 237, 215));
        graphics.drawString(Messages.format("keyboard.title", russianLayout ? "RU" : "EN"), 42, panelY + 34);

        keys.clear();
        String[] rows = russianLayout
                ? new String[]{"1234567890", "ЙЦУКЕНГШЩЗХЪ", "ФЫВАПРОЛДЖЭ", "ЯЧСМИТЬБЮ"}
                : new String[]{"1234567890", "QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"};
        int keyHeight = 50;
        int gap = 8;
        int contentWidth = logicalWidth - 80;
        int y = panelY + 48;
        for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
            String row = rows[rowIndex];
            int keyWidth = Math.min(80, (contentWidth - gap * (row.length() - 1)) / row.length());
            int rowWidth = row.length() * keyWidth + (row.length() - 1) * gap;
            int x = (logicalWidth - rowWidth) / 2;
            if (rowIndex > 1) {
                x += keyWidth / 2;
            }
            for (int keyIndex = 0; keyIndex < row.length(); keyIndex++) {
                drawKey(graphics, row.substring(keyIndex, keyIndex + 1),
                        x + keyIndex * (keyWidth + gap), y + rowIndex * (keyHeight + 6),
                        keyWidth, keyHeight, hoveredId);
            }
        }
        int actionY = y + rows.length * (keyHeight + 6) - 2;
        int actionGap = 8;
        int languageWidth = 76;
        int utilityWidth = 82;
        int spaceWidth = contentWidth - languageWidth - utilityWidth * 3 - actionGap * 4;
        int actionX = (logicalWidth - contentWidth) / 2;
        drawKey(graphics, "LANGUAGE", actionX, actionY, languageWidth, keyHeight, hoveredId);
        actionX += languageWidth + actionGap;
        drawKey(graphics, "SPACE", actionX, actionY, spaceWidth, keyHeight, hoveredId);
        actionX += spaceWidth + actionGap;
        drawKey(graphics, "BACKSPACE", actionX, actionY, utilityWidth, keyHeight, hoveredId);
        actionX += utilityWidth + actionGap;
        drawKey(graphics, "CLEAR", actionX, actionY, utilityWidth, keyHeight, hoveredId);
        actionX += utilityWidth + actionGap;
        drawKey(graphics, "DONE", actionX, actionY, utilityWidth, keyHeight, hoveredId);
    }

    private void drawKey(Graphics2D graphics, String action, int x, int y, int width, int height,
                         String hoveredId) {
        keys.add(new Key(action, x, y, width, height));
        boolean hovered = ("key-" + action).equals(hoveredId);
        graphics.setColor(hovered ? new Color(73, 139, 213, 220) : new Color(74, 80, 88, 215));
        graphics.fillRoundRect(x, y, width, height, 12, 12);
        graphics.setColor(new Color(230, 236, 243, 200));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, action.length() > 1 ? 18 : 25));
        String label = switch (action) {
            case "BACKSPACE" -> "⌫";
            case "SPACE" -> Messages.text("keyboard.space");
            case "CLEAR" -> Messages.text("keyboard.clear");
            case "DONE" -> Messages.text("keyboard.done");
            case "LANGUAGE" -> russianLayout ? "EN" : "RU";
            default -> action;
        };
        int labelWidth = graphics.getFontMetrics().stringWidth(label);
        graphics.drawString(label, x + (width - labelWidth) / 2,
                y + (height + graphics.getFontMetrics().getAscent()) / 2 - 4);
    }

    private static boolean inside(int x, int y, int left, int top, int width, int height) {
        return x >= left && x <= left + width && y >= top && y <= top + height;
    }

    private record Key(String action, int x, int y, int width, int height) {
    }
}
