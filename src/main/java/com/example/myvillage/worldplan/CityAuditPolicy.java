package com.example.myvillage.worldplan;

/** One narrow runtime transition, not a general list of blocks to ignore. */
public final class CityAuditPolicy {
    private CityAuditPolicy() {}

    public static boolean naturalChange(String expected, String actual, boolean originalOpaqueCoverIntact) {
        return originalOpaqueCoverIntact
            && expected.equals("minecraft:grass_block")
            && actual.equals("minecraft:dirt");
    }
}
