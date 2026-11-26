package org.example.ui;

enum PanelPage {
    TECHNICAL_OBJECTS("page.tree"),
    ROUNDS("page.rounds");

    private final String titleKey;

    PanelPage(String title) {
        this.titleKey = title;
    }

    String title() {
        return Messages.text(titleKey);
    }

    PanelPage step(int direction) {
        PanelPage[] pages = values();
        return pages[Math.clamp(ordinal() + direction, 0, pages.length - 1)];
    }
}
