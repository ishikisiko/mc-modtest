package com.example.myvillage.client.cultivation;

/** Screen-space arithmetic only; all resource values come from the server snapshot. */
public record QiBarLayout(int x, int y, int width) {
    public static QiBarLayout at(int screenWidth, int screenHeight) {
        int width = Math.min(91, Math.max(1, screenWidth - 16));
        return new QiBarLayout((screenWidth - width) / 2, Math.max(12, screenHeight - 68), width);
    }

    public int fill(long current, long maximum) {
        if (maximum <= 0 || current <= 0) return 0;
        return (int) Math.round(width * Math.min(1.0, (double) current / maximum));
    }
}
