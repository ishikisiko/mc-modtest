package com.example.myvillage.client.boss;

/** GUI-space layout, including space reserved by the top-left Qi HUD. */
public record BossBarLayout(int x, int top, int width, int increment) {
    public static final int HEIGHT = 35;
    public static BossBarLayout at(int screenWidth, int eventY, boolean qiVisible, int qiRight, int qiBottom) {
        int width = Math.min(Math.max(1, screenWidth - 32), Math.max(200, Math.min(300, screenWidth * 3 / 5)));
        int x = (screenWidth - width) / 2;
        int top = Math.max(4, eventY - 9);
        if (qiVisible && x - 10 < qiRight + 4 && top < qiBottom + 4) top = qiBottom + 4;
        return new BossBarLayout(x, top, width, top + HEIGHT + 6 - (eventY - 9));
    }
    public int barY() { return top + 16; }
    public int detailsY() { return top + 26; }
    public int fill(float progress) { return Math.round(width * fraction(progress)); }
    public static float fraction(float value) { return Float.isFinite(value) ? Math.clamp(value, 0, 1) : 0; }
}
