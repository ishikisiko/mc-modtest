package com.example.myvillage.sim.runtime.player;

import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.sim.Admission;
import com.example.myvillage.sim.PersonView;
import com.example.myvillage.sim.PlayerMemberView;
import com.example.myvillage.sim.SectView;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimText;
import com.example.myvillage.sim.runtime.net.SectDialoguePayload;
import com.example.myvillage.sim.runtime.net.SectIntentPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The server side of the sect dialogue (守山执事 and elders). Server-authoritative: a right click
 * on a steward or elder avatar ({@link #open}) and every option the player picks
 * ({@link #handleIntent}) are checked here (same level, the overworld, within
 * {@code rules.player.steward.interact_range}, an active ledger, a living person of an active sect
 * whose ledger role is still steward or elder) before anything is read or changed; the page sent
 * back ({@link SectDialoguePayload}) is built from the ledger alone. Joining and leaving go through
 * {@link WorldSimPlayers}. The decisions themselves are {@link SectDialogueScenes}.
 */
public final class SectDialogue {
    private static final Logger LOGGER = LoggerFactory.getLogger(SectDialogue.class);
    /** Used when the rules are not loaded (the ledger is inactive then anyway). */
    static final double DEFAULT_RANGE = 6.0;
    /** Least server ticks between two handled intents of one player. */
    static final int MIN_TICKS_BETWEEN = 4;
    static final String UNAVAILABLE_KEY = "message.myvillage.world.sect.unavailable";
    static final String NONE_KEY = "screen.myvillage.sect_dialogue.none";
    private static final int MAX_TRACKED_PLAYERS = 256;

    private static final Map<UUID, Integer> LAST_INTENT_TICK = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Integer> eldest) {
            return size() > MAX_TRACKED_PLAYERS;
        }
    };

    private SectDialogue() {
    }

    /** What a checked avatar stands for. */
    private record Speaker(WorldSim sim, NpcEntity npc, PersonView person, SectView sect, String role) {
    }

    /**
     * A player right-clicked an avatar with a dialogue role: checks and sends the opening page.
     * Returns whether a page was sent; when the ledger cannot answer, the player gets one chat line.
     */
    public static boolean open(ServerPlayer player, NpcEntity npc) {
        Optional<Speaker> speaker = check(player, npc);
        if (speaker.isEmpty()) {
            return false;
        }
        Speaker s = speaker.get();
        Optional<PlayerMemberView> me = s.sim().playerMember(player.getUUID().toString());
        Admission admission = admission(s.sim(), player, s.sect().id());
        SectDialogueScenes.Scene scene = SectDialogueScenes.decide(s.role(), me, admission, s.sect().id());
        send(player, s, me, admission, SectDialogueScenes.openingLines(s.role(), scene), scene.options());
        return true;
    }

    /** An option the player picked: checked again, then JOIN/LEAVE through {@link WorldSimPlayers}. */
    public static void handleIntent(ServerPlayer player, SectIntentPayload intent) {
        if (!allow(player.getUUID(), player.getServer() == null ? 0 : player.getServer().getTickCount())) {
            return;
        }
        Entity entity = player.serverLevel().getEntity(intent.entityId());
        if (!(entity instanceof NpcEntity npc)) {
            LOGGER.info("SectDialogue: {} chose {} at entity {}, which is gone", name(player), intent.option(),
                    intent.entityId());
            return;
        }
        Optional<Speaker> speaker = check(player, npc);
        if (speaker.isEmpty()) {
            return;
        }
        Speaker s = speaker.get();
        if (s.sect().id() != intent.sectId()) {
            LOGGER.warn("SectDialogue: {} chose {} for sect {} at an avatar of sect {}; ignored", name(player),
                    intent.option(), intent.sectId(), s.sect().id());
            return;
        }
        SectDialogueScenes.Option option = intent.option();
        if (option == SectDialogueScenes.Option.FAREWELL) {
            LOGGER.debug("SectDialogue: {} took leave of {}", name(player), s.person().name());
            return;
        }
        String playerId = player.getUUID().toString();
        Optional<PlayerMemberView> me = s.sim().playerMember(playerId);
        boolean here = me.isPresent() && me.get().inSect() && me.get().sectId() == s.sect().id();
        boolean offered = SectDialogueScenes.ROLE_STEWARD.equals(s.role())
                && (option == SectDialogueScenes.Option.JOIN ? !here : here);
        if (!offered) {
            // not an option this page offered: show the page as it stands now
            Admission admission = admission(s.sim(), player, s.sect().id());
            SectDialogueScenes.Scene scene = SectDialogueScenes.decide(s.role(), me, admission, s.sect().id());
            send(player, s, me, admission, scene.lines(), scene.options());
            return;
        }
        WorldSimPlayers.Result result;
        try {
            result = option == SectDialogueScenes.Option.JOIN
                    ? WorldSimPlayers.join(player, s.sect().id(), false)
                    : WorldSimPlayers.leave(player);
        } catch (RuntimeException ex) {
            LOGGER.error("SectDialogue: {} of {} at sect {} failed", option, name(player), s.sect().id(), ex);
            result = new WorldSimPlayers.Result(false, SectDialogueKeys.INACTIVE);
        }
        SectDialogueScenes.Scene scene;
        if (!result.ok()) {
            scene = SectDialogueScenes.refused(result.reason());
        } else if (option == SectDialogueScenes.Option.JOIN) {
            scene = SectDialogueScenes.welcome();
        } else {
            scene = SectDialogueScenes.farewellLeft();
        }
        Optional<PlayerMemberView> after = s.sim().playerMember(playerId);
        send(player, s, after, admission(s.sim(), player, s.sect().id()), scene.lines(), scene.options());
    }

    // ------------------------------------------------------------------ checks

    private static Optional<Speaker> check(ServerPlayer player, NpcEntity npc) {
        if (!npc.isLedgerAvatar() || NpcEntity.ROLE_NONE.equals(npc.ledgerRole()) || npc.isRemoved()) {
            return Optional.empty();
        }
        if (player.getServer() == null || player.level() != player.getServer().overworld()
                || npc.level() != player.level()) {
            return Optional.empty();
        }
        double range = WorldSimRuntime.data().map(d -> d.rules().player().steward().interactRange())
                .orElse(DEFAULT_RANGE);
        if (player.distanceToSqr(npc) > range * range) {
            return Optional.empty();
        }
        Optional<WorldSim> active = WorldSimRuntime.sim();
        if (active.isEmpty()) {
            unavailable(player, "the ledger is inactive");
            return Optional.empty();
        }
        WorldSim sim = active.get();
        Optional<PersonView> person = sim.person(npc.ledgerPersonId());
        if (person.isEmpty() || !person.get().alive() || person.get().sectId() < 0) {
            unavailable(player, "person " + npc.ledgerPersonId() + " is gone from the ledger");
            return Optional.empty();
        }
        Optional<SectView> sect = sim.sect(person.get().sectId());
        if (sect.isEmpty() || !"active".equals(sect.get().state())) {
            unavailable(player, "sect " + person.get().sectId() + " is not active");
            return Optional.empty();
        }
        String role = ledgerRole(sim, person.get());
        if (NpcEntity.ROLE_NONE.equals(role)) {
            unavailable(player, "person " + person.get().id() + " no longer has a dialogue role");
            return Optional.empty();
        }
        return Optional.of(new Speaker(sim, npc, person.get(), sect.get(), role));
    }

    /** The person's dialogue role by the ledger: the sect's steward, an elder (master included), or none. */
    static String ledgerRole(WorldSim sim, PersonView person) {
        if (sim.stewardOf(person.sectId()).map(PersonView::id).orElse(-1) == person.id()) {
            return NpcEntity.ROLE_STEWARD;
        }
        return switch (person.rank()) {
            case "elder", "sect_master" -> NpcEntity.ROLE_ELDER;
            default -> NpcEntity.ROLE_NONE;
        };
    }

    private static Admission admission(WorldSim sim, ServerPlayer player, int sectId) {
        try {
            return sim.admission(player.getUUID().toString(), sectId, WorldSimPlayers.qualification(player));
        } catch (RuntimeException ex) {
            LOGGER.warn("SectDialogue: admission of {} to sect {} failed", name(player), sectId, ex);
            return Admission.refused(SectDialogueKeys.INACTIVE);
        }
    }

    private static void unavailable(ServerPlayer player, String why) {
        LOGGER.info("SectDialogue: no dialogue for {}: {}", name(player), why);
        player.sendSystemMessage(Component.translatable(UNAVAILABLE_KEY));
    }

    /** True (and remembered) unless the player's previous intent was under {@link #MIN_TICKS_BETWEEN} ticks ago. */
    static boolean allow(UUID player, int tick) {
        Integer last = LAST_INTENT_TICK.get(player);
        if (last != null && tick >= last && tick - last < MIN_TICKS_BETWEEN) {
            return false;
        }
        LAST_INTENT_TICK.put(player, tick);
        return true;
    }

    // ------------------------------------------------------------------ the page

    private static void send(ServerPlayer player, Speaker s, Optional<PlayerMemberView> me, Admission admission,
                             List<String> bases, List<SectDialogueScenes.Option> options) {
        SectView sect = s.sect();
        boolean here = me.isPresent() && me.get().inSect() && me.get().sectId() == sect.id();
        String rank = here ? me.get().rank() : "";
        int standing = me.map(m -> m.standings().getOrDefault(sect.id(), 0)).orElse(0);
        int prestige = (int) Math.round(sect.prestige());
        Object master = sect.masterName() == null || sect.masterName().isEmpty()
                ? Component.translatable(NONE_KEY) : sect.masterName();
        SectDialogueScenes.Facts facts = new SectDialogueScenes.Facts(sect.name(), prestige, sect.memberCount(),
                master, here ? WorldSimText.rank(rank) : Component.empty());
        long salt = (long) player.getUUID().hashCode() + s.sim().day();
        List<Component> lines = new ArrayList<>();
        for (String base : bases) {
            if (lines.size() == SectDialoguePayload.MAX_LINES) {
                break;
            }
            lines.add(Component.translatable(SectDialogueKeys.pick(base, salt), SectDialogueScenes.args(base, facts)));
        }
        List<Integer> optionIds = new ArrayList<>();
        for (SectDialogueScenes.Option o : options) {
            optionIds.add(o.id());
        }
        SectDialoguePayload payload = new SectDialoguePayload(
                s.npc().getId(), sect.id(),
                SectDialoguePayload.clip(sect.name(), SectDialoguePayload.MAX_NAME),
                s.role(),
                SectDialoguePayload.clip(s.person().name(), SectDialoguePayload.MAX_NAME),
                prestige, sect.memberCount(),
                SectDialoguePayload.clip(sect.masterName(), SectDialoguePayload.MAX_NAME),
                SectDialoguePayload.clip(WorldSimRuntime.regionName(sect.regionId()), SectDialoguePayload.MAX_NAME),
                SectDialoguePayload.clip(rank, SectDialoguePayload.MAX_WORD),
                standing, admission.ok(),
                SectDialoguePayload.clip(admission.reason(), SectDialoguePayload.MAX_REASON),
                lines, optionIds);
        PacketDistributor.sendToPlayer(player, payload);
        LOGGER.info("SectDialogue: {} speaks with {} ({} of sect {}): {} -> options {}", name(player),
                s.person().name(), s.role(), sect.id(), bases, options);
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }
}
