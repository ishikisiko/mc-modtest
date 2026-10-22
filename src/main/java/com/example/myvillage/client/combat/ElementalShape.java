package com.example.myvillage.client.combat;

import com.example.myvillage.combat.elemental.ElementalSpell;
import java.util.*;
import static com.example.myvillage.client.combat.ArtRibbonShape.*;

/** Small procedural elemental silhouettes; colors and reach come from the shared authored art. */
public final class ElementalShape {
    private ElementalShape() {}
    public static List<Quad> mesh(ElementalSpell spell,boolean warning,int age,int segments) {
        List<Quad> out=new ArrayList<>();
        double r=spell.shape().equals("bolt")||spell.shape().equals("ray")?.65:spell.role().equals("ward")?1:spell.radius();
        double y=warning?.08:.16;
        for(int i=0;i<segments;i++) {
            double a=2*Math.PI*i/segments,b=2*Math.PI*(i+1)/segments;
            out.add(new Quad(point(a,r,y),point(b,r,y),point(b,r-.035,y),point(a,r-.035,y)));
        }
        if(warning && !spell.role().equals("ward"))return List.copyOf(out);
        int count=Math.max(4,segments/4);
        for(int i=0;i<count;i++) {
            double a=2*Math.PI*i/count;
            double x=Math.sin(a)*r*.65,z=Math.cos(a)*r*.65;
            switch(spell.element()) {
                case METAL -> {
                    // Fine converging needles: no sword hilts or melee slash silhouettes.
                    out.add(new Quad(new Point(x-.035,1.8,z),new Point(x+.035,1.8,z),new Point(x,0.2,z),new Point(x,0.2,z)));
                    out.add(new Quad(new Point(x,1.8,z-.035),new Point(x,1.8,z+.035),new Point(x,0.2,z),new Point(x,0.2,z)));
                }
                case WOOD -> {
                    for(int j=0;j<8;j++) {
                        double t=j/8.0,u=(j+1)/8.0;
                        out.add(new Quad(vine(x,z,a,t,0),vine(x,z,a,u,0),vine(x,z,a,u,.055),vine(x,z,a,t,.055)));
                    }
                }
                case WATER -> {
                    double h=.15+i*.16;
                    for(int j=0;j<segments;j++) {
                        double b=2*Math.PI*j/segments,c=2*Math.PI*(j+1)/segments,rad=r*(1-i/(double)(count+1));
                        out.add(new Quad(point(b,rad,h),point(c,rad,h),point(c,rad-.045,h),point(b,rad-.045,h)));
                    }
                }
                case FIRE -> {
                    double top=1.2+.6*(1+Math.sin(a*3))/2;
                    out.add(new Quad(new Point(x-.14,.1,z),new Point(x+.14,.1,z),new Point(x+.06,top*.65,z),new Point(x,top,z)));
                    out.add(new Quad(new Point(x,.1,z-.14),new Point(x,.1,z+.14),new Point(x,top*.65,z+.06),new Point(x,top,z)));
                }
                case EARTH -> {
                    double w=.18,top=.6+.3*Math.abs(Math.cos(a*2));
                    out.add(new Quad(new Point(x-w,.1,z-w),new Point(x+w,.1,z-w),new Point(x+w*.6,top,z),new Point(x-w*.6,top,z)));
                    out.add(new Quad(new Point(x+w,.1,z+w),new Point(x-w,.1,z+w),new Point(x-w*.6,top,z),new Point(x+w*.6,top,z)));
                }
                default -> throw new IllegalArgumentException("Not an elemental channel");
            }
        }
        return List.copyOf(out);
    }
    private static Point point(double a,double r,double y){return new Point(Math.sin(a)*r,y,Math.cos(a)*r);}
    private static Point vine(double x,double z,double a,double t,double width){return new Point(x+Math.sin(a+t*5)*.18+width,t*1.6+.1,z+Math.cos(a+t*5)*.18);}
}
