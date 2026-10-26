package com.example.myvillage.cultivation.tribulation;

import com.example.myvillage.cultivation.CultivationProfile;
import net.minecraft.resources.ResourceLocation;

/** 只规划不可变替换，实际唯一写入仍经 CultivationService。 */
public final class TribulationSettlement {
    public record Failure(TribulationAttempt attempt, CultivationProfile profile) { }
    private TribulationSettlement() { }
    public static Failure failure(TribulationAttempt attempt, CultivationProfile profile, String reason, boolean penalize) {
        if (!attempt.active()) return new Failure(attempt, profile);
        var ended = attempt.fail(reason, penalize);
        if (penalize && attempt.rules().source(profile.realmId().toString(), profile.stageId().toString()))
            profile = profile.withStability(attempt.failedStability(profile.stability()));
        return new Failure(ended, profile);
    }
    public static CultivationProfile success(TribulationAttempt attempt, CultivationProfile current) {
        if (!attempt.readyToSucceed() || !attempt.rules().source(current.realmId().toString(), current.stageId().toString())
                || current.cultivationProgress() < attempt.cultivationCap() || current.stability() < attempt.requiredStability())
            throw new IllegalStateException("雷劫尚未完整结束或资格已失效");
        return current.withRealmAndStage(ResourceLocation.parse(attempt.rules().targetRealm()), ResourceLocation.parse(attempt.rules().targetStage()))
                .withCultivationProgress(0).withStability(current.stability() / 2);
    }
}
