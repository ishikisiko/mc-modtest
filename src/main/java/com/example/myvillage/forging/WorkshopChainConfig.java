package com.example.myvillage.forging;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class WorkshopChainConfig {
    public static final String FILE_NAME = "yuanxian-workshop-harvest-server.toml";
    public static final ModConfigSpec SPEC;
    private static final List<String> REALMS = List.of("yuanxian:qi_refining", "yuanxian:foundation_establishment",
            "yuanxian:golden_core", "yuanxian:nascent_soul", "yuanxian:spirit_transformation");
    private static final ModConfigSpec.IntValue INITIAL_RADIUS, RADIUS_STEP, INITIAL_COUNT, COUNT_MULTIPLIER;
    private static final List<ModConfigSpec.IntValue> AXE_INTERVALS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> ORE_FAMILIES;

    static {
        var b = new ModConfigSpec.Builder();
        b.push("pickaxe");
        INITIAL_RADIUS = b.comment("练气镐离首块矿石的最大距离。").defineInRange("initial_radius", 3, 1, 32);
        RADIUS_STEP = b.comment("每升一个大境界增加的搜矿半径。").defineInRange("radius_per_realm", 3, 0, 16);
        INITIAL_COUNT = b.comment("练气镐一次最多挖多少块，包含首块。").defineInRange("initial_count", 8, 1, 128);
        COUNT_MULTIPLIER = b.comment("每升一个大境界，连锁数量上限乘此整数。运行时最多为 512 块。")
                .defineInRange("count_multiplier", 2, 1, 4);
        ORE_FAMILIES = b.comment("连锁矿种的方块标签。同一标签内的矿石可以相连；按列出的顺序匹配。")
                .defineListAllowEmpty("ore_families", List.of("c:ores/spirit_stone", "c:ores/xuantie",
                        "c:ores/coal", "c:ores/copper", "c:ores/iron", "c:ores/gold", "c:ores/redstone",
                        "c:ores/lapis", "c:ores/diamond", "c:ores/emerald", "c:ores/quartz", "c:ores/netherite_scrap"),
                        () -> "c:ores/<矿种>",
                        element -> element instanceof String id && ResourceLocation.tryParse(id) != null);
        b.pop();
        b.push("axe");
        b.comment("砍断首块以后，每处理一根原木的间隔，单位为 tick。");
        AXE_INTERVALS = List.of(b.defineInRange("qi_refining",10,1,200),
                b.defineInRange("foundation_establishment",6,1,200),
                b.defineInRange("golden_core",4,1,200),
                b.defineInRange("nascent_soul",2,1,200),
                b.defineInRange("spirit_transformation",1,1,200));
        b.pop();
        SPEC = b.build();
    }

    private WorkshopChainConfig() {}

    record PickaxeLimits(int radius, int count) {}

    static int rank(String realm) { return REALMS.indexOf(realm); }
    static PickaxeLimits pickaxe(int rank) {
        return pickaxe(rank,value(INITIAL_RADIUS),value(RADIUS_STEP),value(INITIAL_COUNT),value(COUNT_MULTIPLIER));
    }
    static PickaxeLimits pickaxe(int rank,int radius,int step,int count,int multiplier) {
        for (int i = 0; i < rank; i++) count = Math.min(512,count*multiplier);
        return new PickaxeLimits(radius+rank*step,count);
    }
    static int axeInterval(int rank) { return value(AXE_INTERVALS.get(rank)); }
    static List<? extends String> oreFamilies() { return value(ORE_FAMILIES); }

    private static <T> T value(ModConfigSpec.ConfigValue<T> option) {
        return SPEC.isLoaded() ? option.get() : option.getDefault();
    }
}
