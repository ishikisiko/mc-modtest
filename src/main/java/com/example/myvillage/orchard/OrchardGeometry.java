package com.example.myvillage.orchard;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

/** Selection bounds exported from the exact fruit models, never a second hand-authored mesh. */
final class OrchardGeometry {
    private static final VoxelShape[] FRUIT=load();
    private static final VoxelShape[] LANTERN=loadLantern();
    static VoxelShape fruit(int age) { return FRUIT[age]; }
    static VoxelShape lantern(boolean hanging) { return LANTERN[hanging?1:0]; }
    private static VoxelShape[] loadLantern() {
        try(var stream=OrchardGeometry.class.getResourceAsStream("/data/myvillage/orchard/geometry.json")) {
            if(stream==null) throw new IllegalStateException("Missing orchard geometry");
            var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("lantern_shapes");
            var shapes=new VoxelShape[2];
            for(int i=0;i<2;i++) {
                VoxelShape shape=Shapes.empty();
                for(var raw:root.getAsJsonArray(i==1?"true":"false")) {
                    var b=raw.getAsJsonArray();
                    shape=Shapes.or(shape,Block.box(b.get(0).getAsDouble(),b.get(1).getAsDouble(),b.get(2).getAsDouble(),b.get(3).getAsDouble(),b.get(4).getAsDouble(),b.get(5).getAsDouble()));
                }
                shapes[i]=shape.optimize();
            }
            return shapes;
        } catch(java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private static VoxelShape[] load() {
        try(var stream=OrchardGeometry.class.getResourceAsStream("/data/myvillage/orchard/geometry.json")) {
            if(stream==null) throw new IllegalStateException("Missing orchard geometry");
            var array=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("fruit_bounds");
            if(array.size()!=3) throw new IllegalStateException("Fruit geometry needs three stages");
            var shapes=new VoxelShape[3];
            for(int i=0;i<3;i++) {
                var b=array.get(i).getAsJsonArray();
                shapes[i]=Block.box(b.get(0).getAsDouble(),b.get(1).getAsDouble(),b.get(2).getAsDouble(),b.get(3).getAsDouble(),b.get(4).getAsDouble(),b.get(5).getAsDouble());
            }
            return shapes;
        } catch(java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private OrchardGeometry() { }
}
