package com.example.myvillage.boss.lair;

import com.example.myvillage.block.ModBlockEntities;
import com.example.myvillage.entity.beast.ThunderTigerEntity;
import com.example.myvillage.ruins.BattleReceipts;
import com.example.myvillage.ruins.RuinRules.Pos;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One saved boss slot per natural lair. Never interprets an unloaded entity as a defeat. */
public final class ThunderTigerLairBlockEntity extends BlockEntity {
    private LairProgress progress = new LairProgress("DORMANT", null);
    public ThunderTigerLairBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THUNDER_TIGER_LAIR.get(), pos, state);
    }
    private BlockPos target(Pos pos) {
        int turns = switch (getBlockState().getValue(ThunderTigerLairAnchorBlock.FACING)) {
            case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0;
        };
        var delta = pos.subtract(ThunderTigerLairRules.get().anchor()).rotate(turns);
        return worldPosition.offset(delta.x(), delta.y(), delta.z());
    }
    public void tick() {
        if (!(level instanceof ServerLevel server)) return;
        var rules = ThunderTigerLairRules.get();
        if (server.getGameTime() % rules.tickInterval() != 0
                || progress.state() == LairProgress.State.DORMANT || progress.state() == LairProgress.State.CLEARED) return;
        var receipts = BattleReceipts.get(server);
        if (progress.state() == LairProgress.State.ACTIVE) {
            UUID id = progress.boss();
            switch (receipts.status(id)) {
                case "dead" -> { progress.died(id); receipts.forget(id); setChanged(); }
                case "retry" -> { progress.retry(id); receipts.forget(id); setChanged(); }
                default -> { /* Alive, unloaded or unknown identities retain the occupied slot. */ }
            }
        }
        if (progress.state() != LairProgress.State.READY || server.getDifficulty() == Difficulty.PEACEFUL) return;
        BlockPos trigger = target(rules.trigger());
        boolean playerNear = server.players().stream().anyMatch(player -> player.isAlive() && !player.isSpectator()
                && !player.isCreative() && player.distanceToSqr(trigger.getCenter()) <= rules.triggerRadius() * rules.triggerRadius());
        if (!playerNear) return;
        BlockPos home = target(rules.spawn());
        List<BlockPos> pillars = rules.pillars().stream().map(this::target).toList();
        if (!server.hasChunkAt(home) || pillars.stream().anyMatch(p -> !server.hasChunkAt(p))) return;
        var entity = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("myvillage", "thunder_tiger")).create(server);
        if (!(entity instanceof ThunderTigerEntity boss)) return;
        boss.moveTo(home.getX() + .5, home.getY(), home.getZ() + .5, 180, 0);
        var body = boss.getSpawnOccupiedBounds();
        // Check every foot cell and touched chunk before collision queries. Never load or repair terrain.
        for (int x = Mth.floor(body.minX); x <= Mth.floor(body.maxX - 1.0E-7); x++) {
            for (int z = Mth.floor(body.minZ); z <= Mth.floor(body.maxZ - 1.0E-7); z++) {
                var floor = new BlockPos(x, home.getY() - 1, z);
                if (!server.hasChunkAt(floor) || !server.getBlockState(floor).isCollisionShapeFullBlock(server, floor)) return;
            }
        }
        if (!server.noCollision(boss, body) || server.containsAnyLiquid(body)) return;
        boss.setPersistenceRequired();
        boss.setCanPickUpLoot(false);
        boss.bindLair(home, pillars);
        boss.addTag("thunder_tiger_lair_boss");
        boss.getPersistentData().putLong("myvillage:thunder_tiger_lair_anchor", worldPosition.asLong());
        UUID id = boss.getUUID();
        if (!progress.bind(id)) return;
        receipts.register(id);
        setChanged();
        if (!server.addFreshEntity(boss)) {
            progress.retry(id);
            receipts.forget(id);
            setChanged();
        }
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("LairState", progress.state().name());
        if (progress.boss() != null) tag.putUUID("Boss", progress.boss());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progress = new LairProgress(tag.getString("LairState"), tag.hasUUID("Boss") ? tag.getUUID("Boss") : null);
    }
}
