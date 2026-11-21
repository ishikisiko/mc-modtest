package com.example.myvillage.cultivation.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.Optional;

/** Fixed effects carried by the synchronized definition of a single heart technique. */
public sealed interface HeartMethod permits HeartMethod.Attack, HeartMethod.Defense, HeartMethod.Spirit {
    enum Slot implements StringRepresentable {
        ATTACK("攻"), DEFENSE("防守"), SPIRIT("灵力与修炼");

        private final String title;

        Slot(String title) { this.title = title; }
        public String title() { return title; }
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    enum Direction implements StringRepresentable {
        SWORD("剑"), SPEAR("枪"), FIST("拳掌"), SPELL("法术"), GENERAL("通用");

        private final String title;

        Direction(String title) { this.title = title; }
        public String title() { return title; }
        public boolean weapon() { return this == SWORD || this == SPEAR || this == FIST; }
        public boolean matchesWeapon(String family) { return weapon() && getSerializedName().equals(family); }
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    enum Guard implements StringRepresentable {
        NONE, MELEE, RANGED;
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    Codec<Direction> DIRECTION_CODEC = StringRepresentable.fromEnum(Direction::values);
    Codec<HeartMethod> CODEC = StringRepresentable.fromEnum(Slot::values).dispatch(
            "slot", HeartMethod::slot, slot -> switch (slot) {
                case ATTACK -> Attack.CODEC;
                case DEFENSE -> Defense.CODEC;
                case SPIRIT -> Spirit.CODEC;
            });

    Slot slot();
    Direction direction();

    record Attack(Direction direction, double attack, double flatPenetration, Optional<AttackExtra> extra) implements HeartMethod {
        static final MapCodec<Attack> CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
                DIRECTION_CODEC.fieldOf("direction").forGetter(Attack::direction),
                Codec.doubleRange(0, 2048).fieldOf("attack").forGetter(Attack::attack),
                Codec.doubleRange(0, 1024).fieldOf("flat_penetration").forGetter(Attack::flatPenetration),
                AttackExtra.CODEC.optionalFieldOf("extra").forGetter(Attack::extra)
        ).apply(builder, Attack::new));

        @Override public Slot slot() { return Slot.ATTACK; }
    }

    record AttackExtra(double amount, boolean artOnly, boolean paidOnly) {
        static final Codec<AttackExtra> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Codec.doubleRange(0, 2048).fieldOf("amount").forGetter(AttackExtra::amount),
                Codec.BOOL.optionalFieldOf("art_only", false).forGetter(AttackExtra::artOnly),
                Codec.BOOL.optionalFieldOf("paid_only", false).forGetter(AttackExtra::paidOnly)
        ).apply(builder, AttackExtra::new));
    }

    record Defense(Direction direction, double defense, double maxHealth, Optional<DefenseExtra> extra,
            DefenseAttributes attributes, Optional<DefenseShield> shield) implements HeartMethod {
        static final MapCodec<Defense> CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
                DIRECTION_CODEC.fieldOf("direction").forGetter(Defense::direction),
                Codec.doubleRange(0, 1024).fieldOf("defense").forGetter(Defense::defense),
                Codec.doubleRange(0, 1024).fieldOf("max_health").forGetter(Defense::maxHealth),
                DefenseExtra.CODEC.optionalFieldOf("extra").forGetter(Defense::extra),
                DefenseAttributes.CODEC.optionalFieldOf("attributes", DefenseAttributes.NONE).forGetter(Defense::attributes),
                DefenseShield.CODEC.optionalFieldOf("shield").forGetter(Defense::shield)
        ).apply(builder, Defense::new));

        @Override public Slot slot() { return Slot.DEFENSE; }
    }

    record DefenseAttributes(double armor, double armorToughness, double knockbackResistance, double physicalResistance) {
        public static final DefenseAttributes NONE = new DefenseAttributes(0, 0, 0, 0);
        static final Codec<DefenseAttributes> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Codec.doubleRange(0, 30).optionalFieldOf("armor", 0.0).forGetter(DefenseAttributes::armor),
                Codec.doubleRange(0, 20).optionalFieldOf("armor_toughness", 0.0).forGetter(DefenseAttributes::armorToughness),
                Codec.doubleRange(0, 1).optionalFieldOf("knockback_resistance", 0.0)
                        .forGetter(DefenseAttributes::knockbackResistance),
                Codec.doubleRange(0, 0.8).optionalFieldOf("physical_resistance", 0.0)
                        .forGetter(DefenseAttributes::physicalResistance)
        ).apply(builder, DefenseAttributes::new));
    }

    record DefenseShield(double healthThreshold, float amount, int durationTicks, int cooldownTicks) {
        static final Codec<DefenseShield> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Codec.doubleRange(0.0001, 0.9999).fieldOf("health_threshold").forGetter(DefenseShield::healthThreshold),
                Codec.floatRange(0.01F, 2048).fieldOf("amount").forGetter(DefenseShield::amount),
                Codec.intRange(1, 12000).fieldOf("duration_ticks").forGetter(DefenseShield::durationTicks),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("cooldown_ticks").forGetter(DefenseShield::cooldownTicks)
        ).apply(builder, DefenseShield::new));

        public boolean crossesThreshold(double healthBefore, double healthAfter, double maxHealth) {
            double threshold = maxHealth * healthThreshold;
            return healthBefore > threshold && healthAfter > 0 && healthAfter <= threshold;
        }
    }

    record DefenseExtra(double amount, Guard guard) {
        static final Codec<DefenseExtra> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Codec.doubleRange(0, 1024).fieldOf("amount").forGetter(DefenseExtra::amount),
                StringRepresentable.fromEnum(Guard::values).optionalFieldOf("guard", Guard.NONE).forGetter(DefenseExtra::guard)
        ).apply(builder, DefenseExtra::new));
    }

    /** Recovery is measured in quarters of one point per second. */
    record Spirit(Direction direction, int capacity, int recoveryQuarters, int cultivation, int stoneExtra) implements HeartMethod {
        static final MapCodec<Spirit> CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
                DIRECTION_CODEC.fieldOf("direction").forGetter(Spirit::direction),
                Codec.intRange(0, 1_000_000).fieldOf("capacity").forGetter(Spirit::capacity),
                Codec.intRange(0, 1_000_000).fieldOf("recovery_quarters").forGetter(Spirit::recoveryQuarters),
                Codec.intRange(0, 1_000_000).fieldOf("cultivation").forGetter(Spirit::cultivation),
                Codec.intRange(0, 1_000_000).optionalFieldOf("stone_extra", 0).forGetter(Spirit::stoneExtra)
        ).apply(builder, Spirit::new));

        @Override public Slot slot() { return Slot.SPIRIT; }
    }
}
