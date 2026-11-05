package com.example.myvillage.talisman;

import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.Optional;

/** 每位玩家只有一个可覆盖标记，与符箓堆叠无关。 */
public record RecallMark(Optional<TravelPoint> point) {
    public static final RecallMark EMPTY = new RecallMark(Optional.empty());
    public static final Codec<RecallMark> CODEC = TravelPoint.CODEC.optionalFieldOf("point").codec()
            .xmap(RecallMark::new, RecallMark::point);
    public RecallMark { Objects.requireNonNull(point); }
    public RecallMark replace(TravelPoint value) {
        if (!value.valid()) throw new IllegalArgumentException("闪回地点数据无效");
        return new RecallMark(Optional.of(value));
    }
}
