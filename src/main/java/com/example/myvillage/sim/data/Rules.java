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
        Gates gates,
        Naming naming,
        Importance importance,
        Travel travel,
        Seclusion seclusion,
        Danger danger,
        Fortune fortune,
        Map<String, Double> artifactPower,
        Meetings meetings,
        Combat combat,
        Revenge revenge,
        SectRelations sectRelations,
        Succession succession,
        Decline decline,
        Founding founding) {

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
            double youngSectBoost,
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

    /** Surnames are listed common-first: weight = 1 / (1 + index / surnameRankScale). */
    public record Naming(double surnameRankScale, List<String> bannedNamePrefixes) {
    }

    /** When routine events become notable. */
    public record Importance(int prodigyFoundationAge, RealmStage notable) {
    }

    public record Travel(
            double startRatePerYear,
            Map<String, Double> rankFactor,
            int[] years,
            double movesPerYear,
            double qiWeight,
            double tierWeight,
            double dangerWeight,
            int walledBoldness) {
    }

    public record Seclusion(double ratePerYear, int[] years, double nearCapFactor) {
    }

    public record Danger(
            double beastRatePerYear,
            double dangerExponent,
            double[] beastPower,
            double fleeChance,
            double deathOnLoss,
            int injury,
            double slayProgressYears,
            int recordedBeastRank) {
    }

    public record Fortune(
            double ratePerYear,
            double secludedRatePerYear,
            double tierWeight,
            double rarityBoost,
            int richnessCost,
            int richnessRegenPerYear,
            double contestChance) {
    }

    public record Meetings(
            /** Expected meetings per year by status. */
            Map<String, Double> ratePerYear,
            double friend,
            double quarrel,
            double spar,
            double rob,
            double duelChance,
            double grudgeChance,
            int maxFriends,
            int friendStrength,
            int enemyStrength) {
    }

    public record Combat(
            double steepness,
            double noise,
            double injuryPerPoint,
            double injuryFloor,
            double fleeBase,
            double killBase,
            double killEnemy,
            double killWar,
            double sameSectKillFactor,
            int[] wound,
            double lootChance) {
    }

    public record Revenge(
            int strength,
            int friendMinStrength,
            double confidence,
            double cautionWeight,
            int recklessAggression,
            double seekRatePerYear,
            int giveUpYears,
            int lateYears) {
    }

    public record SectRelations(
            int driftPerYear,
            int neighbourBaseline,
            int killPenalty,
            int notableKillPenalty,
            int woundPenalty,
            int feudAt,
            int warAt,
            double warChancePerYear,
            double trucePerYear,
            double trucePerYearOfWar,
            double battlesPerYear,
            int battleIntervalYears,
            int championMaxInjury,
            int tributeScore,
            int tributeYears,
            double tributeShare,
            int annexBelow,
            int peaceYears,
            int splitValue,
            double victoryPrestige) {
    }

    public record Succession(
            int contestAmbition,
            double contestGap,
            double contestChance,
            double leaveChance,
            double followerShare,
            RealmStage minMaster,
            int minYearsLeft) {
    }

    public record Decline(
            double desertRatePerYear,
            double desertionRatePerYear,
            int minMembers,
            double minShare,
            double recruitFactor,
            double rogueJoinRatePerYear,
            double schismSizeRatio,
            double schismRatePerYear,
            int schismAmbition,
            int graceYears) {
    }

    public record Founding(
            RealmStage minRealm,
            int minAmbition,
            double ratePerYear,
            double deficitBoost,
            double surplusExponent,
            int[] followers) {
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
                "breakthrough", "injury", "death_importance_by_rank", "entrants", "sects", "genesis", "gates", "naming",
                "importance", "travel", "seclusion", "danger", "fortune", "artifact_power", "meetings", "combat",
                "revenge", "sect_relations", "succession", "decline", "founding"));
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

        SimJson.Fields s = root.object("sects", Set.of("recruit_weight_floor", "recruit_size_damping", "young_sect_boost", "prestige_per_candidate",
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
                s.nonNegativeNumber("young_sect_boost"),
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


        SimJson.Fields nm = root.object("naming", Set.of("surname_rank_scale", "banned_name_prefixes"));
        JsonArray banned = nm.array("banned_name_prefixes");
        Naming naming = new Naming(nm.positiveNumber("surname_rank_scale"),
                banned.isEmpty() ? List.of() : nm.stringList("banned_name_prefixes"));

        SimJson.Fields im = root.object("importance", Set.of("prodigy_foundation_age", "notable"));
        Importance importance = new Importance(im.positiveInteger("prodigy_foundation_age"),
                realmStage(im.object("notable", REALM_STAGE_FIELDS), realms));

        SimJson.Fields tr = root.object("travel", Set.of("start_rate_per_year", "rank_factor", "years",
                "moves_per_year", "qi_weight", "tier_weight", "danger_weight", "walled_boldness"));
        Travel travel = new Travel(tr.fraction("start_rate_per_year"),
                numberMap(tr.object("rank_factor", Set.copyOf(RANKS)), RANKS), tr.intRange("years"),
                tr.nonNegativeNumber("moves_per_year"), tr.nonNegativeNumber("qi_weight"),
                tr.nonNegativeNumber("tier_weight"), tr.nonNegativeNumber("danger_weight"),
                tr.integer("walled_boldness", 0, 100));

        SimJson.Fields se = root.object("seclusion", Set.of("rate_per_year", "years", "near_cap_factor"));
        Seclusion seclusion = new Seclusion(se.fraction("rate_per_year"), se.intRange("years"),
                se.nonNegativeNumber("near_cap_factor"));

        SimJson.Fields dg = root.object("danger", Set.of("beast_rate_per_year", "danger_exponent", "beast_power",
                "flee_chance", "death_on_loss", "injury", "slay_progress_years", "recorded_beast_rank"));
        Danger danger = new Danger(dg.fraction("beast_rate_per_year"), dg.nonNegativeNumber("danger_exponent"),
                dg.numbers("beast_power", 4), dg.fraction("flee_chance"), dg.fraction("death_on_loss"),
                dg.integer("injury", 0, 100), dg.nonNegativeNumber("slay_progress_years"),
                dg.integer("recorded_beast_rank", 1, 5));

        SimJson.Fields fo = root.object("fortune", Set.of("rate_per_year", "secluded_rate_per_year", "tier_weight",
                "rarity_boost", "richness_cost", "richness_regen_per_year", "contest_chance"));
        Fortune fortune = new Fortune(fo.fraction("rate_per_year"), fo.fraction("secluded_rate_per_year"),
                fo.fraction("tier_weight"), fo.positiveNumber("rarity_boost"), fo.integer("richness_cost", 0, 100),
                fo.integer("richness_regen_per_year", 0, 100), fo.fraction("contest_chance"));

        SimJson.Fields ap = root.object("artifact_power", ContentTables.GRADES);
        Map<String, Double> artifactPower = new LinkedHashMap<>();
        for (String grade : ContentTables.GRADE_ORDER) {
            artifactPower.put(grade, ap.positiveNumber(grade));
        }

        SimJson.Fields me = root.object("meetings", Set.of("rate_per_year", "friend", "quarrel", "spar", "rob",
                "duel_chance", "grudge_chance", "max_friends", "friend_strength", "enemy_strength"));
        Meetings meetings = new Meetings(numberMap(me.object("rate_per_year", Set.copyOf(STATUSES)), STATUSES),
                me.nonNegativeNumber("friend"), me.nonNegativeNumber("quarrel"), me.nonNegativeNumber("spar"),
                me.nonNegativeNumber("rob"), me.fraction("duel_chance"), me.fraction("grudge_chance"), me.nonNegativeInteger("max_friends"),
                me.integer("friend_strength", 1, 100),
                me.integer("enemy_strength", 1, 100));

        SimJson.Fields co = root.object("combat", Set.of("steepness", "noise", "injury_per_point", "injury_floor",
                "flee_base", "kill_base", "kill_enemy", "kill_war", "same_sect_kill_factor", "wound", "loot_chance"));
        Combat combat = new Combat(co.positiveNumber("steepness"), co.fraction("noise"),
                co.nonNegativeNumber("injury_per_point"), co.fraction("injury_floor"), co.fraction("flee_base"),
                co.fraction("kill_base"), co.fraction("kill_enemy"), co.fraction("kill_war"),
                co.fraction("same_sect_kill_factor"), co.intRange("wound"),
                co.fraction("loot_chance"));

        SimJson.Fields rv = root.object("revenge", Set.of("strength", "friend_min_strength", "confidence",
                "caution_weight", "reckless_aggression", "seek_rate_per_year", "give_up_years", "late_years"));
        Revenge revenge = new Revenge(rv.integer("strength", 1, 100), rv.integer("friend_min_strength", 0, 100),
                rv.fraction("confidence"), rv.fraction("caution_weight"), rv.integer("reckless_aggression", 0, 101),
                rv.fraction("seek_rate_per_year"), rv.positiveInteger("give_up_years"), rv.nonNegativeInteger("late_years"));

        SimJson.Fields sr = root.object("sect_relations", Set.of("drift_per_year", "neighbour_baseline", "kill_penalty",
                "notable_kill_penalty", "wound_penalty", "feud_at", "war_at", "war_chance_per_year", "truce_per_year",
                "truce_per_year_of_war", "battles_per_year", "battle_interval_years", "champion_max_injury", "tribute_score", "tribute_years", "tribute_share",
                "annex_below", "peace_years", "split_value", "victory_prestige"));
        SectRelations sectRelations = new SectRelations(sr.nonNegativeInteger("drift_per_year"),
                sr.integer("neighbour_baseline", -100, 0),
                sr.nonNegativeInteger("kill_penalty"), sr.nonNegativeInteger("notable_kill_penalty"),
                sr.nonNegativeInteger("wound_penalty"), sr.integer("feud_at", -100, 0), sr.integer("war_at", -100, 0),
                sr.fraction("war_chance_per_year"), sr.fraction("truce_per_year"),
                sr.fraction("truce_per_year_of_war"), sr.nonNegativeNumber("battles_per_year"),
                sr.positiveInteger("battle_interval_years"), sr.integer("champion_max_injury", 0, 100),
                sr.positiveInteger("tribute_score"), sr.positiveInteger("tribute_years"), sr.fraction("tribute_share"),
                sr.nonNegativeInteger("annex_below"), sr.nonNegativeInteger("peace_years"),
                sr.integer("split_value", -100, 100),
                sr.nonNegativeNumber("victory_prestige"));
        if (sectRelations.warAt() > sectRelations.feudAt()) {
            throw sr.error("war_at", "must not be above feud_at");
        }

        SimJson.Fields su = root.object("succession", Set.of("contest_ambition", "contest_gap", "contest_chance",
                "leave_chance", "follower_share", "min_master", "min_years_left"));
        Succession succession = new Succession(su.integer("contest_ambition", 0, 101),
                su.nonNegativeNumber("contest_gap"), su.fraction("contest_chance"), su.fraction("leave_chance"),
                su.fraction("follower_share"), realmStage(su.object("min_master", REALM_STAGE_FIELDS), realms),
                su.nonNegativeInteger("min_years_left"));

        SimJson.Fields de = root.object("decline", Set.of("desert_rate_per_year", "desertion_rate_per_year",
                "min_members", "min_share", "recruit_factor", "rogue_join_rate_per_year", "schism_size_ratio",
                "schism_rate_per_year", "schism_ambition", "grace_years"));
        Decline decline = new Decline(de.fraction("desert_rate_per_year"), de.fraction("desertion_rate_per_year"),
                de.nonNegativeInteger("min_members"), de.fraction("min_share"), de.fraction("recruit_factor"),
                de.fraction("rogue_join_rate_per_year"), de.positiveNumber("schism_size_ratio"),
                de.fraction("schism_rate_per_year"), de.integer("schism_ambition", 0, 101),
                de.nonNegativeInteger("grace_years"));

        SimJson.Fields fd = root.object("founding", Set.of("min_realm", "min_ambition", "rate_per_year",
                "deficit_boost", "surplus_exponent", "followers"));
        Founding founding = new Founding(realmStage(fd.object("min_realm", REALM_STAGE_FIELDS), realms),
                fd.integer("min_ambition", 0, 101), fd.fraction("rate_per_year"), fd.nonNegativeNumber("deficit_boost"),
                fd.nonNegativeNumber("surplus_exponent"), fd.intRange("followers"));

        return new Rules(time, Collections.unmodifiableMap(tiers), chronicle, scheduler, roots, techniques,
                cultivation, breakthrough, injury, Collections.unmodifiableMap(deathImportance), entrants, sects,
                genesis, gates, naming, importance, travel, seclusion, danger, fortune,
                Collections.unmodifiableMap(artifactPower), meetings, combat, revenge, sectRelations, succession,
                decline, founding);
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
