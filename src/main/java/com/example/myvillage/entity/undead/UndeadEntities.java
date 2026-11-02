package com.example.myvillage.entity.undead;

import com.example.myvillage.entity.ModEntities;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.neoforged.neoforge.registries.DeferredHolder;

/** The catalogue owns registration IDs and dimensions; implementations are shared by both families. */
public final class UndeadEntities {
    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<UndeadEntity>>> TYPES = registerTypes();
    public static final DeferredHolder<EntityType<?>, EntityType<UndeadArrowEntity>> ARROW = ModEntities.ENTITY_TYPES.register(
            "undead_arrow", id -> EntityType.Builder.<UndeadArrowEntity>of(UndeadArrowEntity::new, MobCategory.MISC)
                    .sized(.5F, .5F).clientTrackingRange(8).updateInterval(1).noSave().build(id.toString()));
    private UndeadEntities() {}
    private static Map<String, DeferredHolder<EntityType<?>, EntityType<UndeadEntity>>> registerTypes() {
        Map<String, DeferredHolder<EntityType<?>, EntityType<UndeadEntity>>> result = new LinkedHashMap<>();
        for (var v : UndeadRules.get().variants()) {
            result.put(v.id(), ModEntities.ENTITY_TYPES.register(v.id(), id -> EntityType.Builder.<UndeadEntity>of(UndeadEntity::new, MobCategory.MONSTER)
                    .sized((float)v.physical("width"), (float)v.physical("height")).eyeHeight((float)v.physical("eye_height"))
                    .clientTrackingRange(10).updateInterval(1).build(id.toString())));
        }
        return Collections.unmodifiableMap(result);
    }
    public static void initialize() { /* trigger registration before DeferredRegister joins the bus */ }
}
