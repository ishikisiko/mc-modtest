package com.example.myvillage.worldplan.travel;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.capital.CapitalCity;
import java.util.Optional;

/** Resolves an exact ID against the current frozen plan; never accepts a destination coordinate. */
public final class AtlasCityTravelTarget {
    public static final int MAX_CITY_ID = 56;
    public static final int FINGERPRINT_LENGTH = 64;
    private AtlasCityTravelTarget() { }

    public static boolean validCityId(String id) {
        return id != null && id.length() <= MAX_CITY_ID && id.matches("capital_[a-z0-9_]{1,48}");
    }
    public static boolean validFingerprint(String fingerprint) {
        return fingerprint != null && fingerprint.matches("[0-9a-f]{64}");
    }
    public static Optional<CapitalCity> resolve(WorldPlan plan, String cityId, String fingerprint) {
        if (!validCityId(cityId) || !validFingerprint(fingerprint) || !plan.fingerprint().equals(fingerprint))
            return Optional.empty();
        // Both V8 and V9 expose the same frozen collection. Legacy maps deliberately have no cities.
        return plan.cities().stream().filter(city -> city.cityId().equals(cityId)).findFirst();
    }
}
