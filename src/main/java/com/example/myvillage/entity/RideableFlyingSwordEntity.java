package com.example.myvillage.entity;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.entity.flight.*;
import com.example.myvillage.item.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import com.example.myvillage.network.FlyingSwordInputPayload;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class RideableFlyingSwordEntity extends Entity {
    private static final String PLAYER_BINDING_TAG = MyVillageMod.MOD_ID + ":rideable_flying_sword";
    private static final double MAX_OWNER_DISTANCE_SQR = 64.0 * 64.0;
    private static final double COLLISION_EPSILON = 1.0E-7;
    private static final EntityDataAccessor<Boolean> AZURE = SynchedEntityData.defineId(RideableFlyingSwordEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(RideableFlyingSwordEntity.class, EntityDataSerializers.INT);
    private FlyingSwordRules rules;
    private boolean landing;
    private int flightTicks;
    private int emptyTicks;
    private long prepaidTicks;
    private boolean warned;

    private UUID ownerUuid;
    private byte inputFlags;
    private long lastInputTick = Long.MIN_VALUE;
    private int clientLerpSteps;
    private double clientLerpX;
    private double clientLerpY;
    private double clientLerpZ;
    private double clientLerpYRot;

    public RideableFlyingSwordEntity(EntityType<? extends RideableFlyingSwordEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public void initializeFlight(ServerPlayer owner, boolean azure, FlyingSwordRules rules) {
        this.rules = rules;
        entityData.set(AZURE, azure);
        bindTo(owner);
    }
    public ItemStack visualStack() { return new ItemStack(entityData.get(AZURE) ? ModItems.AZURE_CLOUD_FLYING_SWORD.get() : ModItems.RIDEABLE_FLYING_SWORD.get()); }
    public FlyingSwordFlight.Phase phase() { return FlyingSwordFlight.Phase.values()[entityData.get(PHASE)]; }
    public boolean isLanding() { return landing || emptyTicks > 0; }
    public boolean azure() { return entityData.get(AZURE); }
    public void requestLanding() { landing = true; }
    public void emergencyRecovery() {
        landing = true; emptyTicks = rules.empty_steering_ticks()+1;
        entityData.set(PHASE, FlyingSwordFlight.Phase.EMPTY_DESCENT.ordinal());
    }

    public void bindTo(ServerPlayer owner) {
        ownerUuid = owner.getUUID();
        owner.getPersistentData().putUUID(PLAYER_BINDING_TAG, getUUID());
    }

    public boolean isOwnedBy(Player player) {
        return ownerUuid != null && ownerUuid.equals(player.getUUID());
    }

    public void acceptInput(ServerPlayer sender, byte flags, long gameTime) {
        if (!FlyingSwordAuthority.controls(ownerUuid, sender.getUUID(), sender.isAlive() && !sender.isRemoved(),
                hasPassenger(sender) && sender.getVehicle() == this, sender.level() == level())) return;
        if ((Byte.toUnsignedInt(flags) & ~FlyingSwordInputPayload.ALL_FLAGS) != 0) {
            return;
        }
        inputFlags = flags;
        lastInputTick = gameTime;
    }

    public static boolean recallOwned(ServerPlayer owner) {
        MinecraftServer server = owner.getServer();
        if (server == null) {
            return false;
        }

        CompoundTag ownerData = owner.getPersistentData();
        Set<RideableFlyingSwordEntity> owned = new LinkedHashSet<>();
        if (ownerData.hasUUID(PLAYER_BINDING_TAG)) {
            UUID boundEntityUuid = ownerData.getUUID(PLAYER_BINDING_TAG);
            for (ServerLevel level : server.getAllLevels()) {
                Entity indexed = level.getEntity(boundEntityUuid);
                if (indexed instanceof RideableFlyingSwordEntity sword && sword.isOwnedBy(owner)) {
                    owned.add(sword);
                    break;
                }
            }
        }

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof RideableFlyingSwordEntity sword && sword.isOwnedBy(owner)) {
                    owned.add(sword);
                }
            }
        }

        ownerData.remove(PLAYER_BINDING_TAG);
        owned.forEach(Entity::discard);
        FlyingSwordInventory.release(owner);
        return !owned.isEmpty();
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        if (level().isClientSide()) {
            tickClientInterpolation();
            return;
        }

        setXRot(0.0F);
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ServerPlayer owner = resolveOwner(serverLevel);
        if (owner == null
                || owner.hasDisconnected()
                || !owner.isAlive()
                || owner.isRemoved()
                || owner.level() != level()
                || owner.distanceToSqr(this) > MAX_OWNER_DISTANCE_SQR) {
            discard();
            return;
        }

        removeUnauthorizedPassengers(owner);
        if (!hasPassenger(owner) || rules == null) { discard(); return; }
        owner.resetFallDistance();
        setYRot(owner.getYRot());
        byte activeFlags = isInputFresh(serverLevel.getGameTime()) ? inputFlags : 0;
        long qi = CultivationService.getProfile(owner).currentSpiritualPower();
        boolean creative = owner.isCreative();
        if (!creative && qi <= rules.low_qi() && !warned) {
            owner.displayClientMessage(Component.literal("灵力不足，已禁催剑；请尽快落地。"), true); warned = true;
        }
        if (isInWaterOrBubble() || isInLava()) landing = true;
        FlyingSwordFlight.Phase phase = emptyTicks > 0 ? FlyingSwordFlight.Phase.EMPTY_DESCENT
                : FlyingSwordFlight.phase(landing, qi, creative, activeFlags, flightTicks++, rules);
        // Prepaid billing is charged on entry and every billing interval. Boost must pay its premium immediately.
        long rate = FlyingSwordFlight.cost(phase, rules);
        if (!creative && phase != FlyingSwordFlight.Phase.EMPTY_DESCENT
                && (prepaidTicks <= 0 || phase == FlyingSwordFlight.Phase.BOOST && phase() != phase)) {
            if (!CultivationService.spendSpiritualPower(owner, rate)) {
                landing = true;
                // Spend the remaining qi once, then enter irreversible emergency descent.
                long left = CultivationService.getProfile(owner).currentSpiritualPower();
                if (left > 0) CultivationService.spendSpiritualPower(owner, left);
                phase = FlyingSwordFlight.Phase.EMPTY_DESCENT;
            }
            prepaidTicks = rules.billing_ticks();
        }
        if (!creative && CultivationService.getProfile(owner).currentSpiritualPower() <= 0)
            phase = FlyingSwordFlight.Phase.EMPTY_DESCENT;
        prepaidTicks--;
        if (phase == FlyingSwordFlight.Phase.EMPTY_DESCENT) { landing = true; emptyTicks++; }
        // Once emergency descent started a pill cannot turn it into a free new launch.
        if (emptyTicks > 0) phase = FlyingSwordFlight.Phase.EMPTY_DESCENT;
        entityData.set(PHASE, phase.ordinal());
        moveFromInput(owner, activeFlags);
        if (onGround() && (landing || phase == FlyingSwordFlight.Phase.EMPTY_DESCENT)) {
            owner.resetFallDistance(); FlyingSwordInventory.clearLanding(owner); discard();
        }
    }

    private void tickClientInterpolation() {
        if (clientLerpSteps > 0) {
            lerpPositionAndRotationStep(
                    clientLerpSteps,
                    clientLerpX,
                    clientLerpY,
                    clientLerpZ,
                    clientLerpYRot,
                    0.0);
            clientLerpSteps--;
            return;
        }

        reapplyPosition();
        setRot(getYRot(), 0.0F);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        clientLerpX = x;
        clientLerpY = y;
        clientLerpZ = z;
        clientLerpYRot = yRot;
        clientLerpSteps = steps + 2;
    }

    @Override
    public double lerpTargetX() {
        return clientLerpSteps > 0 ? clientLerpX : getX();
    }

    @Override
    public double lerpTargetY() {
        return clientLerpSteps > 0 ? clientLerpY : getY();
    }

    @Override
    public double lerpTargetZ() {
        return clientLerpSteps > 0 ? clientLerpZ : getZ();
    }

    @Override
    public float lerpTargetXRot() {
        return 0.0F;
    }

    @Override
    public float lerpTargetYRot() {
        return clientLerpSteps > 0 ? (float) clientLerpYRot : getYRot();
    }

    private ServerPlayer resolveOwner(ServerLevel level) {
        return ownerUuid == null ? null : level.getServer().getPlayerList().getPlayer(ownerUuid);
    }

    private boolean isInputFresh(long gameTime) {
        return FlyingSwordFlight.fresh(gameTime, lastInputTick, rules.input_timeout_ticks());
    }

    private void removeUnauthorizedPassengers(ServerPlayer owner) {
        for (Entity passenger : List.copyOf(getPassengers())) {
            if (passenger != owner) {
                passenger.stopRiding();
            }
        }
    }

    private void moveFromInput(ServerPlayer owner, byte flags) {
        Vec3 v = getDeltaMovement();
        var motion = FlyingSwordFlight.step(new FlyingSwordFlight.Motion(v.x,v.y,v.z), flags, owner.getYRot(), phase(), emptyTicks, rules);
        Vec3 nextVelocity = new Vec3(motion.x(),motion.y(),motion.z());
        // Stop before unknown chunks or the world border; never request chunk loading from flight.
        var swept = getBoundingBox().expandTowards(nextVelocity).inflate(.05);
        if (!level().hasChunksAt(net.minecraft.core.BlockPos.containing(swept.minX,swept.minY,swept.minZ),
                net.minecraft.core.BlockPos.containing(swept.maxX,swept.maxY,swept.maxZ))
                || !level().getWorldBorder().isWithinBounds(swept)) {
            landing = true;
            nextVelocity = new Vec3(0, -rules.landing_speed(), 0);
        }
        Vec3 beforeMove = position();
        setDeltaMovement(nextVelocity);
        move(MoverType.SELF, nextVelocity);
        Vec3 actualMove = position().subtract(beforeMove);
        setDeltaMovement(blocked(nextVelocity.x,actualMove.x)?0:nextVelocity.x,
                blocked(nextVelocity.y,actualMove.y)?0:nextVelocity.y,
                blocked(nextVelocity.z,actualMove.z)?0:nextVelocity.z);
    }

    private static boolean blocked(double requested, double actual) {
        return Math.abs(requested - actual) > COLLISION_EPSILON;
    }

    @Override
    public boolean causeFallDamage(float distance, float damageMultiplier, DamageSource source) {
        resetFallDistance();
        getPassengers().forEach(Entity::resetFallDistance);
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player && isOwnedBy((Player) passenger) && getPassengers().isEmpty();
    }

    @Override
    @Deprecated
    protected boolean couldAcceptPassenger() {
        return getPassengers().isEmpty();
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        clearBindingIfCurrent();
        super.remove(reason);
    }

    @Override
    public void onRemovedFromLevel() {
        clearBindingIfCurrent();
        super.onRemovedFromLevel();
    }

    private void clearBindingIfCurrent() {
        if (level().isClientSide() || ownerUuid == null || level().getServer() == null) {
            return;
        }
        ServerPlayer owner = level().getServer().getPlayerList().getPlayer(ownerUuid);
        if (owner == null) {
            return;
        }
        CompoundTag data = owner.getPersistentData();
        if (data.hasUUID(PLAYER_BINDING_TAG) && FlyingSwordAuthority.currentBinding(data.getUUID(PLAYER_BINDING_TAG), getUUID())) {
            FlyingSwordInventory.release(owner);
            data.remove(PLAYER_BINDING_TAG);
        }
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (hasPassenger(passenger)) move.accept(passenger, getX(), getY()+.27, getZ());
    }

    @Override
    public boolean shouldRiderSit() { return false; }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(AZURE, false);
        builder.define(PHASE, FlyingSwordFlight.Phase.TAKEOFF.ordinal());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
