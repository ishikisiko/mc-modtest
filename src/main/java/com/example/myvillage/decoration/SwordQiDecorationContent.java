package com.example.myvillage.decoration;

import com.example.myvillage.MyVillageMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SwordQiDecorationContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MyVillageMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MyVillageMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MyVillageMod.MOD_ID);
    public static final DeferredBlock<SwordQiPedestalBlock> PEDESTAL = BLOCKS.register("sword_qi_pedestal",
            () -> new SwordQiPedestalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN)
                    .strength(2.5f, 6).sound(SoundType.STONE).noOcclusion().pushReaction(PushReaction.BLOCK)
                    .lightLevel(state -> state.getValue(SwordQiPedestalBlock.LIT) ? SwordQiAppearance.get().light_level : 0)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SwordQiPedestalEntity>> ENTITY =
            ENTITIES.register("sword_qi_pedestal", () -> BlockEntityType.Builder.of(SwordQiPedestalEntity::new, PEDESTAL.get()).build(null));
    static { ITEMS.registerSimpleBlockItem(PEDESTAL); }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); }
    private SwordQiDecorationContent() { }
}
