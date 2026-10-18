package com.example.myvillage.sim.life;

import com.example.myvillage.sim.SimFormatException;
import com.example.myvillage.sim.model.WorldState;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;

/** Versioned, deterministic ledger component; entity leases are intentionally never restored. */
public final class LifeCodec {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    private static final Set<String> RESIDENT_TEXT = Set.of("site_id", "role", "home_id", "work_id", "phase",
            "activity", "target_id", "reason", "previous_activity", "previous_target", "blocked_target");
    private static final Set<String> RESIDENT_INTS = Set.of("person_id", "x", "y", "z", "herbs", "coins", "fatigue");
    private static final Set<String> RESIDENT_LONGS = Set.of("revision", "epoch", "progress_ticks", "required_ticks",
            "pause_ticks", "completed_activities", "completed_revision", "cultivation_ticks", "retry_after");

    private LifeCodec() { }

    public static JsonObject write(LifeState s) {
        JsonObject out = new JsonObject();
        out.addProperty("schema", 1);
        out.addProperty("clock_ticks", s.clockTicks);
        out.addProperty("next_epoch", s.nextEpoch);
        out.addProperty("ticks_per_day", s.ticksPerDay);
        JsonArray sites = new JsonArray();
        s.sites.values().forEach(site -> sites.add(GSON.toJsonTree(site)));
        out.add("sites", sites);
        JsonArray residents = new JsonArray();
        s.residents.values().forEach(resident -> residents.add(GSON.toJsonTree(resident)));
        out.add("residents", residents);
        JsonObject observations = new JsonObject();
        s.availability.forEach(observations::addProperty);
        out.add("observations", observations);
        JsonObject supplies = new JsonObject();
        s.supplies.forEach((id, supply) -> supplies.add(id, GSON.toJsonTree(supply)));
        out.add("supplies", supplies);
        JsonObject guides = new JsonObject();
        s.guideAssignments.forEach(guides::addProperty);
        out.add("guide_assignments", guides);
        return out;
    }

    public static LifeState read(JsonObject o) {
        try {
            return readState(o);
        } catch (SimFormatException e) {
            throw e;
        } catch (RuntimeException e) {
            // Gson wraps rejected record constructors in a plain RuntimeException.
            throw new SimFormatException("life: malformed component", e);
        }
    }

    private static LifeState readState(JsonObject o) {
        LifeState state = new LifeState();
        if (o == null) return state; // Pre-v4 saves have no town residents.
        if (number(o, "schema", 1, 1) != 1) throw bad("unsupported schema");
        state.clockTicks = number(o, "clock_ticks", -1, Long.MAX_VALUE);
        state.nextEpoch = number(o, "next_epoch", 1, Long.MAX_VALUE);
        state.ticksPerDay = (int) number(o, "ticks_per_day", 1, Integer.MAX_VALUE);
        for (JsonElement element : array(o, "sites")) {
            JsonObject siteJson = object(element, "site");
            for (String name : Set.of("id", "name", "dimension", "region_id")) string(siteJson, name);
            for (String name : Set.of("anchor_x", "anchor_y", "anchor_z")) integer(siteJson, name);
            for (JsonElement place : array(siteJson, "places")) {
                JsonObject placeJson = object(place, "place");
                for (String name : Set.of("id", "kind", "block")) string(placeJson, name);
                for (String name : Set.of("x", "y", "z", "capacity", "block_x", "block_y", "block_z")) integer(placeJson, name);
            }
            LifeSite site = GSON.fromJson(siteJson, LifeSite.class);
            if (state.sites.put(site.id(), site) != null) throw bad("duplicate site " + site.id());
        }
        for (JsonElement element : array(o, "residents")) {
            JsonObject residentJson = object(element, "resident");
            RESIDENT_TEXT.forEach(name -> string(residentJson, name));
            RESIDENT_INTS.forEach(name -> integer(residentJson, name));
            RESIDENT_LONGS.forEach(name -> number(residentJson, name, name.equals("completed_revision") ? -1 : 0, Long.MAX_VALUE));
            LifeResident r = GSON.fromJson(residentJson, LifeResident.class);
            if (r.personId <= 0 || !LifeEngine.ROLES.contains(r.role) || !LifeEngine.PHASES.contains(r.phase)
                    || !LifeRules.ACTIVITIES.contains(r.activity) || r.herbs < 0 || r.coins < 0
                    || r.fatigue < 0 || r.fatigue > 100 || r.completedRevision > r.revision
                    || r.progressTicks > r.requiredTicks || r.reason.length() > 128 || r.epoch >= state.nextEpoch
                    || (!r.previousActivity.isEmpty() && !LifeRules.ACTIVITIES.contains(r.previousActivity))) {
                throw bad("invalid resident " + r.personId);
            }
            LifePlace.requirePosition(r.x, r.y, r.z);
            LifeSite site = state.sites.get(r.siteId);
            if (site == null || site.place(r.homeId).filter(p -> p.kind().equals("rest")).isEmpty()
                    || site.place(r.workId).isEmpty() || (!r.targetId.isEmpty() && site.place(r.targetId).isEmpty())
                    || (!r.blockedTarget.isEmpty() && site.place(r.blockedTarget).isEmpty())
                    || (!r.previousTarget.isEmpty() && site.place(r.previousTarget).isEmpty())
                    || (!r.phase.equals("waiting") && (r.targetId.isEmpty() || r.requiredTicks == 0))) {
                throw bad("invalid resident place reference " + r.personId);
            }
            r.embodied = false;
            r.pauseTicks = 0; // No client conversation survives a server restart.
            if (state.residents.put(r.personId, r) != null) throw bad("duplicate resident " + r.personId);
        }
        for (var entry : object(o.get("observations"), "observations").entrySet()) {
            String key = entry.getKey();
            int split = key.indexOf('|');
            LifeSite site = split < 1 ? null : state.sites.get(key.substring(0, split));
            String status = text(entry.getValue(), key);
            if (site == null || site.place(key.substring(split + 1)).isEmpty()
                    || !Set.of("available", "blocked").contains(status)) throw bad("invalid observation " + key);
            state.availability.put(key, status);
        }
        for (var entry : object(o.get("supplies"), "supplies").entrySet()) {
            if (!state.sites.containsKey(entry.getKey())) throw bad("unknown supply site " + entry.getKey());
            JsonObject supply = object(entry.getValue(), "supply");
            number(supply, "quota_day", -1, Long.MAX_VALUE);
            number(supply, "services", 0, Long.MAX_VALUE);
            for (String name : Set.of("gathered_today", "stock", "medicine", "treasury")) number(supply, name, 0, Integer.MAX_VALUE);
            state.supplies.put(entry.getKey(), GSON.fromJson(supply, LifeState.Supply.class));
        }
        if (!state.supplies.keySet().equals(state.sites.keySet())) throw bad("missing site supply account");
        if (o.has("guide_assignments")) for (var e : object(o.get("guide_assignments"), "guide_assignments").entrySet()) {
            if (!state.sites.containsKey(e.getKey())) throw bad("unknown guide site");
            long id = number(object(o.get("guide_assignments"), "guide_assignments"), e.getKey(), 1, Integer.MAX_VALUE);
            state.guideAssignments.put(e.getKey(), (int)id);
        }
        // Older saved guide roles (if supplied by a test/admin migration) retain their own identity.
        for (var r : state.residents.values()) if (r.role.equals("guide")) {
            Integer old = state.guideAssignments.putIfAbsent(r.siteId, r.personId);
            if (old != null && old != r.personId) throw bad("duplicate guide identity");
        }
        return state;
    }

    /** Check after the main ledger has restored its people; death never becomes a new identity. */
    public static void validate(WorldState world, LifeRules rules) {
        LifeState life = world.life;
        if (life.sites.size() > rules.maxSites()) throw bad("too many sites for current life rules");
        for (LifeSite site : life.sites.values()) {
            if (site.places().size() > rules.maxPlacesPerSite()) throw bad("too many places at " + site.id());
            long count = life.residents.values().stream().filter(r -> r.siteId.equals(site.id())).count();
            if (count > rules.maxResidentsPerSite()) throw bad("too many residents at " + site.id());
            for (LifePlace home : site.places()) {
                if (!home.kind().equals("rest")) continue;
                long occupants = life.residents.values().stream().filter(r -> r.siteId.equals(site.id()) && r.homeId.equals(home.id())).count();
                if (occupants > home.capacity()) throw bad("overbooked home " + site.id() + "/" + home.id());
            }
        }
        life.residents.entrySet().removeIf(e -> world.tombstones.containsKey(e.getKey()));
        for (LifeResident r : life.residents.values()) {
            var person = world.persons.get(r.personId);
            if (person == null || !person.regionId.equals(life.sites.get(r.siteId).regionId())) {
                throw bad("resident does not belong to site region " + r.personId);
            }
            if (r.coins > rules.maxInventory() || r.herbs > rules.maxInventory()) throw bad("resident inventory exceeds limit");
        }
        for (int id : life.guideAssignments.values())
            if (!world.persons.containsKey(id) && !world.tombstones.containsKey(id)) throw bad("unknown guide identity");
        for (LifeState.Supply supply : life.supplies.values()) {
            if (supply.stock > rules.maxInventory() || supply.medicine > rules.maxInventory()
                    || supply.treasury > rules.maxInventory()) throw bad("site supply exceeds limit");
        }
    }

    private static long number(JsonObject object, String key, long min, long max) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw bad("expected number " + key);
        try {
            long result = value.getAsBigDecimal().longValueExact();
            if (result < min || result > max) throw bad("out of range " + key);
            return result;
        } catch (ArithmeticException | NumberFormatException e) { throw bad("expected integer " + key); }
    }

    private static void integer(JsonObject object, String key) { number(object, key, Integer.MIN_VALUE, Integer.MAX_VALUE); }
    private static String string(JsonObject object, String key) { return text(object.get(key), key); }
    private static String text(JsonElement value, String key) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw bad("expected string " + key);
        return value.getAsString();
    }
    private static JsonObject object(JsonElement value, String key) {
        if (value == null || !value.isJsonObject()) throw bad("expected object " + key);
        return value.getAsJsonObject();
    }
    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) throw bad("expected list " + key);
        return value.getAsJsonArray();
    }
    private static SimFormatException bad(String detail) { return new SimFormatException("life: " + detail); }
}
