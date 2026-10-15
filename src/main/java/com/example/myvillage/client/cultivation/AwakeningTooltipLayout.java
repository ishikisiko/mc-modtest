package com.example.myvillage.client.cultivation;

/** Places a complete tooltip inside the GUI, including its background and padding. */
public final class AwakeningTooltipLayout {
    public static final int MARGIN = 6;
    public static final int PADDING = 7;
    private AwakeningTooltipLayout() { }

    public record Box(int x, int y, int width, int height, double textScale) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
    }
    public static int wrapWidth(int screenWidth) {
        return Math.max(1, Math.min(260, screenWidth - 2 * (MARGIN + PADDING)));
    }
    public static int widestWrap(int screenWidth) {
        return Math.max(1, screenWidth - 2 * (MARGIN + PADDING));
    }
    public static Box place(int screenWidth, int screenHeight, int pointerX, int pointerY,
            int textWidth, int textHeight) {
        int availableWidth = Math.max(1, screenWidth - 2 * (MARGIN + PADDING));
        int availableHeight = Math.max(1, screenHeight - 2 * (MARGIN + PADDING));
        // Normal catalogue tooltips remain at native font size. Oversized datapack text is
        // scaled as a last resort, so no title/effect can disappear beyond the safe rectangle.
        double scale = Math.min(1.0, Math.min(availableWidth / (double)Math.max(1, textWidth),
                availableHeight / (double)Math.max(1, textHeight)));
        int w = Math.min(availableWidth, (int)Math.ceil(textWidth * scale)) + 2 * PADDING;
        int h = Math.min(availableHeight, (int)Math.ceil(textHeight * scale)) + 2 * PADDING;
        int x = pointerX + 12;
        if (x + w > screenWidth - MARGIN) x = pointerX - 12 - w;
        x = Math.max(MARGIN, Math.min(x, screenWidth - MARGIN - w));
        int y = Math.max(MARGIN, Math.min(pointerY + 12, screenHeight - MARGIN - h));
        return new Box(x, y, w, h, scale);
    }
}
