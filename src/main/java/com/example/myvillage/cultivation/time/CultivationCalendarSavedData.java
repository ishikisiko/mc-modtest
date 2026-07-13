package com.example.myvillage.cultivation.time;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CultivationCalendarSavedData extends SavedData {
    private static final Logger LOGGER = LoggerFactory.getLogger(CultivationCalendarSavedData.class);
    private static final String DATA_NAME = "myvillage_cultivation_calendar";
    private static final String ELAPSED_TICKS_TAG = "elapsed_calendar_ticks";

    private long elapsedCalendarTicks;

    public CultivationCalendarSavedData() {
    }

    private CultivationCalendarSavedData(CompoundTag tag) {
        long stored = tag.getLong(ELAPSED_TICKS_TAG);
        if (stored < 0) {
            LOGGER.error("Cultivation calendar contained negative elapsed ticks {}; clamping to zero", stored);
            stored = 0;
            setDirty();
        }
        elapsedCalendarTicks = stored;
    }

    public long elapsedCalendarTicks() {
        return elapsedCalendarTicks;
    }

    public void incrementSaturated() {
        elapsedCalendarTicks = CultivationTimeMath.saturatingAdd(elapsedCalendarTicks, 1);
    }

    public void checkpoint() {
        setDirty();
    }

    public static CultivationCalendarSavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(CultivationCalendarSavedData::new, CultivationCalendarSavedData::load),
                DATA_NAME);
    }

    private static CultivationCalendarSavedData load(
            CompoundTag tag, HolderLookup.Provider registries) {
        return new CultivationCalendarSavedData(tag);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong(ELAPSED_TICKS_TAG, elapsedCalendarTicks);
        return tag;
    }
}
