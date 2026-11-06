package com.example.myvillage.stats.runtime;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** The shared saved contribution is replaced by identity, never accumulated. */
final class RealmAttributeModifiers {
    private RealmAttributeModifiers() {}

    static void replace(AttributeInstance attribute, ResourceLocation id, double addition) {
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(id);
        if (addition != 0) {
            attribute.addPermanentModifier(new AttributeModifier(
                    id, addition, AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
