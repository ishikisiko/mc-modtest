package com.example.myvillage.block;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.block.entity.ScriptureShelfBlockEntity;
import com.example.myvillage.block.entity.AlchemyFurnaceBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** MyVillage block entity types; registered right after {@link ModBlocks}. */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MyVillageMod.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<com.example.myvillage.ruins.BattleCacheBlockEntity>> BATTLE_CACHE =
            BLOCK_ENTITIES.register("battle_cache", () -> BlockEntityType.Builder.of(
                    com.example.myvillage.ruins.BattleCacheBlockEntity::new, ModBlocks.BATTLE_CACHE.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<com.example.myvillage.ruins.RuinCoreBlockEntity>> RUIN_CORE =
            BLOCK_ENTITIES.register("ruin_core", () -> BlockEntityType.Builder.of(
                    com.example.myvillage.ruins.RuinCoreBlockEntity::new, ModBlocks.RUIN_CORE.get()).build(null));

    @SuppressWarnings("DataFlowIssue") // a null data fixer type is the vanilla idiom for mod block entities
    public static final Supplier<BlockEntityType<ScriptureShelfBlockEntity>> SCRIPTURE_SHELF =
            BLOCK_ENTITIES.register("scripture_shelf",
                    () -> BlockEntityType.Builder.of(ScriptureShelfBlockEntity::new,
                            ModBlocks.SCRIPTURE_SHELF.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<AlchemyFurnaceBlockEntity>> ALCHEMY_FURNACE =
            BLOCK_ENTITIES.register("alchemy_furnace", () -> BlockEntityType.Builder.of(
                    AlchemyFurnaceBlockEntity::new, ModBlocks.ALCHEMY_FURNACE.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
