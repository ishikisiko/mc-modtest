package com.example.myvillage.client.forging.jei;

import com.example.myvillage.block.ModBlocks;
import java.util.Arrays;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public final class ForgingJeiCategory implements IRecipeCategory<ForgingJeiRecipe> {
    public static final RecipeType<ForgingJeiRecipe> TYPE =
            RecipeType.create("myvillage", "forging", ForgingJeiRecipe.class);
    private final IDrawable ding;
    private final IDrawable arrow;

    public ForgingJeiCategory(IGuiHelper gui) {
        ding = gui.createDrawableItemLike(ModBlocks.FORGING_TABLE.get());
        arrow = gui.getRecipeArrow();
    }

    @Override public RecipeType<ForgingJeiRecipe> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return text("title"); }
    @Override public IDrawable getIcon() { return ding; }
    @Override public int getWidth() { return 176; }
    @Override public int getHeight() { return 108; }

    static ItemStack stack(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ForgingJeiRecipe recipe, IFocusGroup focuses) {
        int index = 0;
        for (var cost : recipe.materials().entrySet()) {
            Ingredient ingredient = cost.getKey().startsWith("#")
                    ? Ingredient.of(TagKey.create(Registries.ITEM, ResourceLocation.parse(cost.getKey().substring(1))))
                    : Ingredient.of(stack(cost.getKey()));
            builder.addSlot(RecipeIngredientRole.INPUT, 5 + index % 3 * 18,
                            38 - (recipe.materials().size() - 1) / 3 * 9 + index / 3 * 18)
                    .setStandardSlotBackground()
                    .addItemStacks(Arrays.stream(ingredient.getItems())
                            .map(item -> item.copyWithCount(cost.getValue())).toList());
            index++;
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, 150, 38).setOutputSlotBackground()
                .addItemStack(stack(recipe.output()))
                .addRichTooltipCallback((slot, tooltip) -> tooltip.add(text("quality")));
        if (!recipe.pattern().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.CATALYST, 86, 81).setStandardSlotBackground()
                    .addItemStack(stack(recipe.pattern()))
                    .addRichTooltipCallback((slot, tooltip) -> tooltip.add(text("pattern_hint")));
        }
    }

    @Override
    public void draw(ForgingJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
            double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, text("materials"), 4, 5, 0x404040, false);
        graphics.drawString(font, text("output"), 146, 5, 0x404040, false);
        graphics.pose().pushPose();
        graphics.pose().translate(80, 26, 0);
        graphics.pose().scale(2, 2, 1);
        ding.draw(graphics);
        graphics.pose().popPose();
        arrow.draw(graphics, 120, 38);
        graphics.drawString(font, text("workshop"), 72, 63, 0x606060, false);
        graphics.drawString(font, text(recipe.pattern().isEmpty() ? "no_pattern" : "pattern"),
                4, 85, 0x606060, false);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, ForgingJeiRecipe recipe, IRecipeSlotsView slots,
            double mouseX, double mouseY) {
        if (mouseX >= 72 && mouseX < 114 && mouseY >= 22 && mouseY < 76) {
            tooltip.add(text("overview"));
            tooltip.add(text("auxiliaries"));
        }
    }

    @Override public ResourceLocation getRegistryName(ForgingJeiRecipe recipe) {
        return ResourceLocation.fromNamespaceAndPath("myvillage", "forging/" + recipe.id());
    }

    private static Component text(String key) {
        return Component.translatable("jei.myvillage.forging." + key);
    }
}
