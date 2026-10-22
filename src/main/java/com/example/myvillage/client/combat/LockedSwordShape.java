package com.example.myvillage.client.combat;

import com.example.myvillage.combat.art.LockedSwordDefinition;
import java.util.*;
import static com.example.myvillage.client.combat.ArtRibbonShape.*;

/** Small, deterministic sword silhouettes and ground seals; no bitmap assets or hit decisions. */
public final class LockedSwordShape {
    private LockedSwordShape() {}
    public static List<Quad> mesh(LockedSwordDefinition lock,boolean warning,int age) {
        List<Quad> out=new ArrayList<>();
        double radius=lock.mode().equals("array")?lock.radius():.85;
        int count=lock.mode().equals("array")?lock.strikes():1;
        if(warning) {
            for(int i=0;i<48;i++) {
                double a=2*Math.PI*i/48,b=2*Math.PI*(i+1)/48;
                out.add(new Quad(point(a,radius,.06),point(b,radius,.06),point(b,radius-.04,.06),point(a,radius-.04,.06)));
            }
            double progress=Math.clamp((double)age/lock.firstStrike(),0,1);
            for(int i=0;i<count;i++) {
                double a=2*Math.PI*i/count;
                double r=count==1?0:radius*.8;
                sword(out,Math.sin(a)*r,Math.cos(a)*r,2.1+progress*.7,.75+progress*.3,.11);
            }
        } else if(lock.mode().equals("pierce")) {
            // Thin forward lance, converging at the tracked target.
            out.add(new Quad(new Point(-.12,1,-4),new Point(.12,1,-4),new Point(0,1,1),new Point(0,1,1)));
            out.add(new Quad(new Point(0,.88,-4),new Point(0,1.12,-4),new Point(0,1,1),new Point(0,1,1)));
        } else if(lock.mode().equals("judgment")) {
            sword(out,0,0,3.7,3.4,.24);
            out.add(new Quad(new Point(-.035,.1,-3),new Point(.035,.1,-3),new Point(.035,.1,3),new Point(-.035,.1,3)));
        } else {
            int pulse=Math.clamp((age-lock.firstStrike())/lock.interval(),0,count-1);
            double a=2*Math.PI*pulse/count;
            sword(out,Math.sin(a)*radius*.65,Math.cos(a)*radius*.65,2.9,2.7,.14);
            // Every pulse's central sword identifies the actual area hit without filling the screen.
            sword(out,0,0,2.9,2.7,.12);
        }
        return List.copyOf(out);
    }
    private static Point point(double a,double r,double y) {return new Point(Math.sin(a)*r,y,Math.cos(a)*r);}
    private static void sword(List<Quad> out,double x,double z,double top,double length,double width) {
        double shoulder=top-length*.7,bottom=top-length;
        out.add(new Quad(new Point(x-width,top,z),new Point(x+width,top,z),new Point(x+width,shoulder,z),new Point(x-width,shoulder,z)));
        out.add(new Quad(new Point(x-width,shoulder,z),new Point(x+width,shoulder,z),new Point(x,bottom,z),new Point(x,bottom,z)));
        out.add(new Quad(new Point(x,top,z-width),new Point(x,top,z+width),new Point(x,bottom,z),new Point(x,bottom,z)));
        out.add(new Quad(new Point(x-width*2.7,top+.03,z),new Point(x+width*2.7,top+.03,z),new Point(x+width*2.7,top-.05,z),new Point(x-width*2.7,top-.05,z)));
        out.add(new Quad(new Point(x-width*.3,top+.35,z),new Point(x+width*.3,top+.35,z),new Point(x+width*.3,top,z),new Point(x-width*.3,top,z)));
    }
}
