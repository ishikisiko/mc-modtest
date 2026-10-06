package com.example.myvillage.sim.runtime.avatar;

import com.example.myvillage.entity.ModEntities;
import com.example.myvillage.entity.npc.CultivatorEntity;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.sect.SectCourtyard;
import com.example.myvillage.sim.PersonView;
import com.example.myvillage.sim.SectView;
import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimText;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ledger avatars (化身): while a player is near a realized compound, the ledger members who are at
 * that sect appear as {@link CultivatorEntity} avatars standing on its courtyard ground
 * ({@link SectCourtyard#cells}), named {@code name · realm · sect}. The ledger is the authority and
 * this class only projects it:
 *
 * <ul>
 *   <li>An avatar exists only while this manager holds it; it is never saved with its chunk
 *       ({@link NpcEntity#shouldBeSaved()}), and a managed cultivator this manager does not know is
 *       refused when it joins a level.</li>
 *   <li>One entity per person id. The manager keeps the entity itself, not only its UUID, so it can
 *       discard an avatar even in a chunk that has been unloaded meanwhile (such an entity stays in
 *       memory, hidden, because it is not saved).</li>
 *   <li>Every {@value #PASS_TICKS} ticks, for each gate the ledger agrees is realized (sect active,
 *       gate realized, same x/z as the {@link GateRealizations} record): if a player is within
 *       {@code avatar_spawn_radius} of the compound's site, the selected members
 *       ({@link AvatarPlanner#select}: master, elders, then by realm, up to the per-sect cap and the
 *       global cap, nearest compound first) get an avatar on a free cell whose chunk is loaded; once
 *       no player is within the radius + {@value WorldSimAvatarConfig#WITHDRAW_MARGIN}, the gate's
 *       avatars are discarded. Between the two radii nothing is spawned or withdrawn.</li>
 *   <li>Each pass and after every settled sim day, avatars of people no longer selected (dead, left,
 *       travelling, secluded, displaced by the cap) are discarded and names are refreshed.</li>
 *   <li>Everything is discarded when the ledger is inactive, avatars are disabled, and on server stop.</li>
 * </ul>
 */
public final class WorldSimAvatars {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldSimAvatars.class);
    static final int PASS_TICKS = 20;
    static final String NAME_KEY = "entity.myvillage.cultivator.avatar";

    private record Avatar(int personId, int sectId, CultivatorEntity entity, BlockPos cell) {
    }

    private static final Map<Integer, Avatar> AVATARS = new HashMap<>();
    /** Courtyard cells per gate record (recomputed when the record changes). */
    private static final Map<GateRealizations.Gate, List<BlockPos>> CELLS = new HashMap<>();
    private static int ticks;

    private WorldSimAvatars() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(WorldSimAvatars::onServerStarted);
        NeoForge.EVENT_BUS.addListener(WorldSimAvatars::onServerTick);
        NeoForge.EVENT_BUS.addListener(WorldSimAvatars::onServerStopping);
        NeoForge.EVENT_BUS.addListener(WorldSimAvatars::onEntityJoin);
        WorldSimRuntime.addListener(WorldSimAvatars::onDaySettled);
    }

    // ------------------------------------------------------------------ events

    static void onServerStarted(ServerStartedEvent event) {
        AVATARS.clear();
        CELLS.clear();
        ticks = 0;
    }

    static void onServerStopping(ServerStoppingEvent event) {
        discardAll("the server is stopping");
        CELLS.clear();
    }

    static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks % PASS_TICKS != 0) {
            return;
        }
        try {
            pass(event.getServer());
        } catch (RuntimeException ex) {
            LOGGER.error("World sim avatars: pass failed; withdrawing every avatar", ex);
            discardAll("a pass failed");
        }
    }

    /** Refuses a managed cultivator this manager did not spawn (e.g. a copy that escaped). */
    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof NpcEntity npc) || !npc.isLedgerAvatar()) {
            return;
        }
        Avatar known = AVATARS.get(npc.ledgerPersonId());
        if (known == null || known.entity() != npc) {
            LOGGER.warn("World sim avatars: refused an unmanaged avatar of person {} at {}", npc.ledgerPersonId(),
                    npc.blockPosition());
            event.setCanceled(true);
        }
    }

    static void onDaySettled(MinecraftServer server, WorldSim sim, List<SimEvent> events) {
        if (AVATARS.isEmpty()) {
            return;
        }
        Set<Integer> sects = new HashSet<>();
        for (Avatar a : AVATARS.values()) {
            sects.add(a.sectId());
        }
        for (int sectId : sects) {
            Optional<SectView> sect = sim.sect(sectId);
            if (sect.isEmpty() || !sect.get().state().equals("active") || !sect.get().gateRealized()) {
                withdraw(sectId, "the sect or its gate changed");
                continue;
            }
            reconcile(sectId, selected(sim, sectId));
        }
    }

    /** The build command changed this sect's gate: its avatars and cached cells are stale. */
    static void gateChanged(int sectId) {
        withdraw(sectId, "its gate was rebuilt");
        CELLS.keySet().removeIf(g -> g.sectId() == sectId);
    }

    // ------------------------------------------------------------------ the pass

    private static void pass(MinecraftServer server) {
        AVATARS.values().removeIf(a -> a.entity().isRemoved()); // killed or otherwise gone: respawned below
        Optional<WorldSim> active = WorldSimRuntime.sim();
        if (active.isEmpty() || !WorldSimAvatarConfig.enabled()) {
            discardAll(active.isEmpty() ? "the ledger is inactive" : "avatars are disabled");
            return;
        }
        WorldSim sim = active.get();
        ServerLevel level = server.overworld();
        int spawnRadius = WorldSimAvatarConfig.spawnRadius();
        int withdrawRadius = WorldSimAvatarConfig.withdrawRadius();
        List<Near> near = new ArrayList<>();
        Set<Integer> valid = new HashSet<>();
        for (GateRealizations.Gate gate : GateRealizations.get(level).all()) {
            Optional<SectView> sect = sim.sect(gate.sectId());
            if (sect.isEmpty() || !agrees(sect.get(), gate)) {
                continue;
            }
            valid.add(gate.sectId());
            double distance = nearestPlayer(level, SectCourtyard.footprint(gate.anchor()));
            if (distance > withdrawRadius) {
                withdraw(gate.sectId(), "no player within " + withdrawRadius + " blocks");
                continue;
            }
            reconcile(gate.sectId(), selected(sim, gate.sectId()));
            if (distance <= spawnRadius) {
                near.add(new Near(gate, distance));
            }
        }
        for (int sectId : sectsWithAvatars()) {
            if (!valid.contains(sectId)) {
                withdraw(sectId, "the ledger no longer agrees with its gate record");
            }
        }
        near.sort(Comparator.comparingDouble(Near::distance).thenComparingInt(n -> n.gate().sectId()));
        for (Near n : near) {
            ensure(level, sim, n.gate());
        }
    }

    private record Near(GateRealizations.Gate gate, double distance) {
    }

    private static boolean agrees(SectView sect, GateRealizations.Gate gate) {
        return sect.state().equals("active") && sect.gateRealized()
                && sect.gateX() == gate.anchor().getX() && sect.gateZ() == gate.anchor().getZ();
    }

    private static double nearestPlayer(ServerLevel level, SectCourtyard.Footprint site) {
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            best = Math.min(best, site.distanceTo(player.getX(), player.getZ()));
        }
        return best;
    }

    /** The members of the sect that should have an avatar now, in priority order. */
    private static List<PersonView> selected(WorldSim sim, int sectId) {
        List<String> realmOrder = new ArrayList<>();
        WorldSimRuntime.data().ifPresent(d -> d.realms().realms().forEach(r -> realmOrder.add(r.id())));
        return AvatarPlanner.select(sim.membersAt(sectId), realmOrder, WorldSimAvatarConfig.maxPerSect());
    }

    /** Discards the avatars of people no longer selected; refreshes the names of the rest. */
    private static void reconcile(int sectId, List<PersonView> selected) {
        Map<Integer, PersonView> byId = new HashMap<>();
        for (PersonView p : selected) {
            byId.put(p.id(), p);
        }
        Iterator<Avatar> it = AVATARS.values().iterator();
        int discarded = 0;
        while (it.hasNext()) {
            Avatar a = it.next();
            if (a.sectId() != sectId) {
                continue;
            }
            PersonView p = byId.get(a.personId());
            if (p == null) {
                a.entity().discard();
                it.remove();
                discarded++;
                continue;
            }
            Component name = name(p);
            if (!Objects.equals(a.entity().getCustomName(), name)) {
                a.entity().setCustomName(name);
            }
        }
        if (discarded > 0) {
            LOGGER.info("World sim avatars: sect {} withdrew {} avatar(s) no longer at the gate; {} remain",
                    sectId, discarded, count(sectId));
        }
    }

    private static void ensure(ServerLevel level, WorldSim sim, GateRealizations.Gate gate) {
        int sectId = gate.sectId();
        List<BlockPos> cells = CELLS.computeIfAbsent(gate,
                g -> SectCourtyard.cells(g.seed(), g.anchor(), g.variant()));
        List<BlockPos> occupied = new ArrayList<>();
        for (Avatar a : AVATARS.values()) {
            if (a.sectId() == sectId) {
                occupied.add(a.cell());
            }
        }
        int spawned = 0;
        for (PersonView p : selected(sim, sectId)) {
            if (AVATARS.size() >= WorldSimAvatarConfig.maxTotal()) {
                break;
            }
            if (AVATARS.containsKey(p.id())) {
                continue;
            }
            BlockPos cell = AvatarPlanner.pickCell(p.id(), cells, occupied);
            if (cell == null) {
                break;
            }
            if (!level.isLoaded(cell) || !level.areEntitiesLoaded(ChunkPos.asLong(cell))) {
                continue; // spawned on a later pass, once a player has loaded that part of the compound
            }
            if (spawn(level, p, sectId, cell)) {
                occupied.add(cell);
                spawned++;
            }
        }
        if (spawned > 0) {
            LOGGER.info("World sim avatars: sect {} spawned {} avatar(s); {} present", sectId, spawned, count(sectId));
        }
    }

    private static boolean spawn(ServerLevel level, PersonView p, int sectId, BlockPos cell) {
        CultivatorEntity entity = ModEntities.CULTIVATOR.get().create(level);
        if (entity == null) {
            return false;
        }
        entity.becomeLedgerAvatar(p.id());
        float yaw = AvatarPlanner.yaw(p.id());
        entity.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, yaw, 0.0F);
        entity.setYHeadRot(yaw);
        entity.setYBodyRot(yaw);
        entity.setCustomName(name(p));
        entity.setCustomNameVisible(true);
        Avatar avatar = new Avatar(p.id(), sectId, entity, cell);
        AVATARS.put(p.id(), avatar); // before adding, so the join check knows it
        if (!level.addFreshEntity(entity)) {
            AVATARS.remove(p.id());
            return false;
        }
        return true;
    }

    /** {@code name · realm · sect}; the realm through its language key, so each client reads its own language. */
    static Component name(PersonView p) {
        return Component.translatable(NAME_KEY, p.name(), WorldSimText.realm(p.realmId()), p.sectName());
    }

    // ------------------------------------------------------------------ withdrawal

    private static void withdraw(int sectId, String why) {
        int n = 0;
        Iterator<Avatar> it = AVATARS.values().iterator();
        while (it.hasNext()) {
            Avatar a = it.next();
            if (a.sectId() == sectId) {
                a.entity().discard();
                it.remove();
                n++;
            }
        }
        if (n > 0) {
            LOGGER.info("World sim avatars: sect {} withdrew {} avatar(s): {}", sectId, n, why);
        }
    }

    private static void discardAll(String why) {
        if (AVATARS.isEmpty()) {
            return;
        }
        int n = AVATARS.size();
        for (Avatar a : AVATARS.values()) {
            a.entity().discard();
        }
        AVATARS.clear();
        LOGGER.info("World sim avatars: withdrew all {} avatar(s): {}", n, why);
    }

    private static Set<Integer> sectsWithAvatars() {
        Set<Integer> out = new HashSet<>();
        for (Avatar a : AVATARS.values()) {
            out.add(a.sectId());
        }
        return out;
    }

    private static int count(int sectId) {
        int n = 0;
        for (Avatar a : AVATARS.values()) {
            if (a.sectId() == sectId) {
                n++;
            }
        }
        return n;
    }
}
