package com.example.myvillage.stats.runtime;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.stats.StatsRules;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Generic, synchronized knobs. Their bounds come from the same bundled numerical rules. */
public final class ModStatsAttributes {
    private static final DeferredRegister<Attribute> REGISTRY =
            DeferredRegister.create(Registries.ATTRIBUTE, MyVillageMod.MOD_ID);
    private static final StatsRules.Limits LIMITS = StatsRules.bundled().limits();
    public static final DeferredHolder<Attribute, Attribute> DAMAGE_FLAT =
            attribute("damage_flat", 0, 0, LIMITS.maxFlatDamage());
    public static final DeferredHolder<Attribute, Attribute> DAMAGE_BONUS =
            attribute("damage_bonus", 0, 0, LIMITS.maxBonus());
    public static final DeferredHolder<Attribute, Attribute> DAMAGE_MULTIPLIER =
            attribute("damage_multiplier", 1, 0, LIMITS.maxTotalMultiplier());
    public static final DeferredHolder<Attribute, Attribute> PHYSICAL_RESISTANCE = resistance("physical");
    public static final DeferredHolder<Attribute, Attribute> METAL_RESISTANCE = resistance("metal");
    public static final DeferredHolder<Attribute, Attribute> WOOD_RESISTANCE = resistance("wood");
    public static final DeferredHolder<Attribute, Attribute> WATER_RESISTANCE = resistance("water");
    public static final DeferredHolder<Attribute, Attribute> FIRE_RESISTANCE = resistance("fire");
    public static final DeferredHolder<Attribute, Attribute> EARTH_RESISTANCE = resistance("earth");
    // Keep the saved registry identity; the formerly shared value now applies only to physical hits.
    public static final DeferredHolder<Attribute, Attribute> PHYSICAL_RESISTANCE_PENETRATION =
            penetration("resistance_penetration");
    public static final DeferredHolder<Attribute, Attribute> METAL_RESISTANCE_PENETRATION =
            penetration("metal_resistance_penetration");
    public static final DeferredHolder<Attribute, Attribute> WOOD_RESISTANCE_PENETRATION =
            penetration("wood_resistance_penetration");
    public static final DeferredHolder<Attribute, Attribute> WATER_RESISTANCE_PENETRATION =
            penetration("water_resistance_penetration");
    public static final DeferredHolder<Attribute, Attribute> FIRE_RESISTANCE_PENETRATION =
            penetration("fire_resistance_penetration");
    public static final DeferredHolder<Attribute, Attribute> EARTH_RESISTANCE_PENETRATION =
            penetration("earth_resistance_penetration");
    public static final List<DeferredHolder<Attribute, Attribute>> ALL = List.of(
            DAMAGE_FLAT, DAMAGE_BONUS, DAMAGE_MULTIPLIER, PHYSICAL_RESISTANCE,
            METAL_RESISTANCE, WOOD_RESISTANCE, WATER_RESISTANCE, FIRE_RESISTANCE,
            EARTH_RESISTANCE, PHYSICAL_RESISTANCE_PENETRATION, METAL_RESISTANCE_PENETRATION,
            WOOD_RESISTANCE_PENETRATION, WATER_RESISTANCE_PENETRATION,
            FIRE_RESISTANCE_PENETRATION, EARTH_RESISTANCE_PENETRATION);

    private ModStatsAttributes() {}

    public static void register(IEventBus bus) {
        REGISTRY.register(bus);
        bus.addListener(ModStatsAttributes::attach);
    }

    private static void attach(EntityAttributeModificationEvent event) {
        // Vanilla does not normally synchronize attack damage; derived attack is inspectable
        // on either side using the ordinary attribute packet, without a new payload.
        Attributes.ATTACK_DAMAGE.value().setSyncable(true);
        for (var type : event.getTypes()) {
            for (var attribute : ALL) {
                if (!event.has(type, attribute)) {
                    event.add(type, attribute);
                }
            }
        }
    }

    private static DeferredHolder<Attribute, Attribute> resistance(String type) {
        return attribute(type + "_resistance", 0, LIMITS.minResistance(), LIMITS.maxResistance());
    }

    private static DeferredHolder<Attribute, Attribute> penetration(String id) {
        return REGISTRY.register(id, () -> penetrationAttribute(id));
    }

    static RangedAttribute penetrationAttribute(String id) {
        RangedAttribute attribute = new RangedAttribute("attribute.myvillage." + id, 0, 0, LIMITS.maxPenetration());
        attribute.setSyncable(true);
        return attribute;
    }

    private static DeferredHolder<Attribute, Attribute> attribute(
            String id, double defaultValue, double min, double max) {
        return REGISTRY.register(id, () -> new RangedAttribute(
                "attribute.myvillage." + id, defaultValue, min, max).setSyncable(true));
    }
}
