package org.example.ui;

enum PanelPage {
    TECHNICAL_OBJECTS("Дерево РТО"),
    ROUNDS("Обходы");

    final String title;

    PanelPage(String title) {
        this.title = title;
    }

    PanelPage step(int direction) {
        PanelPage[] pages = values();
        return pages[Math.clamp(ordinal() + direction, 0, pages.length - 1)];
    }
}
