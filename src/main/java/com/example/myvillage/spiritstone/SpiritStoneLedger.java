package com.example.myvillage.spiritstone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/** Immutable aggregate: one prototype per exact item/components identity, never one entry per stack. */
public final class SpiritStoneLedger {
    public static final Codec<BigInteger> QUANTITY = Codec.STRING.comapFlatMap(value -> {
        if (!value.matches("[1-9][0-9]*")) return DataResult.error(() -> "Quantity must be a positive canonical integer");
        return DataResult.success(new BigInteger(value));
    }, BigInteger::toString);
    public record Entry(ItemStack item, BigInteger quantity) {
        public Entry {
            if (item.isEmpty() || quantity.signum() <= 0) throw new IllegalArgumentException("Invalid ledger entry");
            item = item.copyWithCount(1);
        }
        @Override public ItemStack item() { return item.copy(); }
    }
    // Keep codecs together: constructing Entry before the outer class must not create a
    // nested-class initialization cycle through QUANTITY and a half-initialized Entry.CODEC.
    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.CODEC.fieldOf("item").forGetter(Entry::item),
            QUANTITY.fieldOf("quantity").forGetter(Entry::quantity)).apply(i, Entry::new));
    public static final Codec<SpiritStoneLedger> CODEC = ENTRY_CODEC.listOf().xmap(SpiritStoneLedger::new, SpiritStoneLedger::entries);
    public static final StreamCodec<RegistryFriendlyByteBuf, SpiritStoneLedger> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
    public static final DataComponentType<SpiritStoneLedger> TYPE = DataComponentType.<SpiritStoneLedger>builder()
            .persistent(CODEC).networkSynchronized(STREAM_CODEC).build();
    private record Key(ItemStack item) {
        @Override public boolean equals(Object value) { return value instanceof Key key && ItemStack.isSameItemSameComponents(item, key.item); }
        @Override public int hashCode() { return ItemStack.hashItemAndComponents(item); }
    }
    private final List<Entry> entries;
    public SpiritStoneLedger(List<Entry> values) {
        var totals = new LinkedHashMap<Key, BigInteger>();
        for (var entry : values) totals.merge(new Key(entry.item()), entry.quantity(), BigInteger::add);
        entries = totals.entrySet().stream().map(e -> new Entry(e.getKey().item(), e.getValue())).toList();
    }
    public List<Entry> entries() { return entries; }
    @Override public boolean equals(Object value) {
        if (!(value instanceof SpiritStoneLedger other) || entries.size() != other.entries.size()) return false;
        for (int i = 0; i < entries.size(); i++) {
            var a = entries.get(i); var b = other.entries.get(i);
            if (!a.quantity().equals(b.quantity()) || !ItemStack.isSameItemSameComponents(a.item(), b.item())) return false;
        }
        return true;
    }
    @Override public int hashCode() { return entries.stream().mapToInt(e -> 31 * ItemStack.hashItemAndComponents(e.item()) + e.quantity().hashCode()).reduce(1, (a, b) -> 31 * a + b); }
}
