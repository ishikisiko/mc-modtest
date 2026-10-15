package com.example.myvillage.item;

import com.example.myvillage.alchemy.AlchemyRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A reusable physical recipe credential, kept in the furnace's formula slot. */
public final class FormulaItem extends Item {
    private final String recipeId;

    public FormulaItem(Properties properties, String recipeId) {
        super(properties);
        this.recipeId = recipeId;
    }

    public String recipeId() { return recipeId; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        AlchemyRules.get().recipe(recipeId).ifPresent(recipe -> {
            lines.add(Component.translatable("tooltip.myvillage.alchemy.formula_reusable").withStyle(ChatFormatting.GRAY));
            for (String ingredient : recipe.ingredients()) {
                lines.add(Component.translatable("tooltip.myvillage.alchemy.ingredient",
                        BuiltInRegistries.ITEM.get(ResourceLocation.parse(ingredient)).getDescription())
                        .withStyle(ChatFormatting.DARK_GREEN));
            }
        });
    }
}
