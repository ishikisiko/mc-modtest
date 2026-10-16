package com.example.myvillage.block;

import com.example.myvillage.forging.ForgingDingVisuals;
import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForgingDingTest {
    private static ForgingTableBlock block;
    @BeforeAll @SuppressWarnings("deprecation") static void bootstrap() {
        if (LoadingModList.get()==null) LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        var registry=(MappedRegistry<Block>)BuiltInRegistries.BLOCK;
        synchronized(registry) {
            registry.unfreeze();
            try { block=Registry.register(registry,ResourceLocation.parse("myvillage_test:forging_ding"),new ForgingTableBlock(BlockBehaviour.Properties.of())); }
            finally { registry.freeze(); }
        }
    }
    @Test void propertylessLegacyStateGetsUnlitNorthDefaults() {
        var old=new CompoundTag();old.putString("Name","myvillage_test:forging_ding");
        var restored=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),old);
        assertSame(block,restored.getBlock());
        assertEquals(Direction.NORTH,restored.getValue(ForgingTableBlock.FACING));
        assertFalse(restored.getValue(ForgingTableBlock.LIT));
        assertEquals(8,block.getStateDefinition().getPossibleStates().size());
        var rotated=restored.setValue(ForgingTableBlock.LIT,true).rotate(Rotation.CLOCKWISE_90);
        assertEquals(Direction.EAST,rotated.getValue(ForgingTableBlock.FACING));
        assertTrue(rotated.getValue(ForgingTableBlock.LIT));
    }
    @Test void collisionRotatesWithThreeFootLayoutAndPreservesEarHeight() {
        var v=ForgingDingVisuals.get();
        double x=3.5/16,y=.5/16,z=3.5/16;
        for(var d:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) {
            final double px=x,pz=z;
            assertTrue(v.shape(d).toAabbs().stream().anyMatch(b->b.contains(px,y,pz)),d.toString());
            assertTrue(v.shape(d).max(Direction.Axis.Y)>1); // the upright ears rise above one block
            assertFalse(v.shape(d).toAabbs().stream().anyMatch(b->b.contains(.5,y,.5))); // no invisible cube between feet
            double next=1-z;z=x;x=next;
        }
        for(var b:v.boxes()) {
            var rotated=b.clockwise().clockwise().clockwise().clockwise();
            assertArrayEquals(new double[]{b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1()},
                    new double[]{rotated.x0(),rotated.y0(),rotated.z0(),rotated.x1(),rotated.y1(),rotated.z1()},1e-12);
        }
        assertThrows(IllegalArgumentException.class,()->v.shape(Direction.UP));
    }
    @Test void presentationRejectsUnboundedDurationsLightAndCorruptGeometry() throws Exception {
        String text;
        try(var s=ForgingDingVisuals.class.getResourceAsStream(ForgingDingVisuals.RESOURCE)){text=new String(s.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        for(var entry:Map.of("duration_ticks",0,"light_level",16,"spark_count",33).entrySet()) {
            var o=JsonParser.parseString(text).getAsJsonObject();o.getAsJsonObject("feedback").addProperty(entry.getKey(),entry.getValue());
            assertThrows(IllegalArgumentException.class,()->ForgingDingVisuals.parse(new StringReader(o.toString())));
        }
        var o=JsonParser.parseString(text).getAsJsonObject();o.getAsJsonArray("collision").get(0).getAsJsonArray().set(3,new JsonPrimitive(17));
        assertThrows(IllegalArgumentException.class,()->ForgingDingVisuals.parse(new StringReader(o.toString())));
        assertThrows(IllegalArgumentException.class,()->new ForgingDingVisuals.Box(1,0,1,0,1,2));
    }
}
