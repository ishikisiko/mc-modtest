package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.DodgeDirection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.UUID;

/**
 * Server-authoritative 身法 dodge. SCAFFOLD: the signatures are the contract between the network
 * layer, {@code CombatEvents} and the client; the bodies are filled in by the dodge work package.
 *
 * <p>Planned behaviour: a learned movement technique (highest grade wins) supplies dash distance,
 * invulnerable window and cooldown; the server checks mode, state and cooldown, interrupts a
 * running attack only in its recovery ({@code CombatStopReason.DODGED}), applies a collision-safe
 * impulse like {@link CombatStepService}, cancels incoming damage inside the window, raises the
 * technique's mastery by one and broadcasts {@code CombatDodgeStartPayload}.
 */
public final class CombatDodgeService {
    private CombatDodgeService() {
    }

    /** A dodge key press with the movement input it was pressed with. Returns whether it started. */
    public static boolean handleIntent(ServerPlayer player, DodgeDirection direction) {
        return false;
    }

    /** Server tick: ends windows, expires cooldowns, drops offline players. */
    public static void tick(MinecraftServer server) {
    }

    /** Cancels damage to a player inside its invulnerable window. */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
    }

    /** True while the player's dodge window (invulnerable ticks) is open. */
    public static boolean isDodging(ServerPlayer player) {
        return false;
    }

    /** Drops all dodge state of a player (logout, death, dimension change). */
    public static void clear(UUID playerId) {
    }

    public static void clearAll() {
    }
}
