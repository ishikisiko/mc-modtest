package com.example.myvillage.combat.runtime;

import com.example.myvillage.cultivation.data.TechniqueTraits;
import com.example.myvillage.cultivation.data.TechniqueTraits.ContactState;
import com.example.myvillage.cultivation.fate.FateDefenses;
import com.example.myvillage.cultivation.technique.HeartPassives;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** Source-owned, server-side opportunities with one consumption each. */
public final class CombatTraitStates {
    public enum Kind {
        PURSUIT(ContactState.PURSUIT, "pursuit_ticks", "追击", true, true),
        SPEAR_STANCE(ContactState.SPEAR_STANCE, "spear_stance_ticks", "拒马架势", true, false),
        FIST_UNBALANCE(ContactState.FIST_UNBALANCE, "unbalance_ticks", "拳法失衡", true, true),
        EARTH_UNBALANCE(ContactState.EARTH_UNBALANCE, "unbalance_ticks", "土法失衡", false, true);

        private final ContactState state;
        private final String ticks;
        private final String label;
        private final boolean weaponBound;
        private final boolean harmful;

        Kind(ContactState state, String ticks, String label, boolean weaponBound, boolean harmful) {
            this.state = state;
            this.ticks = ticks;
            this.label = label;
            this.weaponBound = weaponBound;
            this.harmful = harmful;
        }
    }

    private record Key(LivingEntity caster, LivingEntity target, Kind kind) { }
    private record State(ItemStack held, int slot, long until, boolean extended) { }
    private static final Map<Key, State> ACTIVE = new HashMap<>();

    private CombatTraitStates() { }

    public static boolean grant(LivingEntity caster, Entity contact, Kind kind) {
        if (!(CombatTargets.owner(contact) instanceof LivingEntity target) || !legal(caster, target, kind)) return false;
        Key key = new Key(caster, target, kind);
        if (kind == Kind.PURSUIT) ACTIVE.keySet().removeIf(old -> old.caster() == caster && old.kind() == kind && !old.equals(key));
        if (!ACTIVE.containsKey(key) && ACTIVE.keySet().stream().filter(old -> old.caster() == caster).count()
                >= TechniqueTraits.integer("maximum_states_per_caster"))
            return false;
        int ticks = HeartPassives.duration(caster, kind.state, TechniqueTraits.integer(kind.ticks));
        ACTIVE.put(key, new State(caster.getMainHandItem(), CombatCaster.slot(caster), now(caster) + ticks, false));
        CombatCaster.message(caster, net.minecraft.network.chat.Component.literal("获得" + kind.label));
        return true;
    }

    public static boolean active(LivingEntity caster, Entity contact, Kind kind) {
        return key(caster, contact, kind) != null;
    }

    public static boolean consume(LivingEntity caster, Entity contact, Kind kind) {
        Key key = key(caster, contact, kind);
        if (key == null || ACTIVE.remove(key) == null) return false;
        CombatCaster.message(caster, net.minecraft.network.chat.Component.literal("结算" + kind.label));
        return true;
    }

    public static void extendPursuit(LivingEntity caster, Entity contact) {
        Key key = key(caster, contact, Kind.PURSUIT);
        if (key == null) return;
        State state = ACTIVE.get(key);
        if (state.extended()) return;
        int ticks = HeartPassives.duration(caster, ContactState.PURSUIT, TechniqueTraits.integer("pursuit_extend_ticks"));
        ACTIVE.put(key, new State(state.held(), state.slot(), Math.max(state.until(), now(caster) + ticks), true));
        CombatCaster.message(caster, net.minecraft.network.chat.Component.literal("追击时间延续"));
    }

    public static void tick() {
        ACTIVE.entrySet().removeIf(entry -> !valid(entry.getKey(), entry.getValue()));
    }

    public static void forget(Entity entity) {
        ACTIVE.keySet().removeIf(key -> key.caster() == entity || key.target() == entity);
    }

    public static int cleanse(LivingEntity target) {
        int before = ACTIVE.size();
        ACTIVE.keySet().removeIf(key -> key.target() == target && key.kind().harmful);
        return before - ACTIVE.size();
    }

    public static void clear() { ACTIVE.clear(); }

    private static Key key(LivingEntity caster, Entity contact, Kind kind) {
        if (!(CombatTargets.owner(contact) instanceof LivingEntity target)) return null;
        Key key = new Key(caster, target, kind);
        State state = ACTIVE.get(key);
        if (state == null) return null;
        if (valid(key, state)) return key;
        ACTIVE.remove(key);
        return null;
    }

    private static boolean valid(Key key, State state) {
        LivingEntity caster = key.caster();
        return legal(caster, key.target(), key.kind()) && now(caster) < state.until()
                && (!key.kind().weaponBound || state.held() == caster.getMainHandItem()
                    && state.slot() == CombatCaster.slot(caster));
    }

    private static boolean legal(LivingEntity caster, LivingEntity target, Kind kind) {
        return !caster.level().isClientSide() && CombatCaster.active(caster) && target.isAlive()
                && !target.isRemoved() && caster.level() == target.level()
                && (kind.harmful ? CombatActors.mayHarm(caster, target) && !FateDefenses.immune(target) : caster == target);
    }

    private static long now(LivingEntity caster) {
        return CombatCaster.level(caster).getServer().overworld().getGameTime();
    }
}
