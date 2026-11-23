package com.example.myvillage.kitchen;

import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class KitchenFoodItem extends Item {
    private final KitchenRules.Meal meal;

    public KitchenFoodItem(KitchenRules.Meal meal, Properties properties) {
        super(properties.stacksTo(16).food(new FoodProperties(meal.nutrition(), meal.saturation(), true,
                KitchenRules.get().useSeconds,
                meal.recipe().servedInBowl() ? Optional.of(new ItemStack(Items.BOWL)) : Optional.empty(), List.of())));
        this.meal = meal;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (entity instanceof ServerPlayer player) {
            KitchenRuntime.rememberMeal(player);
            player.addEffect(new MobEffectInstance(KitchenContent.effect(meal.id()), KitchenRules.get().duration));
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.yuanxian.kitchen.food", meal.nutrition(), meal.saturation()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.yuanxian." + meal.id(), meal.percent(),
                KitchenRules.get().duration / 1200).withStyle(ChatFormatting.DARK_GREEN));
    }
}
