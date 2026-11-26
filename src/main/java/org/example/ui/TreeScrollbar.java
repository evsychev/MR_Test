package org.example.ui;

record TreeScrollbar(int top, int height, int pageSize, int rowCount) {
    int maxOffset() {
        return Math.max(0, rowCount - pageSize);
    }

    int thumbHeight() {
        if (maxOffset() == 0) return height;
        return Math.min(height, Math.max(54, (int) ((long) height * pageSize / rowCount)));
    }

    int thumbTop(int offset) {
        return top + (int) ((long) (height - thumbHeight()) * offset / Math.max(1, maxOffset()));
    }

    int offsetAt(float thumbTop) {
        int travel = height - thumbHeight();
        if (travel <= 0) return 0;
        float fraction = Math.clamp((thumbTop - top) / travel, 0f, 1f);
        return Math.round(fraction * maxOffset());
    }
}
