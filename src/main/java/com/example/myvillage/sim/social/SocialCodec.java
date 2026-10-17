package com.example.myvillage.sim.social;

import com.example.myvillage.sim.SimFormatException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Canonical saved social state. Missing state in an older world starts empty. */
public final class SocialCodec {
    public static final int SCHEMA = 1;

    private SocialCodec() {
    }

    public static JsonObject write(SocialState state) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        JsonArray players = new JsonArray();
        for (SocialState.PlayerActor player : state.players.values()) {
            JsonObject row = new JsonObject();
            row.addProperty("actor", ActorRef.player(player.playerId).stableKey());
            row.addProperty("name", player.name);
            row.addProperty("first_seen_day", player.firstSeenDay);
            row.addProperty("last_seen_day", player.lastSeenDay);
            players.add(row);
        }
        root.add("players", players);
        JsonArray acquaintances = new JsonArray();
        state.acquaintances.forEach((pair, known) -> {
            JsonObject row = new JsonObject();
            row.addProperty("npc", ActorRef.npc(pair.personId()).stableKey());
            row.addProperty("player", ActorRef.player(pair.playerId()).stableKey());
            row.addProperty("familiarity", known.familiarity);
            row.addProperty("first_conversation_day", known.firstConversationDay);
            row.addProperty("last_conversation_day", known.lastConversationDay);
            JsonArray memories = new JsonArray();
            known.conversationDays.forEach(memories::add);
            row.add("conversation_days", memories);
            acquaintances.add(row);
        });
        root.add("acquaintances", acquaintances);
        return root;
    }

    public static SocialState read(JsonObject root) {
        SocialState state = new SocialState();
        if (root == null) return state;
        long schema = number(root, "schema");
        if (schema != SCHEMA) throw bad("schema", "unsupported version " + schema);
        for (JsonElement entry : array(root, "players")) {
            JsonObject row = object(entry, "players[]");
            ActorRef ref = actor(row, "actor", ActorRef.Kind.PLAYER);
            String name = text(row, "name");
            try {
                SocialEngine.validateName(name);
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw bad("players.name", "invalid name");
            }
            long first = number(row, "first_seen_day");
            long last = number(row, "last_seen_day");
            if (first < 0 || last < first) throw bad("players", "invalid observation days");
            SocialState.PlayerActor player = new SocialState.PlayerActor(ref.playerId(), name, first, last);
            if (state.players.put(ref.playerId(), player) != null) throw bad("players", "duplicate UUID");
        }
        for (JsonElement entry : array(root, "acquaintances")) {
            JsonObject row = object(entry, "acquaintances[]");
            ActorRef npc = actor(row, "npc", ActorRef.Kind.NPC);
            ActorRef player = actor(row, "player", ActorRef.Kind.PLAYER);
            if (!state.players.containsKey(player.playerId())) throw bad("acquaintances.player", "unknown player");
            long familiarity = number(row, "familiarity");
            if (familiarity < 0 || familiarity > Integer.MAX_VALUE) throw bad("familiarity", "out of range");
            SocialState.Acquaintance known = new SocialState.Acquaintance();
            known.familiarity = (int) familiarity;
            known.firstConversationDay = number(row, "first_conversation_day");
            known.lastConversationDay = number(row, "last_conversation_day");
            if (known.firstConversationDay < 0 || known.lastConversationDay < known.firstConversationDay) {
                throw bad("acquaintances", "invalid conversation days");
            }
            JsonArray memories = array(row, "conversation_days");
            if (memories.isEmpty() || memories.size() > SocialEngine.MAX_MEMORY_LIMIT) {
                throw bad("conversation_days", "invalid memory count");
            }
            long previous = -1;
            for (JsonElement memory : memories) {
                long day = number(memory, "conversation_days[]");
                if (day < known.firstConversationDay || day <= previous || day > known.lastConversationDay) {
                    throw bad("conversation_days", "must be unique and increasing within the remembered lifetime");
                }
                known.conversationDays.add(day);
                previous = day;
            }
            if (previous != known.lastConversationDay) throw bad("conversation_days", "last conversation is missing");
            SocialState.Pair pair = new SocialState.Pair(npc.personId(), player.playerId());
            if (state.acquaintances.put(pair, known) != null) throw bad("acquaintances", "duplicate actor pair");
        }
        return state;
    }

    private static ActorRef actor(JsonObject row, String field, ActorRef.Kind kind) {
        ActorRef ref;
        try {
            ref = ActorRef.parse(text(row, field));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw bad(field, "invalid actor reference");
        }
        if (ref.kind() != kind) throw bad(field, "wrong actor kind");
        return ref;
    }

    private static JsonObject object(JsonElement element, String field) {
        if (element == null || !element.isJsonObject()) throw bad(field, "expected object");
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonArray()) throw bad(field, "expected array");
        return element.getAsJsonArray();
    }

    private static String text(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw bad(field, "expected string");
        }
        return element.getAsString();
    }

    private static long number(JsonObject object, String field) {
        return number(object.get(field), field);
    }

    private static long number(JsonElement element, String field) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw bad(field, "expected integer");
        }
        try {
            return element.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            throw bad(field, "expected in-range integer");
        }
    }

    private static SimFormatException bad(String field, String reason) {
        return new SimFormatException("social." + field + ": " + reason);
    }
}
