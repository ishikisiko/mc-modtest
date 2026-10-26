package com.example.myvillage.sim.life;

import com.example.myvillage.sim.SimFormatException;
import com.example.myvillage.sim.model.WorldState;
import com.example.myvillage.sim.profession.ProfessionEngine;
import com.example.myvillage.sim.profession.ProfessionRules;
import com.example.myvillage.sim.profession.ProfessionState;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Versioned, deterministic ledger component; entity leases are intentionally never restored. */
public final class LifeCodec {
    public static final int SCHEMA = 2;
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
        out.addProperty("schema", SCHEMA);
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
        JsonArray actors = new JsonArray();
        s.storyActors.values().forEach(actor -> actors.add(GSON.toJsonTree(actor)));
        out.add("story_actors", actors);
        out.add("professions", GSON.toJsonTree(s.professions));
        out.add("gathering", GSON.toJsonTree(s.gathering));
        out.add("gardens", GSON.toJsonTree(s.gardens));
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
        int schema = (int) number(o, "schema", 1, SCHEMA);
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
            // New accounts are parsed explicitly, so Gson cannot coerce their types or restore
            // fields from a schema-1 payload that never owned classified herbs or recipe plans.
            JsonObject residentData = residentJson.deepCopy();
            residentData.remove("herb_inventory");
            residentData.remove("recipe_id");
            LifeResident r = GSON.fromJson(residentData, LifeResident.class);
            if (schema >= 2) {
                r.recipeId = string(residentJson, "recipe_id");
                readHerbCounts(residentJson, "herb_inventory", r.herbInventory);
            } else if (ProfessionEngine.ROLES.contains(r.role) || ProfessionEngine.ACTIVITIES.contains(r.activity)
                    || ProfessionEngine.ACTIVITIES.contains(r.previousActivity)) {
                throw bad("schema 1 cannot contain herbal profession roles or activities");
            }
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
            JsonObject supplyData = supply.deepCopy();
            supplyData.remove("herb_stock");
            LifeState.Supply account = GSON.fromJson(supplyData, LifeState.Supply.class);
            if (schema >= 2) readHerbCounts(supply, "herb_stock", account.herbStock);
            state.supplies.put(entry.getKey(), account);
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
        if (o.has("story_actors")) for (JsonElement element : array(o, "story_actors")) {
            JsonObject actor = object(element, "story_actor");
            for (String name : Set.of("story_id", "actor_key", "site_id", "initial_place_id", "current_place_id", "return_place_id", "policy"))
                string(actor, name);
            number(actor, "person_id", 1, Integer.MAX_VALUE);
            number(actor, "revision", 0, Long.MAX_VALUE);
            JsonElement returned = actor.get("returned");
            if (returned == null || !returned.isJsonPrimitive() || !returned.getAsJsonPrimitive().isBoolean())
                throw bad("expected boolean returned");
            StoryActorBinding binding = GSON.fromJson(actor, StoryActorBinding.class);
            if (state.storyActors.put(binding.key(), binding) != null) throw bad("duplicate story role " + binding.key());
        }
        if (schema >= 2) {
            for (var entry : object(o.get("professions"), "professions").entrySet()) {
                int id = personId(entry.getKey());
                JsonObject profile = object(entry.getValue(), "profession " + id);
                ProfessionState.Profile p = new ProfessionState.Profile();
                p.gatheringExperience = (int) number(profile, "gathering_experience", 0, Integer.MAX_VALUE);
                p.plantingExperience = (int) number(profile, "planting_experience", 0, Integer.MAX_VALUE);
                p.alchemyExperience = (int) number(profile, "alchemy_experience", 0, Integer.MAX_VALUE);
                p.hasBelongings = bool(profile, "has_belongings");
                p.savedCoins = (int) number(profile, "saved_coins", 0, Integer.MAX_VALUE);
                p.savedHerbs = (int) number(profile, "saved_herbs", 0, Integer.MAX_VALUE);
                p.savedFatigue = (int) number(profile, "saved_fatigue", 0, 100);
                readHerbCounts(profile, "saved_herb_inventory", p.savedHerbInventory);
                state.professions.put(id, p);
            }
            for (var entry : object(o.get("gathering"), "gathering").entrySet()) {
                JsonObject gathering = object(entry.getValue(), "gathering " + entry.getKey());
                ProfessionState.Gathering g = new ProfessionState.Gathering();
                g.herb = string(gathering, "herb");
                g.dailyQuota = (int) number(gathering, "daily_quota", 1, Integer.MAX_VALUE);
                g.quotaDay = number(gathering, "quota_day", -1, Long.MAX_VALUE);
                g.gatheredToday = (int) number(gathering, "gathered_today", 0, Integer.MAX_VALUE);
                state.gathering.put(entry.getKey(), g);
            }
            for (var entry : object(o.get("gardens"), "gardens").entrySet()) {
                JsonObject garden = object(entry.getValue(), "garden " + entry.getKey());
                ProfessionState.Garden g = new ProfessionState.Garden();
                g.herb = string(garden, "herb");
                g.plantedAt = number(garden, "planted_at", -1, Long.MAX_VALUE);
                g.matureAt = number(garden, "mature_at", -1, Long.MAX_VALUE);
                g.tended = bool(garden, "tended");
                state.gardens.put(entry.getKey(), g);
            }
        }
        // Structural bounds do not depend on the current rules. Rule limits and people are
        // checked by validateProfessions once the enclosing ledger has been restored.
        validateProfessionAccounts(state, ProfessionRules.HERBS, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE);
        return state;
    }

    /** Check after the main ledger has restored its people; death never becomes a new identity. */
    public static void validate(WorldState world, LifeRules rules) {
        LifeState life = world.life;
        StoryActors.validate(world);
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
        for (var p : life.professions.values())
            if (p.savedCoins > rules.maxInventory() || p.savedHerbs > rules.maxInventory())
                throw bad("unassigned belongings exceed inventory limit");
        for (LifeState.Supply supply : life.supplies.values()) {
            if (supply.stock > rules.maxInventory() || supply.medicine > rules.maxInventory()
                    || supply.treasury > rules.maxInventory()) throw bad("site supply exceeds limit");
        }
    }

    /** Current-rule checks are separate from the stable life validation entry point. */
    public static void validateProfessions(WorldState world, ProfessionRules rules) {
        LifeState life = world.life;
        validateProfessionAccounts(life, rules.herbs(), rules.maxExperience(),
                rules.gatherQuotaLimit(), rules.maxGardensPerSite());
        if (!life.gardens.isEmpty() && rules.gardenCapacity() != 1)
            throw bad("unsupported garden batch capacity");
        for (int id : life.professions.keySet()) {
            if (!world.persons.containsKey(id) && !world.tombstones.containsKey(id))
                throw bad("unknown profession identity " + id);
            if (life.professions.get(id).hasBelongings && life.residents.containsKey(id))
                throw bad("personal belongings have two owners " + id);
        }
        for (LifeResident r : life.residents.values()) {
            if (r.activity.equals("brew") && rules.recipe(r.recipeId) == null)
                throw bad("unknown resident recipe " + r.personId);
            if (ProfessionEngine.ROLES.contains(r.role)) {
                if (!life.professions.containsKey(r.personId))
                    throw bad("missing profession profile " + r.personId);
                LifeSite site = life.sites.get(r.siteId);
                LifePlace work = site == null ? null : site.place(r.workId).orElse(null);
                if (work == null || (r.role.equals("alchemist") && !work.kind().equals("work"))
                        || (r.role.equals("spirit_grower") && !work.kind().equals("garden"))
                        || (r.role.equals("herb_child") && !Set.of("gather", "garden").contains(work.kind())))
                    throw bad("invalid profession workplace " + r.personId);
            }
        }
    }

    private static void validateProfessionAccounts(LifeState life, Set<String> herbs,
            int maxExperience, int gatherQuotaLimit, int maxGardensPerSite) {
        for (var entry : life.professions.entrySet()) {
            ProfessionState.Profile p = entry.getValue();
            if (entry.getKey() <= 0 || p == null || p.gatheringExperience < 0
                    || p.gatheringExperience > maxExperience || p.plantingExperience < 0
                    || p.plantingExperience > maxExperience || p.alchemyExperience < 0
                    || p.alchemyExperience > maxExperience) throw bad("invalid profession profile " + entry.getKey());
            if (p.savedCoins < 0 || p.savedFatigue < 0 || p.savedFatigue > 100
                    || !p.hasBelongings && (p.savedCoins != 0 || p.savedHerbs != 0 || p.savedFatigue != 0
                        || !p.savedHerbInventory.isEmpty())) throw bad("invalid saved belongings " + entry.getKey());
            validateHerbCounts(p.savedHerbInventory, p.savedHerbs, herbs, "unassigned " + entry.getKey());
        }
        for (LifeResident r : life.residents.values()) {
            validateHerbCounts(r.herbInventory, r.herbs, herbs, "resident " + r.personId);
            if (r.recipeId == null || (r.activity.equals("brew") ? r.recipeId.isEmpty() : !r.recipeId.isEmpty()))
                throw bad("invalid resident recipe plan " + r.personId);
        }
        life.supplies.forEach((site, supply) -> validateHerbCounts(supply.herbStock, supply.stock, herbs, "supply " + site));
        for (var entry : life.gathering.entrySet()) {
            String key = entry.getKey();
            professionPlace(life, key, "gather");
            ProfessionState.Gathering g = entry.getValue();
            if (g == null || !herbs.contains(g.herb) || g.dailyQuota < 1 || g.dailyQuota > gatherQuotaLimit
                    || g.quotaDay < -1 || g.gatheredToday < 0
                    || g.gatheredToday > g.dailyQuota || (g.quotaDay == -1 && g.gatheredToday != 0))
                throw bad("invalid gathering account " + key);
        }
        Map<String, Integer> gardenCounts = new TreeMap<>();
        for (var entry : life.gardens.entrySet()) {
            String key = entry.getKey();
            String site = professionPlace(life, key, "garden");
            if (gardenCounts.merge(site, 1, Integer::sum) > maxGardensPerSite)
                throw bad("too many gardens at " + site);
            ProfessionState.Garden g = entry.getValue();
            if (g == null || !herbs.contains(g.herb)) throw bad("invalid garden herb " + key);
            boolean empty = g.plantedAt == -1 && g.matureAt == -1 && !g.tended;
            // Clock rebases may move backwards; frozen batches keep their original times.
            if (!empty && (g.plantedAt < 0 || g.matureAt <= g.plantedAt
                    || g.matureAt - g.plantedAt > ProfessionRules.MAX_GROWTH_TICKS))
                throw bad("invalid garden timing " + key);
        }
    }

    /** Return the site only after verifying the complete, canonical site|place reference. */
    private static String professionPlace(LifeState life, String key, String kind) {
        int split = key.indexOf('|');
        if (split < 1 || split == key.length() - 1 || split != key.lastIndexOf('|'))
            throw bad("invalid profession place key " + key);
        String siteId = key.substring(0, split);
        LifeSite site = life.sites.get(siteId);
        if (site == null || site.place(key.substring(split + 1)).filter(p -> p.kind().equals(kind)).isEmpty())
            throw bad("invalid " + kind + " place reference " + key);
        return siteId;
    }

    private static void readHerbCounts(JsonObject owner, String field, Map<String, Integer> target) {
        JsonObject counts = object(owner.get(field), field);
        for (String herb : counts.keySet()) {
            if (!ProfessionRules.HERBS.contains(herb)) throw bad("unknown classified herb " + herb);
            target.put(herb, (int) number(counts, herb, 0, Integer.MAX_VALUE));
        }
    }

    private static void validateHerbCounts(Map<String, Integer> counts, int total, Set<String> herbs, String owner) {
        if (total < 0) throw bad("negative herb total at " + owner);
        long classified = 0;
        for (var entry : counts.entrySet()) {
            if (!herbs.contains(entry.getKey()) || entry.getValue() == null || entry.getValue() < 0)
                throw bad("invalid classified herb at " + owner);
            classified += entry.getValue();
            if (classified > total) throw bad("classified herbs exceed aggregate at " + owner);
        }
    }

    private static int personId(String key) {
        if (!key.matches("[1-9][0-9]*")) throw bad("invalid profession person id " + key);
        try { return Integer.parseInt(key); }
        catch (NumberFormatException e) { throw bad("invalid profession person id " + key); }
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
    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw bad("expected boolean " + key);
        return value.getAsBoolean();
    }
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
