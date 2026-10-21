package com.example.myvillage.spiritstone;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Exact denomination arithmetic only. Does not exchange items or erase their components. */
public final class SpiritStoneDenominations {
    public record Tier(ResourceLocation id, ResourceLocation item, TagKey<Item> tag, BigInteger units) { }
    public record Exchange(BigInteger quantity, BigInteger remainderUnits) { }
    private final List<Tier> tiers;
    private final Map<ResourceLocation, Tier> byId;
    public SpiritStoneDenominations(List<Tier> values) {
        tiers = values.stream().sorted(Comparator.comparing(Tier::units)).toList();
        var ids = new LinkedHashMap<ResourceLocation, Tier>(); var tags = new HashSet<TagKey<Item>>();
        var items = new HashSet<ResourceLocation>();
        BigInteger previous = BigInteger.ONE;
        if (tiers.isEmpty() || !tiers.getFirst().units().equals(BigInteger.ONE)) throw new IllegalArgumentException("Base denomination must be one");
        for (int i = 0; i < tiers.size(); i++) {
            var tier = tiers.get(i);
            if (tier.id() == null || tier.item() == null || !items.add(tier.item()) || tier.tag() == null || ids.put(tier.id(), tier) != null || !tags.add(tier.tag())
                    || i > 0 && (tier.units().compareTo(previous) <= 0 || tier.units().remainder(previous).signum() != 0))
                throw new IllegalArgumentException("Denominations must be unique integral multiples");
            previous = tier.units();
        }
        byId = Map.copyOf(ids);
    }
    public boolean recognizes(ItemStack stack) { return tiers.stream().anyMatch(t -> stack.is(t.tag())); }
    public List<Tier> tiers() { return tiers; }
    public Tier tier(ResourceLocation id) {
        var tier = byId.get(id);
        if (tier == null) throw new IllegalArgumentException("Unknown denomination " + id);
        return tier;
    }
    public BigInteger units(ResourceLocation id) {
        var tier = byId.get(id); if (tier == null) throw new IllegalArgumentException("Unknown denomination " + id);
        return tier.units();
    }
    public BigInteger value(Map<ResourceLocation, BigInteger> quantities) {
        BigInteger result = BigInteger.ZERO;
        for (var e : quantities.entrySet()) { nonnegative(e.getValue()); result = result.add(units(e.getKey()).multiply(e.getValue())); }
        return result;
    }
    /** Remainder is expressed in base units, so conversion never rounds away value. */
    public Exchange exchange(ResourceLocation from, ResourceLocation to, BigInteger quantity) {
        nonnegative(quantity);
        var qr = quantity.multiply(units(from)).divideAndRemainder(units(to));
        return new Exchange(qr[0], qr[1]);
    }
    public Map<ResourceLocation, BigInteger> decompose(BigInteger baseUnits) {
        nonnegative(baseUnits); var result = new LinkedHashMap<ResourceLocation, BigInteger>();
        for (int i = tiers.size() - 1; i >= 0; i--) {
            var tier = tiers.get(i); var qr = baseUnits.divideAndRemainder(tier.units());
            if (qr[0].signum() > 0) result.put(tier.id(), qr[0]);
            baseUnits = qr[1];
        }
        return Collections.unmodifiableMap(result);
    }
    private static void nonnegative(BigInteger value) { if (value.signum() < 0) throw new IllegalArgumentException("Negative quantity"); }
    private static final class Holder { private static final SpiritStoneDenominations INSTANCE = load(); }
    public static SpiritStoneDenominations get() { return Holder.INSTANCE; }
    private static SpiritStoneDenominations load() {
        try (var stream = SpiritStoneDenominations.class.getResourceAsStream("/data/myvillage/spirit_stones/denominations.json")) {
            if (stream == null) throw new IllegalStateException("Missing spirit stone denominations");
            var data = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!data.keySet().equals(Set.of("tiers"))) throw new IllegalArgumentException("Unknown denomination configuration");
            var tiers = new ArrayList<Tier>();
            for (var element : data.getAsJsonArray("tiers")) {
                var row = element.getAsJsonObject();
                if (!row.keySet().equals(Set.of("id", "item", "tag", "units"))) throw new IllegalArgumentException("Unknown denomination field");
                var units = SpiritStoneLedger.QUANTITY.parse(com.mojang.serialization.JsonOps.INSTANCE, row.get("units")).getOrThrow();
                tiers.add(new Tier(ResourceLocation.parse(row.get("id").getAsString()),
                        ResourceLocation.parse(row.get("item").getAsString()),
                        TagKey.create(Registries.ITEM, ResourceLocation.parse(row.get("tag").getAsString())), units));
            }
            return new SpiritStoneDenominations(tiers);
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read denominations", e); }
    }
}
