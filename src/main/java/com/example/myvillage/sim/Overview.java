package com.example.myvillage.sim;

import java.util.List;
import java.util.Map;

/**
 * World summary for {@code /myvillage world}.
 *
 * @param livingByRealm living count per realm id, in realm order
 */
public record Overview(
        long day,
        SimDate date,
        String tierId,
        int population,
        int targetPopulation,
        Map<String, Integer> livingByRealm,
        int activeSects,
        int destroyedSects,
        int deadCount,
        long eventCount,
        List<Integer> topPersonIds) {
}
