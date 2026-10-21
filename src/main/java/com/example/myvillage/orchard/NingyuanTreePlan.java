package com.example.myvillage.orchard;

import java.util.*;

/** Pure bounded voxel plan. Placement preflights every cell before modifying the world. */
public final class NingyuanTreePlan {
    public record Cell(int x,int y,int z) {
        Cell add(int dx,int dy,int dz) { return new Cell(x+dx,y+dy,z+dz); }
        public int distance(Cell c) { return Math.abs(x-c.x)+Math.abs(y-c.y)+Math.abs(z-c.z); }
    }
    public record Piece(String kind, String axis, int distance) { }
    private static final int[][] SIDES={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    public static Map<Cell,Piece> create(long seed, OrchardRules rules) {
        var rng=new Random(seed); var out=new LinkedHashMap<Cell,Piece>();
        int height=rules.trunkMin()+rng.nextInt(rules.trunkExtra()+1), turn=rng.nextInt(4);
        for(int y=0;y<height;y++) out.put(new Cell(0,y,0),new Piece("log","y",0));
        int[][] dirs={{1,0},{0,1},{-1,0},{0,-1}};
        var crowns=new ArrayList<Cell>(); crowns.add(new Cell(0,height,0));
        for(int branch=0;branch<3;branch++) {
            int[] d=dirs[(turn+branch)%4]; int y=height-3+(branch%2);
            for(int n=1;n<=rules.branchLength();n++) {
                out.put(new Cell(d[0]*n,y,d[1]*n),new Piece("log",d[0]==0?"z":"x",0));
                if(n==rules.branchLength()) out.put(new Cell(d[0]*n,y+1,d[1]*n),new Piece("log","y",0));
            }
            crowns.add(new Cell(d[0]*rules.branchLength(),y+2,d[1]*rules.branchLength()));
        }
        for(Cell c:crowns) for(int dy=-1;dy<=1;dy++) {
            int radius=dy==1?rules.crownRadius()-1:rules.crownRadius();
            for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
                if(Math.abs(dx)==radius && Math.abs(dz)==radius && (dy==1 || rng.nextBoolean())) continue;
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
        return Collections.unmodifiableMap(out);
    }
    private NingyuanTreePlan() { }
}
