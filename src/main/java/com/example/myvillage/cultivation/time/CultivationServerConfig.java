package com.example.myvillage.cultivation.time;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CultivationServerConfig {
    public static final int DEFAULT_TICKS_PER_DAY = 24_000;
    public static final int DEFAULT_DAYS_PER_MONTH = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(CultivationServerConfig.class);

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue FATE_BUDGET;
    private static final ModConfigSpec.IntValue TICKS_PER_DAY;
    private static final ModConfigSpec.IntValue DAYS_PER_MONTH;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("cultivation_time");
        TICKS_PER_DAY = builder
                .comment("Effective server ticks in one cultivation day. Changing this reinterprets stored raw ticks.")
                .defineInRange("ticks_per_day", DEFAULT_TICKS_PER_DAY, 1, Integer.MAX_VALUE);
        DAYS_PER_MONTH = builder
                .comment("每月的修炼天数，一年固定十二个月。修改后会重新解释已经记录的日历和寿元。")
                .defineInRange("days_per_month", DEFAULT_DAYS_PER_MONTH, 1,
                        Integer.MAX_VALUE / CultivationTimeMath.MONTHS_PER_YEAR);
        builder.pop();
        builder.push("innate_fates");
        FATE_BUDGET = builder.comment("Point limit for new fate confirmations; confirmed choices remain fixed.")
                .defineInRange("point_budget", 8, 3, 18);
        builder.pop();
        SPEC = builder.build();
    }

    private CultivationServerConfig() {
    }

    public static int fateBudget() { return FATE_BUDGET.getAsInt(); }

    public static Scale scale() {
        return new Scale(TICKS_PER_DAY.getAsInt(), DAYS_PER_MONTH.getAsInt());
    }

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            warnAboutRawTickReinterpretation("loaded");
        }
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            warnAboutRawTickReinterpretation("reloaded");
            CultivationTimeRuntime.onScaleReloaded();
        }
    }

    public static void warnAboutRawTickReinterpretation(String action) {
        Scale scale = scale();
        LOGGER.warn(
                "Cultivation time configuration {} with ticks_per_day={}, days_per_month={}, months_per_year={} and days_per_year={}. "
                        + "Stored calendar/lifespan values are raw ticks and are not rescaled; changing any "
                        + "setting retroactively changes displayed dates, remaining lifespan, and exhaustion.",
                action,
                scale.ticksPerDay(),
                scale.daysPerMonth(),
                CultivationTimeMath.MONTHS_PER_YEAR,
                scale.daysPerYear());
    }

    public record Scale(int ticksPerDay, int daysPerMonth) {
        public Scale {
            CultivationTimeMath.ticksPerYear(ticksPerDay, CultivationTimeMath.daysPerYear(daysPerMonth));
        }

        public int daysPerYear() {
            return CultivationTimeMath.daysPerYear(daysPerMonth);
        }

        public long ticksPerYear() {
            return CultivationTimeMath.ticksPerYear(ticksPerDay, daysPerYear());
        }

        public long maximumLifespanTicks(int years) {
            return CultivationTimeMath.maximumLifespanTicks(years, ticksPerYear());
        }
    }
}
