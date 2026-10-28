package com.example.myvillage.worldplan.atlas;

import java.util.List;
import java.util.Optional;

/** Shares rounded marker coordinates between drawing and hit testing; gesture state is pure Java. */
public final class AtlasCityInteraction {
    public record MapArea(int left, int top, int size) {
        public boolean contains(double x, double y) {
            return x>=left && x<left+size && y>=top && y<top+size;
        }
    }
    public record Pixel(int x, int y) { }
    public record Delta(double x, double y) { }
    private final double slop;
    private boolean held, dragged, suppressed;
    private double downX, downY, lastX, lastY, distance;
    private String pressedCity;

    public AtlasCityInteraction(double slop) {
        if (!Double.isFinite(slop) || slop<=0) throw new IllegalArgumentException("Invalid click slop");
        this.slop=slop;
    }
    public static Pixel marker(MapArea area, AtlasViewport viewport, WorldAtlas.City city, int radius) {
        return new Pixel((int)Math.round(area.left()+viewport.mapX(city.x(),radius)*area.size()),
                (int)Math.round(area.top()+viewport.mapZ(city.z(),radius)*area.size()));
    }
    public static Optional<WorldAtlas.City> hit(List<WorldAtlas.City> cities, AtlasViewport viewport, int radius,
            MapArea area, double x, double y, int hitRadius) {
        if (!area.contains(x,y)) return Optional.empty();
        WorldAtlas.City nearest=null;
        double best=Double.POSITIVE_INFINITY;
        for (var city : cities) {
            var at=marker(area,viewport,city,radius);
            if (!area.contains(at.x(),at.y())) continue;
            double dx=x-at.x(), dy=y-at.y(), d=dx*dx+dy*dy;
            if (Math.abs(dx)<=hitRadius && Math.abs(dy)<=hitRadius && d<best) { nearest=city; best=d; }
        }
        return Optional.ofNullable(nearest);
    }
    public void press(double x, double y, String city) {
        held=true; dragged=false; suppressed=false; distance=0;
        downX=lastX=x; downY=lastY=y; pressedCity=city;
    }
    public Delta drag(double x, double y) {
        if (!held) return new Delta(0,0);
        boolean wasDragged=dragged;
        double dx=x-lastX, dy=y-lastY;
        motion(x,y);
        return !dragged ? new Delta(0,0) : wasDragged ? new Delta(dx,dy) : new Delta(x-downX,y-downY);
    }
    private void motion(double x, double y) {
        distance+=Math.hypot(x-lastX,y-lastY); lastX=x; lastY=y;
        if (distance>slop || Math.hypot(x-downX,y-downY)>slop) dragged=true;
    }
    public Optional<String> release(double x, double y, String city) {
        if (!held) return Optional.empty();
        motion(x,y);
        String selected=!dragged && !suppressed && pressedCity!=null && pressedCity.equals(city) ? city : null;
        cancel();
        return Optional.ofNullable(selected);
    }
    public boolean held() { return held; }
    public boolean dragged() { return dragged; }
    public void suppressClick() { suppressed=true; }
    public void cancel() { held=false; dragged=false; suppressed=false; pressedCity=null; }
}
