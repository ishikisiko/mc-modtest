package com.example.myvillage.cultivation.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

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

    record Attack(Direction direction, double basic, double art, boolean paidOnly) implements HeartMethod {
        static final MapCodec<Attack> CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
                DIRECTION_CODEC.fieldOf("direction").forGetter(Attack::direction),
                Codec.doubleRange(0, 2048).fieldOf("basic").forGetter(Attack::basic),
                Codec.doubleRange(0, 2048).fieldOf("art").forGetter(Attack::art),
                Codec.BOOL.optionalFieldOf("paid_only", false).forGetter(Attack::paidOnly)
        ).apply(builder, Attack::new));

        @Override public Slot slot() { return Slot.ATTACK; }
    }

    record Defense(Direction direction, double base, Guard guard, double reaction) implements HeartMethod {
        static final MapCodec<Defense> CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
                DIRECTION_CODEC.fieldOf("direction").forGetter(Defense::direction),
                Codec.doubleRange(0, 1024).fieldOf("base").forGetter(Defense::base),
                StringRepresentable.fromEnum(Guard::values).optionalFieldOf("guard", Guard.NONE).forGetter(Defense::guard),
                Codec.doubleRange(0, 1024).optionalFieldOf("reaction", 0.0).forGetter(Defense::reaction)
        ).apply(builder, Defense::new));

        @Override public Slot slot() { return Slot.DEFENSE; }
    }

    /** Recovery is measured in quarters of one point per second. */
    record Spirit(Direction direction, int capacity, int recoveryQuarters, int normal, int stone) implements HeartMethod {
        static final MapCodec<Spirit> CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
                DIRECTION_CODEC.fieldOf("direction").forGetter(Spirit::direction),
                Codec.intRange(0, 1_000_000).fieldOf("capacity").forGetter(Spirit::capacity),
                Codec.intRange(0, 1_000_000).fieldOf("recovery_quarters").forGetter(Spirit::recoveryQuarters),
                Codec.intRange(0, 1_000_000).fieldOf("normal").forGetter(Spirit::normal),
                Codec.intRange(0, 1_000_000).fieldOf("stone").forGetter(Spirit::stone)
        ).apply(builder, Spirit::new));

        @Override public Slot slot() { return Slot.SPIRIT; }
    }
}
