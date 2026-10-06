package com.example.myvillage.cultivation.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Category-specific technique effects. Only {@link Core} drives a mechanism in the first phase
 * (the meditation route the meridian chart lights); {@link Active}, {@link Movement} and
 * {@link Body} are decoded and validated so the data can ship ahead of their executors.
 * A block is accepted only on a technique of the matching category.
 */
public record TechniqueEffects(
        Optional<Core> core,
        Optional<Active> active,
        Optional<Movement> movement,
        Optional<Body> body) {
    public static final TechniqueEffects NONE =
            new TechniqueEffects(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    public static final Codec<TechniqueEffects> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Core.CODEC.optionalFieldOf("core").forGetter(TechniqueEffects::core),
            Active.CODEC.optionalFieldOf("active").forGetter(TechniqueEffects::active),
            Movement.CODEC.optionalFieldOf("movement").forGetter(TechniqueEffects::movement),
            Body.CODEC.optionalFieldOf("body").forGetter(TechniqueEffects::body)
    ).apply(instance, TechniqueEffects::new));

    public TechniqueEffects {
        core = Objects.requireNonNull(core, "core");
        active = Objects.requireNonNull(active, "active");
        movement = Objects.requireNonNull(movement, "movement");
        body = Objects.requireNonNull(body, "body");
    }

    public static TechniqueEffects core(String meditationRoute) {
        return new TechniqueEffects(
                Optional.of(new Core(meditationRoute)), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public boolean isEmpty() {
        return core.isEmpty() && active.isEmpty() && movement.isEmpty() && body.isEmpty();
    }

    /** Rejects an effects block that belongs to a category other than {@code category}. */
    public void requireCategory(TechniqueCategory category) {
        Objects.requireNonNull(category, "category");
        rejectUnless(core.isPresent(), category == TechniqueCategory.CORE, "core", category);
        rejectUnless(active.isPresent(), category == TechniqueCategory.ACTIVE, "active", category);
        rejectUnless(movement.isPresent(), category == TechniqueCategory.MOVEMENT, "movement", category);
        rejectUnless(body.isPresent(), category == TechniqueCategory.BODY, "body", category);
    }

    private static void rejectUnless(
            boolean present, boolean matches, String block, TechniqueCategory category) {
        if (present && !matches) {
            throw new IllegalArgumentException("effects." + block + " is not allowed on a "
                    + category.serializedName() + " technique");
        }
    }

    static <T> DataResult<T> construct(Supplier<T> constructor) {
        try {
            return DataResult.success(constructor.get());
        } catch (IllegalArgumentException | NullPointerException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    /** 心法: {@code {"meditation_route": "xiaozhoutian"}}; the route only selects the meridian path shown. */
    public record Core(String meditationRoute) {
        public static final Codec<Core> CODEC = Codec.STRING.fieldOf("meditation_route").codec()
                .comapFlatMap(route -> construct(() -> new Core(route)), Core::meditationRoute);

        public Core {
            if (meditationRoute == null || meditationRoute.isBlank()) {
                throw new IllegalArgumentException("effects.core.meditation_route must not be blank");
            }
        }
    }

    /** 绝技 (second phase): {@code {"skill": id, "qi_cost": n, "slots": n}}. */
    public record Active(ResourceLocation skill, int qiCost, int slots) {
        private record Raw(ResourceLocation skill, int qiCost, int slots) {
        }

        private static final Codec<Raw> SERIALIZED = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("skill").forGetter(Raw::skill),
                Codec.INT.fieldOf("qi_cost").forGetter(Raw::qiCost),
                Codec.INT.fieldOf("slots").forGetter(Raw::slots)
        ).apply(instance, Raw::new));
        public static final Codec<Active> CODEC = SERIALIZED.comapFlatMap(
                raw -> construct(() -> new Active(raw.skill(), raw.qiCost(), raw.slots())),
                value -> new Raw(value.skill(), value.qiCost(), value.slots()));

        public Active {
            Objects.requireNonNull(skill, "effects.active.skill");
            if (qiCost < 0) {
                throw new IllegalArgumentException("effects.active.qi_cost must be non-negative, got " + qiCost);
            }
            if (slots < 1) {
                throw new IllegalArgumentException("effects.active.slots must be at least 1, got " + slots);
            }
        }
    }

    /** 身法 (second phase): dash distance, invulnerable window, qi cost and cooldown. */
    public record Movement(double dashDistance, int invulnerableTicks, int qiCost, int cooldownTicks) {
        private record Raw(double dashDistance, int invulnerableTicks, int qiCost, int cooldownTicks) {
        }

        private static final Codec<Raw> SERIALIZED = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.fieldOf("dash_distance").forGetter(Raw::dashDistance),
                Codec.INT.fieldOf("invulnerable_ticks").forGetter(Raw::invulnerableTicks),
                Codec.INT.fieldOf("qi_cost").forGetter(Raw::qiCost),
                Codec.INT.fieldOf("cooldown_ticks").forGetter(Raw::cooldownTicks)
        ).apply(instance, Raw::new));
        public static final Codec<Movement> CODEC = SERIALIZED.comapFlatMap(
                raw -> construct(() -> new Movement(
                        raw.dashDistance(), raw.invulnerableTicks(), raw.qiCost(), raw.cooldownTicks())),
                value -> new Raw(
                        value.dashDistance(), value.invulnerableTicks(), value.qiCost(), value.cooldownTicks()));

        public Movement {
            if (!Double.isFinite(dashDistance) || dashDistance <= 0.0D) {
                throw new IllegalArgumentException(
                        "effects.movement.dash_distance must be positive, got " + dashDistance);
            }
            if (invulnerableTicks < 0 || qiCost < 0 || cooldownTicks < 0) {
                throw new IllegalArgumentException(
                        "effects.movement invulnerable_ticks, qi_cost and cooldown_ticks must be non-negative");
            }
        }
    }

    /** 炼体: attribute modifiers by attribute id plus a stagger-resistance level. */
    public record Body(Map<ResourceLocation, Double> attributes, int staggerResistance) {
        private record Raw(Map<ResourceLocation, Double> attributes, int staggerResistance) {
        }

        private static final Codec<Raw> SERIALIZED = RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE)
                        .optionalFieldOf("attributes", Map.of())
                        .forGetter(Raw::attributes),
                Codec.INT.optionalFieldOf("stagger_resistance", 0).forGetter(Raw::staggerResistance)
        ).apply(instance, Raw::new));
        public static final Codec<Body> CODEC = SERIALIZED.comapFlatMap(
                raw -> construct(() -> new Body(raw.attributes(), raw.staggerResistance())),
                value -> new Raw(value.attributes(), value.staggerResistance()));

        public Body {
            Objects.requireNonNull(attributes, "attributes");
            LinkedHashMap<ResourceLocation, Double> sorted = new LinkedHashMap<>();
            attributes.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> {
                        ResourceLocation id = Objects.requireNonNull(entry.getKey(), "attribute id");
                        Double amount = Objects.requireNonNull(entry.getValue(), "attribute amount for " + id);
                        if (!Double.isFinite(amount)) {
                            throw new IllegalArgumentException(
                                    "effects.body.attributes." + id + " must be finite");
                        }
                        sorted.put(id, amount);
                    });
            attributes = Collections.unmodifiableMap(sorted);
            if (staggerResistance < 0) {
                throw new IllegalArgumentException(
                        "effects.body.stagger_resistance must be non-negative, got " + staggerResistance);
            }
            if (attributes.isEmpty() && staggerResistance == 0) {
                throw new IllegalArgumentException("effects.body needs attributes or stagger_resistance");
            }
        }
    }
}
