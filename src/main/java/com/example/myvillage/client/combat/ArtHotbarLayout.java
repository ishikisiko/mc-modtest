package com.example.myvillage.client.combat;

/** Fill each side outwards before wrapping upwards, clear of the hotbar and offhand. */
public final class ArtHotbarLayout {
    public static final int SIZE = 24;
    private static final int HOTBAR_HALF = 91, SIDE_RESERVE = 32, GAP = 4, EDGE = 2;
    private ArtHotbarLayout() {}
    public record Slot(int x,int y,int size) {}
    public static Slot slot(int width,int height,int index) {
        if (index < 0 || index > 7) throw new IllegalArgumentException("Eight skill slots");
        int columns = columns(width), ordinal = index/2;
        int offset = (ordinal%columns)*(SIZE+GAP);
        int x = index%2 == 0 ? width/2-HOTBAR_HALF-SIDE_RESERVE-SIZE-offset
                : width/2+HOTBAR_HALF+SIDE_RESERVE+offset;
        return new Slot(x,height-SIZE-2-(ordinal/columns)*(SIZE+GAP),SIZE);
    }
    private static int columns(int width) {
        int available = width/2-HOTBAR_HALF-SIDE_RESERVE-EDGE;
        return Math.clamp((available+GAP)/(SIZE+GAP),1,4);
    }
    public static boolean fits(int width,int height) {
        return slot(width,height,0).x() >= 2 && slot(width,height,1).x()+SIZE <= width-2 && height >= 136;
    }
    public static int coverHeight(int remaining,int total,int size) {
        return (int)Math.ceil(size*Math.clamp((double)remaining/Math.max(1,total),0,1));
    }
    public static String cooldownText(int ticks) {
        if (ticks <= 0) return "";
        return ticks < 20 ? String.format(java.util.Locale.ROOT,"%.1f",ticks/20.0) : Integer.toString((ticks+19)/20);
    }
}
