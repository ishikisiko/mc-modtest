package com.example.myvillage.sect;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One server-owned deck, persistent in its shaft, with no player block replacement during travel. */
public final class SectLiftPlatform extends Entity {
    public static final float THICKNESS = .25F;
    private static final double SPEED = .10;
    private static final int MAX_RISE = 128;
    private static final EntityDataAccessor<Boolean> WIDE =
        SynchedEntityData.defineId(SectLiftPlatform.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> MOVING =
        SynchedEntityData.defineId(SectLiftPlatform.class, EntityDataSerializers.BOOLEAN);
    private Route route;
    private int goal;
    private int interpolationSteps;
    private double interpolationY;

    public SectLiftPlatform(EntityType<? extends SectLiftPlatform> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setInvulnerable(true);
    }

    public int width() { return entityData.get(WIDE) ? 3 : 2; }

    public boolean isTravelling() { return entityData.get(MOVING); }

    @Override public boolean canBeCollidedWith() { return true; }

    @Override public boolean shouldRiderSit() { return false; }

    @Override public EntityDimensions getDimensions(Pose pose) { return EntityDimensions.fixed(width(), THICKNESS); }

    @Override protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < width() * width();
    }

    @Override protected void positionRider(Entity passenger, MoveFunction move) {
        int slot = getPassengers().indexOf(passenger);
        if (slot < 0) return;
        double shift = (width() - 1) * .5;
        move.accept(passenger, getX() + .79 * (slot % width() - shift), getY() + THICKNESS,
            getZ() + .79 * (slot / width() - shift));
    }

    @Override public Vec3 getDismountLocationForPassenger(LivingEntity rider) {
        return new Vec3(rider.getX(), getY() + THICKNESS, rider.getZ());
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(WIDE, false);
        builder.define(MOVING, false);
    }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (WIDE.equals(key)) refreshDimensions();
    }

    private void install(Route route, int y) {
        this.route = route;
        this.goal = y;
        entityData.set(WIDE, route.width() == 3);
        setPos(route.centerX(), y - THICKNESS, route.centerZ());
    }

    static String call(ServerLevel level, BlockPos terminal, BlockState state, ServerPlayer operator) {
        Route route = Route.fromTerminal(level, terminal, state);
        if (route == null || !route.ready(level) || !route.deckChunkReady(level)) return "unavailable";
        List<SectLiftPlatform> decks = level.getEntitiesOfClass(SectLiftPlatform.class, route.shaft(),
            deck -> route.equals(deck.route));
        if (decks.size() > 1) return "unavailable";
        if (decks.isEmpty()) {
            if (!route.clear(level)) return "blocked";
            SectLiftPlatform deck = SectCourtContent.LIFT.get().create(level);
            if (deck == null) return "unavailable";
            deck.install(route, terminal.getY());
            if (!level.addFreshEntity(deck)) return "unavailable";
            route.openOnly(level, terminal.getY());
            return "arrived";
        }
        return decks.getFirst().request(level, terminal.getY(), operator);
    }

    private boolean onDeck(ServerPlayer player, int dock) {
        return !player.isSpectator() && Math.abs(player.getY() - dock) < .25
            && getBoundingBox().contains(player.getX(), getY() + .1, player.getZ());
    }

    private String request(ServerLevel level, int station, ServerPlayer operator) {
        if (isTravelling()) return "moving";
        int dock = (int) Math.round(getY() + THICKNESS);
        if (dock == station && !onDeck(operator, dock)) return "arrived";
        if (!route.clear(level) || !route.gatewayClear(level, dock)) return "blocked";
        List<ServerPlayer> riders = level.getEntitiesOfClass(ServerPlayer.class,
            getBoundingBox().expandTowards(0, .27, 0), player -> onDeck(player, dock));
        if (riders.size() > width() * width()) return "full";
        for (ServerPlayer player : riders) {
            if (!player.startRiding(this, true)) {
                ejectPassengers();
                return "blocked";
            }
        }
        goal = dock == station ? route.other(dock) : station;
        entityData.set(MOVING, true);
        route.openOnly(level, Integer.MIN_VALUE);
        return "moving";
    }

    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        setPos(x, getY(), z);
        interpolationY = y;
        interpolationSteps = Math.max(1, steps);
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            if (interpolationSteps > 0) {
                setPos(getX(), Mth.lerp(1.0 / interpolationSteps, getY(), interpolationY), getZ());
                --interpolationSteps;
            }
            return;
        }
        if (route == null) {
            discard();
            return;
        }
        if (!route.ready(level)) return;
        if (!isTravelling()) {
            if (tickCount == 1) route.openOnly(level, (int) Math.round(getY() + THICKNESS));
            return;
        }
        double target = goal - THICKNESS;
        double step = Mth.clamp(target - getY(), -SPEED, SPEED);
        AABB proposed = getBoundingBox().move(0, step, 0).deflate(.001, 0, .001).expandTowards(0, 2.05, 0);
        // A block placed after departure must stop the deck before it reaches riders.
        if (level.getBlockCollisions(this, proposed).iterator().hasNext()) return;
        setPos(getX(), getY() + step, getZ());
        if (Math.abs(getY() - target) < .001) {
            setPos(getX(), target, getZ());
            entityData.set(MOVING, false);
            route.openOnly(level, goal);
            ejectPassengers();
        }
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        if (route == null) return;
        tag.putLong("Lower", route.lower().asLong());
        tag.putInt("UpperY", route.upper().getY());
        tag.putString("Facing", route.facing().getName());
        tag.putBoolean("Wide", route.width() == 3);
        tag.putBoolean("Moving", isTravelling());
        tag.putInt("Goal", goal);
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        if (!tag.contains("Lower")) return;
        BlockPos lower = BlockPos.of(tag.getLong("Lower"));
        int upper = tag.getInt("UpperY");
        Direction facing = Direction.byName(tag.getString("Facing"));
        goal = tag.getInt("Goal");
        if (facing == null || !facing.getAxis().isHorizontal() || upper - lower.getY() < 2 ||
            upper - lower.getY() > MAX_RISE || (goal != upper && goal != lower.getY())) return;
        route = new Route(lower, lower.atY(upper), facing, tag.getBoolean("Wide") ? 3 : 2);
        entityData.set(WIDE, route.width() == 3);
        entityData.set(MOVING, tag.getBoolean("Moving"));
        double y = Math.clamp(getY(), lower.getY() - THICKNESS, upper - THICKNESS);
        setPos(route.centerX(), isTravelling() ? y : goal - THICKNESS, route.centerZ());
    }

    private record Route(BlockPos lower, BlockPos upper, Direction facing, int width) {
        static Route fromTerminal(ServerLevel level, BlockPos pos, BlockState state) {
            boolean top = state.getValue(LiftTerminalBlock.UPPER);
            boolean wide = state.getValue(LiftTerminalBlock.WIDE);
            Direction direction = state.getValue(LiftTerminalBlock.FACING);
            for (int n = 2; n <= MAX_RISE; n++) {
                BlockPos at = pos.offset(0, top ? -n : n, 0);
                if (level.isOutsideBuildHeight(at)) break;
                BlockState other = level.getBlockState(at);
                if (!other.is(SectCourtContent.LIFT_TERMINAL.get())) continue;
                if (!other.equals(state.setValue(LiftTerminalBlock.UPPER, !top))) return null;
                return new Route(top ? at : pos, top ? pos : at, direction, wide ? 3 : 2);
            }
            return null;
        }

        BlockPos offset(BlockPos terminal, int forward, int sideways) {
            return terminal.relative(facing, forward).relative(facing.getClockWise(), sideways);
        }

        BlockPos corner() { return offset(lower, 2, 1); }

        BlockPos oppositeCorner() { return offset(lower, width + 1, width); }

        double centerX() { return (corner().getX() + oppositeCorner().getX() + 1) * .5; }

        double centerZ() { return (corner().getZ() + oppositeCorner().getZ() + 1) * .5; }

        int other(int y) { return y == lower.getY() ? upper.getY() : lower.getY(); }

        AABB shaft() {
            double radius = width * .5;
            return new AABB(centerX() - radius, lower.getY() - 1, centerZ() - radius,
                centerX() + radius, upper.getY() + 3, centerZ() + radius);
        }

        boolean deckChunkReady(ServerLevel level) {
            BlockPos at = BlockPos.containing(centerX(), lower.getY(), centerZ());
            return level.isPositionEntityTicking(at) && level.areEntitiesLoaded(new ChunkPos(at).toLong());
        }

        boolean ready(ServerLevel level) {
            for (BlockPos terminal : List.of(lower, upper)) {
                if (!level.hasChunkAt(terminal)) return false;
                BlockState state = level.getBlockState(terminal);
                if (!state.is(SectCourtContent.LIFT_TERMINAL.get())
                    || state.getValue(LiftTerminalBlock.UPPER) != terminal.equals(upper)
                    || state.getValue(LiftTerminalBlock.WIDE) != (width == 3)
                    || state.getValue(LiftTerminalBlock.FACING) != facing) return false;
                for (int n = 1; n <= width; n++) {
                    BlockPos gate = offset(terminal, 1, n);
                    if (!level.hasChunkAt(gate)) return false;
                    BlockState gateState = level.getBlockState(gate);
                    if (!gateState.is(SectCourtContent.LIFT_GATE.get()) ||
                        gateState.getValue(LiftGateBlock.FACING) != facing) return false;
                }
            }
            for (BlockPos corner : BlockPos.betweenClosed(corner(), oppositeCorner()))
                if (!level.hasChunkAt(corner)) return false;
            return true;
        }

        boolean clear(ServerLevel level) {
            for (BlockPos p : BlockPos.betweenClosed(corner().atY(lower.getY() - 1), oppositeCorner().atY(upper.getY() + 2))) {
                if (!level.hasChunkAt(p) || !level.isEmptyBlock(p)) return false;
            }
            return true;
        }

        boolean gatewayClear(ServerLevel level, int y) {
            BlockPos terminal = lower.atY(y);
            for (int n = 1; n <= width; n++) {
                BlockPos gate = offset(terminal, 1, n);
                AABB gateSpace = new AABB(gate).expandTowards(0, .5, 0);
                if (!level.getEntitiesOfClass(LivingEntity.class, gateSpace,
                    entity -> entity.isAlive() && !entity.isSpectator()).isEmpty()) return false;
            }
            return true;
        }

        void openOnly(ServerLevel level, int y) {
            for (BlockPos terminal : List.of(lower, upper)) {
                for (int n = 1; n <= width; n++) {
                    BlockPos gate = offset(terminal, 1, n);
                    BlockState state = level.getBlockState(gate);
                    BlockState opened = state.setValue(LiftGateBlock.OPEN, terminal.getY() == y);
                    if (opened != state) level.setBlock(gate, opened, Block.UPDATE_CLIENTS);
                }
            }
        }
    }
}
