package com.example.myvillage.sect;

import com.example.myvillage.MyVillageMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Registers the sect {@link StructureType} and its {@link StructurePieceType}.
 * Direct Registry.register() is used instead of DeferredRegister to guarantee
 * registration happens at mod-constructor time (registries are unfrozen then),
 * avoiding any RegisterEvent ordering issues.
 */
public final class SectStructures {
    public static final StructureType<SectStructure> SECT =
            Registry.register(
                    BuiltInRegistries.STRUCTURE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "sect"),
                    () -> SectStructure.CODEC);

    public static final StructurePieceType SECT_PIECE =
            Registry.register(
                    BuiltInRegistries.STRUCTURE_PIECE,
                    ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "sect"),
                    (StructurePieceType.ContextlessType) SectStructurePiece::new);

    private SectStructures() {
    }

    public static void register(IEventBus modEventBus) {
        // Static fields above are initialized here (class load triggers registration).
        // No event-bus subscription needed; direct registration is already done.
    }
}
