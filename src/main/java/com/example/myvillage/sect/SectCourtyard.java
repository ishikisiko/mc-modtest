package com.example.myvillage.sect;

import java.util.List;
import net.minecraft.core.BlockPos;

/** 命簿化身与经架只消费蓝图声明的位置。 */
public final class SectCourtyard {
    private SectCourtyard() { }
    public static List<String> variants() { return List.of(SectBlueprint.current().id.toString()); }
    public static BlockPos base(BlockPos anchor) { return SectBlueprint.current().base(anchor); }
    public static BlockPos stewardCell(BlockPos anchor) { return SectBlueprint.current().world(anchor,SectBlueprint.current().steward); }
    public static Footprint footprint(BlockPos anchor) {
        SectBlueprint d=SectBlueprint.current();BlockPos base=d.base(anchor);
        return new Footprint(base.getX(),base.getZ(),base.getX()+d.width-1,base.getZ()+d.depth-1);
    }
    public static List<BlockPos> cells(long seed,BlockPos anchor,String layout) {
        SectBlueprint d=SectBlueprint.current();d.requireLayout(layout);
        return d.courtyard.stream().map(p->d.world(anchor,p)).toList();
    }
    public static List<BlockPos> scriptureShelfSites(long seed,BlockPos anchor,String layout) {
        SectBlueprint d=SectBlueprint.current();d.requireLayout(layout);
        return d.shelves.stream().map(s->d.world(anchor,s.position())).toList();
    }
    public record Footprint(int minX,int minZ,int maxX,int maxZ) {
        public double distanceTo(double x,double z) {
            return Math.hypot(Math.max(0,Math.max(minX-x,x-(maxX+1))),Math.max(0,Math.max(minZ-z,z-(maxZ+1))));
        }
    }
}
