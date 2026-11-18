package com.example.myvillage.enchanting;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

public record SealDisc(String name, String role, String color, String description) {
    public static final Map<String, SealDisc> TYPES = load();

    private static Map<String, SealDisc> load() {
        var json = EnchantingRules.read("discs", JsonObject.class);
        var gson = new Gson();
        var discs = new LinkedHashMap<String, SealDisc>();
        json.entrySet().forEach(entry -> discs.put(entry.getKey(), gson.fromJson(entry.getValue(), SealDisc.class)));
        return Collections.unmodifiableMap(discs);
    }

    public static String type(ItemStack stack) {
        if (stack.isEmpty()) return "empty";
        var name = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!name.getNamespace().equals(com.example.myvillage.MyVillageMod.MOD_ID)) return "empty";
        var path = name.getPath();
        String key = path.startsWith("seal_disc_") ? path.substring("seal_disc_".length()) : "empty";
        return TYPES.containsKey(key) ? key : "empty";
    }

    public static boolean fits(int slot, ItemStack stack) {
        var type = TYPES.get(type(stack));
        return slot >= 0 && slot < SealProjection.SLOTS.size() && type != null
                && type.role().equals(SealProjection.SLOTS.get(slot).role());
    }

    public static List<String> keys() {
        return TYPES.keySet().stream().filter(key -> !key.equals("empty")).toList();
    }

    public int rgb() { return Integer.parseInt(color.substring(1), 16); }
}
