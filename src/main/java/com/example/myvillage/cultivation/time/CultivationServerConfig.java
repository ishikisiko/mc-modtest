package com.example.myvillage.cultivation.time;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CultivationServerConfig {
    public static final int DEFAULT_TICKS_PER_DAY = 24_000;
    public static final int DEFAULT_DAYS_PER_YEAR = 24;
    public static final int DEFAULT_DAYS_PER_WEEK = 6;

    private static final Logger LOGGER = LoggerFactory.getLogger(CultivationServerConfig.class);

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue TICKS_PER_DAY;
    private static final ModConfigSpec.IntValue DAYS_PER_YEAR;
    private static final ModConfigSpec.IntValue DAYS_PER_WEEK;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("cultivation_time");
        TICKS_PER_DAY = builder
                .comment("Effective server ticks in one cultivation day. Changing this reinterprets stored raw ticks.")
                .defineInRange("ticks_per_day", DEFAULT_TICKS_PER_DAY, 1, Integer.MAX_VALUE);
        DAYS_PER_YEAR = builder
                .comment("Cultivation days in one year. Changing this reinterprets stored raw ticks.")
                .defineInRange("days_per_year", DEFAULT_DAYS_PER_YEAR, 1, Integer.MAX_VALUE);
        DAYS_PER_WEEK = builder
                .comment("Cultivation days in one week, a display unit between day and year. Changing this "
                        + "reinterprets stored raw ticks; days_per_year should be a multiple of it.")
                .defineInRange("days_per_week", DEFAULT_DAYS_PER_WEEK, 1, Integer.MAX_VALUE);
        builder.pop();
        SPEC = builder.build();
    }

    private CultivationServerConfig() {
    }

    public static Scale scale() {
        return new Scale(TICKS_PER_DAY.getAsInt(), DAYS_PER_YEAR.getAsInt(), DAYS_PER_WEEK.getAsInt());
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
                "Cultivation time configuration {} with ticks_per_day={}, days_per_year={} and days_per_week={}. "
                        + "Stored calendar/lifespan values are raw ticks and are not rescaled; changing any "
                        + "setting retroactively changes displayed dates, remaining lifespan, and exhaustion.",
                action,
                scale.ticksPerDay(),
                scale.daysPerYear(),
                scale.daysPerWeek());
        if (!scale.wholeWeeksPerYear()) {
            LOGGER.warn(
                    "Cultivation days_per_year={} is not a multiple of days_per_week={}; the last week of each "
                            + "year is shorter and week numbers restart with every year.",
                    scale.daysPerYear(),
                    scale.daysPerWeek());
        }
    }

    public record Scale(int ticksPerDay, int daysPerYear, int daysPerWeek) {
        public Scale {
            CultivationTimeMath.ticksPerYear(ticksPerDay, daysPerYear);
            if (daysPerWeek <= 0) {
                throw new IllegalArgumentException("Cultivation days per week must be positive");
            }
        }

        /** A scale with the default week length. */
        public Scale(int ticksPerDay, int daysPerYear) {
            this(ticksPerDay, daysPerYear, DEFAULT_DAYS_PER_WEEK);
        }

        /** Whether a year holds a whole number of weeks; a mismatch is allowed and only warned about. */
        public boolean wholeWeeksPerYear() {
            return daysPerYear % daysPerWeek == 0;
        }

        public long ticksPerYear() {
            return CultivationTimeMath.ticksPerYear(ticksPerDay, daysPerYear);
        }

        public long maximumLifespanTicks(int years) {
            return CultivationTimeMath.maximumLifespanTicks(years, ticksPerYear());
        }
    }
}
