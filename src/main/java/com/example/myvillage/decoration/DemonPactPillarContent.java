package com.example.myvillage.decoration;

import com.example.myvillage.MyVillageMod;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class DemonPactPillarContent {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(MyVillageMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(MyVillageMod.MOD_ID);
    public static final DeferredBlock<DemonPactPillarBlock> PILLAR=BLOCKS.register("demon_pact_pillar",()->new DemonPactPillarBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(DemonPactPillarShape.get().strength,DemonPactPillarShape.get().explosionResistance)
                    .sound(SoundType.DEEPSLATE).noOcclusion().pushReaction(PushReaction.BLOCK)));
    static { ITEMS.registerItem("demon_pact_pillar",props->new DemonPactPillarItem(PILLAR.get(),props)); }
    public static void register(IEventBus bus) { BLOCKS.register(bus);ITEMS.register(bus); }
    private DemonPactPillarContent() { }
}
