package com.example.myvillage.client.immortal;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Presentation only: no packet, gameplay attribute, hitbox or server state. */
public final class ImmortalClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue EFFECTS;
    private static final ModConfigSpec.DoubleValue DISTANCE;
    private static final ModConfigSpec.IntValue ELEMENTS;
    static {
        var b = new ModConfigSpec.Builder();
        EFFECTS=b.comment("启用仙衣微动、流光与局部明灭；关闭后保留完整静态外观。").define("effects",true);
        DISTANCE=b.comment("动效最远距离，单位为方块。").defineInRange("effectDistance",32.0,0.0,128.0);
        ELEMENTS=b.comment("每个实体每帧最多绘制的流光、拖尾和弧光线段数量。").defineInRange("maxEffectElements",96,0,512);
        SPEC=b.build();
    }
    static boolean enabled() { return EFFECTS.get(); }
    static double distance() { return DISTANCE.get(); }
    static int elements() { return ELEMENTS.get(); }
    private ImmortalClientConfig() { }
}
