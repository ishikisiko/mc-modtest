package com.example.myvillage.combat;

import com.example.myvillage.MyVillageMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sword-combat sound events. {@code sounds.json} currently aliases each event to
 * vanilla sounds (two or three pitch/volume variants each) so original audio can replace
 * them without code changes. {@link #IMPACT_HEAVY} is an extra low layer played with the
 * normal hit sound on heavy moves. Style files name sounds by id; {@link #resolve} turns an id
 * into the registered event.
 */
public final class CombatSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MyVillageMod.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_CUT = register("combat.sword.cut");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_THRUST = register("combat.sword.thrust");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_HIT = register("combat.sword.hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_HIT_HEAVY = register("combat.sword.hit_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT_HEAVY = register("combat.sword.impact_heavy");

    /** Random swing-pitch spread: each whoosh lands within ±6% of the move's pitch. */
    public static final float SWING_PITCH_JITTER = 0.06F;

    private CombatSounds() {
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }

    /**
     * The registered sound event for a style file's sound id. An id that is not registered still
     * plays through {@code sounds.json} as a variable-range event, like {@code /playsound}.
     */
    public static SoundEvent resolve(ResourceLocation soundId) {
        SoundEvent registered = BuiltInRegistries.SOUND_EVENT.get(soundId);
        return registered != null ? registered : SoundEvent.createVariableRangeEvent(soundId);
    }

    /** The move's swing pitch with a random ±6% spread, so repeated cuts never sound identical. */
    public static float jitteredSwingPitch(float basePitch, RandomSource random) {
        return jitteredSwingPitch(basePitch, random.nextFloat());
    }

    /** {@code unit} in [0, 1) maps linearly onto [-6%, +6%) around {@code basePitch}. */
    public static float jitteredSwingPitch(float basePitch, float unit) {
        return basePitch * (1.0F + SWING_PITCH_JITTER * (2.0F * unit - 1.0F));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {
        return SOUND_EVENTS.register(path, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, path)));
    }
}
