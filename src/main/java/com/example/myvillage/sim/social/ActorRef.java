package com.example.myvillage.sim.social;

import java.util.Objects;
import java.util.UUID;

/** Stable social identity. A player UUID never becomes an NPC person id. */
public record ActorRef(Kind kind, String id) implements Comparable<ActorRef> {
    public enum Kind { NPC, PLAYER }

    public ActorRef {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        if (kind == Kind.NPC) {
            int person = Integer.parseInt(id);
            if (person <= 0 || !Integer.toString(person).equals(id)) {
                throw new IllegalArgumentException("invalid_npc_id");
            }
        } else {
            UUID uuid = UUID.fromString(id);
            if (!uuid.toString().equalsIgnoreCase(id)) {
                throw new IllegalArgumentException("invalid_player_uuid");
            }
            id = uuid.toString();
        }
    }

    public static ActorRef npc(int personId) {
        return new ActorRef(Kind.NPC, Integer.toString(personId));
    }

    public static ActorRef player(String playerId) {
        return new ActorRef(Kind.PLAYER, playerId);
    }

    public static ActorRef parse(String key) {
        Objects.requireNonNull(key, "key");
        if (key.startsWith("npc:")) return new ActorRef(Kind.NPC, key.substring(4));
        if (key.startsWith("player:")) return player(key.substring(7));
        throw new IllegalArgumentException("invalid_actor_ref");
    }

    public int personId() {
        if (kind != Kind.NPC) throw new IllegalStateException("not_npc");
        return Integer.parseInt(id);
    }

    public String playerId() {
        if (kind != Kind.PLAYER) throw new IllegalStateException("not_player");
        return id;
    }

    public String stableKey() {
        return (kind == Kind.NPC ? "npc:" : "player:") + id;
    }

    @Override
    public int compareTo(ActorRef other) {
        int order = kind.compareTo(other.kind);
        if (order != 0) return order;
        return kind == Kind.NPC ? Integer.compare(personId(), other.personId()) : id.compareTo(other.id);
    }
}
