package com.example.myvillage.client.cultivation.panel;

/** Pure geometry for the real H body; no web-preview pixel assumptions. */
public record TechniqueScrollLayout(int width, int height, Rect art, Rect practice,
        Rect firstChoice, Rect secondChoice, int padding, int progressY, int barY, int stagesY) {
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
    }
    public static TechniqueScrollLayout of(int width, int height) {
        width = Math.max(1, width); height = Math.max(1, height);
        int pad = Math.min(8, Math.max(2, width / 32));
        int artH = Math.min(height, width * 2 / 3), artW = artH * 3 / 2;
        int progressY = Math.max(pad, height - 47);
        int buttonW = Math.min(Math.max(52, width / 6), Math.max(1, width - pad * 2));
        int buttonH = Math.min(26, Math.max(1, height / 5));
        Rect practice = new Rect(Math.max(0, width - pad - buttonW), Math.max(pad, progressY - 7), buttonW, buttonH);
        int tagW = Math.min(28, Math.max(18, width / 12));
        int gap = Math.max(4, width / 80), tagH = Math.min(57, Math.max(24, height / 3));
        int tagY = Math.max(pad, Math.min(height / 5, progressY - tagH - 10));
        int tagX = Math.max(pad, width - pad - tagW * 2 - gap);
        return new TechniqueScrollLayout(width, height,
                new Rect((width - artW) / 2, Math.max(0, (height - artH) / 2), artW, artH),
                practice, new Rect(tagX, tagY, tagW, tagH), new Rect(tagX + tagW + gap, tagY, tagW, tagH),
                pad, progressY, Math.max(1, height - 17), Math.max(2, height - 11));
    }
}
