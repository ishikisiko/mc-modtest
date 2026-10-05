package com.example.myvillage.sim.data;

import com.google.gson.JsonArray;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The content worker's three files ({@code names.json}, {@code techniques.json}, {@code lore.json}),
 * parsed against the fixed schemas of design §6.1.
 */
public final class ContentTables {
    public static final Set<String> GRADES = Set.of("huang", "xuan", "di", "tian");
    /** Grades from weakest to strongest; the index is the grade's rank. */
    public static final List<String> GRADE_ORDER = List.of("huang", "xuan", "di", "tian");
    public static final Set<String> ELEMENTS_OR_NONE = Set.of("metal", "wood", "water", "fire", "earth", "none");
    public static final Set<String> SITE_KINDS = Set.of("ruin", "cave", "secret_realm", "battlefield", "tomb");

    private ContentTables() {
    }

    public record WeightedText(String text, int weight) {
    }

    public record Names(
            List<String> surnames,
            List<String> givenMale,
            List<String> givenFemale,
            List<String> givenNeutral,
            List<String> daoTitles,
            List<String> sectPrefixes,
            List<WeightedText> sectSuffixes) {
    }

    public record Technique(String id, String name, String grade, String element) {
        public int gradeRank() {
            return GRADE_ORDER.indexOf(grade);
        }
    }

    public record Artifact(String id, String name, String grade) {
        public int gradeRank() {
            return GRADE_ORDER.indexOf(grade);
        }
    }

    public record Site(String id, String name, String kind) {
    }

    public record Beast(String id, String name, int rank) {
    }

    public record Lore(List<Artifact> artifacts, List<Site> sites, List<Beast> beasts) {
    }

    static Names parseNames(SimJson.Fields root) {
        root.schema();
        JsonArray suffixJson = root.nonEmptyArray("sect_suffixes");
        List<WeightedText> suffixes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < suffixJson.size(); i++) {
            SimJson.Fields s = root.element("sect_suffixes", suffixJson, i, Set.of("text", "weight"));
            String text = s.nonEmptyString("text");
            if (!seen.add(text)) {
                throw s.error("text", "repeats \"" + text + "\"");
            }
            suffixes.add(new WeightedText(text, s.positiveInteger("weight")));
        }
        return new Names(
                root.stringList("surnames"),
                root.stringList("given_male"),
                root.stringList("given_female"),
                root.stringList("given_neutral"),
                root.stringList("dao_titles"),
                root.stringList("sect_prefixes"),
                List.copyOf(suffixes));
    }

    static List<Technique> parseTechniques(SimJson.Fields root) {
        root.schema();
        JsonArray array = root.nonEmptyArray("techniques");
        List<Technique> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            SimJson.Fields t = root.element("techniques", array, i, Set.of("id", "name", "grade", "element"));
            String id = t.nonEmptyString("id");
            if (!ids.add(id)) {
                throw t.error("id", "repeats \"" + id + "\"");
            }
            String name = t.nonEmptyString("name");
            if (!names.add(name)) {
                throw t.error("name", "repeats \"" + name + "\"");
            }
            out.add(new Technique(id, name, t.oneOf("grade", GRADES), t.oneOf("element", ELEMENTS_OR_NONE)));
        }
        for (String grade : GRADE_ORDER) {
            if (out.stream().noneMatch(t -> t.grade().equals(grade))) {
                throw root.error("techniques", "needs at least one technique of grade " + grade);
            }
        }
        return List.copyOf(out);
    }

    static Lore parseLore(SimJson.Fields root) {
        root.schema();
        JsonArray artifactJson = root.nonEmptyArray("artifacts");
        List<Artifact> artifacts = new ArrayList<>();
        Set<String> artifactIds = new HashSet<>();
        for (int i = 0; i < artifactJson.size(); i++) {
            SimJson.Fields a = root.element("artifacts", artifactJson, i, Set.of("id", "name", "grade"));
            artifacts.add(new Artifact(uniqueId(a, artifactIds), a.nonEmptyString("name"), a.oneOf("grade", GRADES)));
        }
        JsonArray siteJson = root.nonEmptyArray("sites");
        List<Site> sites = new ArrayList<>();
        Set<String> siteIds = new HashSet<>();
        for (int i = 0; i < siteJson.size(); i++) {
            SimJson.Fields s = root.element("sites", siteJson, i, Set.of("id", "name", "kind"));
            sites.add(new Site(uniqueId(s, siteIds), s.nonEmptyString("name"), s.oneOf("kind", SITE_KINDS)));
        }
        JsonArray beastJson = root.nonEmptyArray("beasts");
        List<Beast> beasts = new ArrayList<>();
        Set<String> beastIds = new HashSet<>();
        for (int i = 0; i < beastJson.size(); i++) {
            SimJson.Fields b = root.element("beasts", beastJson, i, Set.of("id", "name", "rank"));
            beasts.add(new Beast(uniqueId(b, beastIds), b.nonEmptyString("name"), b.integer("rank", 1, 4)));
        }
        return new Lore(List.copyOf(artifacts), List.copyOf(sites), List.copyOf(beasts));
    }

    private static String uniqueId(SimJson.Fields f, Set<String> ids) {
        String id = f.nonEmptyString("id");
        if (!ids.add(id)) {
            throw f.error("id", "repeats \"" + id + "\"");
        }
        return id;
    }
}
