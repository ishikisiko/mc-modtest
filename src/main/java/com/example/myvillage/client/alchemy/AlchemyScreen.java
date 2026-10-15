package com.example.myvillage.client.alchemy;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.alchemy.*;
import com.example.myvillage.block.entity.AlchemyFurnaceBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Presentation of vanilla synchronized slots and progress, without any action payload. */
public final class AlchemyScreen extends AbstractContainerScreen<AlchemyMenu> {
    private static final String KEY = "screen.myvillage.alchemy.";
    public AlchemyScreen(AlchemyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = AlchemyMenu.WIDTH; imageHeight = AlchemyMenu.HEIGHT;
        inventoryLabelX = 26; inventoryLabelY = 128;
    }
    @Override protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, 0xFF786346);
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF252823);
        graphics.fill(x + 8, y + 24, x + imageWidth - 8, y + 122, 0xFF343A31);
        for (var slot : menu.slots) {
            graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF8B826C);
            graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFF171D18);
        }
        graphics.fill(x + 16, y + 66, x + 196, y + 71, 0xFF171D18);
        int progress = menu.duration() == 0 ? 0 : 180 * menu.progress() / menu.duration();
        graphics.fill(x + 16, y + 66, x + 16 + progress, y + 71, 0xFFD9B568);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 10, 8, 0xFFE5D5AF, false);
        String[] labels = {"formula", "herbs", "fuel", "output"};
        int[] xs = {8, 52, 108, 174};
        for (int i = 0; i < labels.length; i++)
            graphics.drawString(font, Component.translatable(KEY + labels[i]), xs[i], 26, 0xFFC5C6B7, false);
        graphics.drawString(font, Component.translatable(KEY + "status." + menu.status()), 16, 76, 0xFFE5D5AF, false);
        AlchemyRules.get().recipeForFormula(AlchemyFurnaceBlockEntity.id(menu.formula())).ifPresent(recipe -> {
            Component ingredients = Component.translatable(KEY + "ingredients", name(recipe.ingredients().get(0)), name(recipe.ingredients().get(1)));
            var lines = font.split(ingredients, imageWidth - 32);
            for (int i = 0; i < Math.min(2, lines.size()); i++)
                graphics.drawString(font, lines.get(i), 16, 88 + i * 10, 0xFFC5C6B7, false);
            String seconds = java.math.BigDecimal.valueOf(recipe.durationTicks()).divide(java.math.BigDecimal.valueOf(20))
                    .stripTrailingZeros().toPlainString();
            graphics.drawString(font, Component.translatable(KEY + "batch", recipe.fuelCount(), recipe.outputCount(), seconds), 16, 111, 0xFFC5C6B7, false);
        });
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFC5C6B7, false);
    }
    private static Component name(String id) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))).getHoverName(); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta); renderTooltip(graphics, mouseX, mouseY);
    }
    @EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(AlchemyMenus.FURNACE.get(), AlchemyScreen::new); }
    }
}
