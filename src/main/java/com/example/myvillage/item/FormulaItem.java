package com.example.myvillage.item;

import com.example.myvillage.alchemy.AlchemyRules;
import com.example.myvillage.alchemy.AlchemyKnowledge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Read once to permanently learn a recipe; duplicate copies are not consumed. */
public final class FormulaItem extends Item {
    private final String recipeId;

    public FormulaItem(Properties properties, String recipeId) {
        super(properties);
        this.recipeId = recipeId;
    }

    public String recipeId() { return recipeId; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) {
            boolean learned = AlchemyKnowledge.learn(server, recipeId);
            if (learned && !player.getAbilities().instabuild) stack.shrink(1);
            player.displayClientMessage(Component.translatable("message.myvillage.alchemy." +
                    (learned ? "formula_learned" : "formula_known"), getDescription()), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        AlchemyRules.get().recipe(recipeId).ifPresent(recipe -> {
            lines.add(Component.translatable("tooltip.myvillage.alchemy.formula_learn").withStyle(ChatFormatting.GRAY));
            for (String ingredient : recipe.ingredients()) {
                lines.add(Component.translatable("tooltip.myvillage.alchemy.ingredient",
                        BuiltInRegistries.ITEM.get(ResourceLocation.parse(ingredient)).getDescription())
                        .withStyle(ChatFormatting.DARK_GREEN));
            }
        });
    }
}
