package com.example.myvillage.client.combat;

import java.util.ArrayList;
import java.util.List;

/** Pure local-space luminous ribbons. Geometry is deterministic and bounded to authored reach. */
public final class ArtRibbonShape {
    public record Point(double x,double y,double z) {}
    public record Quad(Point a,Point b,Point c,Point d) {}
    private ArtRibbonShape() {}
    public static List<Quad> mesh(String effect,double reach,double arc,double height,double width,int segments,boolean ward) {
        if (!java.util.Set.of("arc","helix","ring","lotus","shock").contains(effect)
                || !Double.isFinite(reach) || reach<=0 || reach>16 || !Double.isFinite(arc) || arc<=0 || arc>360
                || !Double.isFinite(height) || height<0 || height>3 || !Double.isFinite(width) || width<=0 || width>3 || segments<8 || segments>64)
            throw new IllegalArgumentException("Invalid ribbon geometry");
        List<Quad> quads=new ArrayList<>();
        if(ward) {
            for(int i=0;i<segments;i++) {
                double a=i*2*Math.PI/segments,b=(i+1)*2*Math.PI/segments;
                quads.add(new Quad(ward(a,.85),ward(b,.85),ward(b,.74),ward(a,.74)));
            }
        } else if(effect.equals("helix")) {
            for(int strand=0;strand<2;strand++) for(int i=0;i<segments;i++) {
                double a=(double)i/segments,b=(double)(i+1)/segments;
                quads.add(new Quad(helix(a,reach,height,strand,width*.5),helix(b,reach,height,strand,width*.5),
                        helix(b,reach,height,strand,width*.3),helix(a,reach,height,strand,width*.3)));
            }
        } else {
            int layers=effect.equals("shock")?3:2;
            for(int layer=0;layer<layers;layer++) for(int i=0;i<segments;i++) {
                double a=Math.toRadians(-arc/2+arc*i/segments),b=Math.toRadians(-arc/2+arc*(i+1)/segments);
                double r=reach*(1-layer*.22);
                double y=effect.equals("shock")?.12+layer*.04:height;
                double band=Math.min(r*.4,width*.5);
                quads.add(new Quad(radial(a,r,y,effect),radial(b,r,y,effect),
                        radial(b,r-band,y,effect),radial(a,r-band,y,effect)));
            }
        }
        if(!ward && effect.equals("lotus")) for(int i=0;i<segments;i++) {
            double a=Math.toRadians(-arc/2+arc*i/segments),b=Math.toRadians(-arc/2+arc*(i+1)/segments);
            quads.add(new Quad(radial(a,reach,height,"ring"),radial(b,reach,height,"ring"),
                    radial(b,reach-width*.15,height,"ring"),radial(a,reach-width*.15,height,"ring")));
        }
        return List.copyOf(quads);
    }
    private static Point ward(double angle,double radius) {
        return new Point(Math.sin(angle)*radius,1.05+Math.cos(angle)*radius,.7);
    }
    private static Point helix(double t,double reach,double height,int strand,double radius) {
        double a=t*Math.PI*4+strand*Math.PI;
        // Round the head inside the reach, rather than extending a halo past the hit envelope.
        double r=radius*Math.sin(Math.PI*t);
        return new Point(Math.sin(a)*r,height+Math.cos(a)*r,t*reach);
    }
    private static Point radial(double angle,double radius,double height,String effect) {
        double r=effect.equals("lotus")?radius*(.82+.18*Math.cos(angle*6)):radius;
        double y=height+(effect.equals("arc")?.12*Math.sin(angle*2):0);
        return new Point(Math.sin(angle)*r,y,Math.cos(angle)*r);
    }
}
