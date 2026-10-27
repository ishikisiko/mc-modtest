package com.example.myvillage.forging;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One generated, bounded presentation contract; no costs or output are decided here. */
public record InfusionVisuals(int version,int sync_ticks,int segments,int streams,int rune_count,
        double rune_size,double ribbon_width,double core_width,double ring_width,
        double hearth_height,double hearth_radius,double ring_radius,double item_height,double lift,
        double rise_end,double converge_start,double spin_degrees,double helix_turns,
        List<Integer> stream_color,List<Integer> core_color,List<Integer> rune_color,
        double stream_alpha,double rune_alpha,double item_extent,double armor_extent,int view_distance,Map<String,Display> items) {
    public record Display(List<Double> center,double scale,double rotate_z){
        public Display{center=List.copyOf(center);if(center.size()!=3||center.stream().anyMatch(v->!Double.isFinite(v)))throw new IllegalArgumentException("Infusion item center");bound(scale,.01,4);bound(rotate_z,-180,180);}
    }
    public InfusionVisuals {
        if(version!=1||sync_ticks<1||sync_ticks>20||segments<8||segments>64||streams<1||streams>4||rune_count<1||rune_count>8||view_distance<8||view_distance>64)throw new IllegalArgumentException("Infusion render limits");
        bound(rune_size,.05,.3);bound(ribbon_width,.005,.08);bound(core_width,.001,.03);bound(ring_width,.001,.05);
        bound(hearth_height,.8,1);bound(hearth_radius,.1,.5);bound(ring_radius,.2,.8);bound(item_height,1.3,2);bound(lift,0,.5);
        bound(rise_end,.05,.4);bound(converge_start,.6,.95);bound(spin_degrees,0,360);bound(helix_turns,.5,2);
        bound(stream_alpha,0,1);bound(rune_alpha,0,1);bound(item_extent,.5,1.5);bound(armor_extent,.4,1);
        stream_color=color(stream_color);core_color=color(core_color);rune_color=color(rune_color);items=Map.copyOf(items);
        if(items.isEmpty())throw new IllegalArgumentException("Missing infusion item poses");
    }
    private static void bound(double v,double min,double max){if(!Double.isFinite(v)||v<min||v>max)throw new IllegalArgumentException("Infusion presentation outside bounds");}
    private static List<Integer> color(List<Integer> c){if(c==null||c.size()!=3||c.stream().anyMatch(v->v<0||v>255))throw new IllegalArgumentException("Infusion RGB");return List.copyOf(c);}
    public int syncTicks(){return sync_ticks;}
    public static InfusionVisuals parse(Reader reader){return new Gson().fromJson(reader,InfusionVisuals.class);}
    private static final class Holder{static final InfusionVisuals VALUE=load();}
    public static InfusionVisuals get(){return Holder.VALUE;}
    private static InfusionVisuals load(){
        try(var stream=InfusionVisuals.class.getResourceAsStream("/data/myvillage/forging/infusion_visuals.json")){
            if(stream==null)throw new IllegalStateException("Missing infusion visuals");return parse(new InputStreamReader(stream,StandardCharsets.UTF_8));
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    public static double ease(double p){p=Math.max(0,Math.min(1,p));return p*p*(3-2*p);}
    public double contraction(double p){return ease((p-converge_start)/(1-converge_start));}
    public double height(double p){return item_height+lift*ease(p/rise_end)*(1-contraction(p));}
    public double visibility(double p){return ease(p/rise_end)*(1-contraction(p));}
    public double[] stream(double progress,double fraction,int index){
        double angle=fraction*Math.PI*2*helix_turns+index*Math.PI*2/streams+Math.toRadians(spin_degrees)*progress*2;
        double radius=(hearth_radius+(ring_radius-hearth_radius)*Math.sin(fraction*Math.PI))*(1-contraction(progress));
        return new double[]{Math.cos(angle)*radius,hearth_height+fraction*(height(progress)-hearth_height),Math.sin(angle)*radius};
    }
}
