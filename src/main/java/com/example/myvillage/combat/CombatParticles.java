package com.example.myvillage.combat;

import com.example.myvillage.MyVillageMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sword-combat particle types. The server spawns {@link #BLADE_CUT} with
 * {@code sendParticles(BLADE_CUT.get(), x, y, z, 0, rollRadians, heavy ? 1 : 0, 0, 1.0)}; with a
 * count of 0 the three offsets arrive on the client as the particle velocity, which the client
 * provider reads as the cut roll (x) and the heavy flag (y > 0.5).
 */
public final class CombatParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, MyVillageMod.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLADE_CUT =
            PARTICLE_TYPES.register("blade_cut", () -> new SimpleParticleType(true));

    private CombatParticles() {
    }

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}
