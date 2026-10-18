package com.example.myvillage.spiritstone;

import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpiritStoneDenominationsTest {
    private static ResourceLocation id(String name) { return ResourceLocation.parse("fixture:" + name); }
    private static SpiritStoneDenominations.Tier tier(String name, BigInteger units) {
        return new SpiritStoneDenominations.Tier(id(name), TagKey.create(Registries.ITEM, id(name)), units);
    }
    @Test void hugeMultilevelValuesRoundTripWithoutRoundingOrFloatingPoint() {
        var middle = BigInteger.TEN.pow(30); var high = middle.multiply(BigInteger.valueOf(128));
        var rules = new SpiritStoneDenominations(List.of(tier("high", high), tier("low", BigInteger.ONE), tier("middle", middle)));
        var value = high.multiply(BigInteger.TEN.pow(100)).add(middle.multiply(BigInteger.valueOf(127))).add(BigInteger.valueOf(37));
        var split = rules.decompose(value);
        assertEquals(BigInteger.TEN.pow(100), split.get(id("high")));
        assertEquals(BigInteger.valueOf(127), split.get(id("middle"))); assertEquals(BigInteger.valueOf(37), split.get(id("low")));
        assertEquals(value, rules.value(split));
        var conversion = rules.exchange(id("low"), id("high"), value);
        assertEquals(BigInteger.TEN.pow(100), conversion.quantity());
        assertEquals(value, conversion.quantity().multiply(high).add(conversion.remainderUnits()));
        assertEquals(high, rules.exchange(id("high"), id("low"), BigInteger.ONE).quantity());
        assertTrue(rules.decompose(BigInteger.ZERO).isEmpty());
    }
    @Test void invalidOrUnknownDenominationsFailWithoutImplicitConversions() {
        assertThrows(IllegalArgumentException.class, () -> new SpiritStoneDenominations(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SpiritStoneDenominations(List.of(tier("low", BigInteger.TWO))));
        assertThrows(IllegalArgumentException.class, () -> new SpiritStoneDenominations(List.of(tier("low", BigInteger.ONE), tier("a", BigInteger.valueOf(100)), tier("b", BigInteger.valueOf(150)))));
        assertThrows(IllegalArgumentException.class, () -> new SpiritStoneDenominations(List.of(tier("low", BigInteger.ONE), tier("low", BigInteger.TEN))));
        var rules = new SpiritStoneDenominations(List.of(tier("low", BigInteger.ONE)));
        assertThrows(IllegalArgumentException.class, () -> rules.units(id("unknown")));
        assertThrows(IllegalArgumentException.class, () -> rules.exchange(id("low"), id("low"), BigInteger.ONE.negate()));
    }
}
