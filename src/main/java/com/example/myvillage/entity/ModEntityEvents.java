package com.example.myvillage.entity;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.entity.beast.BeastDefinitions;
import com.example.myvillage.entity.beast.BeastEntity;
import com.example.myvillage.entity.beast.DemonWolfEntity;
import com.example.myvillage.entity.beast.ThunderTigerEntity;
import com.example.myvillage.entity.npc.CultivatorEntity;
import com.example.myvillage.wilderness.WildernessRules;
import com.example.myvillage.wilderness.runtime.WildernessRuntime;
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
        com.example.myvillage.wilderness.ExpeditionRules.get();
        event.put(ModEntities.RIDGE_REAVER.get(), com.example.myvillage.entity.npc.RidgeReaverEntity.createAttributes().build());
        event.put(ModEntities.ASH_TALISMANIST.get(), BeastEntity.createAttributes(BeastDefinitions.bundled().require(com.example.myvillage.entity.beast.AshTalismanistEntity.ID)).build());
        event.put(ModEntities.THORNBACK_BADGER.get(), BeastEntity.createAttributes(BeastDefinitions.bundled().require(com.example.myvillage.entity.beast.ThornbackBadgerEntity.ID)).build());
        event.put(ModEntities.REDPLATE_LIZARD.get(), BeastEntity.createAttributes(BeastDefinitions.bundled().require(com.example.myvillage.entity.beast.RedplateLizardEntity.ID)).build());
        event.put(ModEntities.FROSTPLUME_PHEASANT.get(), BeastEntity.createAttributes(BeastDefinitions.bundled().require(com.example.myvillage.entity.beast.FrostplumePheasantEntity.ID)).build());
        event.put(ModEntities.LANTERN_SPORELING.get(), BeastEntity.createAttributes(BeastDefinitions.bundled().require(com.example.myvillage.entity.beast.LanternSporelingEntity.ID)).build());
        WildernessRules.bundled(); // reject malformed encounter ranges at startup
        com.example.myvillage.entity.beast.ThunderTigerRules.bundled();
        event.put(ModEntities.SIMPLE_FOX.get(), Fox.createAttributes().build());
        // Beast data loads here on both sides; a broken file stops startup.
        event.put(ModEntities.DEMON_WOLF.get(),
                BeastEntity.createAttributes(BeastDefinitions.bundled().require(DemonWolfEntity.ID)).build());
        event.put(ModEntities.CULTIVATOR.get(), CultivatorEntity.createAttributes().build());
        event.put(ModEntities.THUNDER_TIGER.get(),
                BeastEntity.createAttributes(BeastDefinitions.bundled().require(ThunderTigerEntity.ID)).build());
    }

    @SubscribeEvent
    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(ModEntities.RIDGE_REAVER.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, com.example.myvillage.wilderness.WildernessRosterRuntime::checkSpawn,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.ASH_TALISMANIST.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, com.example.myvillage.wilderness.WildernessRosterRuntime::checkSpawn,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.THORNBACK_BADGER.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, com.example.myvillage.wilderness.WildernessRosterRuntime::checkSpawn,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.REDPLATE_LIZARD.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, com.example.myvillage.wilderness.WildernessRosterRuntime::checkSpawn,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.FROSTPLUME_PHEASANT.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, com.example.myvillage.wilderness.WildernessRosterRuntime::checkSpawn,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.LANTERN_SPORELING.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, com.example.myvillage.wilderness.WildernessRosterRuntime::checkSpawn,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.DEMON_WOLF.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, WildernessRuntime::checkWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(
                ModEntities.SIMPLE_FOX.get(),
                SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (entityType, level, spawnType, pos, random) ->
                        Fox.checkFoxSpawnRules(EntityType.FOX, level, spawnType, pos, random),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
