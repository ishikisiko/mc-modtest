package com.example.myvillage.cultivation.fate;

import com.example.myvillage.cultivation.CultivationService;
import java.math.BigDecimal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-owned pool, deliberately separate from all absorption hearts and mob effects. */
public final class PillAegisRuntime {
    private static final String MESSAGE = "fate.myvillage.pill_aegis.";
    private PillAegisRuntime() { }

    /** Register immediately AFTER StatsRuntime, whose LOWEST pre hook applies typed resistance. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PillAegisRuntime::damage);
        NeoForge.EVENT_BUS.addListener(PillAegisRuntime::tick);
    }

    /** Called only after normal successful item use has applied its medicine and consumed its stack. */
    public static void afterSuccessfulConsumption(ServerPlayer player, String medicine) {
        if (!"yangyuan".equals(medicine) || !player.isAlive() || player.isSpectator()) return;
        FateState current = FateService.state(player);
        long now = player.getServer().overworld().getGameTime();
        PillAegisState next = PillAegisEffects.afterConsumption(current.pillAegis(), now, medicine, true,
                player.getMaxHealth(), FateService.effect(player, "pill_shield_fraction"),
                (int) FateService.effect(player, "pill_shield_ticks"),
                (int) FateService.effect(player, "pill_shield_cooldown"));
        if (next.equals(current.pillAegis())) return;
        CultivationService.replaceFates(player, current.withPillAegis(next), false);
        if (next.shield() > 0 && next.cooldownReadyAt() > current.pillAegis().cooldownReadyAt())
            player.displayClientMessage(Component.translatable(MESSAGE + "granted", number(next.shield()),
                    number((next.shieldExpiresAt() - now) / 20.0)), true);
    }

    public static void clearOnDeath(ServerPlayer player) {
        FateState current = FateService.state(player);
        PillAegisState next = current.pillAegis().clearShield();
        if (!next.equals(current.pillAegis()))
            CultivationService.replaceFates(player, current.withPillAegis(next), false);
    }

    private static void damage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) return;
        FateState current = FateService.state(player);
        if (current.pillAegis().shield() == 0) return;
        // Explicit void/admin exclusions also cover a datapack removing the ordinary bypass tag.
        boolean bypass = event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)
                || event.getSource().is(DamageTypes.GENERIC_KILL);
        PillAegisState available = FateService.effect(player, "pill_shield_fraction") > 0
                ? current.pillAegis() : current.pillAegis().clearShield();
        var result = PillAegisEffects.absorb(available, player.getServer().overworld().getGameTime(),
                event.getNewDamage(), bypass);
        if (!result.next().equals(current.pillAegis()))
            CultivationService.replaceFates(player, current.withPillAegis(result.next()), false);
        if (result.blocked() > 0) {
            event.setNewDamage(result.damage());
            TemperedBonesRuntime.shieldAbsorbed(event.getContainer(), result.blocked());
            player.displayClientMessage(Component.translatable(MESSAGE + "blocked", number(result.blocked()),
                    number(result.next().shield())), true);
        }
        // Vanilla now consumes only its own remaining absorption hearts against the residual hit.
    }

    private static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        FateState current = FateService.state(player);
        if (current.pillAegis().shield() == 0) return;
        PillAegisState next = player.isAlive() && FateService.effect(player, "pill_shield_fraction") > 0
                ? current.pillAegis().expire(player.getServer().overworld().getGameTime())
                : current.pillAegis().clearShield();
        if (next.equals(current.pillAegis())) return;
        CultivationService.replaceFates(player, current.withPillAegis(next), false);
        if (player.isAlive()) player.displayClientMessage(Component.translatable(MESSAGE + "expired"), true);
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
