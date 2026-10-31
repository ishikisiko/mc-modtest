package com.example.myvillage.manor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** 玩家名称与材质来自独立生成目录；不改变注册、模型、碰撞或造型状态。 */
public final class ManorPresentation {
    public record Info(String city, String nameKey, List<String> materials) {
        public Info { materials = List.copyOf(materials); }
        public Component name() { return Component.translatable(nameKey); }
        public Component materialsLine() {
            var names = Component.empty();
            for (String material : materials) {
                if (!names.getSiblings().isEmpty()) names.append("、");
                names.append(Component.translatable(material));
            }
            return Component.translatable("tooltip.myvillage.manor_materials", names);
        }
        public Component styleLine() {
            return Component.translatable("tooltip.myvillage.manor_style", get().cityNames.get(city));
        }
    }

    private final Map<String, List<Info>> banks;
    private final Map<ResourceLocation, Info> materials;
    private final Map<String, String> cityNames;

    private ManorPresentation(Map<String, List<Info>> banks, Map<ResourceLocation, Info> materials, Map<String, String> cityNames) {
        this.banks = Map.copyOf(banks); this.materials = Map.copyOf(materials); this.cityNames = Map.copyOf(cityNames);
    }
    private static final class Holder { static final ManorPresentation DATA = load(); }
    public static ManorPresentation get() { return Holder.DATA; }

    public Info component(String bank, int shape) {
        var profiles = banks.get(bank);
        if (profiles == null || shape < 0 || shape >= profiles.size()) throw new IllegalArgumentException("未知建筑造型");
        return profiles.get(shape);
    }
    public Optional<Info> describe(BlockState state) {
        if (state.getBlock() instanceof ManorComponentBlock block)
            return Optional.of(component(block.bankId(), state.getValue(ManorComponentBlock.SHAPE)));
        return Optional.ofNullable(materials.get(BuiltInRegistries.BLOCK.getKey(state.getBlock())));
    }
    public Optional<Info> describe(ItemStack stack) {
        if (stack.getItem() instanceof ManorComponentItem item)
            return Optional.of(component(((ManorComponentBlock)item.getBlock()).bankId(), shape(stack)));
        return Optional.empty();
    }
    /** 与 BlockItem 的造型状态同源；缺失或无效状态使用方块默认造型。 */
    public static int shape(ItemStack stack) {
        var state = stack.get(DataComponents.BLOCK_STATE);
        if (state == null) return 0;
        String value = state.properties().get("shape");
        if (value == null || value.length() > 2) return 0;
        try { int shape = Integer.parseInt(value); return shape >= 0 && shape < 32 ? shape : 0; }
        catch (NumberFormatException ignored) { return 0; }
    }
    private static Info info(String city, JsonObject data) {
        String name = data.get("name").getAsString();
        List<String> materials = new ArrayList<>();
        for (var value : data.getAsJsonArray("materials")) materials.add(value.getAsString());
        if (name.isBlank() || materials.isEmpty()) throw new IllegalArgumentException("建筑显示信息不完整");
        return new Info(city, name, materials);
    }
    private static ManorPresentation load() {
        String path = "/data/myvillage/lord_manors/presentation.json";
        try (var input = ManorPresentation.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("缺少建筑显示目录");
            var root = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1) throw new IllegalArgumentException("建筑显示目录格式错误");
            Map<String, String> cities = new HashMap<>();
            root.getAsJsonObject("city_names").entrySet().forEach(e -> cities.put(e.getKey(), e.getValue().getAsString()));
            Map<String, List<Info>> banks = new HashMap<>();
            for (var entry : root.getAsJsonObject("banks").entrySet()) {
                var data = entry.getValue().getAsJsonObject(); String city = data.get("city").getAsString();
                if (!cities.containsKey(city)) throw new IllegalArgumentException("建筑显示目录含未知城");
                List<Info> profiles = new ArrayList<>();
                for (var profile : data.getAsJsonArray("profiles")) profiles.add(info(city, profile.getAsJsonObject()));
                if (profiles.size() != 32) throw new IllegalArgumentException("建筑造型显示信息不齐全");
                banks.put(entry.getKey(), List.copyOf(profiles));
            }
            Map<ResourceLocation, Info> materials = new HashMap<>();
            for (var entry : root.getAsJsonObject("materials").entrySet()) {
                var data = entry.getValue().getAsJsonObject(); String city = data.get("city").getAsString();
                if (!cities.containsKey(city)) throw new IllegalArgumentException("建筑材质含未知城");
                materials.put(ResourceLocation.fromNamespaceAndPath("myvillage", entry.getKey()), info(city, data));
            }
            return new ManorPresentation(banks, materials, cities);
        } catch (IOException e) { throw new IllegalStateException("无法读取建筑显示目录", e); }
    }
}
