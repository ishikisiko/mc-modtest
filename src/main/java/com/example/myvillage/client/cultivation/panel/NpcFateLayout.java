package com.example.myvillage.client.cultivation.panel;

/** 三个出生词条的流式布局，窄视口换行后仍由宿主正文滚动。 */
public final class NpcFateLayout {
    public static final int HEADER = 16, CARD_HEIGHT = 64, GAP = 6;
    private NpcFateLayout() { }
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
    public static int columns(int width) { return Math.max(1, Math.min(3, (width + GAP) / 110)); }
    public static int height(int width) {
        int rows = (3 + columns(width) - 1) / columns(width);
        return HEADER + rows * CARD_HEIGHT + (rows - 1) * GAP;
    }
    public static Rect card(int x, int y, int width, int index) {
        if (width < 1 || index < 0 || index >= 3) throw new IllegalArgumentException("NPC 天赋卡片布局");
        int columns = columns(width), cell = (width - (columns - 1) * GAP) / columns;
        return new Rect(x + index % columns * (cell + GAP),
                y + HEADER + index / columns * (CARD_HEIGHT + GAP), cell, CARD_HEIGHT);
    }
}
