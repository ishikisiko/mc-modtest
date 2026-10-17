package com.example.myvillage.sim.social;

import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.PlayerMember;
import com.example.myvillage.sim.model.Tombstone;
import com.example.myvillage.sim.model.WorldState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pure social mutations; runtime callers must go through the WorldSim facade. */
public final class SocialEngine {
    /** Serialization/input safety bounds, not game balance. Balance comes from life rules. */
    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_MEMORY_LIMIT = 256;
    public static final String NPC_NOT_ALIVE = "npc_not_alive";
    public static final String PLAYER_NOT_REGISTERED = "player_not_registered";

    private SocialEngine() {
    }

    /** Idempotent within a day; a rename keeps every existing relationship and memory. */
    public static boolean registerPlayerActor(SocialState social, WorldState world, String id, String name) {
        String playerId = ActorRef.player(id).playerId();
        validateName(name);
        requireDay(world.day);
        SocialState.PlayerActor current = social.players.get(playerId);
        if (current == null) {
            social.players.put(playerId, new SocialState.PlayerActor(playerId, name, world.day, world.day));
            return true;
        }
        long seen = Math.max(current.lastSeenDay, world.day);
        boolean changed = !current.name.equals(name) || current.lastSeenDay != seen;
        current.name = name;
        current.lastSeenDay = seen;
        return changed;
    }

    /**
     * Add identities for old membership records without changing any membership or overwriting a
     * more recently observed name. Legacy opaque/non-UUID ids remain untouched in playerMembers.
     */
    public static boolean backfillMembers(SocialState social, WorldState world) {
        boolean changed = false;
        for (PlayerMember member : world.playerMembers.values()) {
            ActorRef ref;
            try {
                ref = ActorRef.player(member.playerId);
            } catch (IllegalArgumentException | NullPointerException ex) {
                continue;
            }
            if (social.players.containsKey(ref.playerId())) continue;
            String name = member.playerName;
            try {
                validateName(name);
            } catch (IllegalArgumentException | NullPointerException ex) {
                name = ref.playerId();
            }
            changed |= registerPlayerActor(social, world, ref.playerId(), name);
        }
        return changed;
    }

    public static Optional<ActorView> actor(SocialState social, WorldState world, ActorRef ref) {
        Objects.requireNonNull(ref, "ref");
        if (ref.kind() == ActorRef.Kind.NPC) {
            Person person = world.persons.get(ref.personId());
            if (person != null) return Optional.of(new ActorView(ref, person.name(), true, person.sectId));
            Tombstone dead = world.tombstones.get(ref.personId());
            return dead == null ? Optional.empty() : Optional.of(new ActorView(ref, dead.name, false, dead.sectId));
        }
        SocialState.PlayerActor player = social.players.get(ref.playerId());
        if (player == null) return Optional.empty();
        PlayerMember member = world.playerMembers.get(ref.playerId());
        return Optional.of(new ActorView(ref, player.name, true, member == null ? -1 : member.sectId));
    }

    /** No empty relation is created by inspecting a stranger. */
    public static Optional<SocialRelationView> relation(SocialState social, ActorRef observer, ActorRef other) {
        Objects.requireNonNull(observer, "observer");
        Objects.requireNonNull(other, "other");
        if (observer.kind() == other.kind()) return Optional.empty();
        ActorRef npc = observer.kind() == ActorRef.Kind.NPC ? observer : other;
        ActorRef player = observer.kind() == ActorRef.Kind.PLAYER ? observer : other;
        SocialState.Acquaintance known = social.acquaintances.get(new SocialState.Pair(npc.personId(), player.playerId()));
        if (known == null) return Optional.empty();
        return Optional.of(new SocialRelationView(observer, other, known.familiarity, known.firstConversationDay,
                known.lastConversationDay, memories(known, other)));
    }

    public static ConversationView conversationContext(SocialState social, WorldState world, int npcId, String playerId) {
        Person npc = requireLivingNpc(world, npcId);
        ActorRef playerRef = ActorRef.player(playerId);
        SocialState.PlayerActor player = requirePlayer(social, playerRef.playerId());
        Optional<SocialRelationView> relation = relation(social, ActorRef.npc(npcId), playerRef);
        return new ConversationView(npcId, player.playerId, npc.name(), player.name,
                relation.map(SocialRelationView::familiarity).orElse(0), relation.isPresent(),
                relation.map(SocialRelationView::lastConversationDay).orElse(-1L),
                relation.map(SocialRelationView::memories).orElse(List.of()));
    }

    /**
     * One remembered conversation per NPC/player/sim day. Repeated packets, reloads and a rewound
     * day cannot increase familiarity again. The runtime validates distance and real interaction.
     */
    public static boolean rememberConversation(SocialState social, WorldState world, int npcId, String playerId,
                                               int memoryLimit, int familiarityGain, int standingMax) {
        validateRules(memoryLimit, familiarityGain, standingMax);
        requireDay(world.day);
        requireLivingNpc(world, npcId);
        String id = ActorRef.player(playerId).playerId();
        requirePlayer(social, id);
        SocialState.Pair key = new SocialState.Pair(npcId, id);
        SocialState.Acquaintance known = social.acquaintances.get(key);
        if (known != null && known.lastConversationDay >= world.day) return false;
        if (known == null) {
            known = new SocialState.Acquaintance();
            known.firstConversationDay = world.day;
            social.acquaintances.put(key, known);
        }
        known.familiarity = (int) Math.min(standingMax, (long) known.familiarity + familiarityGain);
        known.lastConversationDay = world.day;
        known.conversationDays.add(world.day);
        int discard = known.conversationDays.size() - memoryLimit;
        if (discard > 0) known.conversationDays.subList(0, discard).clear();
        return true;
    }

    static void validateName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank() || name.length() > MAX_NAME_LENGTH || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("invalid_player_name");
        }
    }

    private static void validateRules(int memoryLimit, int familiarityGain, int standingMax) {
        if (memoryLimit < 1 || memoryLimit > MAX_MEMORY_LIMIT || familiarityGain < 0 || standingMax < 0) {
            throw new IllegalArgumentException("invalid_social_rules");
        }
    }

    private static void requireDay(long day) {
        if (day < 0) throw new IllegalArgumentException("invalid_sim_day");
    }

    private static Person requireLivingNpc(WorldState world, int npcId) {
        ActorRef.npc(npcId);
        Person person = world.persons.get(npcId);
        if (person == null) throw new IllegalArgumentException(NPC_NOT_ALIVE);
        return person;
    }

    private static SocialState.PlayerActor requirePlayer(SocialState social, String playerId) {
        SocialState.PlayerActor player = social.players.get(playerId);
        if (player == null) throw new IllegalArgumentException(PLAYER_NOT_REGISTERED);
        return player;
    }

    private static List<SocialMemoryView> memories(SocialState.Acquaintance known, ActorRef other) {
        List<SocialMemoryView> memories = new ArrayList<>();
        for (int i = known.conversationDays.size() - 1; i >= 0; i--) {
            memories.add(new SocialMemoryView(other, SocialMemoryView.CONVERSATION, known.conversationDays.get(i)));
        }
        return List.copyOf(memories);
    }
}
