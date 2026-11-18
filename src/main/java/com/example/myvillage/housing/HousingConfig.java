package com.example.myvillage.housing;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class HousingConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue CLAIM_COST,BUILD_COST,BLOCKS_PER_TICK;
    public static final ModConfigSpec.ConfigValue<String> CURRENCY;
    static {
        var b=new ModConfigSpec.Builder();b.push("housing");
        CLAIM_COST=b.comment("领取一块宅地所需的灵石数量；开发期默认免费。").defineInRange("claim_cost",0,0,1_000_000);
        BUILD_COST=b.comment("使用住宅模板建房所需的灵石数量；开发期默认免费。自由搭建不收费。").defineInRange("build_cost",0,0,1_000_000);
        CURRENCY=b.comment("宅地与模板建房使用的灵石物品 ID。").define("currency","yuanxian:low_grade_spirit_stone");
        BLOCKS_PER_TICK=b.comment("施工每刻最多写入的方块数量；整件构件不会拆成两刻。").defineInRange("blocks_per_tick",1024,128,8192);
        b.pop();SPEC=b.build();
    }
    private HousingConfig(){}
}
