package com.example.myvillage.entity.beast;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One bundled catalogue supplies the regular enemies of the sixteen provincial biomes. */
public final class ProvincialHostiles {
    public enum Behavior { GROUND, WING, CLIMB, WADER, SWIM;
        public boolean aquatic() { return this == SWIM || this == WADER; }
    }

    public enum Habitat { BANK, TREES, LEDGE, FALLS, SNOW, ICE, TIDE, REEF, REDROCK, SALT, FEN, LAVA, CRYSTAL, FORD }
    public enum Activity { ANY, DAY, TWILIGHT }
    public enum MoveContext { FREE, NEAR_LAVA, AWAY_LAVA, NEAR_CRYSTAL, AWAY_CRYSTAL }

    public record Rule(String id, ResourceLocation biome, String region, Behavior behavior, Habitat habitat,
                       Activity activity, float width, float height, int color, List<ResourceLocation> palette,
                       List<String> materials, Map<ResourceLocation, MoveContext> moveContexts) {}

    private static final Map<String, Rule> RULES = load();
    private ProvincialHostiles() {}

    public static List<Rule> rules() { return List.copyOf(RULES.values()); }

    public static Rule require(ResourceLocation id) {
        var rule = RULES.get(id.getPath());
        if (rule == null) throw new IllegalArgumentException("缺少州域敌人规则 " + id);
        return rule;
    }

    public static Map<String, DeferredHolder<EntityType<?>, EntityType<ProvincialEnemy>>> register(
            DeferredRegister<EntityType<?>> registry) {
        Map<String, DeferredHolder<EntityType<?>, EntityType<ProvincialEnemy>>> types = new LinkedHashMap<>();
        for (var rule : RULES.values()) {
            types.put(rule.id(), registry.register(rule.id(), id -> {
                var builder = EntityType.Builder.of(ProvincialEnemy::new, MobCategory.MONSTER)
                        .sized(rule.width(), rule.height()).eyeHeight(rule.height() * .76F)
                        .clientTrackingRange(10).updateInterval(2);
                if (rule.habitat() == Habitat.LAVA) builder.fireImmune();
                return builder.build(id.toString());
            }));
        }
        return java.util.Collections.unmodifiableMap(types);
    }

    private static Map<String, Rule> load() {
        var path = "/data/yuanxian/beast/provincial_enemies.json";
        Map<String, Rule> rules = new LinkedHashMap<>();
        try (var data = ProvincialHostiles.class.getResourceAsStream(path)) {
            if (data == null) throw new IllegalStateException("未打包州域敌人目录 " + path);
            var json = JsonParser.parseReader(new InputStreamReader(data, StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.get("schema").getAsInt() != 1) throw new IllegalArgumentException("州域敌人目录格式错误");
            for (var value : json.getAsJsonArray("enemies")) {
                var row = value.getAsJsonObject();
                var id = row.get("id").getAsString();
                var size = row.getAsJsonArray("size");
                Map<ResourceLocation, MoveContext> contexts = new LinkedHashMap<>();
                row.getAsJsonObject("move_contexts").asMap().forEach((move, context) ->
                        contexts.put(ResourceLocation.parse(move), read(MoveContext.class, context.getAsString())));
                var rule = new Rule(id, ResourceLocation.parse(row.get("biome").getAsString()), row.get("region").getAsString(),
                        read(Behavior.class, row.get("behavior").getAsString()), read(Habitat.class, row.get("habitat").getAsString()),
                        read(Activity.class, row.get("activity").getAsString()), size.get(0).getAsFloat(), size.get(1).getAsFloat(),
                        Integer.parseInt(row.get("egg_color").getAsString(), 16),
                        row.getAsJsonArray("palette").asList().stream().map(v -> ResourceLocation.parse(v.getAsString())).toList(),
                        row.getAsJsonArray("materials").asList().stream().map(v -> v.getAsString()).toList(), Map.copyOf(contexts));
                if (rule.width() <= 0 || rule.height() <= 0 || rule.materials().size() != 2
                        || contexts.isEmpty() || rules.putIfAbsent(id, rule) != null) {
                    throw new IllegalArgumentException("州域敌人体型、标识或招式有误 " + id);
                }
            }
            return java.util.Collections.unmodifiableMap(rules);
        } catch (IOException error) {
            throw new IllegalStateException("读取州域敌人目录失败 " + path, error);
        }
    }

    private static <E extends Enum<E>> E read(Class<E> type, String value) {
        return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    }
}
