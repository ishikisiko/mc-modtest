package com.example.myvillage.sim.runtime.player;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.block.entity.ScriptureShelfBlockEntity;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.item.TechniqueManualItem;
import com.example.myvillage.sim.PlayerMemberView;
import com.example.myvillage.sim.SectView;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.sim.runtime.net.ScriptureBorrowPayload;
import com.example.myvillage.sim.runtime.net.ScriptureHallPayload;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The server side of the scripture hall (藏经阁). Server-authoritative: a right click on a
 * scripture shelf ({@link #open}) and every borrow the player asks for ({@link #handleBorrow}) are
 * checked here (the overworld, a shelf with an owning sect, within
 * {@code rules.player.steward.interact_range}, an active ledger, an active sect) before anything is
 * read or changed; the hall sent back ({@link ScriptureHallPayload}) is built from the ledger
 * ({@code WorldSim.borrowable}/{@code hasBorrowed}) and the technique registry
 * ({@link ScriptureHallList}). A borrow is recorded through {@code WorldSim.recordBorrow} and hands
 * the player a technique manual (dropped at their feet when the inventory is full).
 *
 * <p>Logs {@code SCRIPTURE_HALL player=<name> intent=OPEN sect=<id> member=<true|false> entries=<n>}
 * for every hall sent on a right click, and
 * {@code SCRIPTURE_HALL player=<name> intent=BORROW sect=<id> technique=<id> result=ok|<reason>}
 * for every borrow handled (not for one dropped by the throttle).
 */
public final class ScriptureHall {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptureHall.class);
    /** Least server ticks between two handled borrows of one player. */
    static final int MIN_TICKS_BETWEEN = 4;
    static final String MESSAGE = "message.myvillage.world.scripture.";
    static final String UNOWNED_KEY = MESSAGE + "unowned";
    static final String BORROWED_KEY = MESSAGE + "borrowed";
    static final String REFUSED_PREFIX = MESSAGE + "refused.";
    private static final int MAX_TRACKED_PLAYERS = 256;

    private static final Map<UUID, Integer> LAST_BORROW_TICK = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Integer> eldest) {
            return size() > MAX_TRACKED_PLAYERS;
        }
    };

    private ScriptureHall() {
    }

    /** A checked shelf: the live ledger, the owning (active) sect. */
    private record Hall(WorldSim sim, SectView sect) {
    }

    /** Either a checked shelf or the reason (and the shelf's sect id, -1 when unknown) it failed. */
    private record Checked(Optional<Hall> hall, String reason, int sectId) {
        static Checked ok(Hall hall) {
            return new Checked(Optional.of(hall), ScriptureHallList.OK, hall.sect().id());
        }

        static Checked refused(String reason, int sectId) {
            return new Checked(Optional.empty(), reason, sectId);
        }
    }

    /** A player used the scripture shelf at {@code pos}: checks and sends the hall, or one chat line. */
    public static void open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Checked checked = check(player, level, pos);
        if (checked.hall().isEmpty()) {
            LOGGER.info("ScriptureHall: {} at shelf {} (sect {}) gets no hall: {}", name(player), pos.toShortString(),
                    checked.sectId(), checked.reason());
            player.sendSystemMessage(Component.translatable(ScriptureHallList.UNOWNED.equals(checked.reason())
                    ? UNOWNED_KEY : REFUSED_PREFIX + checked.reason()));
            return;
        }
        ScriptureHallPayload payload = page(player, checked.hall().get(), pos);
        PacketDistributor.sendToPlayer(player, payload);
        LOGGER.info("SCRIPTURE_HALL player={} intent=OPEN sect={} member={} entries={}", name(player),
                payload.sectId(), payload.member(), payload.entries().size());
    }

    /** A borrow the player asked for: checked again, recorded in the ledger, the manual given, the hall resent. */
    public static void handleBorrow(ServerPlayer player, ScriptureBorrowPayload payload) {
        if (!allow(player.getUUID(), player.getServer() == null ? 0 : player.getServer().getTickCount())) {
            return;
        }
        String techniqueId = payload.techniqueId();
        BlockPos pos = payload.pos();
        Checked checked = check(player, player.serverLevel(), pos);
        if (checked.hall().isEmpty()) {
            refuse(player, checked.sectId(), techniqueId, checked.reason(), Optional.empty(), pos);
            return;
        }
        Hall hall = checked.hall().get();
        WorldSim sim = hall.sim();
        int sectId = hall.sect().id();
        String playerId = player.getUUID().toString();
        Optional<String> refusal = ScriptureHallList.refusal(sim.playerMember(playerId), sectId, true);
        if (refusal.isPresent()) {
            refuse(player, sectId, techniqueId, refusal.get(), checked.hall(), pos);
            return;
        }
        List<String> borrowable;
        try {
            borrowable = sim.borrowable(playerId);
        } catch (RuntimeException ex) {
            LOGGER.error("ScriptureHall: borrowable list of {} failed", name(player), ex);
            refuse(player, sectId, techniqueId, ScriptureHallList.INACTIVE, Optional.empty(), pos);
            return;
        }
        if (!borrowable.contains(techniqueId)) {
            refuse(player, sectId, techniqueId, ScriptureHallList.NOT_BORROWABLE, checked.hall(), pos);
            return;
        }
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, techniqueId);
        ItemStack manual = TechniqueManualItem.manualFor(player.registryAccess(), rl);
        if (manual.isEmpty()) {
            refuse(player, sectId, techniqueId, ScriptureHallList.NO_MANUAL, checked.hall(), pos);
            return;
        }
        try {
            sim.recordBorrow(playerId, name(player), techniqueId);
        } catch (IllegalArgumentException ex) {
            refuse(player, sectId, techniqueId, reasonOf(ex), checked.hall(), pos);
            return;
        } catch (RuntimeException ex) {
            LOGGER.error("ScriptureHall: borrow of {} by {} failed", techniqueId, name(player), ex);
            refuse(player, sectId, techniqueId, ScriptureHallList.INACTIVE, Optional.empty(), pos);
            return;
        }
        if (player.getServer() != null) {
            WorldSimSavedData.get(player.getServer().overworld()).setDirty();
        }
        Component techniqueName = ModCultivationRegistries.technique(player.registryAccess(), rl)
                .map(def -> (Component) Component.translatable(def.translationKey()))
                .orElse(Component.literal(techniqueId));
        if (!player.getInventory().add(manual)) {
            player.drop(manual, false);
        }
        player.sendSystemMessage(Component.translatable(BORROWED_KEY,
                Component.translatable("item.myvillage.manual.named", techniqueName), hall.sect().name()));
        logBorrow(player, sectId, techniqueId, ScriptureHallList.OK);
        PacketDistributor.sendToPlayer(player, page(player, hall, pos));
    }

    // ------------------------------------------------------------------ checks

    private static Checked check(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD || player.level() != level) {
            return Checked.refused(ScriptureHallList.INACTIVE, -1);
        }
        double range = WorldSimRuntime.data().map(d -> d.rules().player().steward().interactRange())
                .orElse(SectDialogue.DEFAULT_RANGE);
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > range * range) {
            return Checked.refused(ScriptureHallList.TOO_FAR, -1);
        }
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof ScriptureShelfBlockEntity be)
                || be.sectId() < 0) {
            return Checked.refused(ScriptureHallList.UNOWNED, -1);
        }
        int sectId = be.sectId();
        Optional<WorldSim> active = WorldSimRuntime.sim();
        if (active.isEmpty()) {
            return Checked.refused(ScriptureHallList.INACTIVE, sectId);
        }
        Optional<SectView> sect = active.get().sect(sectId);
        if (sect.isEmpty() || !"active".equals(sect.get().state())) {
            return Checked.refused(ScriptureHallList.INACTIVE, sectId);
        }
        return Checked.ok(new Hall(active.get(), sect.get()));
    }

    /** True (and remembered) unless the player's previous borrow was under {@link #MIN_TICKS_BETWEEN} ticks ago. */
    static boolean allow(UUID player, int tick) {
        Integer last = LAST_BORROW_TICK.get(player);
        if (last != null && tick >= last && tick - last < MIN_TICKS_BETWEEN) {
            return false;
        }
        LAST_BORROW_TICK.put(player, tick);
        return true;
    }

    // ------------------------------------------------------------------ the hall

    /** The hall as this player sees it at this shelf now. */
    private static ScriptureHallPayload page(ServerPlayer player, Hall hall, BlockPos pos) {
        WorldSim sim = hall.sim();
        SectView sect = hall.sect();
        String playerId = player.getUUID().toString();
        Optional<PlayerMemberView> me = sim.playerMember(playerId);
        Optional<String> refusal = ScriptureHallList.refusal(me, sect.id(), true);
        List<ScriptureHallPayload.Entry> entries = List.of();
        String rank = "";
        if (refusal.isEmpty()) {
            rank = me.get().rank();
            Map<String, Integer> cost = WorldSimRuntime.data()
                    .map(d -> d.rules().player().scriptureHall().borrowCostByGrade())
                    .orElse(Map.of());
            List<String> borrowable;
            try {
                borrowable = sim.borrowable(playerId);
            } catch (RuntimeException ex) {
                LOGGER.error("ScriptureHall: borrowable list of {} failed", name(player), ex);
                borrowable = List.of();
            }
            entries = ScriptureHallList.entries(borrowable, id -> def(player, id),
                    id -> sim.hasBorrowed(playerId, id), cost);
        }
        return new ScriptureHallPayload(pos, sect.id(),
                ScriptureHallPayload.clip(sect.name(), ScriptureHallPayload.MAX_NAME),
                ScriptureHallPayload.clip(rank, ScriptureHallPayload.MAX_WORD),
                refusal.isEmpty(),
                refusal.orElse(ScriptureHallList.OK),
                entries);
    }

    private static Optional<ScriptureHallList.Def> def(ServerPlayer player, String id) {
        ResourceLocation rl = ResourceLocation.tryBuild(MyVillageMod.MOD_ID, id);
        if (rl == null) {
            return Optional.empty();
        }
        Optional<TechniqueDefinition> def = ModCultivationRegistries.technique(player.registryAccess(), rl);
        return def.map(d -> new ScriptureHallList.Def(Component.translatable(d.translationKey()), d.grade(),
                d.category().name().toLowerCase(Locale.ROOT)));
    }

    private static void refuse(ServerPlayer player, int sectId, String techniqueId, String reason,
                               Optional<Hall> hall, BlockPos pos) {
        // a ledger reason this hall has no line for reads as "not borrowable"; the log keeps it as is
        String line = ScriptureHallList.REFUSALS.contains(reason) ? reason : ScriptureHallList.NOT_BORROWABLE;
        player.sendSystemMessage(Component.translatable(REFUSED_PREFIX + line));
        logBorrow(player, sectId, techniqueId, reason);
        hall.ifPresent(h -> PacketDistributor.sendToPlayer(player, page(player, h, pos)));
    }

    private static void logBorrow(ServerPlayer player, int sectId, String techniqueId, String result) {
        LOGGER.info("SCRIPTURE_HALL player={} intent=BORROW sect={} technique={} result={}", name(player), sectId,
                techniqueId, result);
    }

    private static String reasonOf(IllegalArgumentException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() || message.contains(" ") ? "refused" : message;
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }
}
