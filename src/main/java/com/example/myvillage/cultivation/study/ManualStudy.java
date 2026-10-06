package com.example.myvillage.cultivation.study;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

/**
 * Entry point for reading a technique manual (秘籍): the item calls this on the server when it is used.
 * The study mechanic (docs/technique-manual-brief.md, package F2) replaces the body; the signature is the seam.
 */
public final class ManualStudy {
    private ManualStudy() {
    }

    /** Begin studying the manual held in {@code hand}. Stub: nothing happens yet. */
    public static InteractionResultHolder<ItemStack> use(ServerPlayer player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
}
