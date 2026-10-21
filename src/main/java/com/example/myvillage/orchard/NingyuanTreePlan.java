package com.example.myvillage.orchard;

import java.util.*;

/** Pure bounded voxel plan. Placement preflights every cell before modifying the world. */
public final class NingyuanTreePlan {
    public record Cell(int x,int y,int z) {
        Cell add(int dx,int dy,int dz) { return new Cell(x+dx,y+dy,z+dz); }
        public int distance(Cell c) { return Math.abs(x-c.x)+Math.abs(y-c.y)+Math.abs(z-c.z); }
    }
    public record Piece(String kind, String axis, int distance) { }
    private record Crown(Cell center,int radius) { }
    private static final int[][] SIDES={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    public static Map<Cell,Piece> create(long seed, OrchardRules rules) {
        var rng=new Random(seed); var out=new LinkedHashMap<Cell,Piece>();
        int height=rules.trunkMin()+rng.nextInt(rules.trunkExtra()+1), turn=rng.nextInt(4);
        // A one-block elbow and two unequal boughs keep three distinct leaf masses.
        // Rotate the complete plan afterwards, including log axes, instead of making a conical canopy.
        for(int y=0;y<height;y++) out.put(new Cell(y<3?0:1,y,0),new Piece("log","y",0));
        out.put(new Cell(1,2,0),new Piece("log","x",0));
        var crowns=new ArrayList<Crown>();
        crowns.add(new Crown(new Cell(1,height+1,0),rules.crownRadius()));
        for(int branch=0;branch<2;branch++) {
            int sign=branch==0?1:-1, y=height-3+branch, x=y<3?0:1;
            for(int n=1;n<=rules.branchLength();n++) {
                out.put(new Cell(x,y,sign*n),new Piece("log","z",0));
                if(n==rules.branchLength()) out.put(new Cell(x,y+1,sign*n),new Piece("log","y",0));
            }
            crowns.add(new Crown(new Cell(x,y+2,sign*rules.branchLength()),rules.crownRadius()));
        }
        for(Crown crown:crowns) for(int dy=-1;dy<=1;dy++) {
            Cell c=crown.center;int radius=crown.radius;
            for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
                double edge=(dx*dx+dz*dz)/Math.pow(radius+.25,2)+dy*dy*.5;
                if(edge>1 || (edge>.8 && rng.nextInt(5)==0)) continue;
                out.putIfAbsent(c.add(dx,dy,dz),new Piece("leaves","y",7));
            }
        }
        // Breadth-first leaf distance along occupied cells, matching vanilla decay semantics.
        var queue=new ArrayDeque<Cell>();
        out.forEach((c,p)-> { if(p.kind.equals("log")) queue.add(c); });
        while(!queue.isEmpty()) {
            Cell c=queue.remove(); int next=out.get(c).distance+1;
            if(next>=7) continue;
            for(int[] d:SIDES) {
                Cell q=c.add(d[0],d[1],d[2]); Piece p=out.get(q);
                if(p!=null && p.kind.equals("leaves") && p.distance>next) {
                    out.put(q,new Piece("leaves","y",next));queue.add(q);
                }
            }
        }
        out.entrySet().removeIf(e->e.getValue().kind.equals("leaves") && e.getValue().distance==7);
        var fruit=new ArrayList<Cell>();
        for(var e:out.entrySet()) if(e.getValue().kind.equals("leaves") && e.getKey().y>=3
                && !out.containsKey(e.getKey().add(0,-1,0)) && rng.nextDouble()<rules.fruitChance())
            fruit.add(e.getKey().add(0,-1,0));
        fruit.forEach(c->out.put(c,new Piece("fruit","y",0)));
        var rotated=new LinkedHashMap<Cell,Piece>();
        out.forEach((c,p)->{
            Cell q=switch(turn) {
                case 1 -> new Cell(-c.z,c.y,c.x);
                case 2 -> new Cell(-c.x,c.y,-c.z);
                case 3 -> new Cell(c.z,c.y,-c.x);
                default -> c;
            };
            String axis=turn%2==0?p.axis:switch(p.axis) {case "x"->"z";case "z"->"x";default->"y";};
            rotated.put(q,new Piece(p.kind,axis,p.distance));
        });
        return Collections.unmodifiableMap(rotated);
    }
    private NingyuanTreePlan() { }
}
