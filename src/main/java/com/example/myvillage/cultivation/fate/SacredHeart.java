package com.example.myvillage.cultivation.fate;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.HeartMethod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

public final class SacredHeart {
    public static final String FATE = "yuanxian:sacred_earth_embryo";

    private SacredHeart() { }

    public static void apply(ServerPlayer player) {
        if (FateService.has(player, FATE) && !CultivationService.getProfile(player).learnedTechniques().containsKey(FateReworkRules.HEART_ID))
            CultivationService.learnTechnique(player, FateReworkRules.HEART_ID);
    }

    public static FateReworkRules.Stage stage(CultivationProfile profile) {
        int index = FateReworkRules.get().stage(profile.realmId());
        return index < 0 ? null : FateReworkRules.get().stages().get(index);
    }

    public static HeartMethod.Defense defense(CultivationProfile profile) {
        var stage = stage(profile);
        return new HeartMethod.Defense(HeartMethod.Direction.GENERAL, stage == null ? 0 : stage.defense(),
                stage == null ? 0 : stage.health(), Optional.empty(), HeartMethod.DefenseAttributes.NONE, Optional.empty());
    }

    public static double earthResistance(LivingEntity target) {
        var stage = stage(target);
        return stage == null ? 0 : stage.earthResistance();
    }

    public static double earthDamage(LivingEntity target) {
        var stage = stage(target);
        return stage == null ? 0 : stage.earthBonus();
    }

    private static FateReworkRules.Stage stage(LivingEntity target) {
        return target instanceof ServerPlayer player && FateService.has(player, FATE)
                ? stage(CultivationService.getProfile(player)) : null;
    }
}
