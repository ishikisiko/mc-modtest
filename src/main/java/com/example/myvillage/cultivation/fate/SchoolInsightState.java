package com.example.myvillage.cultivation.fate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;

/** 确认天命时固定的流派，随玩家保存并在死亡后保留。 */
public record SchoolInsightState(String school) {
    public static final SchoolInsightState EMPTY = new SchoolInsightState("");
    public static final Codec<SchoolInsightState> CODEC = Codec.STRING.comapFlatMap(value -> {
        try { return DataResult.success(new SchoolInsightState(value)); }
        catch (IllegalArgumentException e) { return DataResult.error(e::getMessage); }
    }, SchoolInsightState::school);
    public SchoolInsightState {
        if (school == null || school.length() > 128 || !school.isEmpty() && ResourceLocation.tryParse(school) == null)
            throw new IllegalArgumentException("无效流派");
    }
}
