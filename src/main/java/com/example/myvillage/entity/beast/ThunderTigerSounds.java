package com.example.myvillage.entity.beast;

import com.example.myvillage.MyVillageMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Named boss cues with their own subtitles; resource aliases reuse vanilla recordings. */
public final class ThunderTigerSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, MyVillageMod.MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT = cue("ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> HURT = cue("hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH = cue("death");
    public static final DeferredHolder<SoundEvent, SoundEvent> ATTACK = cue("attack");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGE = cue("charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> THUNDER = cue("thunder");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROAR = cue("roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> HORN_BREAK = cue("horn_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP = cue("step");

    private ThunderTigerSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> cue(String name) {
        String key = "thunder_tiger." + name;
        return SOUNDS.register(key, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, key)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
