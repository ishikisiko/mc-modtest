package com.example.myvillage.entity.beast;

import com.example.myvillage.MyVillageMod;
import net.minecraft.resources.ResourceLocation;

/** Positive local X is the beast's left flank, the same side as its left_armor bone. */
final class LotusGuardCombat {
    static final ResourceLocation LEFT = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "lotus_guard_left_slam");
    static final ResourceLocation RIGHT = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "lotus_guard_right_slam");

    private LotusGuardCombat() {}

    static int side(ResourceLocation move) {
        if (move.equals(LEFT)) return 1;
        if (move.equals(RIGHT)) return -1;
        return 0;
    }

    static float facing(float targetYaw, int side) { return targetYaw + side * 90; }
    static float impact(float bodyYaw, int side) { return bodyYaw - side * 90; }

    static boolean exposed(BeastMoveDefinition move, int tick, LotusGuardRules.Armor armor) {
        return side(move.id()) != 0 && tick > move.activeTicks().last()
                && tick < move.totalTicks() - armor.returnTicks();
    }

    static boolean isFlankHit(double x, double z, float yaw, int side, double sourceX, double sourceZ,
                              LotusGuardRules.Armor armor) {
        return BeastGeometry.forwardDistance(x, z, impact(yaw, side), sourceX, sourceZ) >= armor.flankNear()
            && Math.abs(BeastGeometry.forwardDistance(x, z, yaw, sourceX, sourceZ)) <= armor.flankHalfLength();
    }
}
