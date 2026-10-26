package com.example.myvillage.cultivation.tribulation;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/** 专用标记不绕过护甲、抗性、护盾或原版死亡生命周期。 */
public final class TribulationDamage {
    public static final TagKey<DamageType> ALL = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse("myvillage:tribulation"));
    public static final TagKey<DamageType> UNAVOIDABLE = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse("myvillage:tribulation_unavoidable"));
    private TribulationDamage() { }
    public static boolean bypassesDodge(DamageSource source) { return source.is(UNAVOIDABLE); }
    public static boolean tribulation(DamageSource source) { return source.is(ALL); }
    /** 此时护甲、药效、丹盾与共享盾已处理，原版 absorption 尚未扣除。 */
    public static boolean lethal(float damage, float health, float absorption) { return damage >= health + absorption; }
    public static float nonLethal(float health, float absorption, float configuredMinimum) {
        return Math.max(0, absorption + health - Math.min(configuredMinimum, health));
    }
}
