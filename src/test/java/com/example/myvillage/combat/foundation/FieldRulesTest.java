package com.example.myvillage.combat.foundation;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FieldRulesTest {
    @Test void rootUsesBundledDurationAndExternallyProvidedAbilityRadius() {
        var rules = FieldRules.bundled();
        var spec = rules.woodRoot(4.5);
        assertTrue(rules.valid(spec));
        assertEquals(4.5, spec.radius());
        assertEquals(rules.document().getAsJsonObject("wood_root").get("root_ticks").getAsInt(), spec.effectTicks());
        assertEquals(rules.document().getAsJsonObject("wood_root").get("lifetime_ticks").getAsInt(), spec.lifetimeTicks());
        assertTrue(spec.oncePerTarget());
        assertEquals(FieldSpec.Targeting.ENEMIES, spec.targeting());
        assertTrue(rules.valid(spec.at(new Vec3(0, 64, 0))));
    }

    @Test void malformedNumericsAndUnboundedOrMismatchedEffectsAreRejected() {
        var rules = FieldRules.bundled();
        assertFalse(rules.valid(null));
        assertFalse(rules.valid(rules.woodRoot(Double.NaN)));
        assertFalse(rules.valid(rules.woodRoot(Double.POSITIVE_INFINITY)));
        assertFalse(rules.valid(rules.woodRoot(0)));
        assertFalse(rules.valid(rules.woodRoot(rules.maximumRadius() + 1)));
        assertFalse(rules.valid(rules.woodRoot(4).at(null)));
        assertFalse(rules.valid(rules.woodRoot(4).at(new Vec3(Double.NaN, 0, 0))));
        var root = rules.woodRoot(4);
        assertFalse(rules.valid(new FieldSpec(root.kind(), root.effect(), root.anchor(), null, root.radius(),
                root.lifetimeTicks(), root.pulseIntervalTicks(), root.maximumTargets(), FieldSpec.Effect.ROOT,
                root.effectTicks(), 0, true, FieldSpec.Targeting.ALLIES)));
        assertFalse(rules.valid(new FieldSpec(root.kind(), root.effect(), root.anchor(), null, root.radius(),
                root.lifetimeTicks(), root.pulseIntervalTicks(), root.maximumTargets(), FieldSpec.Effect.HEAL,
                0, 1, false, FieldSpec.Targeting.ENEMIES)));
    }

    @Test void healingAndSlowHaveBoundedServerDescriptorsWithoutEnablingAnyFate() {
        var rules = FieldRules.bundled();
        var root = rules.woodRoot(4);
        var heal = new FieldSpec(root.kind(), root.effect(), root.anchor(), null, root.radius(),
                root.lifetimeTicks(), root.pulseIntervalTicks(), root.maximumTargets(), FieldSpec.Effect.HEAL,
                0, 1, false, FieldSpec.Targeting.ALLIES);
        assertTrue(rules.valid(heal));
        assertTrue(rules.valid(new FieldSpec(root.kind(), root.effect(), root.anchor(), null, root.radius(),
                root.lifetimeTicks(), root.pulseIntervalTicks(), root.maximumTargets(), FieldSpec.Effect.SLOW,
                root.effectTicks(), .5f, false, FieldSpec.Targeting.ENEMIES)));
        assertFalse(rules.valid(new FieldSpec(root.kind(), root.effect(), root.anchor(), null, root.radius(),
                root.lifetimeTicks(), 0, root.maximumTargets(), FieldSpec.Effect.HEAL,
                0, Float.NaN, false, FieldSpec.Targeting.ALLIES)));
    }

    @Test void malformedAuthoredConfigurationFailsClosedAndDocumentAccessIsDefensive() {
        var rules = FieldRules.bundled();
        var doc = rules.document();
        doc.getAsJsonObject("limits").addProperty("maximum_instances", 1.5);
        assertThrows(IllegalArgumentException.class, () -> FieldRules.parse(doc));
        assertNotEquals(1.5, rules.document().getAsJsonObject("limits").get("maximum_instances").getAsDouble());
        var noPulse = rules.document();
        noPulse.getAsJsonObject("wood_root").remove("pulse_interval_ticks");
        assertThrows(IllegalArgumentException.class, () -> FieldRules.parse(noPulse));
    }
}
