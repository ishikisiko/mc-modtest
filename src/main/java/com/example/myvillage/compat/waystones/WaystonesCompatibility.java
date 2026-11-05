package com.example.myvillage.compat.waystones;

import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.waystones.api.EntityTeleportResult;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent;
import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.blay09.mods.waystones.requirement.CombinedRequirement;
import net.blay09.mods.waystones.requirement.ItemRequirement;
import net.blay09.mods.waystones.requirement.ItemRequirementType;
import net.minecraft.world.entity.player.Player;

/** Loaded only when the matching Waystones release is present on the physical side. */
public final class WaystonesCompatibility {
    private WaystonesCompatibility() { }

    public static void register() {
        CapitalWaystonesCompatibility.register();
        com.example.myvillage.talisman.TalismanTravel.installWaystones(new WaystonesTalismanAccess());
        WaystonesAPI.registerVariableResolver(new WaystonesPricingVariable());
        // Standard add/scaled/min/max_item_cost modifiers resolve their instance through this public registry.
        WaystonesAPI.registerRequirementType(new ItemRequirementType() {
            @Override public ItemRequirement createInstance() { return new BagAwareItemRequirement(); }
        });
        Balm.getEvents().onEvent(WaystoneTeleportEvent.Complete.class, WaystonesCompatibility::complete);
    }

    private static void complete(WaystoneTeleportEvent.Complete event) {
        if (event.getContext().getEntity() instanceof Player player) {
            boolean successful = event.getPrimaryResult().map(EntityTeleportResult::isSuccessful).orElse(false);
            finish(event.getContext().getRequirements(), player, successful);
        }
    }

    private static void finish(WarpRequirement requirement, Player player, boolean successful) {
        if (requirement instanceof BagAwareItemRequirement payment) payment.finish(player, successful);
        else if (requirement instanceof CombinedRequirement combined) {
            for (var child : combined.getRequirements()) finish(child, player, successful);
        }
    }
}
