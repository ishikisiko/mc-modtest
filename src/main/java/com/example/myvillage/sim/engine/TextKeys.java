package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.SimData;
import com.example.myvillage.sim.SimDate;
import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.data.EncounterTable;
import com.example.myvillage.sim.data.RealmTable;
import com.example.myvillage.sim.data.Rules;
import java.util.Map;
import java.util.TreeMap;

/**
 * Every language key the engine can emit, with its param count. The chronicle refuses a key that
 * is not listed here or a wrong param count, and a test checks that each key exists in both
 * language files with exactly that many {@code %n$s} slots. Keys that depend on data (realms,
 * encounters) are derived from the loaded {@link SimData}.
 */
public final class TextKeys {
    private TextKeys() {
    }

    public static final String P = "world_sim.event.";

    public static final String GENESIS_SECT = P + "genesis.sect";
    public static final String STAGE_UP = P + "stage_up";
    public static final String DEATH_OLD_AGE = P + "death.old_age";
    public static final String DEATH_OLD_AGE_MASTER = P + "death.old_age.master";
    public static final String ENTRANT_ROGUE = P + "entrant.rogue";
    public static final String RECRUIT = P + "recruit";
    public static final String RECRUIT_PRODIGY = P + "recruit.prodigy";
    public static final String DISCIPLE = P + "disciple";
    public static final String PROMOTE_INNER = P + "promote.inner";
    public static final String PROMOTE_ELDER = P + "promote.elder";
    public static final String SUCCESSION = P + "succession";
    public static final String SUCCESSION_JUNIOR = P + "succession.junior";
    public static final String SECT_EXTINCT = P + "sect.extinct";

    /** Breakthrough variants; the key is {@code breakthrough.<realm>[.<variant>]}. */
    public static final String BT_PLAIN = "";
    public static final String BT_DESPERATE = "desperate";
    public static final String BT_SECT_PILL = "sect_pill";

    public static String breakthrough(String realmId, String variant) {
        return P + "breakthrough." + realmId + (variant.isEmpty() ? "" : "." + variant);
    }

    public static String breakthroughFail(String realmId) {
        return P + "breakthrough_fail." + realmId;
    }

    /** Variant "" (plain), "wound" (an old wound contributed) or "desperate". */
    public static String breakthroughDeath(String realmId, String variant) {
        return P + "breakthrough_death." + realmId + (variant.isEmpty() ? "" : "." + variant);
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

    /** Event keys the engine can emit, with their param counts. */
    public static Map<String, Integer> eventKeys(SimData data) {
        TreeMap<String, Integer> keys = new TreeMap<>();
        keys.put(SimDate.KEY_ERA, 1);
        keys.put(SimDate.KEY_BEFORE_ERA, 1);
        keys.put(GENESIS_SECT, 6);
        keys.put(STAGE_UP, 2);
        keys.put(DEATH_OLD_AGE, 2);
        keys.put(DEATH_OLD_AGE_MASTER, 3);
        keys.put(ENTRANT_ROGUE, 2);
        keys.put(RECRUIT, 2);
        keys.put(RECRUIT_PRODIGY, 3);
        keys.put(DISCIPLE, 2);
        keys.put(PROMOTE_INNER, 2);
        keys.put(PROMOTE_ELDER, 2);
        keys.put(SUCCESSION, 3);
        keys.put(SUCCESSION_JUNIOR, 3);
        keys.put(SECT_EXTINCT, 2);
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
            keys.put(e.textKey(), e.paramCount());
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
        for (Rules.RootGrade g : data.rules().roots().grades()) {
            keys.put(rootGrade(g.id()).substring(1), 0);
        }
        for (String g : ContentTables.GRADE_ORDER) {
            keys.put(techniqueGrade(g).substring(1), 0);
        }
        return keys;
    }

    /** Both maps together. */
    public static Map<String, Integer> all(SimData data) {
        TreeMap<String, Integer> all = new TreeMap<>(eventKeys(data));
        all.putAll(wordKeys(data));
        return all;
    }
}
