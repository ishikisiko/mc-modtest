package com.example.myvillage.worldplan.wonder;

import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.boss.lair.LairProgress;
import com.example.myvillage.entity.ModEntities;
import com.example.myvillage.entity.beast.FieldEncounterBoss;
import com.example.myvillage.entity.beast.FieldEncounterRules;
import com.example.myvillage.entity.beast.LotusGuardEntity;
import com.example.myvillage.ruins.BattleReceipts;
import com.example.myvillage.worldplan.FieldBiomeRules.Kind;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import com.example.myvillage.worldplan.wonder.FieldEncounterSites.Site;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The world's two encounter slots own the boss identity and temporary terrain phases. */
public final class FieldArenaEncounter extends SavedData {
    private static final String SAVE_ID = "yuanxian_field_encounter";
    private static final FieldEncounterRules.Encounter RULES = FieldEncounterRules.bundled().encounter();
    private enum Phase { IDLE, FLOOD, REFLUX, BRIDGE }

    private static final class Slot {
        LairProgress progress = new LairProgress("READY", null);
        BlockPos home;
        final ArrayDeque<BlockPos> incoming = new ArrayDeque<>();
        final LinkedHashMap<Long, BlockPos> water = new LinkedHashMap<>();
        final Set<BlockPos> bridge = new HashSet<>();
        final Set<UUID> surgeVictims = new HashSet<>();
        Phase phase = Phase.IDLE;
        long endsAt;

        boolean owns(UUID id, BlockPos at) {
            return at != null && at.equals(home) && id.equals(progress.boss());
        }
    }

    private final Map<Kind, Slot> slots = new EnumMap<>(Kind.class);
    private FieldArenaEncounter() {
        for (var kind : Kind.values()) slots.put(kind, new Slot());
    }

    private static FieldArenaEncounter get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(FieldArenaEncounter::new, FieldArenaEncounter::load), SAVE_ID);
    }

    private static FieldArenaEncounter load(CompoundTag tag, HolderLookup.Provider registries) {
        var result = new FieldArenaEncounter();
        for (var kind : Kind.values()) {
            if (!tag.contains(kind.name())) continue;
            var saved = tag.getCompound(kind.name());
            var slot = result.slots.get(kind);
            slot.home = saved.contains("Home") ? BlockPos.of(saved.getLong("Home")) : null;
            slot.progress = new LairProgress(saved.getString("State"), saved.hasUUID("Boss") ? saved.getUUID("Boss") : null);
        }
        return result;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        slots.forEach((kind, slot) -> {
            var state = new CompoundTag();
            state.putString("State", slot.progress.state().name());
            if (slot.home != null) state.putLong("Home", slot.home.asLong());
            if (slot.progress.boss() != null) state.putUUID("Boss", slot.progress.boss());
            tag.put(kind.name(), state);
        });
        return tag;
    }

    public static Site site(ServerLevel level, Kind kind, BlockPos at) {
        var selected = WorldPlanRuntime.generator(level).map(g -> g.fieldSites().get(kind)).orElse(null);
        return selected != null && selected.home().equals(at) ? selected : null;
    }

    public static boolean isRewardOwner(ServerLevel level, Kind kind, UUID id, BlockPos at) {
        if (at == null || site(level, kind, at) == null) return false;
        var slot = get(level).slots.get(kind);
        return slot.progress.state() == LairProgress.State.ACTIVE && slot.owns(id, at);
    }

    public static String status(ServerLevel level, Kind kind) {
        return get(level).slots.get(kind).progress.state().name().toLowerCase(Locale.ROOT);
    }

    public static String phase(ServerLevel level, Kind kind) { return get(level).slots.get(kind).phase.name().toLowerCase(Locale.ROOT); }
    public static boolean idle(ServerLevel level, Kind kind) { return get(level).slots.get(kind).phase == Phase.IDLE; }
    public static int temporaryCount(ServerLevel level, Kind kind) {
        var slot = get(level).slots.get(kind);
        return kind == Kind.TIDE ? slot.water.size() : slot.bridge.size();
    }

    public static void tick(ServerTickEvent.Post event) {
        var level = event.getServer().overworld();
        var generator = WorldPlanRuntime.generator(level).orElse(null);
        if (generator == null || generator.fieldSites().isEmpty()) return;
        var data = get(level);
        boolean check = level.getGameTime() % RULES.checkTicks() == 0;
        generator.fieldSites().forEach((kind, site) -> {
            var slot = data.slots.get(kind);
            if (check) data.updateSlot(level, kind, site, slot);
            if (slot.phase != Phase.IDLE) updateTerrain(level, site, slot);
        });
    }

    private void updateSlot(ServerLevel level, Kind kind, Site site, Slot slot) {
        BlockPos home = site.home();
        if (slot.home != null && !slot.home.equals(home)) return;
        var receipts = BattleReceipts.get(level);
        if (slot.progress.state() == LairProgress.State.ACTIVE) {
            var id = slot.progress.boss();
            if (receipts.status(id).equals("dead")) {
                slot.progress.died(id);
                receipts.forget(id);
                setDirty();
            } else if (receipts.status(id).equals("retry")) {
                slot.progress.retry(id);
                receipts.forget(id);
                setDirty();
            }
        }
        if (slot.progress.state() != LairProgress.State.READY || slot.phase != Phase.IDLE
                || level.getDifficulty() == Difficulty.PEACEFUL
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;

        Vec3 feet = Vec3.atBottomCenterOf(home);
        double radius = site.domain().wakeRadius();
        boolean nearby = level.players().stream().anyMatch(p -> LotusGuardEntity.isFighter(p)
                && site.contains(p.getBlockX(), p.getBlockZ()) && p.distanceToSqr(feet) <= radius * radius);
        if (!nearby || !level.isPositionEntityTicking(home) || !level.areEntitiesLoaded(ChunkPos.asLong(home))) return;
        var type = kind == Kind.TIDE ? ModEntities.TIDE_DRAGON.get() : ModEntities.STELE_APE.get();
        var boss = type.create(level);
        if (boss == null) return;
        float yaw = kind == Kind.TIDE ? (site.bay().side() > 0 ? 0 : 180) : -90;
        boss.moveTo(feet.x, feet.y, feet.z, yaw, 0);
        var box = boss.getBoundingBox();
        if (!level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ),
                    BlockPos.containing(box.maxX, box.minY, box.maxZ))
                || !level.getWorldBorder().isWithinBounds(box) || level.containsAnyLiquid(box)
                || !level.noCollision(boss, box)) return;
        boss.bind(site);
        boss.addTag("yuanxian_field_boss");
        var id = boss.getUUID();
        if (!slot.progress.bind(id)) return;
        receipts.register(id);
        if (level.addFreshEntity(boss)) {
            slot.home = home;
            setDirty();
        } else {
            slot.progress.retry(id);
            receipts.forget(id);
        }
    }

    public static void raiseTide(ServerLevel level, Site site, FieldEncounterBoss boss) {
        var slot = get(level).slots.get(Kind.TIDE);
        if (!slot.owns(boss.getUUID(), site.home()) || slot.phase != Phase.IDLE) return;
        var field = WorldPlanRuntime.generator(level).orElseThrow().fieldLayout();
        slot.incoming.addAll(FieldArenaTerrain.tide(level, field, site));
        if (slot.incoming.isEmpty()) return;
        slot.phase = Phase.FLOOD;
        slot.endsAt = level.getGameTime() + RULES.tideHoldTicks();
        slot.surgeVictims.clear();
        level.playSound(null, site.marker(), SoundEvents.BUCKET_EMPTY, SoundSource.HOSTILE, 4, .65F);
    }

    public static boolean setBridge(ServerLevel level, Site site, FieldEncounterBoss boss) {
        var slot = get(level).slots.get(Kind.WIND);
        if (!slot.owns(boss.getUUID(), site.home()) || slot.phase != Phase.IDLE) return false;
        var span = FieldArenaTerrain.bridge(level, site);
        if (span.isEmpty()) return false;
        var slab = ModBlocks.ENCOUNTER_STELE.get();
        for (BlockPos p : span) {
            if (!FieldArenaTerrain.place(level, p, slab)) {
                slot.bridge.forEach(pos -> FieldArenaTerrain.remove(level, pos, slab));
                slot.bridge.clear();
                return false;
            }
            slot.bridge.add(p);
        }
        slot.phase = Phase.BRIDGE;
        slot.endsAt = level.getGameTime() + RULES.steleHoldTicks();
        boss.setFieldPhase(true, true);
        level.playSound(null, site.marker(), SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 4, .65F);
        return true;
    }

    public static boolean ownsTemporary(ServerLevel level, BlockPos pos, boolean bridge) {
        var slot = get(level).slots.get(bridge ? Kind.WIND : Kind.TIDE);
        return bridge ? slot.bridge.contains(pos) : pos.equals(slot.water.get(column(pos.getX(), pos.getZ())));
    }

    public static void useSluice(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || event.isCanceled()
                || !LotusGuardEntity.isFighter(player) || !(player.level() instanceof ServerLevel level)) return;
        var site = WorldPlanRuntime.generator(level).map(g -> g.fieldSites().get(Kind.TIDE)).orElse(null);
        if (site == null || !site.marker().equals(event.getPos())
                || !level.mayInteract(player, event.getPos()) || !level.getBlockState(event.getPos()).is(Blocks.LEVER)) return;
        var slot = get(level).slots.get(Kind.TIDE);
        var boss = loadedBoss(level, slot);
        if (slot.phase != Phase.FLOOD || boss == null) return;
        beginReflux(level, site, slot, boss);
        player.displayClientMessage(Component.translatable("block.yuanxian.field_sluice.open"), true);
        // Allow the original lever interaction to update its position, sound and redstone state.
    }

    private static FieldEncounterBoss loadedBoss(ServerLevel level, Slot slot) {
        return slot.progress.boss() != null && level.getEntity(slot.progress.boss()) instanceof FieldEncounterBoss boss && boss.isAlive()
                && slot.home != null && boss.isBoundTo(slot.home) ? boss : null;
    }

    private static void updateTerrain(ServerLevel level, Site site, Slot slot) {
        var boss = loadedBoss(level, slot);
        long now = level.getGameTime();
        if (slot.phase == Phase.BRIDGE) {
            if (boss != null && now >= slot.endsAt) boss.setFieldPhase(false, true);
            var walkway = FieldArenaTerrain.bridgeWalkway(site);
            if ((boss == null || now >= slot.endsAt) && level.players().stream().noneMatch(p ->
                    p.isAlive() && !p.isSpectator() && p.getBoundingBox().intersects(walkway))) {
                slot.bridge.forEach(pos -> FieldArenaTerrain.remove(level, pos, ModBlocks.ENCOUNTER_STELE.get()));
                slot.bridge.clear();
                if (boss != null) boss.setFieldPhase(false, false);
                slot.phase = Phase.IDLE;
            }
            return;
        }
        if (slot.phase == Phase.FLOOD && (boss == null || now >= slot.endsAt)) beginReflux(level, site, slot, boss);
        var sheet = ModBlocks.ENCOUNTER_TIDE_SHEET.get();
        if (slot.phase == Phase.FLOOD) {
            for (int n = 0; n < RULES.tideWriteBatch() && !slot.incoming.isEmpty(); n++) {
                BlockPos pos = slot.incoming.removeFirst();
                if (FieldArenaTerrain.place(level, pos, sheet)) slot.water.put(column(pos.getX(), pos.getZ()), pos);
            }
            if (boss != null) applyCurrent(level, site, slot, boss);
        } else if (slot.phase == Phase.REFLUX) {
            var water = slot.water.reversed().values().iterator();
            for (int n = 0; n < RULES.tideWriteBatch() && water.hasNext(); n++) {
                FieldArenaTerrain.remove(level, water.next(), sheet);
                water.remove();
            }
            if (slot.water.isEmpty() && now >= slot.endsAt) {
                if (boss != null) boss.setFieldPhase(false, false);
                slot.phase = Phase.IDLE;
            }
        }
    }

    private static long column(int x, int z) { return (long)x << 32 | z & 0xFFFFFFFFL; }

    private static void applyCurrent(ServerLevel level, Site site, Slot slot, FieldEncounterBoss boss) {
        for (ServerPlayer player : level.players()) {
            if (!LotusGuardEntity.isFighter(player)) continue;
            var wet = slot.water.get(column(player.getBlockX(), player.getBlockZ()));
            if (wet == null || player.getY() >= wet.getY() + 1 || !level.hasChunkAt(wet)
                    || !level.getBlockState(wet).is(ModBlocks.ENCOUNTER_TIDE_SHEET.get())
                    || player.getY() < level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR,
                            player.getBlockX(), player.getBlockZ()) - 1) continue;
            // Keep the river's pull in the channel and register only one damage event per player per surge.
            if (level.getGameTime() % 4 == 0) {
                player.push(0, .018, site.bay().side() * -.145);
                player.hurtMarked = true;
            }
            if (slot.surgeVictims.add(player.getUUID())) boss.surgeHit(player);
        }
    }

    private static void beginReflux(ServerLevel level, Site site, Slot slot, FieldEncounterBoss boss) {
        slot.phase = Phase.REFLUX;
        slot.endsAt = level.getGameTime() + RULES.tideReturnTicks();
        slot.incoming.clear();
        if (boss != null) {
            boss.setFieldPhase(true, false);
            boss.startReflux();
            level.playSound(null, site.marker(), SoundEvents.BUCKET_FILL, SoundSource.HOSTILE, 4, .75F);
        }
    }
}
