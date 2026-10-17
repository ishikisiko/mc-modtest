package com.example.myvillage.sim.combat;

import com.example.myvillage.sim.SimFormatException;
import com.example.myvillage.sim.model.WorldState;
import com.example.myvillage.sim.social.ActorRef;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Explicit v5 component: profiles survive restart, body execution leases do not. */
public final class NpcCombatCodec {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private NpcCombatCodec() {}

    public static JsonObject write(NpcCombatState state) {
        JsonObject root = new JsonObject();
        root.addProperty("next_epoch", state.nextEpoch);
        JsonArray profiles = new JsonArray();
        state.profiles.forEach((id, profile) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("person", id);
            entry.add("state", JSON.toJsonTree(profile));
            profiles.add(entry);
        });
        root.add("profiles", profiles);
        JsonArray grievances = new JsonArray();
        state.grievances.forEach((id, actors) -> actors.forEach((actor, event) -> {
            JsonObject e = new JsonObject(); e.addProperty("person", id);
            e.addProperty("actor", actor); e.addProperty("event", event); grievances.add(e);
        }));
        root.add("grievances", grievances);
        return root;
    }

    public static NpcCombatState read(JsonObject root, WorldState world) {
        NpcCombatState state = new NpcCombatState();
        if (root == null) return state; // v1-v4: initialized once at the first real projection.
        try {
            state.nextEpoch = root.get("next_epoch").getAsLong();
            if (state.nextEpoch < 1 || state.nextEpoch == Long.MAX_VALUE) throw new IllegalArgumentException("epoch");
            for (var element : root.getAsJsonArray("profiles")) {
                var e = element.getAsJsonObject(); int id = e.get("person").getAsInt();
                var p = JSON.fromJson(e.get("state"), NpcCombatProfile.class);
                if (p == null || !world.persons.containsKey(id) || state.profiles.putIfAbsent(id, p) != null)
                    throw new IllegalArgumentException("profile identity");
            }
            for (var element : root.getAsJsonArray("grievances")) {
                var e = element.getAsJsonObject(); int id = e.get("person").getAsInt();
                String actor = ActorRef.parse(e.get("actor").getAsString()).stableKey();
                long event = e.get("event").getAsLong();
                if (!world.persons.containsKey(id) || event < 0
                        || state.grievances.computeIfAbsent(id, ignored -> new java.util.TreeMap<>()).putIfAbsent(actor, event) != null)
                    throw new IllegalArgumentException("grievance identity");
            }
        } catch (RuntimeException ex) {
            throw new SimFormatException("invalid NPC combat save: " + ex.getMessage(), ex);
        }
        return state;
    }
}
