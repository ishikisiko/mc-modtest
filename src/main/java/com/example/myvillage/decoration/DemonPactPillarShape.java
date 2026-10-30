package com.example.myvillage.decoration;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Placed models and actual collision consume the same generated, bounded geometry. */
final class DemonPactPillarShape {
    record Part(BlockPos offset, VoxelShape[] shapes) {
        VoxelShape shape(Direction facing) { return shapes[turn(facing)]; }
    }
    private final List<Part> parts;
    final float strength;
    final float explosionResistance;
    private DemonPactPillarShape(List<Part> parts, float strength, float explosionResistance) { this.parts=List.copyOf(parts);this.strength=strength;this.explosionResistance=explosionResistance; }
    private static final class Holder { static final DemonPactPillarShape DATA=load(); }
    static DemonPactPillarShape get() { return Holder.DATA; }
    int count() { return parts.size(); }
    Part part(int index) { return parts.get(index); }
    int mirroredPart(int index) {
        var p=part(index).offset();var mirrored=new BlockPos(3-p.getX(),p.getY(),p.getZ());
        for(int i=0;i<parts.size();i++)if(parts.get(i).offset().equals(mirrored))return i;
        throw new IllegalStateException("魔契柱不是完整对称石身");
    }
    static int turn(Direction facing) { return switch(facing) { case NORTH->0;case EAST->1;case SOUTH->2;case WEST->3;default->throw new IllegalArgumentException("需要水平朝向"); }; }
    BlockPos offset(int index, Direction facing) {
        var p=part(index).offset();int x=p.getX(),z=p.getZ();
        for(int i=0;i<turn(facing);i++) { int next=-z;z=x;x=next; }
        return new BlockPos(x,p.getY(),z);
    }
    private static DemonPactPillarShape load() {
        String resource="/data/myvillage/decoration/demon_pact_pillar.json";
        try(var stream=DemonPactPillarShape.class.getResourceAsStream(resource)) {
            if(stream==null)throw new IllegalStateException("Missing "+resource);
            var data=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            List<Part> result=new ArrayList<>();
            for(var entry:data.getAsJsonArray("parts")) {
                var p=entry.getAsJsonObject();var o=p.getAsJsonArray("offset");
                BlockPos offset=new BlockPos(o.get(0).getAsInt(),o.get(1).getAsInt(),o.get(2).getAsInt());
                if(offset.getX()<0||offset.getX()>=4||offset.getY()<0||offset.getY()>=10||offset.getZ()<0||offset.getZ()>=4)throw new IllegalArgumentException("魔契柱偏移越界");
                List<double[]> boxes=new ArrayList<>();
                for(var element:p.getAsJsonArray("boxes")) {
                    var b=element.getAsJsonObject();var lo=b.getAsJsonArray("from");var hi=b.getAsJsonArray("to");double[] box=new double[6];
                    for(int i=0;i<3;i++) { box[i]=lo.get(i).getAsDouble();box[i+3]=hi.get(i).getAsDouble();
                        if(!Double.isFinite(box[i]+box[i+3])||box[i]<0||box[i]>=box[i+3]||box[i+3]>16)throw new IllegalArgumentException("魔契柱碰撞越界"); }
                    boxes.add(box);
                }
                if(boxes.isEmpty()||boxes.size()>8)throw new IllegalArgumentException("魔契柱碰撞数量越界");
                VoxelShape[] rotations=new VoxelShape[4];
                for(int turn=0;turn<4;turn++) {
                    VoxelShape shape=Shapes.empty();List<double[]> next=new ArrayList<>();
                    for(double[] b:boxes) { shape=Shapes.or(shape,Block.box(b[0],b[1],b[2],b[3],b[4],b[5]));next.add(new double[]{16-b[5],b[1],b[0],16-b[2],b[4],b[3]}); }
                    rotations[turn]=shape.optimize();boxes=next;
                }
                result.add(new Part(offset,rotations));
            }
            float strength=data.get("strength").getAsFloat();
            if(result.size()<2||result.size()>128||!result.getFirst().offset().equals(BlockPos.ZERO)||result.stream().map(Part::offset).distinct().count()!=result.size()
                    ||!Float.isFinite(strength)||strength<=0||strength>20)throw new IllegalArgumentException("魔契柱目录不合法");
            float resistance=data.get("explosion_resistance").getAsFloat();
            if(!Float.isFinite(resistance)||resistance<0||resistance>1200)throw new IllegalArgumentException("魔契柱抗爆参数越界");
            return new DemonPactPillarShape(result,strength,resistance);
        } catch(java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
