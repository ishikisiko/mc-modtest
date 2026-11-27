package com.example.myvillage.production;

import com.example.myvillage.resources.BuiltinResources;
import com.example.myvillage.spiritstone.SpiritStoneDenominations;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Fixed, server and client shared definitions for structure checks and production. */
record ProductionRules(int pulse, Mining mining, Generation generation, int lowSupply, int mediumSupply,
                       Map<ProductionKind, Layout> layouts, Map<ProductionKind, Integer> rates) {
    static final long TICKS_PER_HOUR = 20L * 60 * 60;
    static final int STACK_TARGET = 64;

    record Mining(int capacity, int recovery, int steady, int rush, int reserve, int target) { }
    record Generation(int minY, int maxY, int xuantieY, int columns) { }
    record Cell(BlockPos offset, ProductionPart part, Direction facing) { }
    record Layout(Cell controller, Cell outlet, List<Cell> cells) {
        Layout { cells = List.copyOf(cells); }
    }

    Layout layout(ProductionKind kind) { return layouts.get(kind); }
    int rate(ProductionKind kind) { return rates.get(kind); }

    static SpiritStoneDenominations.Tier denomination(int grade) {
        return SpiritStoneDenominations.get().tiers().get(grade);
    }

    static long cost(int grade) {
        return Math.multiplyExact(denomination(grade).units().longValueExact(), TICKS_PER_HOUR);
    }

    static ResourceLocation outputId(int grade) { return denomination(grade).item(); }

    static ProductionRules get() { return Holder.VALUE; }

    private static class Holder {
        private static final ProductionRules VALUE = load();
    }

    private static ProductionRules load() {
        try (var stream = BuiltinResources.open("data/yuanxian/production/rules.json");
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            var ore = root.getAsJsonObject("mining");
            var gen = root.getAsJsonObject("generation");
            var supply = root.getAsJsonObject("default_supply");
            var costs = root.getAsJsonObject("condensation_units_per_hour");
            int cap = integer(ore, "capacity", 1, Short.MAX_VALUE);
            int recovery = integer(ore, "recovery_ticks", 20, 72000);
            int steady = integer(ore, "steady_ticks", recovery, 72000);
            var mining = new Mining(cap, recovery, steady, integer(ore, "rush_ticks", 20, steady - 1),
                    integer(ore, "default_reserve", 0, cap - 1), integer(ore, "default_target", 1, STACK_TARGET));
            int min = integer(gen, "min_y", -64, 320);
            int max = integer(gen, "max_y", min + 1, 320);
            var generation = new Generation(min, max, integer(gen, "xuantie_y", min, max), integer(gen, "columns", 1, 64));
            var layouts = new EnumMap<ProductionKind, Layout>(ProductionKind.class);
            var rates = new EnumMap<ProductionKind, Integer>(ProductionKind.class);
            for (var kind : ProductionKind.values()) {
                layouts.put(kind, readLayout(root.getAsJsonObject("layouts"), kind));
                if (!kind.mining()) rates.put(kind, integer(costs, kind.id, 1, 72000));
            }
            return new ProductionRules(integer(root, "pulse_ticks", 1, 20), mining, generation,
                    integer(supply, "low", 0, STACK_TARGET), integer(supply, "medium", 0, STACK_TARGET),
                    Map.copyOf(layouts), Map.copyOf(rates));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load production rules", e);
        }
    }

    private static Layout readLayout(JsonObject root, ProductionKind kind) {
        var cells = new ArrayList<Cell>();
        var occupied = new HashSet<BlockPos>();
        Cell controller = null, outlet = null;
        for (var element : root.getAsJsonArray(kind.id)) {
            var row = element.getAsJsonArray();
            var pos = new BlockPos(row.get(0).getAsInt(), row.get(1).getAsInt(), row.get(2).getAsInt());
            var part = ProductionPart.byId(row.get(3).getAsString());
            var dir = row.size() > 4 ? Direction.byName(row.get(4).getAsString()) : Direction.NORTH;
            if (dir == null || !dir.getAxis().isHorizontal() || pos.getY() < 1 || pos.getY() > 3
                    || Math.abs(pos.getX()) > 8 || Math.abs(pos.getZ()) > 8 || !occupied.add(pos) || part.eye()) {
                throw new IllegalArgumentException("Invalid production layout " + kind.id + " at " + pos);
            }
            var cell = new Cell(pos, part, dir);
            if (part.controller()) {
                if (controller != null || part != kind.controller) throw new IllegalArgumentException("Duplicate or wrong controller " + kind.id);
                controller = cell;
            }
            if (part.outlet()) {
                if (outlet != null || part != (kind.mining() ? ProductionPart.MINE_OUTLET : ProductionPart.CRYSTAL_OUTLET)) {
                    throw new IllegalArgumentException("Duplicate or wrong outlet " + kind.id);
                }
                outlet = cell;
            }
            cells.add(cell);
        }
        if (controller == null || outlet == null || occupied.contains(outlet.offset.below())) {
            throw new IllegalArgumentException("Layout requires a controller and a free outlet underside " + kind.id);
        }
        return new Layout(controller, outlet, cells);
    }

    private static int integer(JsonObject object, String key, int min, int max) {
        int value = object.get(key).getAsBigDecimal().intValueExact();
        if (value < min || value > max) throw new IllegalArgumentException(key);
        return value;
    }
}
