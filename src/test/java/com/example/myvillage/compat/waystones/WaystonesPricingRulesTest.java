package com.example.myvillage.compat.waystones;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaystonesPricingRulesTest {
    private static final ResourceLocation QI = ResourceLocation.parse("myvillage:qi_refining");
    private static final ResourceLocation FOUNDATION = ResourceLocation.parse("myvillage:foundation_establishment");
    private static final ResourceLocation CORE = ResourceLocation.parse("myvillage:golden_core");

    @Test void configuredDefaultsUseOnlyTheMajorRealm() {
        var rules = WaystonesPricingConfig.rules();
        assertEquals(1, rules.cost(ResourceLocation.parse("myvillage:mortal"), 64, false));
        assertEquals(5, rules.cost(QI, 5000, false));
        assertEquals(15, rules.cost(FOUNDATION, 5000, false));
        assertEquals(50, rules.cost(CORE, 5000, false));
    }

    @Test void distanceStepsRoundUpWithoutChargingAnExtraExactBoundary() {
        var rules = WaystonesPricingConfig.rules();
        for (double distance : new double[]{0, .01, 999.99, 1000}) assertEquals(1, rules.distanceCoefficient(distance, false));
        for (double distance : new double[]{1000.000001, 1999.99, 2000}) assertEquals(2, rules.distanceCoefficient(distance, false));
        assertEquals(3, rules.distanceCoefficient(2000.000001, false));
        assertEquals(9, rules.distanceCoefficient(9000, false));
        assertEquals(10, rules.distanceCoefficient(9000.000001, false));
    }

    @Test void longRoutesKeepTheConfiguredRealmSpecificMaximum() {
        var rules = WaystonesPricingConfig.rules();
        for (double distance : new double[]{10_000, 30_000, 60_000_000, Double.MAX_VALUE}) {
            assertEquals(10, rules.cost(QI, distance, false));
            assertEquals(30, rules.cost(FOUNDATION, distance, false));
            assertEquals(100, rules.cost(CORE, distance, false));
        }
    }

    @Test void crossDimensionUsesItsOwnFixedCoefficientWithoutCoordinateComparison() {
        var rules = WaystonesPricingConfig.rules();
        assertEquals(10, rules.cost(QI, 0, true));
        assertEquals(30, rules.cost(FOUNDATION, Double.NaN, true));
        assertEquals(100, rules.cost(CORE, Double.POSITIVE_INFINITY, true));
    }

    @Test void settingsControlBasePriceDistanceBoundsAndInterdimensionalPrice() {
        var rules = new WaystonesPricingRules(2, 500, 2, 4, 3, 6, Map.of(FOUNDATION, 5));
        assertEquals(20, rules.cost(FOUNDATION, 0, false));
        assertEquals(30, rules.cost(FOUNDATION, 1001, false));
        assertEquals(40, rules.cost(FOUNDATION, 9000, false));
        assertEquals(30, rules.cost(FOUNDATION, 0, true));
        assertEquals(48, rules.cost(QI, 9000, false));
    }

    @Test void unknownOrUnsynchronizedRealmUsesTheConfiguredFallback() {
        var rules = WaystonesPricingConfig.rules();
        assertEquals(10, rules.cost(null, 64, false));
        assertEquals(100, rules.cost(ResourceLocation.parse("example:unmapped"), 50_000, false));
    }

    @Test void realmMappingIsAnImmutableSnapshot() {
        var mutable = new HashMap<ResourceLocation, Integer>();
        mutable.put(QI, 2);
        var rules = new WaystonesPricingRules(1, 1000, 1, 10, 10, 10, mutable);
        mutable.put(QI, 50);
        assertEquals(2, rules.cost(QI, 1, false));
        assertThrows(UnsupportedOperationException.class, () -> rules.realmMultipliers().put(QI, 3));
    }

    @Test void invalidDistancesNeverBecomeFreeOrNegativePrices() {
        var rules = WaystonesPricingConfig.rules();
        for (double distance : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> rules.cost(QI, distance, false));
    }

    @Test void invalidAndReversedConfigurationBoundsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WaystonesPricingRules(0, 1000, 1, 10, 10, 10, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new WaystonesPricingRules(1, 0, 1, 10, 10, 10, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new WaystonesPricingRules(1, 1000, 0, 10, 10, 10, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new WaystonesPricingRules(1, 1000, 5, 4, 4, 10, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new WaystonesPricingRules(1, 1000, 1, 10, 11, 10, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new WaystonesPricingRules(1, 1000, 1, 10, 0, 10, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new WaystonesPricingRules(1, 1000, 1, 10, 10, 0, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new WaystonesPricingRules(1, 1000, 1, 10, 10, 10, Map.of(QI, 0)));
    }

    @Test void publicVariableFloatBoundaryIsExactAndLargerPricesAreRejected() {
        var exact = new WaystonesPricingRules(1, 1000, 1, 1, 1, 1 << 24, Map.of());
        int price = exact.cost(null, 1, false);
        assertEquals(price, (int) (float) price);
        assertThrows(IllegalArgumentException.class,
                () -> new WaystonesPricingRules(1, 1000, 1, 1, 1, (1 << 24) + 1, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new WaystonesPricingRules(Integer.MAX_VALUE, 1, 1, Integer.MAX_VALUE, 1, Integer.MAX_VALUE, Map.of()));
    }

    @Test void realmEntriesUseExactIdsAndRejectMalformedOrDuplicateValues() {
        var parsed = WaystonesPricingConfig.parseRealmMultipliers(List.of("myvillage:qi_refining=2", " example:extra = 7 "));
        assertEquals(Map.of(QI, 2, ResourceLocation.parse("example:extra"), 7), parsed);
        assertTrue(WaystonesPricingConfig.parseRealmMultipliers(List.of()).isEmpty());
        for (String entry : List.of("qi_refining=1", "myvillage:qi_refining=0", "myvillage:qi_refining=-1",
                "myvillage:qi_refining=1.5", "myvillage:qi_refining=2147483648", "myvillage:qi_refining=1=2"))
            assertThrows(IllegalArgumentException.class, () -> WaystonesPricingConfig.parseRealmMultipliers(List.of(entry)));
        assertThrows(IllegalArgumentException.class, () -> WaystonesPricingConfig.parseRealmMultipliers(
                List.of("myvillage:qi_refining=1", "myvillage:qi_refining=2")));
    }
}
