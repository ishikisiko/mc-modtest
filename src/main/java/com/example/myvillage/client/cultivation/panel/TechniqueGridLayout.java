package com.example.myvillage.client.cultivation.panel;

/** Shared geometry for catalogue and skill assignment; cells stay inside the supplied width. */
final class TechniqueGridLayout {
    static final int GAP = 4;
    static final int HEIGHT = 60;
    record Grid(int columns, int cellWidth, int rows, int height) {
        int x(int index) { return index % columns * (cellWidth + GAP); }
        int y(int index) { return index / columns * (HEIGHT + GAP); }
    }
    private TechniqueGridLayout() {}
    static Grid of(int width, int count) {
        if (width < 1 || count < 0) throw new IllegalArgumentException("Invalid grid bounds");
        int columns = Math.max(1, Math.min(4, (width + GAP) / 68));
        int cell = Math.max(1, (width - (columns - 1) * GAP) / columns);
        int rows = (count + columns - 1) / columns;
        return new Grid(columns, cell, rows, rows == 0 ? 0 : rows * (HEIGHT + GAP) - GAP);
    }
}
