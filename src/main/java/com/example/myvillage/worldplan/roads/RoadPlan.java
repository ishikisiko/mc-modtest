package com.example.myvillage.worldplan.roads;

import java.util.*;

/** Feet heights use half blocks, allowing continuous slab ramps without mandatory jumping. */
public record RoadPlan(String from, String to, String worldHash, String rulesHash, int width,
                       Map<String,Integer> gateWidths,List<WorldRoadPlanner.Box> obstacles,List<RoadAStar.Point> centerline, List<Cell> cells) {
    public record Cell(int x, int z, int feet2, boolean existing, int band, int rail) {
        public Cell(int x,int z,int feet2,boolean existing,int band){this(x,z,feet2,existing,band,-1);}
        public Cell(int x,int z,int feet2,boolean existing){this(x,z,feet2,existing,0);}
        public Cell {if(band<0||band>3||existing&&band!=0||rail< -1||rail>255||rail>=0&&(existing||band!=2||((rail>>>4)&~rail)!=0))throw new IllegalArgumentException("驿道路带不合法");}
        public long key(){return key(x,z);}
        public static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
        public long chunk(){return (Math.floorDiv(x,16)&0xffffffffL)|((long)Math.floorDiv(z,16)<<32);}
        public int floorY(){return Math.floorDiv(feet2-1,2);}
    }
    public RoadPlan {
        gateWidths=Map.copyOf(gateWidths);obstacles=List.copyOf(obstacles);centerline=List.copyOf(centerline);cells=List.copyOf(cells);
        if(cells.isEmpty()||centerline.size()<2)throw new IllegalArgumentException("空驿道");
    }
    public Map<Long,List<Cell>> chunks() {
        return chunks(cells);
    }
    public static Map<Long,List<Cell>> chunks(List<Cell> cells) {
        Map<Long,List<Cell>> result=new LinkedHashMap<>();
        for(var cell:cells)result.computeIfAbsent(cell.chunk(),k->new ArrayList<>()).add(cell);
        return result;
    }
}
