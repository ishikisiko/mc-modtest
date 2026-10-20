package com.example.myvillage.forging;

import com.example.myvillage.MyVillageMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import java.util.*;

/** Resource-pack-replaceable aliases, emitted only on committed server operations. */
public final class WorkshopSounds {
    private static final DeferredRegister<SoundEvent> EVENTS=DeferredRegister.create(Registries.SOUND_EVENT,MyVillageMod.MOD_ID);
    private static final Map<String,DeferredHolder<SoundEvent,SoundEvent>> SOUNDS=new HashMap<>();
    static{for(String name:List.of("strike","heat","quench","temper","grind","assemble","infuse","repair"))
        SOUNDS.put(name,EVENTS.register("forging."+name,()->SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("myvillage","forging."+name))));}
    private WorkshopSounds(){}
    public static SoundEvent get(String name){return SOUNDS.get(name).get();}
    public static void register(IEventBus bus){EVENTS.register(bus);}
}
