package com.example.myvillage.client.cultivation;

/** Screen-space arithmetic only; all resource values come from the server snapshot. */
public record QiBarLayout(int x, int y, int width) {
    public static QiBarLayout at(int screenWidth, int screenHeight) {
        int width = Math.min(91, Math.max(1, screenWidth - 55));
        return new QiBarLayout(38, 25, width);
    }

    public int frameX() { return x - 30; }
    public int frameY() { return y - 17; }
    public int frameWidth() { return width + 39; }
    public int frameHeight() { return 38; }
    public int textY() { return frameY() + 5; }
    public int chargeY() { return y + 10; }
    public int chargeX() { return x + 52; }
    public int chargeWidth() { return Math.max(0, width - 52); }

    public int chargeFill(double fraction) {
        return (int) Math.round(chargeWidth() * (Double.isFinite(fraction) ? Math.clamp(fraction, 0, 1) : 0));
    }

    public int fill(long current, long maximum) {
        if (maximum <= 0 || current <= 0) return 0;
        return (int) Math.round(width * Math.min(1.0, (double) current / maximum));
    }
}
