package com.example.myvillage.sim.model;

import com.example.myvillage.sim.SettlementScheduler;
import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.SimFormatException;
import com.example.myvillage.sim.data.RealmTable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The save payload: canonical UTF-8 JSON (fixed key order, maps in id order, compact), so equal
 * states give equal bytes. Readers of a later version must keep accepting this one: a missing
 * optional field takes its default and unknown fields are ignored, but an unknown format or a newer
 * version fails loudly.
 */
public final class StateCodec {
    public static final String FORMAT = "myvillage:world_sim";
    public static final int VERSION = 1;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private StateCodec() {
    }

    public static byte[] toBytes(WorldState s, RealmTable realms) {
        return GSON.toJson(toJson(s, realms)).getBytes(StandardCharsets.UTF_8);
    }

    public static JsonObject toJson(WorldState s, RealmTable realms) {
        JsonObject o = new JsonObject();
        o.addProperty("format", FORMAT);
        o.addProperty("version", VERSION);
        o.addProperty("seed", s.seed);
        o.addProperty("tier", s.tierId);
        o.addProperty("genesis_days_per_year", s.genesisDaysPerYear);
        o.addProperty("prehistory_days", s.prehistoryDays);
        o.addProperty("day", s.day);
        o.addProperty("next_person_id", s.nextPersonId);
        o.addProperty("next_sect_id", s.nextSectId);
        o.addProperty("next_event_id", s.nextEventId);
        JsonObject sch = new JsonObject();
        if (s.scheduler.lastCalendarDay() != SettlementScheduler.UNANCHORED) {
            sch.addProperty("last_calendar_day", s.scheduler.lastCalendarDay());
        }
        sch.addProperty("pending_days", s.scheduler.pendingDays());
        sch.addProperty("paused", s.scheduler.paused());
        o.add("scheduler", sch);
        JsonArray regions = new JsonArray();
        for (RegionState r : s.regions.values()) {
            JsonObject ro = new JsonObject();
            ro.addProperty("id", r.id);
            ro.addProperty("richness", r.richness);
            regions.add(ro);
        }
        o.add("regions", regions);
        JsonArray sects = new JsonArray();
        for (Sect sect : s.sects.values()) {
            sects.add(sect(sect));
        }
        o.add("sects", sects);
        JsonArray persons = new JsonArray();
        for (Person p : s.persons.values()) {
            persons.add(person(p, realms));
        }
        o.add("persons", persons);
        JsonArray tombs = new JsonArray();
        for (Tombstone t : s.tombstones.values()) {
            tombs.add(tombstone(t, realms));
        }
        o.add("tombstones", tombs);
        JsonArray chronicle = new JsonArray();
        for (SimEvent e : s.chronicle) {
            chronicle.add(event(e));
        }
        o.add("chronicle", chronicle);
        return o;
    }

    private static JsonObject sect(Sect s) {
        JsonObject o = new JsonObject();
        o.addProperty("id", s.id);
        o.addProperty("name", s.name);
        o.addProperty("region", s.homeRegionId);
        o.addProperty("gate_x", s.gateX);
        o.addProperty("gate_z", s.gateZ);
        o.addProperty("gate_realized", s.gateRealized);
        o.addProperty("founder", s.founderId);
        o.addProperty("founded_day", s.foundedDay);
        o.addProperty("master", s.masterId);
        o.addProperty("master_since", s.masterSinceDay);
        o.addProperty("resources", s.resources);
        o.addProperty("prestige", s.prestige);
        o.addProperty("signature_technique", s.signatureTechniqueId);
        o.addProperty("basic_technique", s.basicTechniqueId);
        JsonArray rel = new JsonArray();
        for (SectRelation r : s.relations.values()) {
            JsonObject ro = new JsonObject();
            ro.addProperty("other", r.other);
            ro.addProperty("value", r.value);
            ro.addProperty("state", r.state);
            ro.addProperty("cause", r.causeEventId);
            ro.addProperty("since", r.sinceDay);
            ro.addProperty("score", r.score);
            ro.addProperty("tribute_until", r.tributeUntilDay);
            ro.addProperty("last_war_end", r.lastWarEndDay);
            ro.addProperty("last_battle", r.lastBattleDay);
            ro.addProperty("last_champion_a", r.lastChampionA);
            ro.addProperty("last_champion_b", r.lastChampionB);
            rel.add(ro);
        }
        o.add("relations", rel);
        o.addProperty("state", s.state);
        o.addProperty("destroyed_day", s.destroyedDay);
        o.addProperty("parent", s.parentSectId);
        o.addProperty("decline_since", s.declineSinceDay);
        o.addProperty("decline_cause", s.declineCauseEventId);
        o.addProperty("victories", s.victories);
        o.addProperty("vacancy_cause", s.vacancyCauseEventId);
        return o;
    }

    private static JsonObject person(Person p, RealmTable realms) {
        JsonObject o = new JsonObject();
        o.addProperty("id", p.id);
        o.addProperty("surname", p.surname);
        o.addProperty("given", p.given);
        o.addProperty("gender", p.gender);
        o.addProperty("birth", p.birthDay);
        o.add("root", ints(p.root));
        o.addProperty("root_event", p.rootEventId);
        o.addProperty("realm", realms.get(p.realm).id());
        o.addProperty("stage", p.stage);
        o.addProperty("progress", p.progress);
        o.addProperty("failures", p.failures);
        o.addProperty("bonus_lifespan", p.bonusLifespanYears);
        o.addProperty("lifespan_event", p.lifespanEventId);
        o.addProperty("sect", p.sectId);
        o.addProperty("rank", p.rank);
        o.addProperty("master", p.masterId);
        o.addProperty("joined", p.joinedDay);
        o.addProperty("region", p.regionId);
        o.addProperty("status", p.status);
        o.addProperty("status_until", p.statusUntil);
        o.addProperty("home", p.homeRegionId);
        o.addProperty("dest", p.destRegionId);
        o.add("traits", ints(new int[] {p.ambition, p.aggression, p.caution, p.wanderlust, p.loyalty}));
        JsonArray rel = new JsonArray();
        for (Relation r : p.relations) {
            JsonArray ra = new JsonArray();
            ra.add(r.other);
            ra.add(r.kind);
            ra.add(r.strength);
            ra.add(r.causeEventId);
            if (!r.reason.isEmpty()) {
                ra.add(r.reason);
            }
            rel.add(ra);
        }
        o.add("relations", rel);
        o.addProperty("technique", p.techniqueId);
        o.addProperty("technique_event", p.techniqueEventId);
        JsonArray boons = new JsonArray();
        for (Boon b : p.boons) {
            JsonArray ba = new JsonArray();
            ba.add(b.kind);
            ba.add(b.refId);
            ba.add(b.amount);
            ba.add(b.sourceEventId);
            boons.add(ba);
        }
        o.add("boons", boons);
        o.addProperty("injury", p.injury);
        o.addProperty("injury_event", p.injuryEventId);
        o.addProperty("injurer", p.injurerId);
        o.addProperty("dao_name", p.daoName);
        o.addProperty("quarry", p.quarryId);
        o.addProperty("insight_day", p.insightDay);
        o.addProperty("journeys", p.journeys);
        o.addProperty("kills", p.kills);
        o.addProperty("fortunes", p.fortunes);
        return o;
    }

    private static JsonObject tombstone(Tombstone t, RealmTable realms) {
        JsonObject o = new JsonObject();
        o.addProperty("id", t.id);
        o.addProperty("name", t.name);
        o.addProperty("title", t.title);
        o.addProperty("dao_name", t.daoName);
        o.addProperty("gender", t.gender);
        o.addProperty("sect", t.sectId);
        o.addProperty("rank", t.rank);
        o.addProperty("root_grade", t.rootGrade);
        o.addProperty("realm", realms.get(t.realm).id());
        o.addProperty("stage", t.stage);
        o.addProperty("birth", t.birthDay);
        o.addProperty("death", t.deathDay);
        o.addProperty("cause", t.cause);
        o.addProperty("killer", t.killerId);
        o.addProperty("death_event", t.deathEventId);
        o.addProperty("master", t.masterId);
        o.addProperty("technique", t.techniqueId);
        o.addProperty("technique_grade", t.techniqueGrade);
        return o;
    }

    public static JsonObject event(SimEvent e) {
        JsonObject o = new JsonObject();
        o.addProperty("id", e.id());
        o.addProperty("day", e.day());
        o.addProperty("type", e.type());
        o.addProperty("importance", e.importance());
        JsonArray actors = new JsonArray();
        e.actors().forEach(actors::add);
        o.add("actors", actors);
        JsonArray sects = new JsonArray();
        e.sects().forEach(sects::add);
        o.add("sects", sects);
        o.addProperty("region", e.regionId());
        o.addProperty("cause", e.causeId());
        o.addProperty("key", e.textKey());
        JsonArray params = new JsonArray();
        e.params().forEach(params::add);
        o.add("params", params);
        return o;
    }

    private static JsonArray ints(int[] values) {
        JsonArray a = new JsonArray();
        for (int v : values) {
            a.add(v);
        }
        return a;
    }

    // ------------------------------------------------------------------ reading

    public static WorldState fromBytes(byte[] bytes, RealmTable realms) {
        if (bytes == null || bytes.length == 0) {
            throw new SimFormatException("empty payload");
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
        } catch (JsonParseException e) {
            throw new SimFormatException("not valid JSON: " + e.getMessage(), e);
        }
        if (!root.isJsonObject()) {
            throw new SimFormatException("top level must be a JSON object");
        }
        Obj o = new Obj(root.getAsJsonObject(), "");
        String format = o.str("format");
        if (!FORMAT.equals(format)) {
            throw new SimFormatException("unknown format \"" + format + "\" (expected " + FORMAT + ")");
        }
        int version = o.integer("version");
        if (version > VERSION) {
            throw new SimFormatException("saved by a newer world-sim format version " + version
                    + "; this build reads up to version " + VERSION + ". Refusing to load rather than lose data.");
        }
        if (version < 1) {
            throw new SimFormatException("invalid format version " + version);
        }
        try {
            return read(o, realms);
        } catch (IllegalArgumentException | IllegalStateException | ClassCastException
                 | UnsupportedOperationException | IndexOutOfBoundsException e) {
            throw new SimFormatException("malformed payload: " + e.getMessage(), e);
        }
    }

    private static WorldState read(Obj o, RealmTable realms) {
        WorldState s = new WorldState();
        s.seed = o.lng("seed");
        s.tierId = o.str("tier");
        s.genesisDaysPerYear = o.integer("genesis_days_per_year");
        s.prehistoryDays = o.lng("prehistory_days");
        s.day = o.lng("day");
        s.nextPersonId = o.integer("next_person_id");
        s.nextSectId = o.integer("next_sect_id");
        s.nextEventId = o.lng("next_event_id");
        Obj sch = o.obj("scheduler");
        s.scheduler = new SettlementScheduler(
                sch.optLong("last_calendar_day", SettlementScheduler.UNANCHORED),
                sch.optInt("pending_days", 0), sch.optBool("paused", false));
        for (Obj r : o.objects("regions")) {
            RegionState rs = new RegionState(r.str("id"));
            rs.richness = r.optInt("richness", 100);
            s.regions.put(rs.id, rs);
        }
        for (Obj so : o.objects("sects")) {
            Sect sect = readSect(so);
            s.sects.put(sect.id, sect);
        }
        for (Obj po : o.objects("persons")) {
            Person p = readPerson(po, realms);
            s.persons.put(p.id, p);
        }
        for (Obj to : o.objects("tombstones")) {
            Tombstone t = readTombstone(to, realms);
            s.tombstones.put(t.id, t);
        }
        long lastId = 0;
        for (Obj eo : o.objects("chronicle")) {
            SimEvent e = readEvent(eo);
            if (e.id() <= lastId) {
                throw new SimFormatException("chronicle ids must increase (" + e.id() + " after " + lastId + ")");
            }
            lastId = e.id();
            s.chronicle.add(e);
        }
        if (s.nextEventId <= lastId) {
            throw new SimFormatException("next_event_id " + s.nextEventId + " is not above the last event " + lastId);
        }
        return s;
    }

    private static Sect readSect(Obj o) {
        Sect s = new Sect();
        s.id = o.integer("id");
        s.name = o.str("name");
        s.homeRegionId = o.str("region");
        s.gateX = o.integer("gate_x");
        s.gateZ = o.integer("gate_z");
        s.gateRealized = o.optBool("gate_realized", false);
        s.founderId = o.optInt("founder", -1);
        s.foundedDay = o.lng("founded_day");
        s.masterId = o.integer("master");
        s.masterSinceDay = o.optLong("master_since", 0);
        s.resources = o.optDouble("resources", 0.0);
        s.prestige = o.optDouble("prestige", 0.0);
        s.signatureTechniqueId = o.optStr("signature_technique", "");
        s.basicTechniqueId = o.optStr("basic_technique", "");
        for (Obj r : o.optObjects("relations")) {
            SectRelation rel = new SectRelation(r.integer("other"));
            rel.value = r.optInt("value", 0);
            rel.state = r.optStr("state", SectRelation.NONE);
            rel.causeEventId = r.optLong("cause", -1);
            rel.sinceDay = r.optLong("since", -1);
            rel.score = r.optInt("score", 0);
            rel.tributeUntilDay = r.optLong("tribute_until", -1);
            rel.lastWarEndDay = r.optLong("last_war_end", -1);
            rel.lastBattleDay = r.optLong("last_battle", -1);
            rel.lastChampionA = r.optInt("last_champion_a", -1);
            rel.lastChampionB = r.optInt("last_champion_b", -1);
            s.relations.put(rel.other, rel);
        }
        s.state = o.str("state");
        s.destroyedDay = o.optLong("destroyed_day", -1);
        s.parentSectId = o.optInt("parent", -1);
        s.declineSinceDay = o.optLong("decline_since", -1);
        s.declineCauseEventId = o.optLong("decline_cause", -1);
        s.victories = o.optInt("victories", 0);
        s.vacancyCauseEventId = o.optLong("vacancy_cause", -1);
        return s;
    }

    private static Person readPerson(Obj o, RealmTable realms) {
        Person p = new Person();
        p.id = o.integer("id");
        p.surname = o.str("surname");
        p.given = o.str("given");
        p.gender = o.optStr("gender", "m");
        p.birthDay = o.lng("birth");
        p.root = o.intArray("root", 5);
        p.rootEventId = o.optLong("root_event", -1);
        p.realm = realmIndex(realms, o.str("realm"), "person " + p.id);
        p.stage = o.integer("stage");
        if (p.stage < 0 || p.stage > realms.get(p.realm).lastStage()) {
            throw new SimFormatException("person " + p.id + " has stage " + p.stage + " outside realm " + realms.get(p.realm).id());
        }
        p.progress = o.dbl("progress");
        p.failures = o.optInt("failures", 0);
        p.bonusLifespanYears = o.optInt("bonus_lifespan", 0);
        p.lifespanEventId = o.optLong("lifespan_event", -1);
        p.sectId = o.optInt("sect", -1);
        p.rank = o.optStr("rank", "rogue");
        p.masterId = o.optInt("master", -1);
        p.joinedDay = o.optLong("joined", 0);
        p.regionId = o.str("region");
        p.status = o.optStr("status", "at_sect");
        p.statusUntil = o.optLong("status_until", -1);
        p.homeRegionId = o.optStr("home", p.regionId);
        p.destRegionId = o.optStr("dest", "");
        int[] traits = o.intArray("traits", 5);
        p.ambition = traits[0];
        p.aggression = traits[1];
        p.caution = traits[2];
        p.wanderlust = traits[3];
        p.loyalty = traits[4];
        for (JsonArray ra : o.optArrays("relations")) {
            Relation rel = new Relation(ra.get(0).getAsInt(), ra.get(1).getAsString(), ra.get(2).getAsInt(),
                    ra.get(3).getAsLong());
            if (ra.size() > 4) {
                rel.reason = ra.get(4).getAsString();
            }
            p.relations.add(rel);
        }
        p.techniqueId = o.optStr("technique", "");
        p.techniqueEventId = o.optLong("technique_event", -1);
        for (JsonArray ba : o.optArrays("boons")) {
            p.boons.add(new Boon(ba.get(0).getAsString(), ba.get(1).getAsString(), ba.get(2).getAsDouble(),
                    ba.get(3).getAsLong()));
        }
        p.injury = o.optInt("injury", 0);
        p.injuryEventId = o.optLong("injury_event", -1);
        p.injurerId = o.optInt("injurer", -1);
        p.daoName = o.optStr("dao_name", "");
        p.quarryId = o.optInt("quarry", -1);
        p.insightDay = o.optLong("insight_day", -1);
        p.journeys = o.optInt("journeys", 0);
        p.kills = o.optInt("kills", 0);
        p.fortunes = o.optInt("fortunes", 0);
        return p;
    }

    private static Tombstone readTombstone(Obj o, RealmTable realms) {
        Tombstone t = new Tombstone();
        t.id = o.integer("id");
        t.name = o.str("name");
        t.title = o.optStr("title", "");
        t.daoName = o.optStr("dao_name", "");
        t.gender = o.optStr("gender", "m");
        t.sectId = o.optInt("sect", -1);
        t.rank = o.optStr("rank", "rogue");
        t.rootGrade = o.optStr("root_grade", "");
        t.realm = realmIndex(realms, o.str("realm"), "tombstone " + t.id);
        t.stage = o.optInt("stage", 0);
        t.birthDay = o.lng("birth");
        t.deathDay = o.lng("death");
        t.cause = o.optStr("cause", "");
        t.killerId = o.optInt("killer", -1);
        t.deathEventId = o.optLong("death_event", -1);
        t.masterId = o.optInt("master", -1);
        t.techniqueId = o.optStr("technique", "");
        t.techniqueGrade = o.optStr("technique_grade", "");
        return t;
    }

    private static SimEvent readEvent(Obj o) {
        List<Integer> actors = new ArrayList<>();
        for (JsonElement a : o.arr("actors")) {
            actors.add(a.getAsInt());
        }
        List<Integer> sects = new ArrayList<>();
        for (JsonElement a : o.arr("sects")) {
            sects.add(a.getAsInt());
        }
        List<String> params = new ArrayList<>();
        for (JsonElement a : o.arr("params")) {
            params.add(a.getAsString());
        }
        return new SimEvent(o.lng("id"), o.lng("day"), o.str("type"), o.integer("importance"), actors, sects,
                o.optStr("region", ""), o.optLong("cause", -1), o.str("key"), params);
    }

    private static int realmIndex(RealmTable realms, String id, String owner) {
        int index = realms.indexOf(id);
        if (index < 0) {
            throw new SimFormatException(owner + " has unknown realm \"" + id + "\"");
        }
        return index;
    }

    /** JSON object accessor that names the path in every error. */
    private static final class Obj {
        private final JsonObject json;
        private final String path;

        Obj(JsonObject json, String path) {
            this.json = json;
            this.path = path;
        }

        private JsonElement req(String key) {
            JsonElement e = json.get(key);
            if (e == null || e.isJsonNull()) {
                throw new SimFormatException("missing field " + path + key);
            }
            return e;
        }

        private SimFormatException bad(String key, String expected) {
            return new SimFormatException("field " + path + key + " must be " + expected);
        }

        String str(String key) {
            JsonElement e = req(key);
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
                throw bad(key, "a string");
            }
            return e.getAsString();
        }

        long lng(String key) {
            JsonElement e = req(key);
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
                throw bad(key, "a number");
            }
            try {
                return e.getAsJsonPrimitive().getAsBigDecimal().longValueExact();
            } catch (ArithmeticException ex) {
                throw bad(key, "an integer");
            }
        }

        int integer(String key) {
            long v = lng(key);
            if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
                throw bad(key, "a 32-bit integer");
            }
            return (int) v;
        }

        double dbl(String key) {
            JsonElement e = req(key);
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
                throw bad(key, "a number");
            }
            return e.getAsDouble();
        }

        boolean has(String key) {
            return json.has(key) && !json.get(key).isJsonNull();
        }

        String optStr(String key, String def) {
            return has(key) ? str(key) : def;
        }

        int optInt(String key, int def) {
            return has(key) ? integer(key) : def;
        }

        long optLong(String key, long def) {
            return has(key) ? lng(key) : def;
        }

        double optDouble(String key, double def) {
            return has(key) ? dbl(key) : def;
        }

        boolean optBool(String key, boolean def) {
            if (!has(key)) {
                return def;
            }
            JsonElement e = json.get(key);
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) {
                throw bad(key, "true or false");
            }
            return e.getAsBoolean();
        }

        JsonArray arr(String key) {
            JsonElement e = req(key);
            if (!e.isJsonArray()) {
                throw bad(key, "a list");
            }
            return e.getAsJsonArray();
        }

        Obj obj(String key) {
            JsonElement e = req(key);
            if (!e.isJsonObject()) {
                throw bad(key, "an object");
            }
            return new Obj(e.getAsJsonObject(), path + key + ".");
        }

        int[] intArray(String key, int size) {
            JsonArray a = arr(key);
            if (a.size() != size) {
                throw bad(key, "a list of " + size + " integers");
            }
            int[] out = new int[size];
            for (int i = 0; i < size; i++) {
                out[i] = a.get(i).getAsInt();
            }
            return out;
        }

        List<Obj> objects(String key) {
            return objectsOf(arr(key), key);
        }

        List<Obj> optObjects(String key) {
            return has(key) ? objectsOf(arr(key), key) : List.of();
        }

        private List<Obj> objectsOf(JsonArray a, String key) {
            List<Obj> out = new ArrayList<>(a.size());
            for (int i = 0; i < a.size(); i++) {
                if (!a.get(i).isJsonObject()) {
                    throw bad(key + "[" + i + "]", "an object");
                }
                out.add(new Obj(a.get(i).getAsJsonObject(), path + key + "[" + i + "]."));
            }
            return out;
        }

        List<JsonArray> optArrays(String key) {
            if (!has(key)) {
                return List.of();
            }
            JsonArray a = arr(key);
            List<JsonArray> out = new ArrayList<>(a.size());
            for (int i = 0; i < a.size(); i++) {
                if (!a.get(i).isJsonArray() || a.get(i).getAsJsonArray().size() < 4) {
                    throw bad(key + "[" + i + "]", "a list of at least 4 values");
                }
                out.add(a.get(i).getAsJsonArray());
            }
            return out;
        }
    }
}
