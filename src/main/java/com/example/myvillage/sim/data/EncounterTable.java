package com.example.myvillage.sim.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fortunes (奇遇) a person can stumble on ({@code encounters.json}). Each entry has a draw weight,
 * conditions on where and who, and effects. Its chronicle line is
 * {@code world_sim.event.fortune.<text>} with params (name, region[, site][, item]): the site when
 * {@code site_kind} is set, the item (technique or artifact name) when an effect grants one. A
 * {@code heritage} effect (a manual of a lost heritage) adds two: the technique and the heritage name;
 * such an encounter only happens while a heritage is lost.
 */
public record EncounterTable(List<Encounter> encounters) {
    public static final String TEXT_PREFIX = "world_sim.event.fortune.";
    public static final Set<String> EFFECT_KINDS = Set.of(
            "progress", "technique", "breakthrough_pill", "lifespan", "root", "artifact", "injury", "death",
            "heritage");
    public static final Set<String> STATUSES = Set.of("at_sect", "travelling", "secluded");

    /**
     * @param kind   one of {@link #EFFECT_KINDS}
     * @param amount progress years, pill bonus, lifespan years, root basis points, injury points,
     *               or death chance; 0 for technique/artifact/heritage
     * @param grade  technique/artifact grade, else null
     */
    public record Effect(String kind, double amount, String grade) {
    }

    /**
     * @param rarity    0 common .. 3 legendary; rarer entries favour high-tier, dangerous regions
     * @param siteKind  a lore site kind named in the text, or null
     * @param realms    realm ids allowed (empty = any)
     * @param statuses  statuses allowed (empty = any)
     * @param contested two finders may fight over it
     * @param quiet     no line of its own: the progress shows in the next stage-up line as an insight
     */
    public record Encounter(
            String id,
            int weight,
            int rarity,
            int importance,
            String text,
            String siteKind,
            int minDanger,
            int maxDanger,
            int minTier,
            List<String> realms,
            List<String> statuses,
            boolean contested,
            List<Effect> effects,
            boolean quiet) {

        public String textKey() {
            return TEXT_PREFIX + text;
        }

        /** Params of the chronicle line: name, region, then site and item when used. */
        public int paramCount() {
            return 2 + extraParams();
        }

        /** Params after the who block and region: site, item, and the heritage name. */
        public int extraParams() {
            return (siteKind != null ? 1 : 0) + (has("technique") || has("artifact") ? 1 : 0)
                    + (has("heritage") ? 2 : 0);
        }

        public boolean has(String kind) {
            for (Effect e : effects) {
                if (e.kind().equals(kind)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final Set<String> ROOT_FIELDS = Set.of("schema", "encounters");
    private static final Set<String> ENCOUNTER_FIELDS = Set.of(
            "id", "weight", "rarity", "importance", "text", "site_kind", "min_danger", "max_danger", "min_tier",
            "realms", "statuses", "contested", "effects", "quiet");
    private static final Set<String> EFFECT_FIELDS = Set.of("kind", "amount", "grade");

    static EncounterTable parse(String file, JsonObject json, RealmTable realms) {
        SimJson.Fields root = SimJson.Fields.root(file, json, ROOT_FIELDS);
        root.schema();
        JsonArray array = root.nonEmptyArray("encounters");
        List<Encounter> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            SimJson.Fields e = root.element("encounters", array, i, ENCOUNTER_FIELDS);
            String id = e.nonEmptyString("id");
            if (!ids.add(id)) {
                throw e.error("id", "repeats \"" + id + "\"");
            }
            String text = e.nonEmptyString("text");
            if (!text.matches("[a-z0-9_.]+")) {
                throw e.error("text", "must match [a-z0-9_.]+, got \"" + text + "\"");
            }
            String siteKind = e.nullableString("site_kind");
            if (siteKind != null && !ContentTables.SITE_KINDS.contains(siteKind)) {
                throw e.error("site_kind", "must be null or one of " + ContentTables.SITE_KINDS);
            }
            int minDanger = e.integer("min_danger", 0, 10);
            int maxDanger = e.integer("max_danger", minDanger, 10);
            List<String> realmIds = optionalList(e, "realms");
            for (String r : realmIds) {
                if (realms.indexOf(r) < 0) {
                    throw e.error("realms", "names unknown realm \"" + r + "\"");
                }
            }
            List<String> statuses = optionalList(e, "statuses");
            for (String s : statuses) {
                if (!STATUSES.contains(s)) {
                    throw e.error("statuses", "names unknown status \"" + s + "\"");
                }
            }
            JsonArray effectJson = e.nonEmptyArray("effects");
            List<Effect> effects = new ArrayList<>();
            for (int k = 0; k < effectJson.size(); k++) {
                SimJson.Fields f = e.element("effects", effectJson, k, EFFECT_FIELDS);
                String kind = f.oneOf("kind", EFFECT_KINDS);
                String grade = null;
                double amount = 0.0;
                if (kind.equals("technique") || kind.equals("artifact")) {
                    grade = f.oneOf("grade", ContentTables.GRADES);
                    if (f.has("amount")) {
                        throw f.error("amount", "is not used by " + kind);
                    }
                } else if (kind.equals("heritage")) {
                    if (f.has("amount") || f.has("grade")) {
                        throw f.error(f.has("amount") ? "amount" : "grade", "is not used by heritage");
                    }
                } else {
                    amount = kind.equals("death") || kind.equals("breakthrough_pill")
                            ? f.fraction("amount") : f.positiveNumber("amount");
                    if (f.has("grade")) {
                        throw f.error("grade", "is only used by technique and artifact");
                    }
                }
                effects.add(new Effect(kind, amount, grade));
            }
            long items = effects.stream().filter(x -> x.kind().equals("technique") || x.kind().equals("artifact")
                    || x.kind().equals("heritage")).count();
            if (items > 1) {
                throw e.error("effects", "may grant at most one technique, artifact or heritage (the text names one item)");
            }
            out.add(new Encounter(id, e.positiveInteger("weight"), e.integer("rarity", 0, 3),
                    e.integer("importance", 1, 3), text, siteKind, minDanger, maxDanger,
                    e.integer("min_tier", 0, 100), realmIds, statuses, e.bool("contested"), List.copyOf(effects),
                    e.has("quiet") && e.bool("quiet")));
        }
        return new EncounterTable(List.copyOf(out));
    }

    private static List<String> optionalList(SimJson.Fields f, String key) {
        JsonArray array = f.array(key);
        return array.isEmpty() ? List.of() : f.stringList(key);
    }
}
