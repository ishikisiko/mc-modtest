package com.example.myvillage.gathering;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Generated from tools/gathering/visual.json with the models; safe on either physical side. */
public final class GatheringAppearance {
    public record Style(int primary, int secondary, int idle, int flow, int segments, int rings, VoxelShape shape) { }
    private GatheringAppearance() { }
    private static class Holder { private static final Map<GatheringTier, Style> VALUES = load(); }
    public static Style get(GatheringTier tier) { return Holder.VALUES.get(tier); }
    private static Map<GatheringTier, Style> load() {
        try (var stream = GatheringAppearance.class.getResourceAsStream("/data/myvillage/gathering/appearance.json")) {
            if (stream == null) throw new IllegalStateException("Missing gathering appearance");
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var result = new EnumMap<GatheringTier, Style>(GatheringTier.class);
            for (var tier : GatheringTier.values()) {
                var row = root.getAsJsonObject(tier.id);
                VoxelShape shape = Shapes.empty();
                for (var value : row.getAsJsonArray("collision_boxes")) {
                    var box = value.getAsJsonObject(); var a = box.getAsJsonArray("from"); var b = box.getAsJsonArray("to");
                    shape = Shapes.or(shape, Block.box(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(),
                            b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble()));
                }
                result.put(tier, new Style((int)Long.parseLong(row.get("primary").getAsString(), 16),
                        (int)Long.parseLong(row.get("secondary").getAsString(), 16),
                        (int)Long.parseLong(row.get("idle").getAsString(), 16), (int)Long.parseLong(row.get("flow").getAsString(), 16), row.get("segments").getAsInt(),
                        row.get("rings").getAsInt(), shape));
            }
            return Map.copyOf(result);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
