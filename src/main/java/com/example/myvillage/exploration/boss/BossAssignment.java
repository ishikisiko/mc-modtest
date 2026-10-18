package com.example.myvillage.exploration.boss;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Typed, immutable ownership. Invalid/foreign state is never treated as an invitation to resample. */
public final class BossAssignment {
    public static final String BIRTH = "myvillage:exploration_boss_birth";
    public static final String REALM = "myvillage:stats_realm";
    private BossAssignment() { }

    public static boolean mayAssign(CompoundTag data) {
        return !data.contains(BIRTH) && !data.contains(REALM)
                && !data.contains("myvillage:vanilla_monster_birth");
    }
    public static CompoundTag birth(BossRules.Profile profile) {
        var birth = new CompoundTag();
        birth.putInt("schema", 1); birth.putString("profile", profile.id()); birth.putString("entity", profile.entity());
        birth.putString("realm", profile.realm().realmId()); birth.putInt("stage", profile.realm().stage());
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
        if (!birth.contains("schema", Tag.TAG_INT) || birth.getInt("schema") != 1
                || !birth.contains("profile", Tag.TAG_STRING) || !birth.contains("entity", Tag.TAG_STRING)
                || !birth.contains("realm", Tag.TAG_STRING) || !birth.contains("stage", Tag.TAG_INT)
                || !realm.contains("realm", Tag.TAG_STRING) || !realm.contains("stage", Tag.TAG_INT)) return Optional.empty();
        return rules.profile(birth.getString("profile")).filter(p -> p.entity().equals(entityType)
                && p.entity().equals(birth.getString("entity")) && p.realm().realmId().equals(birth.getString("realm"))
                && p.realm().stage() == birth.getInt("stage") && p.realm().realmId().equals(realm.getString("realm"))
                && p.realm().stage() == realm.getInt("stage"));
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
