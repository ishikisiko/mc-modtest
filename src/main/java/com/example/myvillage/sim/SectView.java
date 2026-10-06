package com.example.myvillage.sim;

import java.util.List;

/**
 * Read-only sect record. {@code topRealmId} is empty when the sect has no living member.
 *
 * @param basicTechniqueId the sect's basic (entry) technique, a ledger technique path; "" for none
 * @param heritageId   the heritage (传承) the sect holds or, once destroyed, held; "" for none
 * @param heritageName its Chinese name, "" for none
 * @param state        "active" or "destroyed"
 */
public record SectView(
        int id,
        String name,
        String regionId,
        int gateX,
        int gateZ,
        boolean gateRealized,
        int founderId,
        String founderName,
        long foundedDay,
        int masterId,
        String masterName,
        int memberCount,
        String topRealmId,
        double resources,
        double prestige,
        String basicTechniqueId,
        String signatureTechniqueId,
        String signatureTechniqueName,
        String heritageId,
        String heritageName,
        String state,
        long destroyedDay,
        int parentSectId,
        List<Relation> relations) {

    /** Standing towards another sect: value -100..100 and state none/feud/war. */
    public record Relation(int otherSectId, int value, String state, long causeEventId) {
    }
}
