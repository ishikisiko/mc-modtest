package com.example.myvillage.region.runtime;

import java.util.Set;

/**
 * String constants and the known-subject allow-list for the region layer —
 * Java mirror of the module-level constants in
 * {@code tools/buildgen/region_topology.py}. Centralized so the generator and
 * its validators reference one symbol per contract.
 */
public final class RegionContract {
    private RegionContract() {
    }

    /** Separator palette id: mountain range (特殊山脉). */
    public static final String SEP_MOUNTAIN = "特殊山脉";
    /** Separator palette id: ocean (特殊海洋). */
    public static final String SEP_OCEAN = "特殊海洋";

    /** Edge type: passable (连). */
    public static final String EDGE_LIAN = "连";
    /** Edge type: separated (隔). */
    public static final String EDGE_GE = "隔";

    /** Pass label for a walled region's retained 连 edge (关隘). */
    public static final String PASS_GUANAI = "关隘";

    /**
     * Known worldgen subjects the mod owns today. An {@code admitted_subjects}
     * entry must name one of these. Extend as new self-generating subjects land.
     */
    public static final Set<String> KNOWN_SUBJECTS = Set.of("sect");
}
