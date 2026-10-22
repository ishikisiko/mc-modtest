package com.example.myvillage.client.combat;

import com.example.myvillage.combat.elemental.ElementalSpell;
import com.example.myvillage.combat.elemental.ElementalRules;
import java.util.*;
import static com.example.myvillage.client.combat.ArtRibbonShape.*;

/** Small procedural elemental silhouettes; colors and reach come from the shared authored art. */
public final class ElementalShape {
    private ElementalShape() {}
    public static List<Quad> mesh(ElementalSpell spell,boolean warning,int age,int segments) {
        List<Quad> out=new ArrayList<>();
        double scale=ElementalRules.number("visual_scale",.5,3);
        if(spell.shape().equals("cone"))return cone(spell,warning,segments,scale);
        double r=spell.shape().equals("bolt")?spell.radius()*1.3:
                spell.shape().equals("ray")?ElementalRules.number("aim_width",.05,2):spell.role().equals("ward")?scale:spell.radius();
        double y=warning?.08:.16;
        for(int i=0;i<segments;i++) {
            double a=2*Math.PI*i/segments,b=2*Math.PI*(i+1)/segments;
            out.add(new Quad(point(a,r,y),point(b,r,y),point(b,r-.035*scale,y),point(a,r-.035*scale,y)));
        }
        if(warning && !spell.role().equals("ward"))return List.copyOf(out);
        int count=Math.max(4,segments/4);
        for(int i=0;i<count;i++) {
            double a=2*Math.PI*i/count;
            double x=Math.sin(a)*r*.65,z=Math.cos(a)*r*.65;
            switch(spell.element()) {
                case METAL -> {
                    // Fine converging needles: no sword hilts or melee slash silhouettes.
                    out.add(new Quad(new Point(x-.035*scale,1.8*scale,z),new Point(x+.035*scale,1.8*scale,z),new Point(x,0.2,z),new Point(x,0.2,z)));
                    out.add(new Quad(new Point(x,1.8*scale,z-.035*scale),new Point(x,1.8*scale,z+.035*scale),new Point(x,0.2,z),new Point(x,0.2,z)));
                }
                case WOOD -> {
                    for(int j=0;j<8;j++) {
                        double t=j/8.0,u=(j+1)/8.0;
                        out.add(new Quad(vine(x,z,a,t,0,scale),vine(x,z,a,u,0,scale),vine(x,z,a,u,.055*scale,scale),vine(x,z,a,t,.055*scale,scale)));
                    }
                }
                case WATER -> {
                    double h=.15+i*.16*scale;
                    for(int j=0;j<segments;j++) {
                        double b=2*Math.PI*j/segments,c=2*Math.PI*(j+1)/segments,rad=r*(1-i/(double)(count+1));
                        out.add(new Quad(point(b,rad,h),point(c,rad,h),point(c,rad-.045*scale,h),point(b,rad-.045*scale,h)));
                    }
                }
                case FIRE -> {
                    double top=(1.2+.6*(1+Math.sin(a*3))/2)*scale;
                    out.add(new Quad(new Point(x-.14*scale,.1,z),new Point(x+.14*scale,.1,z),new Point(x+.06*scale,top*.65,z),new Point(x,top,z)));
                    out.add(new Quad(new Point(x,.1,z-.14*scale),new Point(x,.1,z+.14*scale),new Point(x,top*.65,z+.06*scale),new Point(x,top,z)));
                }
                case EARTH -> {
                    double w=.18*scale,top=(.6+.3*Math.abs(Math.cos(a*2)))*scale;
                    out.add(new Quad(new Point(x-w,.1,z-w),new Point(x+w,.1,z-w),new Point(x+w*.6,top,z),new Point(x-w*.6,top,z)));
                    out.add(new Quad(new Point(x+w,.1,z+w),new Point(x-w,.1,z+w),new Point(x-w*.6,top,z),new Point(x+w*.6,top,z)));
                }
                default -> throw new IllegalArgumentException("Not an elemental channel");
            }
        }
        if(spell.role().equals("ward") && spell.push()>0)for(int i=0;i<segments;i++) {
            double a=2*Math.PI*i/segments,b=2*Math.PI*(i+1)/segments,edge=spell.radius();
            out.add(new Quad(point(a,edge,.08),point(b,edge,.08),point(b,edge-.06*scale,.08),point(a,edge-.06*scale,.08)));
        }
        return List.copyOf(out);
    }
    private static List<Quad> cone(ElementalSpell spell,boolean warning,int segments,double scale) {
        var out=new ArrayList<Quad>();double reach=spell.footprintRadius();
        int layers=warning?1:6;
        for(int layer=0;layer<layers;layer++)for(int i=0;i<segments;i++) {
            double a=Math.toRadians(-60+120.0*i/segments),b=Math.toRadians(-60+120.0*(i+1)/segments);
            double r=reach*(1-layer/7.0),y=warning?.08:.15+layer*.16*scale,w=.065*scale;
            out.add(new Quad(point(a,r,y),point(b,r,y),point(b,r-w,y),point(a,r-w,y)));
        }
        return List.copyOf(out);
    }
    private static Point point(double a,double r,double y){return new Point(Math.sin(a)*r,y,Math.cos(a)*r);}
    private static Point vine(double x,double z,double a,double t,double width,double scale){return new Point(x+Math.sin(a+t*5)*.18*scale+width,t*1.6*scale+.1,z+Math.cos(a+t*5)*.18*scale);}
}
