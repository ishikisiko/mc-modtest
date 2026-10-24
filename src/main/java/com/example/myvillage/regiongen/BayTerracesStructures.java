package com.example.myvillage.regiongen;

import com.example.myvillage.MyVillageMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BayTerracesStructures {
    private static final DeferredRegister<StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, MyVillageMod.MOD_ID);
    private static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, MyVillageMod.MOD_ID);
    public static final DeferredHolder<StructureType<?>, StructureType<BayTerracesStructure>> TYPE =
            TYPES.register("bay_terraces", () -> () -> BayTerracesStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> PIECE =
            PIECES.register("bay_terraces", () -> (StructurePieceType.ContextlessType) BayTerracesPiece::new);

    private BayTerracesStructures() {}
    public static void register(IEventBus bus) { TYPES.register(bus); PIECES.register(bus); }
}
