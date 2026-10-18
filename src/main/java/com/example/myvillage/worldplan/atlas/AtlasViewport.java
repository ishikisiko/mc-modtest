package com.example.myvillage.worldplan.atlas;

/** Map-space transform, independent of Minecraft and window scale. */
public final class AtlasViewport {
    private double zoom=1,dx,dy;
    public double zoom(){return zoom;} public double dx(){return dx;} public double dy(){return dy;}
    public void reset(){zoom=1;dx=dy=0;}
    public void pan(double x,double y){dx+=x;dy+=y;clamp();}
    public void scale(double factor,double x,double y){double next=Math.max(1,Math.min(2.8,zoom*factor));dx=x-(x-dx)*next/zoom;dy=y-(y-dy)*next/zoom;zoom=next;clamp();}
    public void locate(double worldX,double worldZ,int radius){zoom=1.6;dx=.5-(worldX+radius)/(2.0*radius)*zoom;dy=.5-(worldZ+radius)/(2.0*radius)*zoom;clamp();}
    private void clamp(){dx=Math.max(1-zoom,Math.min(0,dx));dy=Math.max(1-zoom,Math.min(0,dy));}
    public double mapX(double worldX,int radius){return dx+(worldX+radius)/(2.0*radius)*zoom;}
    public double mapZ(double worldZ,int radius){return dy+(worldZ+radius)/(2.0*radius)*zoom;}
}
