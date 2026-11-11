package com.example.myvillage.estate;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Geometry and payment are read from the same generated, hashed city manifest. */
public record EstateDefinition(String id, String name, EstateBounds bounds, BlockPos steward,
        BlockPos exit, String stewardName, float yaw, ResourceLocation currency, int price, String currencyName) {
    public static EstateDefinition read(JsonObject root, int width, int height, int depth) {
        String id = root.get("id").getAsString(), name = root.get("name").getAsString();
        if (!id.matches("[a-z0-9_]{1,64}") || name.isBlank() || name.length() > 64
                || !root.get("access").getAsString().equals("open_after_purchase")
                || !root.get("vertical_scope").getAsString().equals("entire_column"))
            throw new IllegalArgumentException("庄园身份无效");
        var a = root.getAsJsonArray("bounds");
        if (a.size() != 4) throw new IllegalArgumentException("庄园范围字段无效");
        var b = new EstateBounds(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsInt());
        if (b.minX() < 0 || b.minZ() < 0 || b.maxX() > width || b.maxZ() > depth)
            throw new IllegalArgumentException("庄园范围超出城市");
        var s = root.getAsJsonObject("steward");
        BlockPos steward = point(s, "position", width, height, depth), exit = point(root, "exit", width, height, depth);
        if (b.overlaps(steward.getX() + .5, steward.getZ() + .5, .3)
                || b.overlaps(exit.getX() + .5, exit.getZ() + .5, .3))
            throw new IllegalArgumentException("管家与出口必须处于公共巷道");
        var p = root.getAsJsonObject("price"); int count = p.get("count").getAsInt();
        if (count < 1 || count > 1_000_000) throw new IllegalArgumentException("房价无效");
        return new EstateDefinition(id, name, b, steward, exit, s.get("name").getAsString(),
                s.get("yaw").getAsFloat(), ResourceLocation.parse(p.get("item").getAsString()), count, p.get("name").getAsString());
    }
    private static BlockPos point(JsonObject root, String key, int w, int h, int d) {
        var p = root.getAsJsonArray(key);
        if (p.size() != 3) throw new IllegalArgumentException("庄园坐标无效");
        var at = new BlockPos(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt());
        if (at.getX() < 0 || at.getX() >= w || at.getZ() < 0 || at.getZ() >= d || at.getY() < 1 || at.getY() >= h - 2)
            throw new IllegalArgumentException("庄园坐标越界");
        return at;
    }
}
