package com.example.myvillage.talisman;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** 已锁入一纸一墨的持久符稿；品质与已支付脉冲不依赖当前操作者资质。 */
public record TalismanBatch(UUID owner, int recipe, int aptitude, int cap, int quality, int pulses, int required, int powerPerPulse) {
    public TalismanBatch {
        if (owner == null || recipe < 0 || recipe > 2 || aptitude < 0 || aptitude > 100
                || cap < 1 || cap > 4 || quality < 1 || quality > cap || required < 1 || required > 1000 || pulses < 0 || pulses > required
                || powerPerPulse < 1 || powerPerPulse > 1000)
            throw new IllegalArgumentException("符稿数据无效");
    }
    public TalismanBatch advance() {
        if (pulses == required) throw new IllegalStateException("符稿已经完成");
        return new TalismanBatch(owner, recipe, aptitude, cap, quality, pulses + 1, required, powerPerPulse);
    }
    public CompoundTag save() {
        CompoundTag t = new CompoundTag(); t.putUUID("Owner", owner); t.putInt("Recipe", recipe);
        t.putInt("Aptitude", aptitude); t.putInt("Quality", quality); t.putInt("Cap", cap); t.putInt("Pulses", pulses);
        t.putInt("Required", required); t.putInt("Power", powerPerPulse); return t;
    }
    public static TalismanBatch load(CompoundTag t) {
        return new TalismanBatch(t.getUUID("Owner"), t.getInt("Recipe"), t.getInt("Aptitude"), t.getInt("Cap"), t.getInt("Quality"),
                t.getInt("Pulses"), t.getInt("Required"), t.getInt("Power"));
    }
}
