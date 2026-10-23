package com.example.myvillage.combat.foundation;

import com.example.myvillage.cultivation.fate.ability.FateAbilityRuntime;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Composition only: each foundation retains its own state, rules and lifecycle. */
public final class FoundationRuntime {
    private FoundationRuntime() { }

    /** Register after typed resistance and legacy pill shields, before fate damage receipts. */
    public static void register() {
        ShieldRuntime.register();
        ControlService.register();
        HealingRuntime.register();
        FieldRuntime.register();
        FateAbilityRuntime.register();
        FateAbilityRuntime.registerExecutor(ResourceLocation.parse("myvillage:wood_root"), (player, definition) -> {
            var field = FieldRuntime.woodRoot(definition.radius()).at(player.getBoundingBox().getCenter());
            if (!FieldRuntime.canSpawn(player, field)) return Optional.empty();
            return Optional.of(() -> {
                if (!FieldRuntime.spawn(player, field)) {
                    throw new IllegalStateException("已准备的缠根领域无法创建");
                }
            });
        });
    }
}
