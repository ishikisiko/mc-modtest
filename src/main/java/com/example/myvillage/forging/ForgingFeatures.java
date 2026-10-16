package com.example.myvillage.forging;

import com.example.myvillage.MyVillageMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ForgingFeatures {
    private static final DeferredRegister<Feature<?>> FEATURES=DeferredRegister.create(Registries.FEATURE,MyVillageMod.MOD_ID);
    static {FEATURES.register("edge_tempering_sand",EdgeSandFeature::new);}
    public static void register(IEventBus bus){FEATURES.register(bus);}
    private ForgingFeatures(){}
}
