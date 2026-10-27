package com.example.myvillage.equipment;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;

/** Bundled item identities are fixed at registration; resource reloads only replace their art. */
public final class EquipmentCatalogue {
    private static final String PATH = "/data/myvillage/equipment/catalogue.json";

    public record Entry(ResourceLocation id, ArmorItem.Type type, int durabilityMultiplier,
                        ResourceLocation model, ResourceLocation texture) { }

    private EquipmentCatalogue() { }

    public static List<Entry> bundled() { return Bundled.VALUE; }

    private static final class Bundled {
        private static final List<Entry> VALUE = load();
        private static List<Entry> load() {
            try (var stream = EquipmentCatalogue.class.getResourceAsStream(PATH)) {
                if (stream == null) throw new IllegalStateException("Missing " + PATH);
                return parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
            } catch (java.io.IOException error) {
                throw new IllegalStateException("Cannot read equipment catalogue", error);
            }
        }
    }

    public static List<Entry> parse(Reader reader) {
        var root = JsonParser.parseReader(reader).getAsJsonObject();
        if (root.get("version").getAsInt() != 1) throw new IllegalArgumentException("Equipment catalogue version");
        var array = root.getAsJsonArray("items");
        if (array == null || array.isEmpty() || array.size() > 512) throw new IllegalArgumentException("Equipment count");
        var result = new ArrayList<Entry>();
        var seen = new HashSet<ResourceLocation>();
        for (var element : array) {
            var item = element.getAsJsonObject();
            var id = ResourceLocation.parse(item.get("id").getAsString());
            if (!id.getNamespace().equals("myvillage") || !id.getPath().matches("[a-z][a-z0-9_]*") || !seen.add(id))
                throw new IllegalArgumentException("Invalid or duplicate equipment id " + id);
            var type = switch (item.get("slot").getAsString()) {
                case "head" -> ArmorItem.Type.HELMET;
                case "chest" -> ArmorItem.Type.CHESTPLATE;
                case "legs" -> ArmorItem.Type.LEGGINGS;
                case "feet" -> ArmorItem.Type.BOOTS;
                default -> throw new IllegalArgumentException("Unknown armor slot");
            };
            if (!item.get("material").getAsString().equals("minecraft:iron"))
                throw new IllegalArgumentException("Unsupported base armor material");
            int durability = item.get("durability_multiplier").getAsBigDecimal().intValueExact();
            if (durability < 1 || durability > 100) throw new IllegalArgumentException("Armor durability multiplier");
            var model = ResourceLocation.parse(item.get("model").getAsString());
            var texture = ResourceLocation.parse(item.get("texture").getAsString());
            if (!model.getPath().matches("equipment/[a-z][a-z0-9_]*\\.json")
                    || !texture.getPath().matches("textures/equipment/[a-z][a-z0-9_]*\\.png"))
                throw new IllegalArgumentException("Equipment resource path");
            result.add(new Entry(id, type, durability, model, texture));
        }
        return List.copyOf(result);
    }
}
