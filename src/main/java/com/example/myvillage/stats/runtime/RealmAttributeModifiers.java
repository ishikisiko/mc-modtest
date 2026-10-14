package com.example.myvillage.stats.runtime;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** The shared saved contribution is replaced by identity, never accumulated. */
final class RealmAttributeModifiers {
    private RealmAttributeModifiers() {}

    static void replace(AttributeInstance attribute, ResourceLocation id, double factor) {
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(id);
        if (factor != 1) {
            attribute.addPermanentModifier(new AttributeModifier(
                    id, factor - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
