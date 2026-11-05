package com.example.myvillage.worldplan.capital;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 城市局部坐标中的传送石碑；装饰由同城稀疏模板负责。 */
public record CapitalWaystone(String id, String name, int x, int y, int z, String facing, String block) {
    public static List<CapitalWaystone> read(JsonObject city, int width, int height, int depth) {
        if (!city.has("waystones")) return List.of();
        var rows = city.getAsJsonArray("waystones");
        if (rows.isEmpty() || rows.size() > 16) throw new IllegalArgumentException("主城传送石碑数量越界");
        var result = new ArrayList<CapitalWaystone>();
        Set<String> ids = new HashSet<>(), positions = new HashSet<>();
        for (var element : rows) {
            var row = element.getAsJsonObject();
            String id = row.get("id").getAsString(), name = row.get("name").getAsString();
            String facing = row.get("facing").getAsString(), block = row.get("block").getAsString();
            var p = row.getAsJsonArray("position");
            if (p.size() != 3) throw new IllegalArgumentException("传送石碑必须有三维坐标");
            int x = integer(p.get(0)), y = integer(p.get(1)), z = integer(p.get(2));
            if (!id.matches("[a-z0-9_]{1,64}") || name.isBlank() || name.length() > 64
                    || !Set.of("north", "south", "east", "west").contains(facing)
                    || !block.matches("waystones:[a-z0-9_]+") || !ids.add(id)
                    || !positions.add(x + "," + y + "," + z)
                    || x < 0 || x >= width || z < 0 || z >= depth || y < 1 || y + 1 >= height) {
                throw new IllegalArgumentException("传送石碑定义不合法：" + id);
            }
            result.add(new CapitalWaystone(id, name, x, y, z, facing, block));
        }
        return List.copyOf(result);
    }

    private static int integer(com.google.gson.JsonElement value) {
        try { return value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException failure) { throw new IllegalArgumentException("石碑坐标必须为整数", failure); }
    }

    public boolean inSlice(int localX, int localZ) {
        return x >= localX && x < localX + 16 && z >= localZ && z < localZ + 16;
    }
}
