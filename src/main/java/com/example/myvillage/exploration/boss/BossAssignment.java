package com.example.myvillage.exploration.boss;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Typed, immutable ownership. Invalid/foreign state is never treated as an invitation to resample. */
public final class BossAssignment {
    public static final String BIRTH = "yuanxian:exploration_boss_birth";
    public static final String REALM = "yuanxian:stats_realm";
    private BossAssignment() { }

    public static boolean mayAssign(CompoundTag data) {
        return !data.contains(BIRTH) && !data.contains(REALM)
                && !data.contains("yuanxian:vanilla_monster_birth");
    }
    public static CompoundTag birth(BossRules.Profile profile) {
        var birth = new CompoundTag();
        birth.putInt("schema", 2); birth.putString("profile", profile.id()); birth.putString("entity", profile.entity());
        birth.putString("realm", profile.realm().realmId()); birth.putInt("stage", profile.realm().stage());
        birth.putInt("foundation_quality", profile.growth().foundation());
        birth.putInt("golden_core_quality", profile.growth().goldenCore());
        birth.putInt("nascent_soul_quality", profile.growth().nascentSoul());
        return birth;
    }
    public static CompoundTag realm(BossRules.Profile profile) {
        var realm = new CompoundTag();
        realm.putString("realm", profile.realm().realmId()); realm.putInt("stage", profile.realm().stage());
        return realm;
    }
    public static Optional<BossRules.Profile> resolve(CompoundTag data, String entityType, BossRules rules) {
        if (!data.contains(BIRTH, Tag.TAG_COMPOUND) || !data.contains(REALM, Tag.TAG_COMPOUND)) return Optional.empty();
        var birth = data.getCompound(BIRTH); var realm = data.getCompound(REALM);
        if (!birth.contains("schema", Tag.TAG_INT) || birth.getInt("schema") != 2
                || !birth.contains("profile", Tag.TAG_STRING) || !birth.contains("entity", Tag.TAG_STRING)
                || !birth.contains("realm", Tag.TAG_STRING) || !birth.contains("stage", Tag.TAG_INT)
                || !birth.contains("foundation_quality", Tag.TAG_INT) || !birth.contains("golden_core_quality", Tag.TAG_INT)
                || !birth.contains("nascent_soul_quality", Tag.TAG_INT)
                || !realm.contains("realm", Tag.TAG_STRING) || !realm.contains("stage", Tag.TAG_INT)) return Optional.empty();
        var growth = new BossRules.Growth(birth.getInt("stage"), birth.getInt("foundation_quality"),
                birth.getInt("golden_core_quality"), birth.getInt("nascent_soul_quality"));
        return rules.profile(birth.getString("profile")).flatMap(set -> set.variant(growth))
                .filter(p -> p.entity().equals(entityType) && p.entity().equals(birth.getString("entity"))
                        && p.realm().realmId().equals(birth.getString("realm"))
                        && p.realm().realmId().equals(realm.getString("realm")) && p.realm().stage() == realm.getInt("stage"));
    }
    /** Used only by the exact native wither charge-heal call site, before vanilla heal processing. */
    public static float chargeHealing(float amount, double effectiveMaximum, double nativeBaseMaximum) {
        if (!Float.isFinite(amount) || amount <= 0 || !Double.isFinite(effectiveMaximum)
                || !Double.isFinite(nativeBaseMaximum) || effectiveMaximum <= 0 || nativeBaseMaximum <= 0) return amount;
        double scaled = amount * effectiveMaximum / nativeBaseMaximum;
        return Double.isFinite(scaled) && scaled <= Float.MAX_VALUE ? (float) scaled : amount;
    }
    /** Adoption scales the current fraction, including a charging wither or a wounded legacy boss. */
    public static float adoptedHealth(float oldHealth, float oldMaximum, float newMaximum) {
        if (!Float.isFinite(oldHealth) || !Float.isFinite(oldMaximum) || !Float.isFinite(newMaximum)
                || oldMaximum <= 0 || newMaximum <= 0) throw new IllegalArgumentException("invalid boss health");
        return (float) (Math.clamp((double) oldHealth / oldMaximum, 0, 1) * newMaximum);
    }
}
