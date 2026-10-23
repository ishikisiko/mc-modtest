package com.example.myvillage.combat.foundation;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** 服务器创建的有界领域描述；客户端不能提交此对象。 */
public record FieldSpec(ResourceLocation kind, ResourceLocation effect, Anchor anchor, Vec3 fixedCenter,
                        double radius, int lifetimeTicks, int pulseIntervalTicks, int maximumTargets,
                        Effect effectType, int effectTicks, float magnitude, boolean oncePerTarget,
                        Targeting targeting) {
    public enum Anchor { FIXED, FOLLOW_SOURCE }
    public enum Effect { ROOT, SLOW, HEAL }
    public enum Targeting { ENEMIES, ALLIES }

    public FieldSpec at(Vec3 center) {
        return new FieldSpec(kind, effect, Anchor.FIXED, center, radius, lifetimeTicks, pulseIntervalTicks,
                maximumTargets, effectType, effectTicks, magnitude, oncePerTarget, targeting);
    }
}
