package com.example.myvillage.sect;

import com.example.myvillage.MyVillageMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the sect {@link StructureType} and its {@link StructurePieceType}.
 * The structure/structure_set/placement and the high-relief biome tag live in
 * {@code data/myvillage/worldgen/} + {@code data/myvillage/tags/worldgen/biome/};
 * registering the type here is what makes {@code myvillage:sect} a real worldgen
 * structure (and so locatable via {@code /locate structure myvillage:sect}).
 */
public final class SectStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, MyVillageMod.MOD_ID);
    public static final DeferredRegister<StructurePieceType> PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, MyVillageMod.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<SectStructure>> SECT =
            STRUCTURE_TYPES.register("sect", () -> () -> SectStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> SECT_PIECE =
            PIECE_TYPES.register("sect", () -> (StructurePieceType.ContextlessType) SectStructurePiece::new);

    private SectStructures() {
    }

    public static void register(IEventBus modEventBus) {
        STRUCTURE_TYPES.register(modEventBus);
        PIECE_TYPES.register(modEventBus);
    }
}
