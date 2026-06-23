package com.example.myvillage.sect;

import com.example.myvillage.MyVillageMod;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public final class SectStructures {
    private static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, MyVillageMod.MOD_ID);

    private static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, MyVillageMod.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<SectStructure>> SECT =
            STRUCTURE_TYPES.register("sect", () -> () -> SectStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> SECT_PIECE =
            STRUCTURE_PIECE_TYPES.register("sect", () -> (StructurePieceType.ContextlessType) SectStructurePiece::new);

    private SectStructures() {
    }

    public static void register(IEventBus modEventBus) {
        STRUCTURE_TYPES.register(modEventBus);
        STRUCTURE_PIECE_TYPES.register(modEventBus);
    }
}
