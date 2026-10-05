package com.example.myvillage.entity;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.entity.beast.BeastDefinitions;
import com.example.myvillage.entity.beast.BeastEntity;
import com.example.myvillage.entity.beast.DemonWolfEntity;
import com.example.myvillage.entity.npc.CultivatorEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

@EventBusSubscriber(modid = MyVillageMod.MOD_ID)
public final class ModEntityEvents {
    private ModEntityEvents() {
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SIMPLE_FOX.get(), Fox.createAttributes().build());
        // Beast data loads here on both sides; a broken file stops startup.
        event.put(ModEntities.DEMON_WOLF.get(),
                BeastEntity.createAttributes(BeastDefinitions.bundled().require(DemonWolfEntity.ID)).build());
        event.put(ModEntities.CULTIVATOR.get(), CultivatorEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
                ModEntities.SIMPLE_FOX.get(),
                SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (entityType, level, spawnType, pos, random) ->
                        Fox.checkFoxSpawnRules(EntityType.FOX, level, spawnType, pos, random),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
