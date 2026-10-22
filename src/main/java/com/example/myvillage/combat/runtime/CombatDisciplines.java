package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.combat.CombatService;
import com.example.myvillage.combat.definition.*;
import com.example.myvillage.combat.session.CombatSession;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.LoggerFactory;
import java.util.*;

/** Online transient state, keyed to the exact equipped stack. No new save or C2S authority. */
public final class CombatDisciplines {
    private record Entry(ItemStack held, int slot, ResourceLocation dimension, String family,
                         DisciplinePool pool) {}
    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
    private CombatDisciplines() {}
    private static Entry current(ServerPlayer player) {
        String family = CombatStyles.bundled().weapon(player.getMainHandItem())
                .flatMap(WeaponDefinition::family).orElse("");
        boolean allowed = player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && CombatService.getMode(player) == CombatMode.CULTIVATION
                && Set.of("sword","spear","fist").contains(family);
        Entry e = ENTRIES.get(player.getUUID());
        if (e != null && (!allowed || e.held != player.getMainHandItem()
                || e.slot != player.getInventory().selected || !e.family.equals(family)
                || !e.dimension.equals(player.level().dimension().location()))) {
            ENTRIES.remove(player.getUUID()); e = null;
        }
        if (!allowed) return null;
        if (e == null) {
            e = new Entry(player.getMainHandItem(), player.getInventory().selected,
                    player.level().dimension().location(), family, new DisciplinePool());
            ENTRIES.put(player.getUUID(), e);
        }
        return e;
    }
    private static long now(ServerPlayer player) { return player.serverLevel().getGameTime(); }
    public static void meleeAccepted(ServerPlayer player, CombatSession session, double actualDamage) {
        if (actualDamage <= 0 || session.isExternal() || !session.hasActiveAction()) return;
        Entry e = current(player);
        if (e != null && e.family.equals("sword")) gain(player,e,session.revision(),DisciplineRules.get().sword());
    }
    /** Only called after a matching positive-damage frontal guard receipt, once per cast. */
    public static void guarded(ServerPlayer player, long revision) {
        Entry e = current(player);
        if (e == null) return;
        if (e.family.equals("sword")) gain(player,e,revision,DisciplineRules.get().sword());
        if (e.family.equals("fist")) gain(player,e,revision,DisciplineRules.get().body());
    }
    private static void gain(ServerPlayer p, Entry e, long revision, DisciplineRules.Pool rules) {
        if (!e.pool.gain(revision,now(p),rules)) return;
        int stacks = e.pool.stacks(now(p));
        p.displayClientMessage(Component.literal((e.family.equals("sword") ? "剑意 " : "蓄劲 ")
                + stacks + "/" + rules.maximum()),true);
        log(p,"gain",e.family,stacks);
    }
    /** Paid release consumes once and freezes the multiplier for every target of that cast. */
    public static double consume(ServerPlayer p, String family) {
        Entry e = current(p);
        if (e == null || !e.family.equals(family)) return 1;
        double multiplier = e.pool.consume(now(p), family.equals("sword")
                ? DisciplineRules.get().sword() : DisciplineRules.get().body());
        if (multiplier > 1) {
            p.displayClientMessage(Component.literal(family.equals("sword") ? "剑意贯虹：剑气强化" : "蓄劲释放：崩山劲强化"),true);
            log(p,"consume",family,multiplier);
        }
        return multiplier;
    }
    public static boolean spearTip(ServerPlayer p, Entity target) {
        Entry e = current(p);
        // Nearest surface, not centre: large bodies cannot fake the spear-tip spacing.
        return e != null && e.family.equals("spear")
                && DisciplineRules.get().spearTip(Math.sqrt(target.getBoundingBox().distanceToSqr(p.position())));
    }
    public static void tipAccepted(ServerPlayer p, long revision) {
        Entry e = current(p);
        if (e != null && e.pool.gain(revision,now(p),DisciplineRules.get().body())) {
            p.displayClientMessage(Component.literal("枪锋命中：破势增强"),true);
            log(p,"spear_tip","spear",revision);
        }
    }
    public static AttackMoveDefinition scale(AttackMoveDefinition m, double multiplier, int extraStun) {
        var r = m.reaction();
        return new AttackMoveDefinition(m.id(),m.displayKey(),m.kind(),m.totalTicks(),m.activeStartTick(),m.activeEndTick(),
                m.damageMultiplier()*multiplier,m.maximumTargets(),m.range(),m.bufferStartTick(),m.chainTick(),
                new ReactionDefinition(Math.min(60,r.hitstunTicks()+extraStun),r.slideDistance(),r.lift(),r.lateralBias()),
                m.animation(),m.hitbox(),m.step(),m.feedback(),m.camera(),m.trail());
    }
    public static String status(ServerPlayer p) {
        Entry e = current(p);
        return "discipline family=" + (e == null ? "none" : e.family) + " stacks="
                + (e == null || e.family.equals("spear") ? 0 : e.pool.stacks(now(p)));
    }
    public static void tick(MinecraftServer server) {
        ENTRIES.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        for (var p : server.getPlayerList().getPlayers()) {
            Entry e = current(p);
            if (e != null) e.pool.stacks(now(p));
        }
    }
    public static void forget(UUID id) { ENTRIES.remove(id); }
    public static void clear() { ENTRIES.clear(); }
    private static void log(ServerPlayer p,String result,String family,double value) {
        LoggerFactory.getLogger(CombatDisciplines.class).info("DISCIPLINE player={} result={} family={} value={}",
                p.getGameProfile().getName(),result,family,value);
    }
}
