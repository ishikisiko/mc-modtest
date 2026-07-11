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

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
