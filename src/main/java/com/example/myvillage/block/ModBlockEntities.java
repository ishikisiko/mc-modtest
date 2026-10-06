package com.example.myvillage.block;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.block.entity.ScriptureShelfBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** MyVillage block entity types; registered right after {@link ModBlocks}. */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MyVillageMod.MOD_ID);

    @SuppressWarnings("DataFlowIssue") // a null data fixer type is the vanilla idiom for mod block entities
    public static final Supplier<BlockEntityType<ScriptureShelfBlockEntity>> SCRIPTURE_SHELF =
            BLOCK_ENTITIES.register("scripture_shelf",
                    () -> BlockEntityType.Builder.of(ScriptureShelfBlockEntity::new,
                            ModBlocks.SCRIPTURE_SHELF.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
