package com.example.myvillage.worldplan.roads;

import com.example.myvillage.MyVillageMod;
import java.util.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

/** 官道半砖复用已有青石纹理；注册、名称和模型参数来自同一正式配置。 */
public final class RoadContent {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(MyVillageMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(MyVillageMod.MOD_ID);
    public static final Map<String,DeferredBlock<SlabBlock>> SLABS=new LinkedHashMap<>();
    static {
        var rules=RoadRules.load();
        for(var spec:rules.style.slabs()) {
            var block=BLOCKS.register(spec.id(),()->new SlabBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
                .strength(rules.style.strength(),rules.style.resistance()).sound(SoundType.STONE)));
            SLABS.put(spec.id(),block);ITEMS.registerSimpleBlockItem(block);
        }
    }
    public static final DeferredBlock<RoadGuardrailBlock> GUARDRAIL=BLOCKS.register(RoadRules.load().guard.id,()->new RoadGuardrailBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(RoadRules.load().style.strength(),RoadRules.load().style.resistance()).sound(SoundType.STONE).noOcclusion()));
    static {ITEMS.registerSimpleBlockItem(GUARDRAIL);}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);}
    private RoadContent() {}
}
