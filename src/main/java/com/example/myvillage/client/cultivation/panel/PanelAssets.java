package com.example.myvillage.client.cultivation.panel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Resource-owned presentation only: six explicit art overrides, shared category symbols and grade colours. */
public final class PanelAssets {
    private static final ResourceLocation CONFIG = ResourceLocation.fromNamespaceAndPath("myvillage", "cultivation_panel.json");
    private static volatile Look look;

    public record Grade(int color, int text, int wash, int label, int edge) {}
    public record Icon(ResourceLocation texture, int size) {}
    record Look(List<Grade> grades, Map<ResourceLocation, Icon> techniques,
                Map<String, Icon> categories, Map<String, Icon> navigation) {
        Icon iconFor(ResourceLocation id, TechniqueCategory category) {
            return techniques.getOrDefault(id,categories.get(category==null?"unknown":category.serializedName()));
        }
    }

    private PanelAssets() {}

    public static void reload(ResourceManager resources) {
        try (Reader reader = resources.openAsReader(CONFIG)) {
            look = decode(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot read H panel presentation resources", e);
        }
    }

    static Look decode(JsonObject root) {
        int size = root.get("icon_size").getAsInt();
        int categorySize = root.get("category_size").getAsInt();
        if ((size != 128 && size != 256) || categorySize != 128) throw new IllegalArgumentException("Unbounded panel texture size");
        var grades = root.getAsJsonArray("grades").asList().stream().map(element -> {
            JsonObject g = element.getAsJsonObject();
            return new Grade(color(g,"color"),color(g,"text"),color(g,"wash"),color(g,"label"),color(g,"edge"));
        }).toList();
        if (grades.size() != 5) throw new IllegalArgumentException("Missing grade palettes");
        Map<ResourceLocation, Icon> techniques = new HashMap<>();
        root.getAsJsonObject("technique_icons").entrySet().forEach(e ->
                techniques.put(ResourceLocation.parse(e.getKey()), new Icon(ResourceLocation.parse(e.getValue().getAsString()),size)));
        Map<String, Icon> categories = icons(root.getAsJsonObject("categories"),categorySize);
        Map<String, Icon> navigation = icons(root.getAsJsonObject("navigation"),categorySize);
        for (String key : List.of("core","active","movement","body","unknown")) {
            if (!categories.containsKey(key)) throw new IllegalArgumentException("Missing category icon: "+key);
        }
        return new Look(grades,Map.copyOf(techniques),categories,navigation);
    }

    private static int color(JsonObject g, String field) {
        String rgb = g.get(field).getAsString();
        if (!rgb.matches("[0-9A-Fa-f]{6}")) throw new IllegalArgumentException("Invalid panel RGB");
        return 0xFF000000 | Integer.parseInt(rgb,16);
    }

    private static Map<String, Icon> icons(JsonObject values, int size) {
        Map<String, Icon> result = new HashMap<>();
        values.entrySet().forEach(e -> result.put(e.getKey(),new Icon(ResourceLocation.parse(e.getValue().getAsString()),size)));
        return Map.copyOf(result);
    }

    public static Grade grade(int grade) {
        Look value = current();
        return value.grades().get(grade >= 0 && grade < value.grades().size() ? grade : 0);
    }

    public static Icon technique(ResourceLocation id, TechniqueCategory category) {
        return current().iconFor(id,category);
    }

    public static Icon category(TechniqueCategory category) {
        return current().categories().get(category == null ? "unknown" : category.serializedName());
    }

    public static Icon navigation(String name) {
        return current().navigation().getOrDefault(name,category(null));
    }

    private static Look current() {
        Look value = look;
        if (value == null) throw new IllegalStateException("H panel resources have not been loaded");
        return value;
    }

    public static void draw(GuiGraphics g, Icon icon, int x, int y, int size) {
        g.pose().pushPose();
        g.pose().translate(x,y,0);
        float scale = (float)size/icon.size();
        g.pose().scale(scale,scale,1);
        RenderSystem.enableBlend();
        g.setColor(1,1,1,1);
        g.blit(icon.texture(),0,0,0,0,icon.size(),icon.size(),icon.size(),icon.size());
        RenderSystem.disableBlend();
        g.pose().popPose();
    }
}
