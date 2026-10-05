package com.example.myvillage.sim.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every balance number of the world sim ({@code rules.json}). Rates are per year; the engine
 * converts them with the caller's {@code daysPerYear}. Java holds no balance constants.
 */
public record Rules(
        Time time,
        Map<String, Tier> tiers,
        Chronicle chronicle,
        Scheduler scheduler,
        Roots roots,
        Techniques techniques,
        Cultivation cultivation,
        Breakthrough breakthrough,
        Injury injury,
        Map<String, Integer> deathImportanceByRank,
        Entrants entrants,
        Sects sects,
        Genesis genesis,
        Gates gates) {

    public static final List<String> RANKS = List.of("sect_master", "elder", "inner", "outer", "rogue");
    public static final List<String> STATUSES = List.of("at_sect", "travelling", "secluded");

    public record Time(int prehistoryYears, int defaultDaysPerYear) {
    }

    public record Tier(String id, int population, int sects, double rogueShare) {
    }

    public record Chronicle(int minorPerPerson) {
    }

    public record Scheduler(int maxPendingDays) {
    }

    /** A root grade; {@code maxElements} counts the elements at or above the threshold. */
    public record RootGrade(String id, int maxElements, int weight, double cultivation, double breakthrough) {
    }

    public record Roots(int elementThresholdBp, List<RootGrade> grades) {
    }

    public record GradeFactors(double cultivation, double breakthrough, double combat) {
    }

    public record Techniques(Map<String, GradeFactors> grades, GradeFactors none, double elementMatchBonus) {
    }

    public record Cultivation(
            double basePerYear,
            Map<String, Double> status,
            double qiBase,
            double qiPerPoint,
            Map<String, Double> rank,
            double resourceBonus,
            double resourceReferencePerMember,
            double masterGuidance,
            double injuryPerPoint,
            double injuryFloor) {
    }

    public record Breakthrough(
            double attemptRatePerYear,
            double cautionWeight,
            int desperateYearsLeft,
            double desperateAttemptRatePerYear,
            double desperateChanceFactor,
            double desperateDeathFactor,
            double failurePenalty,
            double retryDamping,
            double ageWeight,
            double injuryPerPoint,
            double masterBonus,
            double minChance,
            double maxChance,
            int woundCauseThreshold) {
    }

    public record Injury(double healPerYear) {
    }

    public record Entrants(
            double baseRate,
            double gain,
            double maxFractionPerYear,
            int[] age,
            double rogueTechniqueChance) {
    }

    public record RealmStage(String realm, int stage) {
    }

    public record Pill(double cost, double bonus) {
    }

    public record Sects(
            double recruitWeightFloor,
            double recruitSizeDamping,
            double prestigePerCandidate,
            int maxCandidates,
            RealmStage promoteInner,
            RealmStage promoteElder,
            int disciplesPerMaster,
            double mentorChancePerYear,
            double incomeBase,
            double incomePerMember,
            double upkeepPerMember,
            double resourceCapPerMember,
            double prestigeSmoothing,
            Map<String, Pill> pills) {
    }

    public record Genesis(
            int[] sectAgeYears,
            List<RealmStage> masters,
            double[] sizeWeights,
            List<String> signatureGrades,
            double elderShare,
            double innerShare,
            double regionQiExponent,
            double regionCrowding,
            Map<String, int[]> ages,
            double rogueFoundationChance,
            int founderRealmBonus) {
    }

    public record Gates(int maxRadius, int minSpacing, int maxOffset, int retries) {
    }

    public Tier tier(String id) {
        Tier tier = tiers.get(id);
        if (tier == null) {
            throw new IllegalArgumentException("unknown world-sim tier \"" + id + "\" (known: " + tiers.keySet() + ")");
        }
        return tier;
    }

    // ---------------------------------------------------------------- parsing

    static Rules parse(String file, JsonObject json, RealmTable realms) {
        SimJson.Fields root = SimJson.Fields.root(file, json, Set.of(
                "schema", "time", "tiers", "chronicle", "scheduler", "roots", "techniques", "cultivation",
                "breakthrough", "injury", "death_importance_by_rank", "entrants", "sects", "genesis", "gates"));
        root.schema();

        SimJson.Fields t = root.object("time", Set.of("prehistory_years", "default_days_per_year"));
        Time time = new Time(t.nonNegativeInteger("prehistory_years"), t.positiveInteger("default_days_per_year"));

        SimJson.Fields tiersJson = root.object("tiers", null);
        Map<String, Tier> tiers = new LinkedHashMap<>();
        for (String id : tiersJson.keys()) {
            SimJson.Fields tier = tiersJson.object(id, Set.of("population", "sects", "rogue_share"));
            tiers.put(id, new Tier(id, tier.positiveInteger("population"), tier.positiveInteger("sects"),
                    tier.fraction("rogue_share")));
        }
        if (tiers.isEmpty()) {
            throw root.error("tiers", "needs at least one tier");
        }

        Chronicle chronicle = new Chronicle(
                root.object("chronicle", Set.of("minor_per_person")).positiveInteger("minor_per_person"));
        Scheduler scheduler = new Scheduler(
                root.object("scheduler", Set.of("max_pending_days")).positiveInteger("max_pending_days"));

        SimJson.Fields r = root.object("roots", Set.of("element_threshold_bp", "grades"));
        int threshold = r.integer("element_threshold_bp", 1, 2000);
        JsonArray gradeJson = r.nonEmptyArray("grades");
        List<RootGrade> grades = new ArrayList<>();
        for (int i = 0; i < gradeJson.size(); i++) {
            SimJson.Fields g = r.element("grades", gradeJson, i,
                    Set.of("id", "max_elements", "weight", "cultivation", "breakthrough"));
            int maxElements = g.integer("max_elements", 1, 5);
            if (maxElements != i + 1) {
                throw g.error("max_elements", "grades must list 1..5 elements in order, expected " + (i + 1));
            }
            grades.add(new RootGrade(g.nonEmptyString("id"), maxElements, g.positiveInteger("weight"),
                    g.positiveNumber("cultivation"), g.positiveNumber("breakthrough")));
        }
        if (grades.size() != 5) {
            throw r.error("grades", "must list exactly five grades (1..5 elements)");
        }
        Roots roots = new Roots(threshold, List.copyOf(grades));

        SimJson.Fields te = root.object("techniques", Set.of("grades", "none", "element_match_bonus"));
        SimJson.Fields tg = te.object("grades", ContentTables.GRADES);
        Map<String, GradeFactors> gradeFactors = new LinkedHashMap<>();
        for (String grade : ContentTables.GRADE_ORDER) {
            gradeFactors.put(grade, gradeFactors(tg.object(grade, GRADE_FACTOR_FIELDS)));
        }
        Techniques techniques = new Techniques(Collections.unmodifiableMap(gradeFactors),
                gradeFactors(te.object("none", GRADE_FACTOR_FIELDS)), te.nonNegativeNumber("element_match_bonus"));

        SimJson.Fields c = root.object("cultivation", Set.of("base_per_year", "status", "qi_base", "qi_per_point",
                "rank", "resource_bonus", "resource_reference_per_member", "master_guidance", "injury_per_point",
                "injury_floor"));
        Cultivation cultivation = new Cultivation(
                c.positiveNumber("base_per_year"),
                numberMap(c.object("status", Set.copyOf(STATUSES)), STATUSES),
                c.nonNegativeNumber("qi_base"),
                c.nonNegativeNumber("qi_per_point"),
                numberMap(c.object("rank", Set.copyOf(RANKS)), RANKS),
                c.nonNegativeNumber("resource_bonus"),
                c.positiveNumber("resource_reference_per_member"),
                c.nonNegativeNumber("master_guidance"),
                c.nonNegativeNumber("injury_per_point"),
                c.fraction("injury_floor"));

        SimJson.Fields b = root.object("breakthrough", Set.of("attempt_rate_per_year", "caution_weight",
                "desperate_years_left", "desperate_attempt_rate_per_year", "desperate_chance_factor",
                "desperate_death_factor", "failure_penalty", "retry_damping", "age_weight", "injury_per_point", "master_bonus",
                "min_chance", "max_chance", "wound_cause_threshold"));
        Breakthrough breakthrough = new Breakthrough(
                b.fraction("attempt_rate_per_year"),
                b.fraction("caution_weight"),
                b.nonNegativeInteger("desperate_years_left"),
                b.fraction("desperate_attempt_rate_per_year"),
                b.fraction("desperate_chance_factor"),
                b.nonNegativeNumber("desperate_death_factor"),
                b.fraction("failure_penalty"),
                b.fraction("retry_damping"),
                b.fraction("age_weight"),
                b.nonNegativeNumber("injury_per_point"),
                b.nonNegativeNumber("master_bonus"),
                b.fraction("min_chance"),
                b.fraction("max_chance"),
                b.integer("wound_cause_threshold", 0, 100));

        Injury injury = new Injury(root.object("injury", Set.of("heal_per_year")).nonNegativeNumber("heal_per_year"));

        SimJson.Fields di = root.object("death_importance_by_rank", Set.copyOf(RANKS));
        Map<String, Integer> deathImportance = new LinkedHashMap<>();
        for (String rank : di.keys()) {
            deathImportance.put(rank, di.integer(rank, 1, 3));
        }

        SimJson.Fields e = root.object("entrants", Set.of("base_rate", "gain", "max_fraction_per_year", "age",
                "rogue_technique_chance"));
        Entrants entrants = new Entrants(e.nonNegativeNumber("base_rate"), e.nonNegativeNumber("gain"),
                e.fraction("max_fraction_per_year"), e.intRange("age"), e.fraction("rogue_technique_chance"));

        SimJson.Fields s = root.object("sects", Set.of("recruit_weight_floor", "recruit_size_damping", "prestige_per_candidate",
                "max_candidates", "promotion", "disciples_per_master", "mentor_chance_per_year", "income_base",
                "income_per_member", "upkeep_per_member", "resource_cap_per_member", "prestige_smoothing", "pills"));
        SimJson.Fields promo = s.object("promotion", Set.of("inner", "elder"));
        SimJson.Fields pillsJson = s.object("pills", null);
        Map<String, Pill> pills = new LinkedHashMap<>();
        for (String realm : pillsJson.keys()) {
            if (realms.indexOf(realm) <= 0) {
                throw pillsJson.error(realm, "must name a realm reached by breakthrough");
            }
            SimJson.Fields p = pillsJson.object(realm, Set.of("cost", "bonus"));
            pills.put(realm, new Pill(p.positiveNumber("cost"), p.fraction("bonus")));
        }
        Sects sects = new Sects(
                s.nonNegativeNumber("recruit_weight_floor"),
                s.nonNegativeNumber("recruit_size_damping"),
                s.positiveNumber("prestige_per_candidate"),
                s.positiveInteger("max_candidates"),
                realmStage(promo.object("inner", REALM_STAGE_FIELDS), realms),
                realmStage(promo.object("elder", REALM_STAGE_FIELDS), realms),
                s.positiveInteger("disciples_per_master"),
                s.fraction("mentor_chance_per_year"),
                s.nonNegativeNumber("income_base"),
                s.nonNegativeNumber("income_per_member"),
                s.nonNegativeNumber("upkeep_per_member"),
                s.positiveNumber("resource_cap_per_member"),
                s.fraction("prestige_smoothing"),
                Collections.unmodifiableMap(pills));

        SimJson.Fields g = root.object("genesis", Set.of("sect_age_years", "masters", "size_weights",
                "signature_grades", "elder_share", "inner_share", "region_qi_exponent", "region_crowding", "ages",
                "rogue_foundation_chance", "founder_realm_bonus"));
        JsonArray mastersJson = g.nonEmptyArray("masters");
        List<RealmStage> masters = new ArrayList<>();
        for (int i = 0; i < mastersJson.size(); i++) {
            masters.add(realmStage(g.element("masters", mastersJson, i, REALM_STAGE_FIELDS), realms));
        }
        List<String> signatureGrades = new ArrayList<>();
        JsonArray sigJson = g.nonEmptyArray("signature_grades");
        for (int i = 0; i < sigJson.size(); i++) {
            String grade = sigJson.get(i).getAsString();
            if (!ContentTables.GRADES.contains(grade)) {
                throw g.error("signature_grades[" + i + "]", "must be one of " + ContentTables.GRADES);
            }
            signatureGrades.add(grade);
        }
        SimJson.Fields agesJson = g.object("ages", null);
        Map<String, int[]> ages = new LinkedHashMap<>();
        for (RealmTable.Realm realm : realms.realms()) {
            if (!agesJson.has(realm.id())) {
                throw agesJson.error(realm.id(), "is required (an age range for every realm)");
            }
            int[] range = agesJson.intRange(realm.id());
            if (range[1] >= realm.lifespanYears()) {
                throw agesJson.error(realm.id(), "upper age must be below the realm lifespan " + realm.lifespanYears());
            }
            ages.put(realm.id(), range);
        }
        Genesis genesis = new Genesis(
                g.intRange("sect_age_years"),
                List.copyOf(masters),
                g.numberList("size_weights"),
                List.copyOf(signatureGrades),
                g.fraction("elder_share"),
                g.fraction("inner_share"),
                g.nonNegativeNumber("region_qi_exponent"),
                g.nonNegativeNumber("region_crowding"),
                Collections.unmodifiableMap(ages),
                g.fraction("rogue_foundation_chance"),
                g.nonNegativeInteger("founder_realm_bonus"));

        SimJson.Fields ga = root.object("gates", Set.of("max_radius", "min_spacing", "max_offset", "retries"));
        Gates gates = new Gates(ga.positiveInteger("max_radius"), ga.positiveInteger("min_spacing"),
                ga.positiveInteger("max_offset"), ga.positiveInteger("retries"));

        return new Rules(time, Collections.unmodifiableMap(tiers), chronicle, scheduler, roots, techniques,
                cultivation, breakthrough, injury, Collections.unmodifiableMap(deathImportance), entrants, sects,
                genesis, gates);
    }

    private static final Set<String> GRADE_FACTOR_FIELDS = Set.of("cultivation", "breakthrough", "combat");
    private static final Set<String> REALM_STAGE_FIELDS = Set.of("realm", "stage");

    private static GradeFactors gradeFactors(SimJson.Fields f) {
        return new GradeFactors(f.positiveNumber("cultivation"), f.positiveNumber("breakthrough"),
                f.positiveNumber("combat"));
    }

    private static Map<String, Double> numberMap(SimJson.Fields f, List<String> required) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String key : required) {
            out.put(key, f.nonNegativeNumber(key));
        }
        return Collections.unmodifiableMap(out);
    }

    private static RealmStage realmStage(SimJson.Fields f, RealmTable realms) {
        String realm = f.nonEmptyString("realm");
        int index = realms.indexOf(realm);
        if (index < 0) {
            throw f.error("realm", "names unknown realm \"" + realm + "\"");
        }
        int stage = f.integer("stage", 0, realms.get(index).lastStage());
        return new RealmStage(realm, stage);
    }
}
