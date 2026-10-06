package com.example.myvillage.sim.runtime.player;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.sim.Admission;
import com.example.myvillage.sim.PersonView;
import com.example.myvillage.sim.PlayerMemberView;
import com.example.myvillage.sim.PlayerQualification;
import com.example.myvillage.sim.SectView;
import com.example.myvillage.sim.SimData;
import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.sim.runtime.WorldSimText;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The runtime side of player sect membership (player sect entry, slice 1): reads a player's
 * qualification from the cultivation profile, joins and leaves through the {@code WorldSim}
 * facade, marks {@code WorldSimSavedData} dirty, sends the player's chat lines and logs
 * {@code SECT_ENTRY player=<name> intent=JOIN|LEAVE sect=<id> result=ok|<reason>}.
 * {@link #register()} refreshes snapshots on login and on settlement days, and tells an online
 * player the day's {@code player_*} events about them.
 *
 * <p>Admission is the ledger's ({@code WorldSim.joinSect}, with {@code force} for the admin
 * command); the command path logs the same {@code SECT_ENTRY} line as the dialogue. Sect tasks and
 * apprenticeship (slice 3) are {@link SectTasks}, which logs its intents with the same line;
 * {@link #masterGuidanceBasisPoints} is the master's meditation factor.
 */
public final class WorldSimPlayers {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldSimPlayers.class);
    static final String JOINED_KEY = "message.myvillage.world.sect.joined";
    static final String LEFT_KEY = "message.myvillage.world.sect.left";
    /** The event types sent to the player they name ({@code params[0]}). */
    static final String PLAYER_EVENT_PREFIX = "player_";
    /** {@link #masterGuidanceBasisPoints} without a master: the factor 1. */
    static final int NO_GUIDANCE = 10_000;

    private WorldSimPlayers() {
    }

    /** The outcome of a join or leave; {@code reason} is an {@code Admission} reason or "ok". */
    public record Result(boolean ok, String reason) {
    }

    /**
     * Subscribes the login and settlement-day listeners, and the sect-task progress hooks of
     * {@link SectTasks} (beast kills, the courier check every second). Called from
     * {@code WorldSimRuntime.register()}.
     */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(WorldSimPlayers::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(SectTasks::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(SectTasks::onServerTick);
        WorldSimRuntime.addListener(WorldSimPlayers::onDaySettled);
    }

    // ------------------------------------------------------------------ listeners

    static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Optional<WorldSim> sim = WorldSimRuntime.sim();
        if (sim.isEmpty()) {
            return;
        }
        if (refresh(sim.get(), player)) {
            markDirty(player.getServer());
        }
    }

    static void onDaySettled(MinecraftServer server, WorldSim sim, List<SimEvent> events) {
        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        boolean members = false;
        for (ServerPlayer player : online) {
            members |= refresh(sim, player);
        }
        if (members) {
            markDirty(server);
        }
        for (SimEvent e : events) {
            if (!e.type().startsWith(PLAYER_EVENT_PREFIX) || e.params().isEmpty()) {
                continue;
            }
            String name = e.params().get(0);
            for (ServerPlayer player : online) {
                if (player.getGameProfile().getName().equals(name)) {
                    player.sendSystemMessage(WorldSimText.event(e));
                }
            }
        }
    }

    /** Refreshes the player's snapshot; true when the player has a ledger record (so the save changed). */
    private static boolean refresh(WorldSim sim, ServerPlayer player) {
        String id = player.getUUID().toString();
        if (sim.playerMember(id).isEmpty()) {
            return false;
        }
        sim.updatePlayerQualification(id, player.getGameProfile().getName(), qualification(player));
        return true;
    }

    // ------------------------------------------------------------------ qualification

    /** The player's qualification as the ledger judges it, from their cultivation profile. */
    public static PlayerQualification qualification(ServerPlayer player) {
        CultivationProfile profile = CultivationService.getProfile(player);
        int stage = 0;
        Optional<Registry<RealmDefinition>> realms = player.registryAccess().registry(ModCultivationRegistries.REALMS);
        RealmDefinition realm = realms.map(r -> r.get(profile.realmId())).orElse(null);
        if (realm != null) {
            for (int i = 0; i < realm.stages().size(); i++) {
                if (realm.stages().get(i).id().equals(profile.stageId())) {
                    stage = i;
                    break;
                }
            }
        }
        int rootPeak = profile.spiritualRoot()
                .map(root -> root.affinitiesBasisPoints().values().stream().mapToInt(Integer::intValue).max().orElse(0))
                .orElse(0);
        return new PlayerQualification(profile.realmId().getPath(), stage, profile.awakened(), Math.max(0, rootPeak));
    }

    /** The player's ledger record, or empty when the ledger is inactive or has none. */
    public static Optional<PlayerMemberView> member(ServerPlayer player) {
        return WorldSimRuntime.sim().flatMap(sim -> sim.playerMember(player.getUUID().toString()));
    }

    // ------------------------------------------------------------------ master guidance (slice 3)

    /**
     * The meditation progress factor the player's master gives, in basis points (10000 = none):
     * {@code 10000 × (1 + rules.cultivation.master_guidance)} while the player has a master who is
     * alive and still in the player's sect, else 10000. {@code MeditationManager} multiplies it into
     * its progress factor.
     */
    public static int masterGuidanceBasisPoints(ServerPlayer player) {
        Optional<WorldSim> active = WorldSimRuntime.sim();
        Optional<SimData> data = WorldSimRuntime.data();
        if (active.isEmpty() || data.isEmpty()) {
            return NO_GUIDANCE;
        }
        try {
            WorldSim sim = active.get();
            Optional<PlayerMemberView> me = sim.playerMember(player.getUUID().toString());
            if (me.isEmpty() || me.get().masterId() < 0) {
                return NO_GUIDANCE;
            }
            Optional<PersonView> master = sim.person(me.get().masterId());
            return SectTasks.guidanceBasisPoints(me.get().sectId(), me.get().masterId(),
                    master.map(PersonView::alive).orElse(false), master.map(PersonView::sectId).orElse(-1),
                    data.get().rules().cultivation().masterGuidance());
        } catch (RuntimeException ex) {
            LOGGER.warn("WorldSimPlayers: master guidance of {} unreadable", player.getGameProfile().getName(), ex);
            return NO_GUIDANCE;
        }
    }

    // ------------------------------------------------------------------ join and leave

    /**
     * Joins the player to the sect through {@code WorldSim.joinSect}, whose refusal (an
     * IllegalArgumentException carrying the {@link Admission} reason) is returned as the reason.
     * {@code force} (admin command) is passed on: the ledger then only needs the sect to be active
     * and the player not already its member (a member elsewhere leaves that sect first, without
     * penalty); cooldown, standing, awakening, realm and selectivity are skipped.
     */
    public static Result join(ServerPlayer player, int sectId, boolean force) {
        String name = player.getGameProfile().getName();
        Optional<WorldSim> active = WorldSimRuntime.sim();
        if (active.isEmpty()) {
            return logged(name, "JOIN", sectId, new Result(false, SectDialogueKeys.INACTIVE));
        }
        WorldSim sim = active.get();
        String id = player.getUUID().toString();
        PlayerQualification q = qualification(player);
        sim.updatePlayerQualification(id, name, q);
        try {
            sim.joinSect(id, name, sectId, q, force);
        } catch (IllegalArgumentException ex) {
            return logged(name, "JOIN", sectId, new Result(false, reasonOf(ex)));
        }
        markDirty(player.getServer());
        String sectName = sim.sect(sectId).map(SectView::name).orElse(String.valueOf(sectId));
        player.sendSystemMessage(Component.translatable(JOINED_KEY, sectName));
        return logged(name, "JOIN", sectId, new Result(true, Admission.OK));
    }

    /** The player leaves their sect. */
    public static Result leave(ServerPlayer player) {
        String name = player.getGameProfile().getName();
        Optional<WorldSim> active = WorldSimRuntime.sim();
        if (active.isEmpty()) {
            return logged(name, "LEAVE", -1, new Result(false, SectDialogueKeys.INACTIVE));
        }
        WorldSim sim = active.get();
        String id = player.getUUID().toString();
        Optional<PlayerMemberView> me = sim.playerMember(id);
        if (me.isEmpty() || !me.get().inSect()) {
            return logged(name, "LEAVE", -1, new Result(false, SectDialogueKeys.NOT_MEMBER));
        }
        int sectId = me.get().sectId();
        String sectName = me.get().sectName();
        sim.updatePlayerQualification(id, name, qualification(player));
        try {
            sim.leaveSect(id, name);
        } catch (IllegalArgumentException ex) {
            return logged(name, "LEAVE", sectId, new Result(false, reasonOf(ex)));
        }
        markDirty(player.getServer());
        player.sendSystemMessage(Component.translatable(LEFT_KEY, sectName));
        return logged(name, "LEAVE", sectId, new Result(true, Admission.OK));
    }

    static String reasonOf(IllegalArgumentException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() || message.contains(" ") ? "refused" : message;
    }

    /** Logs {@code SECT_ENTRY player=<name> intent=<intent> sect=<id> result=ok|<reason>} and returns the result. */
    static Result logged(String player, String intent, int sectId, Result result) {
        LOGGER.info("SECT_ENTRY player={} intent={} sect={} result={}", player, intent, sectId,
                result.ok() ? Admission.OK : result.reason());
        return result;
    }

    static void markDirty(MinecraftServer server) {
        if (server != null) {
            WorldSimSavedData.get(server.overworld()).setDirty();
        }
    }
}
