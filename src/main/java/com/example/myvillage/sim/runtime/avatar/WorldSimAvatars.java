package com.example.myvillage.sim.runtime.avatar;

import com.example.myvillage.entity.ModEntities;
import com.example.myvillage.entity.npc.CultivatorEntity;
import com.example.myvillage.entity.npc.CultivatorLooks;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.portrait.NpcColours;
import com.example.myvillage.sect.SectCourtyard;
import com.example.myvillage.sim.PersonView;
import com.example.myvillage.sim.SectView;
import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimServerConfig;
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
 *       ({@link AvatarPlanner#select}: the steward ({@code WorldSim.stewardOf}), master, elders, then by
 *       realm, up to the per-sect cap and the global cap, nearest compound first) get an avatar on a
 *       free cell whose chunk is loaded (the steward on {@link AvatarPlanner#stewardCell}, by the gate
 *       opening); once
 *       no player is within the radius + {@value WorldSimServerConfig#AVATAR_WITHDRAW_MARGIN}, the gate's
 *       avatars are discarded. Between the two radii nothing is spawned or withdrawn.</li>
 *   <li>Each pass and after every settled sim day, avatars of people no longer selected (dead, left,
 *       travelling, secluded, displaced by the cap) are discarded and names and dialogue roles
 *       ({@link NpcEntity#ledgerRole()}: steward, elder for elders and the master, none) are
 *       refreshed; a steward handover discards both avatars so they respawn on the right cells.</li>
 *   <li>An avatar wears its person's portrait hair and eye colours ({@link AvatarColours}:
 *       {@code PortraitAssign.of(person, sim.day(), WorldSimRuntime.daysPerYear())}, synced through
 *       {@link NpcEntity#setColours}), set at spawn and refreshed on every reconcile so the hair greys
 *       with age as the portrait does. A person whose portrait cannot be derived keeps the look's
 *       baked colours; the failure is logged once per server run.</li>
 *   <li>Everything is discarded when the ledger is inactive, avatars are disabled, and on server stop.</li>
 * </ul>
 */
public final class WorldSimAvatars {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldSimAvatars.class);
    static final int PASS_TICKS = 20;
    static final String NAME_KEY = "entity.myvillage.cultivator.avatar";
    /** The steward's name tag: the same three params, with a fixed fourth part "守山执事" in the language file. */
    static final String STEWARD_NAME_KEY = "entity.myvillage.cultivator.avatar.steward";

    /** {@code role} is the dialogue role last set on the entity ({@link NpcEntity#ledgerRole()}). */
    private record Avatar(int personId, int sectId, CultivatorEntity entity, BlockPos cell, String role) {
    }

    /**
     * Who of a sect is shown, in priority order, and the sect's steward (-1 for none); {@code day} and
     * {@code daysPerYear} date the portrait colours.
     */
    private record Selection(List<PersonView> people, int stewardId, List<String> realmOrder, long day,
                             int daysPerYear) {
    }

    private static final Map<Integer, Avatar> AVATARS = new HashMap<>();
    /** Courtyard cells per gate record (recomputed when the record changes). */
    private static final Map<GateRealizations.Gate, List<BlockPos>> CELLS = new HashMap<>();
    private static int ticks;
    /** Whether a portrait-colour failure has been logged this server run. */
    private static boolean coloursFailureLogged;

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
        coloursFailureLogged = false;
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
        if (active.isEmpty() || !WorldSimServerConfig.avatarsEnabled()) {
            discardAll(active.isEmpty() ? "the ledger is inactive" : "avatars are disabled");
            return;
        }
        WorldSim sim = active.get();
        ServerLevel level = server.overworld();
        int spawnRadius = WorldSimServerConfig.avatarSpawnRadius();
        int withdrawRadius = WorldSimServerConfig.avatarWithdrawRadius();
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

    /** The members of the sect that should have an avatar now, the steward first, then in priority order. */
    private static Selection selected(WorldSim sim, int sectId) {
        List<String> realmOrder = sim.realmIds();
        int stewardId = sim.stewardOf(sectId).map(PersonView::id).orElse(-1);
        return new Selection(AvatarPlanner.select(sim.membersAt(sectId), realmOrder,
                WorldSimServerConfig.maxAvatarsPerSect(), stewardId), stewardId, List.copyOf(realmOrder), sim.day(),
                WorldSimRuntime.daysPerYear());
    }

    /** The dialogue role of a selected person: the steward, an elder (the master included), or none. */
    static String role(PersonView p, int stewardId) {
        if (stewardId >= 0 && p.id() == stewardId) {
            return NpcEntity.ROLE_STEWARD;
        }
        return switch (p.rank()) {
            case "elder", "sect_master" -> NpcEntity.ROLE_ELDER;
            default -> NpcEntity.ROLE_NONE;
        };
    }

    /**
     * Discards the avatars of people no longer selected; refreshes the names, roles, looks (the
     * look follows the realm, {@link CultivatorLooks#forPerson}) and colours ({@link AvatarColours}) of
     * the rest.
     * An avatar that becomes or stops being the steward is discarded too, so that it is spawned again
     * on the steward's cell (or off it) by the next pass.
     */
    private static void reconcile(int sectId, Selection selection) {
        Map<Integer, PersonView> byId = new HashMap<>();
        for (PersonView p : selection.people()) {
            byId.put(p.id(), p);
        }
        Iterator<Map.Entry<Integer, Avatar>> it = AVATARS.entrySet().iterator();
        int discarded = 0;
        while (it.hasNext()) {
            Map.Entry<Integer, Avatar> entry = it.next();
            Avatar a = entry.getValue();
            if (a.sectId() != sectId) {
                continue;
            }
            PersonView p = byId.get(a.personId());
            String role = p == null ? NpcEntity.ROLE_NONE : role(p, selection.stewardId());
            boolean stewardChanged = NpcEntity.ROLE_STEWARD.equals(role) != NpcEntity.ROLE_STEWARD.equals(a.role());
            if (p == null || stewardChanged) {
                a.entity().discard();
                it.remove();
                discarded++;
                continue;
            }
            if (!role.equals(a.role())) {
                a.entity().setLedgerRole(role);
                entry.setValue(new Avatar(a.personId(), a.sectId(), a.entity(), a.cell(), role));
            }
            Component name = name(p, role);
            if (!Objects.equals(a.entity().getCustomName(), name)) {
                a.entity().setCustomName(name);
            }
            String look = CultivatorLooks.forPerson(p, selection.realmOrder());
            if (!look.equals(a.entity().look())) {
                a.entity().setLook(look);
            }
            applyColours(a.entity(), p, selection);
        }
        if (discarded > 0) {
            LOGGER.info("World sim avatars: sect {} withdrew {} avatar(s) no longer at the gate or whose steward role changed; {} remain",
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
        Selection selection = selected(sim, sectId);
        int axisX = AvatarPlanner.axisX(gate.anchor());
        for (PersonView p : selection.people()) {
            if (AVATARS.size() >= WorldSimServerConfig.maxAvatars()) {
                break;
            }
            if (AVATARS.containsKey(p.id())) {
                continue;
            }
            String role = role(p, selection.stewardId());
            BlockPos cell = NpcEntity.ROLE_STEWARD.equals(role)
                    ? AvatarPlanner.stewardCell(p.id(), cells, occupied, axisX)
                    : AvatarPlanner.pickCell(p.id(), cells, occupied);
            if (cell == null) {
                break;
            }
            if (!level.isLoaded(cell) || !level.areEntitiesLoaded(ChunkPos.asLong(cell))) {
                continue; // spawned on a later pass, once a player has loaded that part of the compound
            }
            if (spawn(level, p, sectId, cell, role, selection)) {
                occupied.add(cell);
                spawned++;
            }
        }
        if (spawned > 0) {
            LOGGER.info("World sim avatars: sect {} spawned {} avatar(s); {} present", sectId, spawned, count(sectId));
        }
    }

    private static boolean spawn(ServerLevel level, PersonView p, int sectId, BlockPos cell, String role,
                                 Selection selection) {
        CultivatorEntity entity = ModEntities.CULTIVATOR.get().create(level);
        if (entity == null) {
            return false;
        }
        entity.becomeLedgerAvatar(p.id());
        float yaw = AvatarPlanner.yaw(p.id());
        entity.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, yaw, 0.0F);
        entity.setYHeadRot(yaw);
        entity.setYBodyRot(yaw);
        entity.setLedgerRole(role);
        entity.setLook(CultivatorLooks.forPerson(p, selection.realmOrder()));
        applyColours(entity, p, selection);
        entity.setCustomName(name(p, role));
        entity.setCustomNameVisible(true);
        Avatar avatar = new Avatar(p.id(), sectId, entity, cell, role);
        AVATARS.put(p.id(), avatar); // before adding, so the join check knows it
        if (!level.addFreshEntity(entity)) {
            AVATARS.remove(p.id());
            return false;
        }
        return true;
    }

    /**
     * Sets the person's portrait colours on the avatar when they differ from what it wears. When the
     * portrait cannot be derived the avatar stays as it is (baked colours at spawn) and the failure is
     * logged once per server run.
     */
    private static void applyColours(NpcEntity entity, PersonView p, Selection selection) {
        NpcColours wanted;
        try {
            wanted = AvatarColours.of(p, selection.day(), selection.daysPerYear());
        } catch (RuntimeException ex) {
            if (!coloursFailureLogged) {
                coloursFailureLogged = true;
                LOGGER.warn("World sim avatars: no portrait colours for person {} (day {}, {} days a year); "
                        + "keeping the baked colours (logged once)", p.id(), selection.day(), selection.daysPerYear(), ex);
            }
            return;
        }
        NpcColours change = AvatarColours.change(entity.colours(), wanted);
        if (change != null) {
            entity.setColours(change);
        }
    }

    /** {@code name · realm · sect}; the realm through its language key, so each client reads its own language. */
    static Component name(PersonView p) {
        return name(p, NpcEntity.ROLE_NONE);
    }

    /** As {@link #name(PersonView)}; the steward's tag adds "守山执事" through {@link #STEWARD_NAME_KEY}. */
    static Component name(PersonView p, String role) {
        String key = NpcEntity.ROLE_STEWARD.equals(role) ? STEWARD_NAME_KEY : NAME_KEY;
        return Component.translatable(key, p.name(), WorldSimText.realm(p.realmId()), p.sectName());
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
