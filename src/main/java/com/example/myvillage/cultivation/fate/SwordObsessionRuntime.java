package com.example.myvillage.cultivation.fate;

import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.runtime.DerivedSwordWaves;
import com.example.myvillage.combat.session.CombatSession;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.LoggerFactory;
import java.util.*;

/** Transient, server-only streak; no save or client intent can supply stacks or proc counts. */
public final class SwordObsessionRuntime {
    private record Entry(ItemStack weapon, int slot, ResourceLocation dimension, FateDefinition definition,
                         SwordObsessionStacks stacks) { }
    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
    private SwordObsessionRuntime() { }

    private static FateDefinition definition(LivingEntity player) {
        if (!FateActors.valid(player) || !FateService.state(player).confirmed()
                || !CombatStyles.bundled().weapon(player.getMainHandItem())
                    .flatMap(w -> w.family()).filter("sword"::equals).isPresent()) return null;
        var definitions = FateService.active(player);
        return FateService.state(player).selected().stream().map(definitions::get).filter(Objects::nonNull)
                .filter(d -> d.effect("sword_stack_bonus") > 0).findFirst().orElse(null);
    }
    private static Entry current(LivingEntity player) {
        var definition = definition(player);
        var entry = ENTRIES.get(player.getUUID());
        if (entry != null && (entry.weapon() != player.getMainHandItem()
                || entry.slot() != FateActors.slot(player)
                || !entry.dimension().equals(player.level().dimension().location())
                || !entry.definition().equals(definition))) {
            ENTRIES.remove(player.getUUID());
            entry = null;
        }
        if (definition == null) return null;
        if (entry == null) {
            entry = new Entry(player.getMainHandItem(), FateActors.slot(player),
                    player.level().dimension().location(), definition, new SwordObsessionStacks());
            ENTRIES.put(player.getUUID(), entry);
        }
        return entry;
    }
    private static int timeout(Entry e) { return (int) e.definition().effect("sword_timeout_ticks"); }
    public static double multiplier(LivingEntity player, CombatSession session) {
        if (session.isExternal()) return 1;
        var entry = current(player);
        return entry == null ? 1 : 1 + entry.stacks().beforeMove(session.revision(),
                FateActors.level(player).getGameTime(), timeout(entry)) * entry.definition().effect("sword_stack_bonus");
    }
    public static void accepted(LivingEntity player, CombatSession session) {
        if (session.isExternal() || !session.hasActiveAction()) return;
        var entry = current(player);
        if (entry == null) return;
        long now = FateActors.level(player).getGameTime();
        boolean launch = entry.stacks().accepted(session.revision(), now, timeout(entry),
                (int) entry.definition().effect("sword_max_stacks"), session.isFinisher());
        LoggerFactory.getLogger(SwordObsessionRuntime.class).info(
                "SWORD_STREAK player={} t={} revision={} stacks={} finisher={} wave={}",
                player.getName().getString(), now, session.revision(),
                entry.stacks().stacks(now, timeout(entry)), session.isFinisher(), launch);
        if (launch) entry.definition().derivedSkill().flatMap(CombatStyles.bundled()::skill)
                .ifPresent(skill -> DerivedSwordWaves.launch(player, skill, session.facingYaw()));
    }
    public static int stacks(LivingEntity player) {
        var entry = current(player);
        return entry == null ? 0 : entry.stacks().stacks(FateActors.level(player).getGameTime(), timeout(entry));
    }
    public static void tick(MinecraftServer server) {
        ENTRIES.keySet().removeIf(id -> FateActors.find(server,id) == null);
        for (var id : List.copyOf(ENTRIES.keySet())) { var actor=FateActors.find(server,id); if(actor!=null)stacks(actor); }
    }
    public static void forget(LivingEntity player) { ENTRIES.remove(player.getUUID()); DerivedSwordWaves.forget(player); }
    public static void clear() { ENTRIES.clear(); DerivedSwordWaves.clear(); }
}
