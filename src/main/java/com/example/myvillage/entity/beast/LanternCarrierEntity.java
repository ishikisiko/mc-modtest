package com.example.myvillage.entity.beast;

import com.example.myvillage.MyVillageMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public final class LanternCarrierEntity extends FoldedRidgeBeast {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "lantern_carrier");
    private @Nullable BlockPos post;

    public LanternCarrierEntity(EntityType<? extends BeastEntity> type, Level level) {
        super(type, level);
        xpReward = 8;
    }

    @Override protected ResourceLocation beastId() { return ID; }

    public static boolean checkSpawn(EntityType<LanternCarrierEntity> type, ServerLevelAccessor level,
                                     MobSpawnType reason, BlockPos pos, RandomSource random) {
        return allowsSpawn(type, level, reason, pos, random);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new BeastAttackGoal(this));
        goalSelector.addGoal(5, new ReturnToPost());
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (!super.canAttack(target)) return false;
        if (post == null || !(level() instanceof ServerLevel server)) return true;
        double radius = getAttributeValue(Attributes.FOLLOW_RANGE);
        return post.distToCenterSqr(target.getX(), target.getY(), target.getZ()) <= radius * radius
                && ridge(server).filter(site -> inInterlayer(site, target.blockPosition())).isPresent();
    }

    @Override
    protected void customServerAiStep() {
        if (post == null && onGround() && level() instanceof ServerLevel server
                && ridge(server).filter(site -> inInterlayer(site, blockPosition())).isPresent()) post = blockPosition();
        super.customServerAiStep();
    }

    @Override
    protected boolean moveEligible(BeastMoveDefinition move, LivingEntity target) {
        return move.hit().low() + getY() <= target.getBoundingBox().maxY
                && move.hit().high() + getY() >= target.getY();
    }

    @Override
    protected void playWindupSound(BeastMoveDefinition move) {
        playSound(SoundEvents.ROOTED_DIRT_BREAK, .9F, .68F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (post != null) tag.putLong("RidgePost", post.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        post = tag.contains("RidgePost") ? BlockPos.of(tag.getLong("RidgePost")) : null;
    }

    private final class ReturnToPost extends Goal {
        private ReturnToPost() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            return getRandom().nextInt(8) == 0 && awayFromPost();
        }

        @Override
        public boolean canContinueToUse() {
            return awayFromPost() && !getNavigation().isDone();
        }

        private boolean awayFromPost() {
            LivingEntity target = getTarget();
            return post != null && (target == null || !canAttack(target))
                    && !isMoveRunning() && post.distToCenterSqr(getX(), getY(), getZ()) > 3;
        }

        @Override
        public void start() {
            getNavigation().moveTo(post.getX()+.5, post.getY(), post.getZ()+.5, .85);
        }
    }
}
