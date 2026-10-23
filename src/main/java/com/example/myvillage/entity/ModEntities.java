package com.example.myvillage.entity;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.entity.npc.RidgeReaverEntity;
import com.example.myvillage.entity.beast.AshTalismanistEntity;
import com.example.myvillage.entity.beast.ThornbackBadgerEntity;
import com.example.myvillage.entity.beast.RedplateLizardEntity;
import com.example.myvillage.entity.beast.FrostplumePheasantEntity;
import com.example.myvillage.entity.beast.LanternSporelingEntity;
import com.example.myvillage.entity.beast.DemonWolfEntity;
import com.example.myvillage.entity.beast.ThunderTigerEntity;
import com.example.myvillage.entity.npc.CultivatorEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MyVillageMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<SimpleFoxEntity>> SIMPLE_FOX =
            ENTITY_TYPES.register("simple_fox", id -> EntityType.Builder
                    .of(SimpleFoxEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 0.7F)
                    .eyeHeight(0.4F)
                    .passengerAttachments(new Vec3(0.0, 0.6375, -0.25))
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .immuneTo(Blocks.SWEET_BERRY_BUSH)
                    .build(id.toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<RideableFlyingSwordEntity>> RIDEABLE_FLYING_SWORD =
            ENTITY_TYPES.register("rideable_flying_sword", id -> EntityType.Builder
                    .of(RideableFlyingSwordEntity::new, MobCategory.MISC)
                    .noSave()
                    .noSummon()
                    .sized(1.4F, 0.25F)
                    .passengerAttachments(new Vec3(0.0, 0.25, 0.0))
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(id.toString()));

    /** 妖狼: shoulder about 1.3 blocks, nose to rump about 2.2; no natural spawning. */
    public static final DeferredHolder<EntityType<?>, EntityType<DemonWolfEntity>> DEMON_WOLF =
            ENTITY_TYPES.register("demon_wolf", id -> EntityType.Builder
                    .of(DemonWolfEntity::new, MobCategory.MONSTER)
                    .sized(1.3F, 1.45F)
                    .eyeHeight(1.2F)
                    .clientTrackingRange(10)
                    .build(id.toString()));

    /** Lair boss; head and body combat volumes are provided by its multipart entity. */
    public static final DeferredHolder<EntityType<?>, EntityType<ThunderTigerEntity>> THUNDER_TIGER =
            ENTITY_TYPES.register("thunder_tiger", id -> EntityType.Builder
                    .of(ThunderTigerEntity::new, MobCategory.MONSTER)
                    .sized(2.8F, 3.0F)
                    .eyeHeight(2.6F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build(id.toString()));

    /** 修仙者: about 1.9 blocks to the top of the head, the hair bun a little above the box; no natural spawning. */
    public static final DeferredHolder<EntityType<?>, EntityType<CultivatorEntity>> CULTIVATOR =
            ENTITY_TYPES.register("cultivator", id -> EntityType.Builder
                    .of(CultivatorEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.9F)
                    .eyeHeight(1.67F)
                    .clientTrackingRange(10)
                    .build(id.toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<RidgeReaverEntity>> RIDGE_REAVER =
            ENTITY_TYPES.register("ridge_reaver", id -> EntityType.Builder.of(RidgeReaverEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.9F).eyeHeight(1.67F)
                    .clientTrackingRange(10).updateInterval(1).build(id.toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<AshTalismanistEntity>> ASH_TALISMANIST =
            ENTITY_TYPES.register("ash_talismanist", id -> EntityType.Builder.of(AshTalismanistEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.9F).eyeHeight(1.67F)
                    .clientTrackingRange(10).updateInterval(1).build(id.toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<ThornbackBadgerEntity>> THORNBACK_BADGER =
            ENTITY_TYPES.register("thornback_badger", id -> EntityType.Builder.of(ThornbackBadgerEntity::new, MobCategory.MONSTER)
                    .sized(1.05F, 0.95F).eyeHeight(0.64F)
                    .clientTrackingRange(10).updateInterval(1).build(id.toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<RedplateLizardEntity>> REDPLATE_LIZARD =
            ENTITY_TYPES.register("redplate_lizard", id -> EntityType.Builder.of(RedplateLizardEntity::new, MobCategory.MONSTER)
                    .sized(1.15F, 0.85F).eyeHeight(0.52F)
                    .clientTrackingRange(10).updateInterval(1).build(id.toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<FrostplumePheasantEntity>> FROSTPLUME_PHEASANT =
            ENTITY_TYPES.register("frostplume_pheasant", id -> EntityType.Builder.of(FrostplumePheasantEntity::new, MobCategory.MONSTER)
                    .sized(0.65F, 1.25F).eyeHeight(0.95F)
                    .clientTrackingRange(10).updateInterval(1).build(id.toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<LanternSporelingEntity>> LANTERN_SPORELING =
            ENTITY_TYPES.register("lantern_sporeling", id -> EntityType.Builder.of(LanternSporelingEntity::new, MobCategory.MONSTER)
                    .sized(0.95F, 1.5F).eyeHeight(1.08F)
                    .clientTrackingRange(10).updateInterval(1).build(id.toString()));

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
