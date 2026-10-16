package com.example.myvillage.item;

import com.example.myvillage.forging.ForgingQualityDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A sword-type item with a combat weapon entry ({@code data/<ns>/combat/weapon/}). A landed combat
 * hit costs durability, so the server resends the held stack with a new {@code minecraft:damage}
 * after every hit. NeoForge's default rule replays the equip animation for any new stack object,
 * which dropped the first-person weapon out of view right after each hit-stop.
 *
 * <p>The rule here is the vanilla behaviour minus that replay: the first-person renderer first
 * keeps a stack that {@code ItemStack.matches} the one it holds, and then asks this item; the
 * answer is "re-equip" unless the two stacks are the same item, count and components with
 * {@code minecraft:damage} ignored. {@code slotChanged} is not consulted (it is true for one tick
 * only). Empty hands are answered by NeoForge before this item is asked.
 */
public class CombatWeaponItem extends SwordItem {
    public CombatWeaponItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        // A styled child keeps vanilla rarity/enchantment styling on the wrapper
        // from replacing the actual quality color of the weapon name.
        return Component.empty().append(super.getName(stack).copy()
                .withStyle(style -> style.withColor(ForgingQualityDisplay.color(ForgingQualityDisplay.quality(stack)))));
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
            java.util.List<net.minecraft.network.chat.Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        var mark = stack.get(ModDataComponents.FORGE_MARK.get());
        int quality = ForgingQualityDisplay.quality(stack);
        int color = ForgingQualityDisplay.color(quality);
        // Also covers anvil names, which bypass Item#getName. Do not mutate saved names.
        if (!lines.isEmpty()) lines.set(0, lines.getFirst().copy().withStyle(style -> style.withColor(color)));
        if (mark != null) {
            lines.add(net.minecraft.network.chat.Component.translatable("tooltip.myvillage.forging.mark",
                    ForgingQualityDisplay.name(quality),
                    net.minecraft.network.chat.Component.translatable("forging.myvillage.trait." + mark.trait()))
                    .withStyle(style -> style.withColor(color)));
            double value = com.example.myvillage.forging.ForgingRules.get().trait(mark.trait()).value(mark.quality());
            String amount = java.math.BigDecimal.valueOf(mark.trait().equals("channeling") ? value * 100 : value).stripTrailingZeros().toPlainString();
            lines.add(net.minecraft.network.chat.Component.translatable("tooltip.myvillage.forging." + mark.trait(), amount)
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        } else {
            lines.add(ForgingQualityDisplay.name(0));
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return reequips(
                ItemStack.isSameItem(oldStack, newStack) && oldStack.getCount() == newStack.getCount(),
                components(oldStack),
                components(newStack),
                DataComponents.DAMAGE);
    }

    private static Map<DataComponentType<?>, Optional<?>> components(ItemStack stack) {
        Map<DataComponentType<?>, Optional<?>> components = new HashMap<>();
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : stack.getComponentsPatch().entrySet()) {
            components.put(entry.getKey(), entry.getValue());
        }
        return components;
    }

    /**
     * Re-equip unless the stacks are the same item and count and their component changes (from
     * the item's defaults) agree once {@code ignored} (the durability) is left out.
     */
    static <K> boolean reequips(boolean sameItemAndCount, Map<K, ?> oldComponents, Map<K, ?> newComponents, K ignored) {
        if (!sameItemAndCount) {
            return true;
        }
        Map<K, Object> before = new HashMap<>(oldComponents);
        Map<K, Object> after = new HashMap<>(newComponents);
        before.remove(ignored);
        after.remove(ignored);
        return !before.equals(after);
    }
}
