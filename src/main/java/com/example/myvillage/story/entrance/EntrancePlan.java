package com.example.myvillage.story.entrance;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure bounded spatial plan, before any chunk queries or block writes. */
public record EntrancePlan(int originX, int groundY, int originZ, Rect scene, List<Cell> route, Cell notice) {
    public record Cell(int x,int z) {}
    public record Rect(int minX,int minZ,int maxX,int maxZ) {
        public Rect { if(minX>maxX||minZ>maxZ)throw new IllegalArgumentException("inverted entrance rectangle"); }
        public boolean contains(int x,int z) {return x>=minX&&x<=maxX&&z>=minZ&&z<=maxZ;}
        public boolean intersects(int x,int z,int radius) {return x>=minX-radius&&x<=maxX+radius&&z>=minZ-radius&&z<=maxZ+radius;}
    }
    public EntrancePlan { route=List.copyOf(route); }
    public static EntrancePlan create(EntranceRules r,int spawnX,int groundY,int spawnZ,EntranceRules.Point offset,
                                      int minX,int minZ,int maxX,int maxZ) {
        int x=Math.addExact(spawnX,offset.x()),z=Math.addExact(spawnZ,offset.z());
        Rect scene=new Rect(x+minX-r.sceneMargin(),z+minZ-r.sceneMargin(),x+maxX+r.sceneMargin(),z+maxZ+r.sceneMargin());
        int approachX=scene.minX-r.roadHalfWidth()-1;
        int anchorX=x+r.roadAnchor().x(),anchorZ=z+r.roadAnchor().z();
        if(approachX<spawnX+r.spawnBuffer()+r.roadHalfWidth()+2||anchorX<scene.minX||anchorX>scene.maxX||anchorZ<scene.minZ||anchorZ>scene.maxZ)
            throw new IllegalArgumentException("entrance route crosses spawn or misses scene");
        var route=new ArrayList<Cell>();
        line(route,spawnX+r.spawnBuffer()+r.roadHalfWidth()+2,spawnZ,approachX,spawnZ,r.maxRouteLength());
        line(route,approachX,spawnZ,approachX,anchorZ,r.maxRouteLength());
        line(route,approachX,anchorZ,anchorX,anchorZ,r.maxRouteLength());
        var p=new EntrancePlan(x,groundY,z,scene,route,new Cell(spawnX+r.spawnBuffer()+r.roadHalfWidth()+2,spawnZ+r.roadHalfWidth()));
        if(p.chunks(r).size()>r.maxChunks())throw new IllegalArgumentException("entrance chunk budget");
        return p;
    }
    private static void line(List<Cell> cells,int x,int z,int endX,int endZ,int budget) {
        if(x!=endX&&z!=endZ)throw new IllegalArgumentException("diagonal entrance path");
        while(true) {
            Cell c=new Cell(x,z);if(cells.isEmpty()||!cells.getLast().equals(c))cells.add(c);
            if(cells.size()>budget)throw new IllegalArgumentException("entrance route budget");
            if(x==endX&&z==endZ)break;x+=Integer.compare(endX,x);z+=Integer.compare(endZ,z);
        }
    }
    public Set<Cell> roadCells(int radius) {
        var cells=new LinkedHashSet<Cell>();
        for(Cell c:route)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)cells.add(new Cell(c.x+dx,c.z+dz));
        return cells;
    }
    public Set<Cell> chunks(EntranceRules r) {
        var cells=new LinkedHashSet<Cell>();
        for(int x=Math.floorDiv(scene.minX,16);x<=Math.floorDiv(scene.maxX,16);x++)
            for(int z=Math.floorDiv(scene.minZ,16);z<=Math.floorDiv(scene.maxZ,16);z++)cells.add(new Cell(x,z));
        for(Cell c:roadCells(r.roadHalfWidth()+1))cells.add(new Cell(Math.floorDiv(c.x,16),Math.floorDiv(c.z,16)));
        return java.util.Collections.unmodifiableSet(cells);
    }
    public boolean onRoad(int x,int y,int z,EntranceRules r) {
        if(y<groundY+1||y>groundY+r.clearance())return false;
        for(Cell c:route)if(Math.abs((long)c.x-x)<r.roadHalfWidth()&&Math.abs((long)c.z-z)<r.roadHalfWidth())return true;
        return false;
    }
}
