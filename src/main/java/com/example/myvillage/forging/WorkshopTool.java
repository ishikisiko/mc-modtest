package com.example.myvillage.forging;

import com.example.myvillage.item.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Marks workshop tools that share the quality display and mining bonus. */
public interface WorkshopTool {
    static float qualityMiningSpeed(ItemStack stack, BlockState state, float speed) {
        if (!(stack.getItem() instanceof WorkshopTool)) return speed;
        var mark = stack.get(ModDataComponents.FORGE_MARK.get());
        if (mark == null || mark.quality() == 1) return speed;
        var tool = stack.get(DataComponents.TOOL);
        if (tool == null) return speed;
        for (var rule : tool.rules()) {
            if (rule.speed().isPresent() && state.is(rule.blocks())) {
                return speed + ForgingRules.get().toolMiningSpeedBonus();
            }
        }
        return speed;
    }
}
