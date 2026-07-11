package com.example.myvillage.entity;

import com.example.myvillage.MyVillageMod;
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

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
