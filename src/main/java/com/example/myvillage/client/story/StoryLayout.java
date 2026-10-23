package com.example.myvillage.client.story;

/** Pure geometry shared by rendering and focused tests. All values are GUI coordinates. */
public final class StoryLayout {
    private StoryLayout() { }
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) { return px >= x && px < right() && py >= y && py < bottom(); }
    }
    public record Paper(Rect panel, Rect body, int optionCount) {
        public Rect option(int index) {
            if (index < 0 || index >= optionCount) throw new IllegalArgumentException("option index");
            int columns = optionCount > 1 ? 2 : 1;
            int rows = (optionCount + columns - 1) / columns;
            int buttonWidth = (panel.width() - 24 - (columns - 1) * 4) / columns;
            int top = panel.bottom() - 20 - rows * 36 - (rows - 1) * 4;
            return new Rect(panel.x() + 12 + index % columns * (buttonWidth + 4),
                    top + index / columns * 40, buttonWidth, 36);
        }
    }
    public static Paper paper(int width, int height, int options) {
        if (width < 160 || height < 160 || options < 0 || options > 4) throw new IllegalArgumentException("story viewport");
        int w = Math.min(520, width - 16), h = Math.min(420, height - 16);
        Rect panel = new Rect((width - w) / 2, (height - h) / 2, w, h);
        int rows = (options + 1) / 2;
        int optionHeight = rows == 0 ? 0 : rows * 36 + (rows - 1) * 4 + 8;
        Rect body = new Rect(panel.x() + 12, panel.y() + 68, w - 28,
                Math.max(1, h - 68 - 20 - optionHeight));
        return new Paper(panel, body, options);
    }
    /** Always right of the crosshair, including at 320x240. No HUD background is drawn. */
    public static Rect tracker(int width, int height) {
        int w = Math.max(1, Math.min(230, width / 2 - 24));
        return new Rect(width - w - 12, Math.min(48, Math.max(8, height / 6)), w, Math.max(1, height - 76));
    }
    public static int scroll(int requested, int contentHeight, int viewportHeight) {
        return Math.max(0, Math.min(requested, Math.max(0, contentHeight - viewportHeight)));
    }
    public enum TrackerMode {
        COMPACT, EXPANDED, HIDDEN;
        public TrackerMode next() { return values()[(ordinal() + 1) % values().length]; }
    }
}
