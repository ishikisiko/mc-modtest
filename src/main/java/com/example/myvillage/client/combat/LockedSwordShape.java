package com.example.myvillage.client.combat;

import com.example.myvillage.combat.art.LockedSwordDefinition;
import java.util.*;
import static com.example.myvillage.client.combat.ArtRibbonShape.*;

/** Authored-scale sword silhouettes and impact boundaries; no client hit decisions. */
public final class LockedSwordShape {
    private LockedSwordShape() {}
    public static List<Quad> mesh(LockedSwordDefinition lock,boolean warning,int age) {
        List<Quad> out=new ArrayList<>();
        var v=lock.visual();
        double radius=lock.radius();
        int count=lock.mode().equals("array")?lock.strikes():1;
        if(warning) {
            ring(out,radius,.07,v.ringWidth(),v.segments());
            ring(out,radius*.86,.08,v.ringWidth()*.4,v.segments());
            double progress=Math.clamp((double)age/lock.firstStrike(),0,1);
            for(int i=0;i<count;i++) {
                double a=2*Math.PI*i/count;
                double r=count==1?0:radius*.8;
                sword(out,Math.sin(a)*r,Math.cos(a)*r,v.swordTop(),
                        v.warningLength()*(.7+progress*.3),v.swordHalfWidth()*(.6+progress*.4));
                if(count>1) seal(out,Math.sin(a)*r,Math.cos(a)*r,v.swordHalfWidth()*2.4,v.ringWidth());
            }
        } else if(lock.mode().equals("pierce")) {
            double w=v.swordHalfWidth(),length=v.pierceLength();
            // Crossed planes remain legible both along the beam and from the side.
            out.add(new Quad(new Point(-w,1,-length),new Point(w,1,-length),new Point(0,1,0),new Point(0,1,0)));
            out.add(new Quad(new Point(0,1-w,-length),new Point(0,1+w,-length),new Point(0,1,0),new Point(0,1,0)));
            sword(out,0,0,v.swordTop(),v.swordLength(),w);
            ring(out,radius,.08,v.ringWidth(),v.segments());
        } else if(lock.mode().equals("judgment")) {
            sword(out,0,0,v.swordTop(),v.swordLength(),v.swordHalfWidth());
            ring(out,radius,.12,v.ringWidth()*2,v.segments());
            ring(out,radius*.7,.16,v.ringWidth(),v.segments());
            ring(out,radius*.4,.2,v.ringWidth(),v.segments());
            rays(out,radius,v.ringWidth(),count*12);
        } else {
            int pulse=Math.clamp((age-lock.firstStrike())/lock.interval(),0,count-1);
            // Keep all seven sword positions visible; the current strike reaches the ground.
            for(int i=0;i<count;i++) {
                double a=2*Math.PI*i/count;
                boolean active=i==pulse || pulse==count-1;
                sword(out,Math.sin(a)*radius*.8,Math.cos(a)*radius*.8,v.swordTop(),
                        active?v.swordLength():v.warningLength(),v.swordHalfWidth()*(active?1:.65));
            }
            sword(out,0,0,v.swordTop(),v.swordLength(),v.swordHalfWidth());
            ring(out,radius,.1,v.ringWidth()*2,v.segments());
            ring(out,radius*.84,.12,v.ringWidth(),v.segments());
            if(pulse==count-1)rays(out,radius,v.ringWidth(),14);
        }
        return List.copyOf(out);
    }
    private static Point point(double a,double r,double y) {return new Point(Math.sin(a)*r,y,Math.cos(a)*r);}
    private static void ring(List<Quad> out,double r,double y,double width,int segments) {
        for(int i=0;i<segments;i++) {
            double a=2*Math.PI*i/segments,b=2*Math.PI*(i+1)/segments;
            out.add(new Quad(point(a,r,y),point(b,r,y),point(b,Math.max(0,r-width),y),point(a,Math.max(0,r-width),y)));
        }
    }
    private static void seal(List<Quad> out,double x,double z,double r,double w) {
        out.add(new Quad(new Point(x-r,.09,z-w),new Point(x+r,.09,z-w),new Point(x+r,.09,z+w),new Point(x-r,.09,z+w)));
        out.add(new Quad(new Point(x-w,.09,z-r),new Point(x+w,.09,z-r),new Point(x+w,.09,z+r),new Point(x-w,.09,z+r)));
    }
    private static void rays(List<Quad> out,double r,double w,int count) {
        for(int i=0;i<count;i++) {
            double a=2*Math.PI*i/count,b=a+w/r;
            out.add(new Quad(point(a,r,.11),point(b,r,.11),point(b,r*.2,.11),point(a,r*.2,.11)));
        }
    }
    private static void sword(List<Quad> out,double x,double z,double top,double length,double width) {
        double shoulder=top-length*.7,bottom=top-length;
        // Two bevels and a narrower central ridge give the blade a readable solid profile.
        for(int axis=0;axis<2;axis++) {
            for(int side:new int[]{-1,1}) {
                Point a=blade(x,z,top,width*side,0,axis),b=blade(x,z,top,0,width*.35,axis);
                Point c=blade(x,z,shoulder,0,width*.35,axis),d=blade(x,z,shoulder,width*side,0,axis);
                out.add(new Quad(a,b,c,d));
                out.add(new Quad(d,c,new Point(x,bottom,z),new Point(x,bottom,z)));
            }
        }
        double guard=width*2.7,thickness=width*.22;
        out.add(new Quad(new Point(x-guard,top+thickness,z),new Point(x+guard,top+thickness,z),new Point(x+guard,top-thickness,z),new Point(x-guard,top-thickness,z)));
        out.add(new Quad(new Point(x-width*.3,top+width*1.8,z),new Point(x+width*.3,top+width*1.8,z),new Point(x+width*.3,top,z),new Point(x-width*.3,top,z)));
    }
    private static Point blade(double x,double z,double y,double side,double depth,int axis) {
        return axis==0?new Point(x+side,y,z+depth):new Point(x+depth,y,z+side);
    }
}
