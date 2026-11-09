package com.example.myvillage.cultivation.fate;

import com.example.myvillage.combat.foundation.ControlService;
import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.combat.runtime.CombatDamageService;
import com.example.myvillage.combat.runtime.SwordArmorBreak;
import com.example.myvillage.cultivation.fate.ability.FateAbilityCooldowns;
import com.example.myvillage.stats.DamageType;
import com.example.myvillage.stats.runtime.StatsRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** 亲和命中特性只读取服务器确认的直接主伤害；不创建额外伤害。 */
public final class ElementAffinityRuntime {
    private static final ResourceLocation WATER_SLOW = ResourceLocation.parse("myvillage:affinity/water_slow");
    private ElementAffinityRuntime() { }
    public static void register() { NeoForge.EVENT_BUS.addListener(ElementAffinityRuntime::damaged); }

    public static ResourceLocation waterCooldownKey(java.util.UUID target) {
        return ResourceLocation.fromNamespaceAndPath("myvillage", "affinity/water/" + target);
    }

    private static void damaged(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity(); var source = event.getSource();
        if (target.level().isClientSide() || event.getNewDamage() <= 0 || !target.isAlive() || target.isRemoved()
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || CombatDamageService.isDerivedHit(target)
                || !(source.getEntity() instanceof LivingEntity player) || !FateActors.valid(player)
                || player.level() != target.level() || player == target
                || !CombatActors.mayHarm(player,target) || player.isAlliedTo(target) || target.isAlliedTo(player)) return;
        boolean direct = source.getDirectEntity() == player || source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() == player;
        if (!direct) return;
        if (target instanceof ServerPlayer other && (other.isCreative() || other.isSpectator())) return;
        var type = StatsRuntime.mappedType(source).orElse(DamageType.PHYSICAL);
        if (type == DamageType.METAL) {
            double fraction = FateService.effect(player,"metal_armor_break_fraction");
            if (fraction > 0) SwordArmorBreak.apply(target,fraction,(int)FateService.effect(player,"metal_armor_break_ticks"));
        } else if (type == DamageType.WATER) {
            double fraction = FateService.effect(player,"water_slow_fraction");
            if (fraction <= 0) return;
            long now = player.getServer().overworld().getGameTime();
            var cooldowns = FateAbilityCooldowns.get(player.getServer().overworld());
            var key = waterCooldownKey(FateActors.identity(target));
            if (cooldowns.remaining(FateActors.identity(player),key,now) > 0) return;
            if (ControlService.slow(player,target,WATER_SLOW,(int)FateService.effect(player,"water_slow_ticks"),fraction))
                cooldowns.start(FateActors.identity(player),key,now,(int)FateService.effect(player,"water_slow_cooldown"));
        }
    }
}
