package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.SimData;
import com.example.myvillage.sim.SimDate;
import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.data.EncounterTable;
import com.example.myvillage.sim.data.RealmTable;
import com.example.myvillage.sim.data.Rules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Every language key the engine can emit, with its param count. The chronicle refuses an
 * unregistered key or a wrong param count, and a test checks each key in both language files.
 *
 * <p>Lines are registered as <em>families</em>: a base such as {@code world_sim.event.stage_up}
 * with n variants emitted as {@code <base>.1 .. <base>.n}, the variant chosen by hash so a common
 * event does not repeat one sentence. Most families start with one or more <em>who</em> blocks of
 * {@link #WHO} params (sect, rank key, name) so each person is anchored on first mention; a rogue's
 * sect and rank are {@link #ROGUE_SECT} and {@link #ROGUE_RANK}.
 *
 * <p>{@link #legacyKeys} are the keys checkpoint-1 builds emitted. Nothing emits them any more, but
 * saved chronicles may contain them, so they stay in the language files.
 */
public final class TextKeys {
    private TextKeys() {
    }

    public static final String P = "world_sim.event.";
    /** Params in one who block: sect name, rank key, person name. */
    public static final int WHO = 3;
    /**
     * A rogue's who-block sect and rank slots. English anchors read {@code (rank sect)} and Chinese
     * {@code sect+rank}, so the pair renders "rogue cultivator" and "" + 散修 without a stray space.
     */
    public static final String ROGUE_SECT = "@world_sim.anchor.rogue_sect";
    public static final String ROGUE_RANK = "@world_sim.anchor.rogue";

    public static final String GENESIS_SECT = P + "genesis.sect";
    /** The genesis line of a sect that holds a heritage: {@link #GENESIS_SECT}'s params plus the heritage name. */
    public static final String GENESIS_SECT_HERITAGE = P + "genesis.sect.heritage";

    // Families (bases); emitted as <base>.<variant>.
    public static final String STAGE_UP = P + "stage_up";
    public static final String STAGE_UP_INSIGHT = P + "stage_up.insight";
    public static final String DEATH_OLD_AGE = P + "death.old_age";
    public static final String DEATH_BEAST = P + "death.beast";
    public static final String DEATH_TRAP = P + "death.trap";
    public static final String SLAIN = P + "slain.";
    public static final String FIGHT_FLEE = P + "fight.flee";
    public static final String FIGHT_WOUND = P + "fight.wound";
    public static final String FIGHT_REVENGE_WOUND = P + "fight.revenge_wound";
    public static final String BEAST_SLAY = P + "beast.slay";
    public static final String BEAST_WOUND = P + "beast.wound";
    public static final String MEET_FRIEND = P + "meet.friend";
    public static final String MEET_QUARREL = P + "meet.quarrel";
    public static final String TRAVEL_DEPART = P + "travel.depart";
    public static final String TRAVEL_WALLED = P + "travel.walled";
    public static final String SECLUSION = P + "seclusion";
    public static final String ENTRANT_ROGUE = P + "entrant.rogue";
    public static final String RECRUIT = P + "recruit";
    public static final String RECRUIT_PRODIGY = P + "recruit.prodigy";
    public static final String DISCIPLE = P + "disciple";
    public static final String PROMOTE_INNER = P + "promote.inner";
    public static final String PROMOTE_ELDER = P + "promote.elder";
    public static final String SUCCESSION = P + "succession";
    public static final String SUCCESSION_JUNIOR = P + "succession.junior";
    public static final String SUCCESSION_CONTESTED = P + "succession.contested";
    public static final String SECT_EXTINCT = P + "sect.extinct";
    public static final String SECT_SPLIT = P + "sect.split";
    public static final String SECT_SCHISM = P + "sect.schism";
    public static final String SECT_FOUNDED = P + "sect.founded";
    public static final String SECT_FOUNDED_FORTUNE = P + "sect.founded.fortune";
    public static final String SECT_DECLINE = P + "sect.decline";
    public static final String SECT_REVIVAL = P + "sect.revival";
    public static final String SECT_DESERT = P + "sect.desert";
    public static final String SECT_RUIN = P + "sect.ruin";
    /** A destroyed sect's heritage is lost: sect name, heritage name. */
    public static final String SECT_HERITAGE_LOST = P + "sect.heritage_lost";
    /** A new sect takes up a lost heritage: sect, founder, technique, heritage, the sect that lost it. */
    public static final String SECT_HERITAGE_REKINDLED = P + "sect.heritage_rekindled";
    public static final String ROGUE_JOIN = P + "rogue.join";
    public static final String FEUD = P + "feud";
    public static final String FEUD_WOUND = P + "feud.wound";
    public static final String WAR_DECLARE = P + "war.declare";
    public static final String WAR_TRUCE = P + "war.truce";
    public static final String WAR_TRIBUTE = P + "war.tribute";
    public static final String WAR_ANNEX = P + "war.annex";
    public static final String WAR_DESTROY = P + "war.destroy";
    public static final String WAR_BATTLE = P + "war.battle";
    public static final String FEUD_DEATH = P + "feud.death";
    public static final String FEUD_INJURY = P + "feud.injury";
    public static final String FEUD_DEATH_NAMED = P + "feud.death.named";
    public static final String FEUD_INJURY_NAMED = P + "feud.injury.named";
    public static final String WAR_CLASH = P + "war.clash";
    public static final String WAR_CLASH_AGAIN = P + "war.clash.again";
    public static final String SLAIN_BATTLE = P + "slain.battle";
    public static final String SLAIN_BATTLE_AGAIN = P + "slain.battle.again";

    // Player sect membership (sect entry, slice 1). Params: player name, sect name.
    public static final String PLAYER_JOIN = P + "player.join";
    public static final String PLAYER_LEAVE = P + "player.leave";
    /** The player's sect was destroyed and the player is a rogue again. */
    public static final String PLAYER_LEAVE_SECT_GONE = P + "player.leave.sect_gone";
    public static final String PLAYER_PROMOTE_INNER = P + "player.promote.inner";
    public static final String PLAYER_PROMOTE_ELDER = P + "player.promote.elder";

    /** Slain variants by fight kind. */
    public static final List<String> SLAIN_KINDS = List.of(
            "duel", "rob", "revenge", "revenge_late", "revenge_failed", "war", "contest", "succession");

    /** Breakthrough variants. */
    public static final String BT_PLAIN = "";
    public static final String BT_DESPERATE = "desperate";
    public static final String BT_SECT_PILL = "sect_pill";
    public static final String BT_FORTUNE_TECHNIQUE = "fortune_technique";
    public static final String BT_FORTUNE_PILL = "fortune_pill";
    public static final String BT_FORTUNE_ROOT = "fortune_root";

    public static String breakthrough(String realmId, String variant) {
        return P + "breakthrough." + realmId + (variant.isEmpty() ? "" : "." + variant);
    }

    public static String breakthroughFail(String realmId) {
        return P + "breakthrough_fail." + realmId;
    }

    /** Variant "" (plain), "wound" (own old wound), "wound_by" (a wound someone dealt) or "desperate". */
    public static String breakthroughDeath(String realmId, String variant) {
        return P + "breakthrough_death." + realmId + (variant.isEmpty() ? "" : "." + variant);
    }

    public static String fortune(EncounterTable.Encounter e) {
        return EncounterTable.TEXT_PREFIX + e.text();
    }

    public static String realm(String realmId) {
        return "@world_sim.realm." + realmId;
    }

    /** {@code stage} is 0-based; the key is 1-based. */
    public static String stage(String realmId, int stage) {
        return "@world_sim.stage." + realmId + "." + (stage + 1);
    }

    public static String rank(String rank) {
        return "@world_sim.rank." + rank;
    }

    public static String rootGrade(String grade) {
        return "@world_sim.root." + grade;
    }

    public static String techniqueGrade(String grade) {
        return "@world_sim.grade." + grade;
    }

    /** Why an avenger acts (relation reason): 师仇, 徒仇, 友仇, 同门之仇, 旧怨. */
    public static String reason(String reason) {
        return "@world_sim.reason." + (REASONS.contains(reason) ? reason : "self");
    }

    public static final List<String> REASONS = List.of("master", "disciple", "friend", "sect", "self");

    /** Family base -> {param count, variant count}. */
    public static Map<String, int[]> families(SimData data) {
        Map<String, int[]> f = new LinkedHashMap<>();
        int w = WHO;
        f.put(STAGE_UP, new int[] {w + 1, 2});
        f.put(STAGE_UP_INSIGHT, new int[] {w + 1, 2});
        f.put(DEATH_OLD_AGE, new int[] {w + 2, 3});
        f.put(DEATH_BEAST, new int[] {w + 3, 2});
        f.put(DEATH_TRAP, new int[] {w + 2, 1});
        f.put(SLAIN + "duel", new int[] {2 * w + 1, 2});
        f.put(SLAIN + "rob", new int[] {2 * w + 2, 1});
        f.put(SLAIN + "revenge", new int[] {2 * w + 2, 2});
        f.put(SLAIN + "revenge_late", new int[] {2 * w + 2, 1});
        f.put(SLAIN + "revenge_failed", new int[] {2 * w + 2, 1});
        f.put(SLAIN + "war", new int[] {2 * w + 1, 2});
        f.put(SLAIN + "contest", new int[] {2 * w + 2, 1});
        f.put(SLAIN + "succession", new int[] {2 * w, 1});
        f.put(FIGHT_FLEE, new int[] {2 * w + 1, 2});
        f.put(FIGHT_WOUND, new int[] {2 * w + 1, 2});
        f.put(FIGHT_REVENGE_WOUND, new int[] {2 * w + 2, 1});
        f.put(BEAST_SLAY, new int[] {w + 2, 2});
        f.put(BEAST_WOUND, new int[] {w + 2, 1});
        f.put(MEET_FRIEND, new int[] {2 * w + 1, 2});
        f.put(MEET_QUARREL, new int[] {2 * w + 1, 2});
        f.put(TRAVEL_DEPART, new int[] {w + 1, 2});
        f.put(TRAVEL_WALLED, new int[] {w + 1, 1});
        f.put(SECLUSION, new int[] {w, 2});
        f.put(ENTRANT_ROGUE, new int[] {3, 2});
        f.put(RECRUIT, new int[] {3, 2});
        f.put(RECRUIT_PRODIGY, new int[] {4, 2});
        f.put(DISCIPLE, new int[] {2 * w, 2});
        f.put(PROMOTE_INNER, new int[] {w, 1});
        f.put(PROMOTE_ELDER, new int[] {w + 1, 1});
        f.put(SUCCESSION, new int[] {w + 1, 2});
        f.put(SUCCESSION_JUNIOR, new int[] {w + 1, 1});
        f.put(SUCCESSION_CONTESTED, new int[] {w + 2, 1});
        f.put(SECT_EXTINCT, new int[] {2, 1});
        f.put(SECT_SPLIT, new int[] {w + 3, 1});
        f.put(SECT_SCHISM, new int[] {w + 3, 1});
        f.put(SECT_FOUNDED, new int[] {w + 2, 2});
        f.put(SECT_FOUNDED_FORTUNE, new int[] {w + 3, 1});
        f.put(SECT_DECLINE, new int[] {w, 2});
        f.put(SECT_REVIVAL, new int[] {w, 1});
        f.put(SECT_DESERT, new int[] {w, 1});
        f.put(SECT_RUIN, new int[] {3, 1});
        f.put(SECT_HERITAGE_LOST, new int[] {2, 2});
        f.put(SECT_HERITAGE_REKINDLED, new int[] {5, 1});
        f.put(ROGUE_JOIN, new int[] {w + 1, 1});
        f.put(FEUD_DEATH_NAMED, new int[] {2 * w, 2});
        f.put(FEUD_INJURY_NAMED, new int[] {2 * w, 1});
        f.put(WAR_DECLARE, new int[] {w + 1, 2});
        f.put(WAR_TRUCE, new int[] {3, 1});
        f.put(WAR_TRIBUTE, new int[] {3, 1});
        f.put(WAR_ANNEX, new int[] {3, 1});
        f.put(WAR_DESTROY, new int[] {2, 1});
        f.put(WAR_CLASH, new int[] {3 + 2 * w, 2});
        f.put(WAR_CLASH_AGAIN, new int[] {3 + 2 * w, 1});
        f.put(SLAIN_BATTLE, new int[] {3 + 2 * w, 2});
        f.put(SLAIN_BATTLE_AGAIN, new int[] {3 + 2 * w, 1});
        f.put(PLAYER_JOIN, new int[] {2, 2});
        f.put(PLAYER_LEAVE, new int[] {2, 2});
        f.put(PLAYER_LEAVE_SECT_GONE, new int[] {2, 1});
        f.put(PLAYER_PROMOTE_INNER, new int[] {2, 1});
        f.put(PLAYER_PROMOTE_ELDER, new int[] {2, 1});
        for (RealmTable.Realm realm : data.realms().realms()) {
            if (realm.breakthrough() == null) {
                continue;
            }
            int base = w + 1 + (realm.titleSuffix() == null ? 0 : 1);
            f.put(breakthrough(realm.id(), BT_PLAIN), new int[] {base, 3});
            f.put(breakthrough(realm.id(), BT_DESPERATE), new int[] {base, 2});
            f.put(breakthrough(realm.id(), BT_SECT_PILL), new int[] {base + 1, 2});
            f.put(breakthrough(realm.id(), BT_FORTUNE_TECHNIQUE), new int[] {base + 1, 2});
            f.put(breakthrough(realm.id(), BT_FORTUNE_PILL), new int[] {base + 1, 2});
            f.put(breakthrough(realm.id(), BT_FORTUNE_ROOT), new int[] {base + 1, 1});
            f.put(breakthroughFail(realm.id()), new int[] {w, 3});
            f.put(breakthroughDeath(realm.id(), ""), new int[] {w + 1, 2});
            f.put(breakthroughDeath(realm.id(), "wound"), new int[] {w + 1, 1});
            f.put(breakthroughDeath(realm.id(), "wound_by"), new int[] {2 * w + 1, 2});
            f.put(breakthroughDeath(realm.id(), "desperate"), new int[] {w + 1, 2});
        }
        for (EncounterTable.Encounter e : data.encounters().encounters()) {
            f.put(fortune(e), new int[] {w + 1 + e.extraParams(), 1});
        }
        return f;
    }

    /**
     * Families checkpoint-2 builds emitted and later builds replaced (base -> {params, variants}).
     * Saved chronicles may contain them, so they stay in the language files.
     */
    public static Map<String, int[]> retiredFamilies() {
        Map<String, int[]> f = new LinkedHashMap<>();
        f.put(FEUD, new int[] {2 * WHO, 2});
        f.put(FEUD_WOUND, new int[] {2 * WHO, 1});
        f.put(WAR_BATTLE, new int[] {2 * WHO, 1});
        f.put(FEUD_DEATH, new int[] {3, 2});
        f.put(FEUD_INJURY, new int[] {3, 1});
        f.put(SLAIN + "champion", new int[] {2 * WHO, 1});
        return f;
    }

    /** Full event keys the engine can emit now, with their param counts. */
    public static Map<String, Integer> eventKeys(SimData data) {
        TreeMap<String, Integer> keys = new TreeMap<>();
        keys.put(SimDate.KEY_ERA, 1);
        keys.put(SimDate.KEY_BEFORE_ERA, 1);
        keys.put(GENESIS_SECT, 6);
        keys.put(GENESIS_SECT_HERITAGE, 7);
        for (Map.Entry<String, int[]> e : families(data).entrySet()) {
            for (int v = 1; v <= e.getValue()[1]; v++) {
                keys.put(e.getKey() + "." + v, e.getValue()[0]);
            }
        }
        return keys;
    }

    /** Keys earlier builds emitted (still rendered for old saves), with their param counts. */
    public static Map<String, Integer> legacyKeys(SimData data) {
        TreeMap<String, Integer> keys = new TreeMap<>();
        keys.put(P + "stage_up", 2);
        keys.put(P + "death.old_age", 2);
        keys.put(P + "death.old_age.master", 3);
        keys.put(P + "entrant.rogue", 2);
        keys.put(P + "recruit", 2);
        keys.put(P + "recruit.prodigy", 3);
        keys.put(P + "disciple", 2);
        keys.put(P + "promote.inner", 2);
        keys.put(P + "promote.elder", 2);
        keys.put(P + "succession", 3);
        keys.put(P + "succession.junior", 3);
        keys.put(P + "sect.extinct", 2);
        for (RealmTable.Realm realm : data.realms().realms()) {
            if (realm.breakthrough() == null) {
                continue;
            }
            int base = realm.titleSuffix() == null ? 1 : 2;
            keys.put(breakthrough(realm.id(), BT_PLAIN), base);
            keys.put(breakthrough(realm.id(), BT_DESPERATE), base);
            keys.put(breakthrough(realm.id(), BT_SECT_PILL), base + 1);
            keys.put(breakthroughFail(realm.id()), 1);
            keys.put(breakthroughDeath(realm.id(), ""), 1);
            keys.put(breakthroughDeath(realm.id(), "wound"), 1);
            keys.put(breakthroughDeath(realm.id(), "desperate"), 1);
        }
        for (EncounterTable.Encounter e : data.encounters().encounters()) {
            // Checkpoint-1 builds had no heritages, so no heritage encounter has an unnumbered line.
            if (!e.has("heritage")) {
                keys.put(e.textKey(), e.paramCount());
            }
        }
        for (Map.Entry<String, int[]> e : retiredFamilies().entrySet()) {
            for (int v = 1; v <= e.getValue()[1]; v++) {
                keys.put(e.getKey() + "." + v, e.getValue()[0]);
            }
        }
        return keys;
    }

    /** Keys used as {@code @} params (translated words with no params of their own). */
    public static Map<String, Integer> wordKeys(SimData data) {
        TreeMap<String, Integer> keys = new TreeMap<>();
        for (RealmTable.Realm realm : data.realms().realms()) {
            keys.put(realm(realm.id()).substring(1), 0);
            for (int s = 0; s < realm.stages().size(); s++) {
                keys.put(stage(realm.id(), s).substring(1), 0);
            }
        }
        for (String rank : Rules.RANKS) {
            keys.put(rank(rank).substring(1), 0);
        }
        keys.put(ROGUE_SECT.substring(1), 0);
        keys.put(ROGUE_RANK.substring(1), 0);
        for (Rules.RootGrade g : data.rules().roots().grades()) {
            keys.put(rootGrade(g.id()).substring(1), 0);
        }
        for (String g : ContentTables.GRADE_ORDER) {
            keys.put(techniqueGrade(g).substring(1), 0);
        }
        for (String r : REASONS) {
            keys.put(reason(r).substring(1), 0);
        }
        return keys;
    }

    /** Everything that must exist in both language files. */
    public static Map<String, Integer> all(SimData data) {
        TreeMap<String, Integer> all = new TreeMap<>(legacyKeys(data));
        all.putAll(eventKeys(data));
        all.putAll(wordKeys(data));
        return all;
    }
}
