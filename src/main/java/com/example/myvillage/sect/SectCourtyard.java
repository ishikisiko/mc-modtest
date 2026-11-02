package com.example.myvillage.sect;

import java.util.List;
import net.minecraft.core.BlockPos;

/** 命簿化身与经架只消费蓝图声明的位置。 */
public final class SectCourtyard {
    private SectCourtyard() { }
    public static List<String> variants() { return SectBlueprint.all().stream().map(d->d.id.toString()).toList(); }
    public static BlockPos base(BlockPos anchor) { return SectBlueprint.current().base(anchor); }
    public static BlockPos stewardCell(BlockPos anchor) { return SectBlueprint.current().world(anchor,SectBlueprint.current().steward); }
    public static BlockPos stewardCell(BlockPos anchor,String layout) { var d=SectBlueprint.get(layout);return d.world(anchor,d.steward); }
    public static Footprint footprint(BlockPos anchor) {
        return footprint(anchor,null);
    }
    public static Footprint footprint(BlockPos anchor,String layout) {
        SectBlueprint d=SectBlueprint.get(layout);BlockPos base=d.base(anchor);
        return new Footprint(base.getX(),base.getZ(),base.getX()+d.width-1,base.getZ()+d.depth-1);
    }
    public static List<BlockPos> cells(long seed,BlockPos anchor,String layout) {
        SectBlueprint d=SectBlueprint.get(layout);
        return d.courtyard.stream().map(p->d.world(anchor,p)).toList();
    }
    public static List<BlockPos> scriptureShelfSites(long seed,BlockPos anchor,String layout) {
        SectBlueprint d=SectBlueprint.get(layout);
        return d.shelves.stream().map(s->d.world(anchor,s.position())).toList();
    }
    public record Footprint(int minX,int minZ,int maxX,int maxZ) {
        public double distanceTo(double x,double z) {
            return Math.hypot(Math.max(0,Math.max(minX-x,x-(maxX+1))),Math.max(0,Math.max(minZ-z,z-(maxZ+1))));
        }
    }
}
