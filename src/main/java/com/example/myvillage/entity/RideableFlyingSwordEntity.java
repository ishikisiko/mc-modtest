package com.example.myvillage.entity;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.network.FlyingSwordInputPayload;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class RideableFlyingSwordEntity extends Entity {
    private static final String PLAYER_BINDING_TAG = MyVillageMod.MOD_ID + ":rideable_flying_sword";
    private static final long INPUT_TIMEOUT_TICKS = 5L;
    private static final double MAX_OWNER_DISTANCE_SQR = 64.0 * 64.0;
    private static final double MAX_HORIZONTAL_SPEED = 0.65;
    private static final double MAX_VERTICAL_SPEED = 0.40;
    private static final double HORIZONTAL_RESPONSE = 0.22;
    private static final double VERTICAL_RESPONSE = 0.28;
    private static final double HORIZONTAL_DRAG = 0.84;
    private static final double VERTICAL_DRAG = 0.72;
    private static final double COLLISION_EPSILON = 1.0E-7;

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

    public void bindTo(ServerPlayer owner) {
        ownerUuid = owner.getUUID();
        owner.getPersistentData().putUUID(PLAYER_BINDING_TAG, getUUID());
    }

    public boolean isOwnedBy(Player player) {
        return ownerUuid != null && ownerUuid.equals(player.getUUID());
    }

    public void acceptInput(byte flags, long gameTime) {
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
        boolean ownerMounted = hasPassenger(owner);
        if (ownerMounted) {
            owner.resetFallDistance();
            setYRot(owner.getYRot());
        }

        byte activeFlags = isInputFresh(serverLevel.getGameTime()) && ownerMounted ? inputFlags : 0;
        moveFromInput(owner, activeFlags);
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
        return lastInputTick != Long.MIN_VALUE && gameTime - lastInputTick <= INPUT_TIMEOUT_TICKS;
    }

    private void removeUnauthorizedPassengers(ServerPlayer owner) {
        for (Entity passenger : List.copyOf(getPassengers())) {
            if (passenger != owner) {
                passenger.stopRiding();
            }
        }
    }

    private void moveFromInput(ServerPlayer owner, byte flags) {
        double forwardAxis = axis(flags, FlyingSwordInputPayload.FORWARD, FlyingSwordInputPayload.BACKWARD);
        double strafeAxis = axis(flags, FlyingSwordInputPayload.LEFT, FlyingSwordInputPayload.RIGHT);
        double verticalAxis = axis(flags, FlyingSwordInputPayload.ASCEND, FlyingSwordInputPayload.DESCEND);

        Vec3 velocity = getDeltaMovement();
        Vec3 horizontalInput = Vec3.ZERO;
        if (forwardAxis != 0.0 || strafeAxis != 0.0) {
            Vec3 forward = Vec3.directionFromRotation(0.0F, owner.getYRot());
            Vec3 left = forward.yRot((float) (Math.PI / 2.0));
            horizontalInput = forward.scale(forwardAxis).add(left.scale(strafeAxis));
            if (horizontalInput.lengthSqr() > 1.0) {
                horizontalInput = horizontalInput.normalize();
            }
        }

        double nextX;
        double nextZ;
        if (horizontalInput.lengthSqr() > 0.0) {
            nextX = Mth.lerp(HORIZONTAL_RESPONSE, velocity.x, horizontalInput.x * MAX_HORIZONTAL_SPEED);
            nextZ = Mth.lerp(HORIZONTAL_RESPONSE, velocity.z, horizontalInput.z * MAX_HORIZONTAL_SPEED);
        } else {
            nextX = velocity.x * HORIZONTAL_DRAG;
            nextZ = velocity.z * HORIZONTAL_DRAG;
        }

        double nextY = verticalAxis == 0.0
                ? velocity.y * VERTICAL_DRAG
                : Mth.lerp(VERTICAL_RESPONSE, velocity.y, verticalAxis * MAX_VERTICAL_SPEED);
        nextY = Mth.clamp(nextY, -MAX_VERTICAL_SPEED, MAX_VERTICAL_SPEED);

        Vec3 nextVelocity = clampHorizontal(new Vec3(nextX, nextY, nextZ));
        Vec3 beforeMove = position();
        setDeltaMovement(nextVelocity);
        move(MoverType.SELF, nextVelocity);

        Vec3 actualMove = position().subtract(beforeMove);
        setDeltaMovement(
                blocked(nextVelocity.x, actualMove.x) ? 0.0 : nextVelocity.x,
                blocked(nextVelocity.y, actualMove.y) ? 0.0 : nextVelocity.y,
                blocked(nextVelocity.z, actualMove.z) ? 0.0 : nextVelocity.z);
    }

    private static double axis(byte flags, int positive, int negative) {
        int unsignedFlags = Byte.toUnsignedInt(flags);
        return ((unsignedFlags & positive) != 0 ? 1.0 : 0.0)
                - ((unsignedFlags & negative) != 0 ? 1.0 : 0.0);
    }

    private static Vec3 clampHorizontal(Vec3 velocity) {
        double horizontalSqr = velocity.x * velocity.x + velocity.z * velocity.z;
        double maxSqr = MAX_HORIZONTAL_SPEED * MAX_HORIZONTAL_SPEED;
        if (horizontalSqr <= maxSqr) {
            return velocity;
        }
        double scale = MAX_HORIZONTAL_SPEED / Math.sqrt(horizontalSqr);
        return new Vec3(velocity.x * scale, velocity.y, velocity.z * scale);
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
        if (data.hasUUID(PLAYER_BINDING_TAG) && getUUID().equals(data.getUUID(PLAYER_BINDING_TAG))) {
            data.remove(PLAYER_BINDING_TAG);
        }
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
