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
import com.example.myvillage.sim.life.LifeResidentView;
import com.example.myvillage.sim.life.LifeRules;
import com.example.myvillage.sim.life.LifeSite;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.sim.runtime.WorldSimServerConfig;
import com.example.myvillage.sim.runtime.WorldSimText;
import com.example.myvillage.sim.runtime.life.NpcLifeInteraction;
import com.example.myvillage.sim.runtime.life.NpcLifeSites;
import com.example.myvillage.stats.runtime.StatsRuntime;
import com.example.myvillage.wilderness.WildernessRules;
import com.example.myvillage.wilderness.runtime.WildernessRuntime;
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
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
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
    private record Avatar(int personId, int sectId, CultivatorEntity entity, BlockPos cell, String role,
                          String wildRegion, String lifeSite, long lifeEpoch) {
        Avatar(int personId, int sectId, CultivatorEntity entity, BlockPos cell, String role, String wildRegion) {
            this(personId, sectId, entity, cell, role, wildRegion, null, -1);
        }
        boolean wild() { return wildRegion != null; }
        boolean life() { return lifeSite != null; }
        boolean legacySect() { return !wild() && !life(); }
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
    /** Presentation-only retry progress. Neither identities nor already occupied cells depend on it. */
    private static final Map<String, Integer> WILD_RETRY_CURSORS = new HashMap<>();
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
        WILD_RETRY_CURSORS.clear();
        NpcLifeInteraction.clear();
        ticks = 0;
        coloursFailureLogged = false;
    }

    static void onServerStopping(ServerStoppingEvent event) {
        discardAll("the server is stopping");
        CELLS.clear();
        WILD_RETRY_CURSORS.clear();
        NpcLifeInteraction.clear();
    }

    static void onServerTick(ServerTickEvent.Post event) {
        ticks++;
        if (ticks % PASS_TICKS != 0) {
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
            if (a.legacySect()) sects.add(a.sectId());
        }
        for (int sectId : sects) {
            Optional<SectView> sect = sim.sect(sectId);
            if (sect.isEmpty() || !sect.get().state().equals("active") || !sect.get().gateRealized()) {
                withdraw(sectId, "the sect or its gate changed");
                continue;
            }
            reconcile(sectId, selected(sim, sectId));
        }
        reconcileWild(server.overworld(), sim);
        reconcileLife(server, sim);
    }

    /** The build command changed this sect's gate: its avatars and cached cells are stale. */
    static void gateChanged(int sectId) {
        withdraw(sectId, "its gate was rebuilt");
        CELLS.keySet().removeIf(g -> g.sectId() == sectId);
    }

    // ------------------------------------------------------------------ the pass

    private static void pass(MinecraftServer server) {
        Optional<WorldSim> active = WorldSimRuntime.sim();
        if (active.isEmpty() || !WorldSimServerConfig.avatarsEnabled()) {
            discardAll(active.isEmpty() ? "the ledger is inactive" : "avatars are disabled");
            return;
        }
        WorldSim sim = active.get();
        Iterator<Avatar> removed = AVATARS.values().iterator();
        while (removed.hasNext()) {
            Avatar a = removed.next();
            if (!a.life() && a.entity().isRemoved()) {
                removed.remove();
            }
        }
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
        if (ticks % WildernessRules.bundled().rogues().intervalTicks() == 0) {
            reconcileWild(level, sim);
            ensureWild(level, sim);
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
        List<PersonView> legacy = sim.membersAt(sectId).stream().filter(p -> sim.life(p.id()).isEmpty()).toList();
        return new Selection(AvatarPlanner.select(legacy, realmOrder,
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
            if (!a.legacySect() || a.sectId() != sectId) {
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
                entry.setValue(new Avatar(a.personId(), a.sectId(), a.entity(), a.cell(), role, null));
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
            StatsRuntime.applyRealm(a.entity(), p.realmId(), p.stage());
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
            if (a.legacySect() && a.sectId() == sectId) {
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
            if (spawn(level, p, sectId, cell, role, selection, null)) {
                occupied.add(cell);
                spawned++;
            }
        }
        if (spawned > 0) {
            LOGGER.info("World sim avatars: sect {} spawned {} avatar(s); {} present", sectId, spawned, count(sectId));
        }
    }

    private static boolean spawn(ServerLevel level, PersonView p, int sectId, BlockPos cell, String role,
                                 Selection selection, String wildRegion) {
        return spawn(level, p, sectId, cell, role, selection, wildRegion, null, -1);
    }

    /** All three projection sources use this one spawner and the same identity and capacity map. */
    private static boolean spawn(ServerLevel level, PersonView p, int sectId, BlockPos cell, String role,
                                 Selection selection, String wildRegion, String lifeSite, long lifeEpoch) {
        if (AVATARS.containsKey(p.id()) || AVATARS.size() >= WorldSimServerConfig.maxAvatars()) return false;
        CultivatorEntity entity = ModEntities.CULTIVATOR.get().create(level);
        if (entity == null) {
            return false;
        }
        entity.becomeLedgerAvatar(p.id());
        if (wildRegion != null) entity.addTag("myvillage:wild_avatar"); // diagnostic only; AVATARS owns identity and caps
        float yaw = AvatarPlanner.yaw(p.id());
        entity.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, yaw, 0.0F);
        entity.setYHeadRot(yaw);
        entity.setYBodyRot(yaw);
        if ((wildRegion != null || lifeSite != null)
                && (!loadedArea(level, entity.getBoundingBox())
                    || !level.getWorldBorder().isWithinBounds(entity.getBoundingBox()) || !level.noCollision(entity))) return false;
        entity.setLedgerRole(role);
        entity.setLook(CultivatorLooks.forPerson(p, selection.realmOrder()));
        applyColours(entity, p, selection);
        entity.setCustomName(name(p, role));
        entity.setCustomNameVisible(true);
        StatsRuntime.applyRealm(entity, p.realmId(), p.stage());
        entity.setHealth(entity.getMaxHealth());
        if (lifeSite != null) {
            entity.enableLedgerLife(lifeEpoch);
            entity.addTag("myvillage:life_avatar"); // diagnostic only; the shared map still owns identity
        }
        Avatar avatar = new Avatar(p.id(), sectId, entity, cell, role, wildRegion, lifeSite, lifeEpoch);
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
        return Component.translatable(key, p.name(), WorldSimText.realm(p.realmId()),
                p.sectId() == -1 && p.rank().equals("rogue") ? WorldSimText.rank("rogue") : p.sectName());
    }

    // ------------------------------------------------------------------ persistent residents

    /** The current executor token only when this exact entity belongs to the shared identity map. */
    public static long lifeEpoch(NpcEntity npc) {
        Avatar a = AVATARS.get(npc.ledgerPersonId());
        return a != null && a.life() && a.entity() == npc ? a.lifeEpoch() : -1;
    }

    public static boolean isManagedLife(NpcEntity npc, long epoch) {
        return epoch >= 0 && lifeEpoch(npc) == epoch;
    }

    /** Read-only diagnostic; residents count against the same global cap as every other avatar. */
    public static int lifeCount(String siteId) {
        int count = 0;
        for (Avatar a : AVATARS.values()) {
            if (a.life() && (siteId == null || siteId.equals(a.lifeSite()))) count++;
        }
        return count;
    }

    /**
     * Called by WorldSimRuntime before advancing life, after checking loaded facilities. Presence
     * is observed even when a cap, obstruction or disabled avatars prevents a body from spawning:
     * a resident near a player must not fall through to invisible cold work. The two radii provide
     * the same hysteresis for an unembodied resident as for an existing body.
     */
    public static void prepareLife(MinecraftServer server, WorldSim sim) {
        reconcileLife(server, sim);
        LifeRules rules = sim.lifeRules();
        for (LifeResidentView life : sim.lifeResidents()) {
            LifeSite site = sim.lifeSite(life.siteId()).orElse(null);
            ServerLevel level = site == null ? null : NpcLifeSites.level(server, site);
            double radius = rules.spawnRadius() + (life.observed() ? rules.withdrawMargin() : 0);
            boolean observed = level != null
                    && nearestLifePlayer(level, life.x() + .5, life.y(), life.z() + .5) <= radius;
            sim.setLifeObserved(life.personId(), observed);
        }
        if (!WorldSimServerConfig.avatarsEnabled()) {
            discardAll("avatars are disabled");
            return;
        }
        ensureLife(server, sim);
        for (Avatar avatar : AVATARS.values()) {
            if (avatar.life()) avatar.entity().validateLedgerLifeWorkplace();
        }
    }

    /** Called before WorldSimRuntime drops its facade on server stop. Does not report anybody dead. */
    public static void flushLife() {
        WorldSim sim = WorldSimRuntime.sim().orElse(null);
        if (sim == null) return;
        for (Avatar a : AVATARS.values()) {
            if (a.life()) {
                a.entity().getNavigation().stop();
                releaseLife(sim, a);
            }
        }
    }

    private static void reconcileLife(MinecraftServer server, WorldSim sim) {
        LifeRules rules = sim.lifeRules();
        Selection presentation = new Selection(List.of(), -1, sim.realmIds(), sim.day(), WorldSimRuntime.daysPerYear());
        Iterator<Map.Entry<Integer, Avatar>> it = AVATARS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Avatar> entry = it.next();
            Avatar a = entry.getValue();
            LifeResidentView life = sim.life(a.personId()).orElse(null);
            if (!a.life()) {
                // An enrolled resident has one physical location and never falls back to gate/wild sampling.
                if (life != null) {
                    a.entity().discard();
                    it.remove();
                }
                continue;
            }
            LifeSite site = life == null ? null : sim.lifeSite(life.siteId()).orElse(null);
            PersonView person = sim.person(a.personId()).filter(PersonView::alive).orElse(null);
            ServerLevel level = site == null ? null : NpcLifeSites.level(server, site);
            if (life == null || person == null || site == null || level == null
                    || !life.embodied() || life.epoch() != a.lifeEpoch() || !life.siteId().equals(a.lifeSite())
                    || a.entity().isRemoved() || !a.entity().isAlive() || a.entity().level() != level
                    || !loadedArea(level, a.entity().getBoundingBox())
                    || nearestLifePlayer(level, a.entity().getX(), a.entity().getY(), a.entity().getZ())
                        > rules.spawnRadius() + rules.withdrawMargin()) {
                releaseLife(sim, a);
                a.entity().discard();
                it.remove();
                continue;
            }
            BlockPos current = a.entity().blockPosition();
            if (sim.checkpointLife(a.personId(), a.lifeEpoch(), current.getX(), current.getY(), current.getZ())) {
                markLifeDirty(server);
            }
            String role = role(person, -1); // a town resident is not the gate's stationed steward
            a.entity().setLedgerRole(role);
            if (!role.equals(a.role()) || person.sectId() != a.sectId()) {
                entry.setValue(new Avatar(a.personId(), person.sectId(), a.entity(), current, role,
                        null, a.lifeSite(), a.lifeEpoch()));
            }
            refreshLifePresentation(a.entity(), person, life, role, presentation);
        }
    }

    private record LifeCandidate(LifeResidentView resident, ServerLevel level, double distance) { }

    private static void ensureLife(MinecraftServer server, WorldSim sim) {
        LifeRules rules = sim.lifeRules();
        List<LifeCandidate> candidates = new ArrayList<>();
        for (LifeResidentView life : sim.lifeResidents()) {
            if (AVATARS.containsKey(life.personId())) continue;
            LifeSite site = sim.lifeSite(life.siteId()).orElse(null);
            ServerLevel level = site == null ? null : NpcLifeSites.level(server, site);
            if (level == null) continue;
            double distance = nearestLifePlayer(level, life.x() + .5, life.y(), life.z() + .5);
            if (distance <= rules.spawnRadius()) candidates.add(new LifeCandidate(life, level, distance));
        }
        candidates.sort(Comparator.comparingDouble(LifeCandidate::distance)
                .thenComparingInt(c -> c.resident().personId()));
        Selection presentation = new Selection(List.of(), -1, sim.realmIds(), sim.day(), WorldSimRuntime.daysPerYear());
        for (LifeCandidate candidate : candidates) {
            if (AVATARS.size() >= WorldSimServerConfig.maxAvatars()) break;
            LifeResidentView life = candidate.resident();
            if (lifeCount(life.siteId()) >= rules.maxResidentsPerSite()) continue;
            PersonView person = sim.person(life.personId()).filter(PersonView::alive).orElse(null);
            if (person == null || AVATARS.containsKey(person.id())) continue;
            BlockPos cell = new BlockPos(life.x(), life.y(), life.z());
            if (!lifeGround(candidate.level(), cell)) continue;

            // A surviving token without a managed entity can only be an interrupted projection.
            // Release it before issuing the replacement executor; never create a second map entry.
            if (life.embodied()) sim.releaseLife(life.personId(), life.epoch(), life.x(), life.y(), life.z());
            long epoch = sim.activateLife(life.personId());
            if (epoch < 0) continue;
            markLifeDirty(server);
            String role = role(person, -1);
            boolean spawned = false;
            try {
                spawned = spawn(candidate.level(), person, person.sectId(), cell, role, presentation,
                        null, life.siteId(), epoch);
                if (spawned) {
                    LifeResidentView active = sim.life(person.id()).orElse(life);
                    refreshLifePresentation(AVATARS.get(person.id()).entity(), person, active, role, presentation);
                    LOGGER.info("NPC_LIFE_AVATAR action=spawn person={} site={} epoch={} x={} y={} z={}",
                            person.id(), life.siteId(), epoch, cell.getX(), cell.getY(), cell.getZ());
                }
            } finally {
                if (!spawned && sim.releaseLife(life.personId(), epoch, cell.getX(), cell.getY(), cell.getZ())) {
                    markLifeDirty(server);
                }
            }
        }
    }

    private static boolean lifeGround(ServerLevel level, BlockPos cell) {
        if (cell.getY() <= level.getMinBuildHeight() || cell.getY() + 2 >= level.getMaxBuildHeight()
                || !loadedArea(level, new AABB(cell).expandTowards(0, 2, 0))) return false;
        return level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP)
                && level.getFluidState(cell.below()).isEmpty() && level.getFluidState(cell).isEmpty()
                && level.getFluidState(cell.above()).isEmpty();
    }

    private static double nearestLifePlayer(ServerLevel level, double x, double y, double z) {
        double distance = Double.POSITIVE_INFINITY;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.isAlive()) {
                distance = Math.min(distance, Math.sqrt(player.distanceToSqr(x, y, z)));
            }
        }
        return distance;
    }

    private static void refreshLifePresentation(CultivatorEntity entity, PersonView person, LifeResidentView life,
                                                String role, Selection presentation) {
        Component name = name(person, role).copy().append(" · ").append(NpcLifeInteraction.stateText(life));
        if (!Objects.equals(entity.getCustomName(), name)) {
            entity.setCustomName(name);
            LOGGER.info("NPC_LIFE_AVATAR action=state person={} activity={} phase={} revision={} reason={}",
                    person.id(), life.activity(), life.phase(), life.revision(), life.reason());
        }
        String look = CultivatorLooks.forPerson(person, presentation.realmOrder());
        if (!look.equals(entity.look())) entity.setLook(look);
        applyColours(entity, person, presentation);
        StatsRuntime.applyRealm(entity, person.realmId(), person.stage());
    }

    private static void releaseLife(WorldSim sim, Avatar avatar) {
        if (!avatar.life()) return;
        BlockPos position = avatar.entity().blockPosition();
        if (sim.releaseLife(avatar.personId(), avatar.lifeEpoch(), position.getX(), position.getY(), position.getZ())) {
            MinecraftServer server = avatar.entity().level().getServer();
            if (server != null) markLifeDirty(server);
            LOGGER.info("NPC_LIFE_AVATAR action=release person={} site={} epoch={} x={} y={} z={}",
                    avatar.personId(), avatar.lifeSite(), avatar.lifeEpoch(), position.getX(), position.getY(), position.getZ());
        }
    }

    private static void markLifeDirty(MinecraftServer server) {
        WorldSimSavedData.get(server.overworld()).setDirty();
    }

    // ------------------------------------------------------------------ wilderness

    /** Read-only diagnostics; the same global person map owns sect and rogue projections. */
    public static int totalCount() { return AVATARS.size(); }

    public static int wildCount(String region) {
        int n = 0;
        for (Avatar a : AVATARS.values()) if (a.wild() && (region == null || region.equals(a.wildRegion()))) n++;
        return n;
    }

    /** Check every chunk collision code can inspect, including its one-block border, without requesting it. */
    public static boolean loadedArea(ServerLevel level, AABB box) {
        AABB probe = box.inflate(1.0);
        int minX = Math.floorDiv((int) Math.floor(probe.minX), 16), maxX = Math.floorDiv((int) Math.floor(probe.maxX), 16);
        int minZ = Math.floorDiv((int) Math.floor(probe.minZ), 16), maxZ = Math.floorDiv((int) Math.floor(probe.maxZ), 16);
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (level.getChunkSource().getChunkNow(x, z) == null || !level.areEntitiesLoaded(ChunkPos.asLong(x, z))) return false;
        }
        return true;
    }

    private static double nearestWildPlayer(ServerLevel level, BlockPos cell) {
        double distance = Double.POSITIVE_INFINITY;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            double dx = player.getX() - cell.getX() - .5, dz = player.getZ() - cell.getZ() - .5;
            distance = Math.min(distance, Math.hypot(dx, dz));
        }
        return distance;
    }

    /** Day settlement and wilderness passes both withdraw changes; no sect cleanup touches this branch. */
    private static void reconcileWild(ServerLevel level, WorldSim sim) {
        WildernessRules rules = WildernessRules.bundled();
        Selection presentation = new Selection(List.of(), -1, sim.realmIds(), sim.day(), WorldSimRuntime.daysPerYear());
        Iterator<Avatar> it = AVATARS.values().iterator();
        while (it.hasNext()) {
            Avatar a = it.next();
            if (!a.wild()) continue;
            PersonView person = sim.person(a.personId()).orElse(null);
            Optional<WildernessRules.Region> region = rules.region(a.wildRegion());
            boolean eligible = person != null && sim.life(a.personId()).isEmpty() && region.isPresent()
                    && WildAvatarPlanner.eligible(person, a.wildRegion(), region.get().rogue());
            BlockPos cell = a.entity().blockPosition();
            if (!eligible || a.entity().isRemoved() || !a.entity().level().dimension().equals(Level.OVERWORLD)
                    || !WildernessRuntime.regionAt(level, cell).filter(a.wildRegion()::equals).isPresent()
                    || !loadedArea(level, a.entity().getBoundingBox())
                    || nearestWildPlayer(level, cell) > rules.rogues().withdrawRadius()) {
                a.entity().discard(); it.remove(); continue;
            }
            a.entity().setLedgerRole(NpcEntity.ROLE_NONE);
            a.entity().setCustomName(name(person));
            a.entity().setLook(CultivatorLooks.forPerson(person, presentation.realmOrder()));
            applyColours(a.entity(), person, presentation);
            StatsRuntime.applyRealm(a.entity(), person.realmId(), person.stage());
        }
    }

    private static void ensureWild(ServerLevel level, WorldSim sim) {
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        WildernessRules rules = WildernessRules.bundled();
        WildernessRules.Rogues settings = rules.rogues();
        int attempts = settings.attemptsPerPass();
        Selection presentation = new Selection(List.of(), -1, sim.realmIds(), sim.day(), WorldSimRuntime.daysPerYear());
        List<ServerPlayer> players = new ArrayList<>(level.players());
        players.removeIf(ServerPlayer::isSpectator);
        players.sort(Comparator.comparing(p -> p.getUUID().toString()));
        Map<String, List<PersonView>> candidates = new HashMap<>();
        Map<String, Integer> passCursors = new HashMap<>();
        for (ServerPlayer player : players) {
            Optional<String> at = WildernessRuntime.regionAt(level, player.blockPosition());
            if (at.isEmpty()) continue;
            String region = at.get();
            List<PersonView> eligible = candidates.computeIfAbsent(region, id -> WildAvatarPlanner.candidates(
                    sim.livingIn(id).stream().filter(p -> sim.life(p.id()).isEmpty()).toList(),
                    id, rules.region(id).orElseThrow().rogue(), sim.seed()));
            // Advance once per region per pass, even when all its attempted columns are obstructed.
            // Several nearby players use the same starting cursor and the shared occupied-person map.
            int cursor = passCursors.computeIfAbsent(region, id -> {
                int current = WILD_RETRY_CURSORS.getOrDefault(id, 0);
                WILD_RETRY_CURSORS.put(id, eligible.isEmpty() ? 0 : (current + 1) % eligible.size());
                return current;
            });
            List<PersonView> selected = WildAvatarPlanner.select(eligible, AVATARS.keySet(), wildCount(region),
                    wildCount(null), AVATARS.size(), settings.maxPerRegion(), settings.maxTotal(), WorldSimServerConfig.maxAvatars(), cursor);
            int anchorX = Math.floorDiv(player.blockPosition().getX(), 16) * 16 + 8;
            int anchorZ = Math.floorDiv(player.blockPosition().getZ(), 16) * 16 + 8;
            for (PersonView person : selected) {
                for (WildAvatarPlanner.Column column : WildAvatarPlanner.columns(sim.seed(), person.id(), anchorX, anchorZ, settings)) {
                    if (attempts-- <= 0) return;
                    double distance = Math.hypot(column.x() + .5 - player.getX(), column.z() + .5 - player.getZ());
                    if (distance < settings.minDistance() || distance > settings.spawnRadius()) continue;
                    BlockPos cell = wildGround(level, column, region);
                    if (cell != null && spawn(level, person, -1, cell, NpcEntity.ROLE_NONE, presentation, region)) {
                        LOGGER.info("World sim avatars: projected wilderness rogue {} in {} at {}", person.id(), region, cell);
                        break;
                    }
                }
            }
        }
    }

    /** Surface lookup only after every possibly inspected chunk is loaded; solid floor, dry free body. */
    private static BlockPos wildGround(ServerLevel level, WildAvatarPlanner.Column column, String region) {
        BlockPos horizontal = new BlockPos(column.x(), 0, column.z());
        if (!WildernessRuntime.regionAt(level, horizontal).filter(region::equals).isPresent()
                || !loadedArea(level, new AABB(column.x(), 0, column.z(), column.x() + 1, 2, column.z() + 1))) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.x(), column.z());
        if (y <= level.getMinBuildHeight() || y + 2 >= level.getMaxBuildHeight()) return null;
        BlockPos cell = new BlockPos(column.x(), y, column.z());
        if (!level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP)
                || !level.getFluidState(cell.below()).isEmpty() || !level.getFluidState(cell).isEmpty()
                || !level.getFluidState(cell.above()).isEmpty()) return null;
        AABB body = new AABB(column.x() + .2, y, column.z() + .2, column.x() + .8, y + 1.9, column.z() + .8);
        return level.getWorldBorder().isWithinBounds(body) && level.noCollision(body) ? cell : null;
    }

    // ------------------------------------------------------------------ withdrawal

    private static void withdraw(int sectId, String why) {
        int n = 0;
        Iterator<Avatar> it = AVATARS.values().iterator();
        while (it.hasNext()) {
            Avatar a = it.next();
            if (a.legacySect() && a.sectId() == sectId) {
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
        WorldSim sim = WorldSimRuntime.sim().orElse(null);
        for (Avatar a : AVATARS.values()) {
            if (sim != null) releaseLife(sim, a);
            a.entity().discard();
        }
        AVATARS.clear();
        LOGGER.info("World sim avatars: withdrew all {} avatar(s): {}", n, why);
    }

    private static Set<Integer> sectsWithAvatars() {
        Set<Integer> out = new HashSet<>();
        for (Avatar a : AVATARS.values()) {
            if (a.legacySect()) out.add(a.sectId());
        }
        return out;
    }

    private static int count(int sectId) {
        int n = 0;
        for (Avatar a : AVATARS.values()) {
            if (a.legacySect() && a.sectId() == sectId) {
                n++;
            }
        }
        return n;
    }
}
