package com.example.myvillage.entity.beast;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The bundled biome catalogue supplies types, materials and spawn behavior to both sides. */
public final class ProvincialFauna {
    public enum Behavior { FLEE, GLIDE, AMBLE, AMPHIBIOUS, SWIM, CLIMB, HOP, RETALIATE, FLY }
    public enum Habitat { BANK, CLEARING, LEDGE, REEF, ROOTS, HOT_EDGE, RIFT }
    public enum Activity { DAY, DUSK, TWILIGHT, RAIN_NIGHT, ANY }

    public record Rule(String id, ResourceLocation biome, String region, MobCategory category, float width, float height,
                       Habitat habitat, Activity activity, Behavior behavior, int color, List<ResourceLocation> palette,
                       List<String> materials) {}

    private static final Map<String, Rule> RULES = load();
    private ProvincialFauna() {}

    public static List<Rule> rules() { return List.copyOf(RULES.values()); }

    public static Rule require(String id) {
        var rule = RULES.get(id);
        if (rule == null) throw new IllegalArgumentException("缺少州域生物规则 " + id);
        return rule;
    }

    public static Map<String, DeferredHolder<EntityType<?>, EntityType<ProvincialAnimal>>> register(DeferredRegister<EntityType<?>> registry) {
        Map<String, DeferredHolder<EntityType<?>, EntityType<ProvincialAnimal>>> types = new LinkedHashMap<>();
        for (Rule rule : RULES.values()) types.put(rule.id(), registry.register(rule.id(), id -> EntityType.Builder
                .of(ProvincialAnimal::new, rule.category()).sized(rule.width(), rule.height()).eyeHeight(rule.height() * .74F)
                .clientTrackingRange(9).updateInterval(3).build(id.toString())));
        return Collections.unmodifiableMap(types);
    }

    private static Map<String, Rule> load() {
        var path = "/data/yuanxian/beast/provincial_fauna.json";
        Map<String, Rule> rules = new LinkedHashMap<>();
        try (var data = ProvincialFauna.class.getResourceAsStream(path)) {
            if (data == null) throw new IllegalStateException("未打包州域生物配置");
            var json = JsonParser.parseReader(new InputStreamReader(data, StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.get("schema").getAsInt() != 1) throw new IllegalArgumentException("州域生物格式版本错误");
            for (var value : json.getAsJsonArray("fauna")) {
                var row = value.getAsJsonObject();
                String id = row.get("id").getAsString();
                var size = row.getAsJsonArray("size");
                var rule = new Rule(id, ResourceLocation.parse(row.get("biome").getAsString()), row.get("region").getAsString(),
                        read(MobCategory.class, row.get("category").getAsString()), size.get(0).getAsFloat(), size.get(1).getAsFloat(),
                        read(Habitat.class, row.get("habitat").getAsString()), read(Activity.class, row.get("activity").getAsString()),
                        read(Behavior.class, row.get("behavior").getAsString()), Integer.parseInt(row.get("material_color").getAsString(), 16),
                        row.getAsJsonArray("palette").asList().stream().map(v -> ResourceLocation.parse(v.getAsString())).toList(),
                        row.getAsJsonArray("materials").asList().stream().map(v -> v.getAsString()).toList());
                if (rule.width() <= 0 || rule.height() <= 0 || rule.materials().size() != 2 || rules.putIfAbsent(id, rule) != null)
                    throw new IllegalArgumentException("州域生物体型或标识无效 " + id);
            }
            return Collections.unmodifiableMap(rules);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("州域生物规则读取失败 " + path, error);
        }
    }

    private static <E extends Enum<E>> E read(Class<E> type, String value) {
        return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    }
}
