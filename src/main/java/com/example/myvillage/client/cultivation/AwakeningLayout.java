package com.example.myvillage.client.cultivation;

/** Deterministic layout shared by rendering, hit targets and GUI-size checks. */
public record AwakeningLayout(Rect frame, Rect root, Rect grid, Rect summary,
        Rect reroll, Rect lock, Rect confirm, Rect close, int columns) {
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }
    public static AwakeningLayout at(int width, int height) {
        int w = Math.min(448, width - 16), h = Math.min(246, height - 16);
        int x = (width - w) / 2, y = (height - h) / 2;
        int inner = w - 24, footer = y + h - 46, gap = 5;
        int action = (inner - gap * 2) / 3;
        return new AwakeningLayout(new Rect(x, y, w, h), new Rect(x + 12, y + 32, inner, 32),
                new Rect(x + 12, y + 84, inner, footer - y - 90),
                new Rect(x + 12, footer, inner, 13),
                new Rect(x + 12, footer + 17, action, 19),
                new Rect(x + 12 + action + gap, footer + 17, action, 19),
                new Rect(x + 12 + (action + gap) * 2, footer + 17, inner - (action + gap) * 2, 19),
                new Rect(x + w - 27, y + 10, 16, 16), w >= 380 ? 3 : 2);
    }
    public Rect card(int slot) {
        if (slot < 0 || slot >= 6) throw new IllegalArgumentException("slot");
        int gap = 4, rows = 6 / columns;
        int w = (grid.width - gap * (columns - 1)) / columns;
        int h = (grid.height - gap * (rows - 1)) / rows;
        return new Rect(grid.x + slot % columns * (w + gap), grid.y + slot / columns * (h + gap), w, h);
    }
}
