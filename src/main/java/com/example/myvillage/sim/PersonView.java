package com.example.myvillage.sim;

import java.util.List;

/**
 * Read-only person record, living or dead. Fields that only the living have are neutral for the
 * dead (empty strings, -1, zero). {@code root} is five-element basis points over
 * metal, wood, water, fire, earth summing to 10000.
 *
 * @param stage   0-based stage within the realm
 * @param status  at_sect, travelling or secluded; "dead" for the dead
 * @param rank    sect_master, elder, inner, outer or rogue
 * @param traits  ambition, aggression, caution, wanderlust, loyalty (0..100); empty for the dead,
 *                whose tombstone keeps none (the portrait reads them for the mood)
 */
public record PersonView(
        int id,
        String name,
        String title,
        String gender,
        boolean alive,
        long birthDay,
        long deathDay,
        String deathCause,
        int killerId,
        List<Integer> root,
        String rootGrade,
        String realmId,
        int stage,
        double progress,
        int sectId,
        String sectName,
        String rank,
        int masterId,
        String regionId,
        String status,
        String techniqueId,
        String techniqueName,
        int injury,
        List<Integer> traits,
        List<Relation> relations) {

    /** A relation to another person; {@code causeEventId} is -1 when it has no recorded cause. */
    public record Relation(int otherId, String kind, int strength, long causeEventId) {
    }
}
