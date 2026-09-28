package com.example.myvillage.combat;

import com.example.myvillage.MyVillageMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sword-combat sound events. {@code sounds.json} currently aliases each event to
 * vanilla attack sounds so original audio can replace them without code changes.
 */
public final class CombatSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MyVillageMod.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_CUT = register("combat.sword.cut");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_THRUST = register("combat.sword.thrust");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_HIT = register("combat.sword.hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_HIT_HEAVY = register("combat.sword.hit_heavy");

    private CombatSounds() {
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {
        return SOUND_EVENTS.register(path, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, path)));
    }
}
