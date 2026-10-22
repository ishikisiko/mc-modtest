package com.example.myvillage.client.combat;

/** Screen-space only: keep both skill slots outside the hotbar, offhand and attack indicator. */
public final class ArtHotbarLayout {
    public static final int SIZE = 24;
    private static final int HOTBAR_HALF = 91, SIDE_RESERVE = 32;
    private ArtHotbarLayout() {}
    public record Slot(int x,int y,int size) {}
    public static Slot slot(int width,int height,int index) {
        if (index < 0 || index > 1) throw new IllegalArgumentException("Two skill slots");
        int x = index == 0 ? width/2-HOTBAR_HALF-SIDE_RESERVE-SIZE : width/2+HOTBAR_HALF+SIDE_RESERVE;
        return new Slot(x,height-SIZE-2,SIZE);
    }
    public static boolean fits(int width,int height) {
        return slot(width,height,0).x() >= 2 && slot(width,height,1).x()+SIZE <= width-2 && height >= 80;
    }
    public static int coverHeight(int remaining,int total,int size) {
        return (int)Math.ceil(size*Math.clamp((double)remaining/Math.max(1,total),0,1));
    }
    public static String cooldownText(int ticks) {
        if (ticks <= 0) return "";
        return ticks < 20 ? String.format(java.util.Locale.ROOT,"%.1f",ticks/20.0) : Integer.toString((ticks+19)/20);
    }
}
