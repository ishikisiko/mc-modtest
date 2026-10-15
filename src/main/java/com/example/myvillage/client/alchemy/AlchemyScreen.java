package com.example.myvillage.client.alchemy;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.alchemy.*;
import com.example.myvillage.block.entity.AlchemyFurnaceBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Server-synchronized batch readout; the vanilla menu button carries start intent only. */
public final class AlchemyScreen extends AbstractContainerScreen<AlchemyMenu> {
    private static final String KEY = "screen.myvillage.alchemy.";
    private Button startButton;
    public AlchemyScreen(AlchemyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = AlchemyMenu.WIDTH; imageHeight = AlchemyMenu.HEIGHT;
        inventoryLabelX = 26; inventoryLabelY = 128;
    }
    @Override protected void init() {
        super.init();
        startButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "start"), button -> {
            if (minecraft != null && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }).bounds(leftPos + 16, topPos + 104, 180, 18).build());
        updateStartButton();
    }
    private void updateStartButton() {
        if (startButton == null) return;
        startButton.active = !menu.active() && menu.status() == 5;
        startButton.setMessage(Component.translatable(KEY + (menu.active() ? "locked" : "start")));
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
        graphics.fill(x + 16, y + 60, x + 196, y + 65, 0xFF171D18);
        int progress = menu.duration() == 0 ? 0 : 180 * menu.progress() / menu.duration();
        graphics.fill(x + 16, y + 60, x + 16 + progress, y + 65, 0xFFD9B568);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 10, 8, 0xFFE5D5AF, false);
        String[] labels = {"formula", "herbs", "fuel", "output"};
        int[] xs = {8, 52, 108, 174};
        for (int i = 0; i < labels.length; i++)
            graphics.drawString(font, Component.translatable(KEY + labels[i]), xs[i], 26, 0xFFC5C6B7, false);
        graphics.drawString(font, Component.translatable(KEY + "status." + menu.status()), 16, 69, 0xFFE5D5AF, false);
        Component ceiling = menu.active() && menu.qualityCap() >= 1 && menu.qualityCap() <= 4
                ? Component.translatable(KEY + "ceiling", menu.aptitude(), PillQuality.name(menu.qualityCap()))
                : Component.translatable(KEY + "ceiling_pending");
        graphics.drawString(font, ceiling, 16, 80, 0xFFD9B568, false);
        AlchemyRules.get().recipeForFormula(AlchemyFurnaceBlockEntity.id(menu.formula())).ifPresent(recipe -> {
            String seconds = java.math.BigDecimal.valueOf(recipe.durationTicks()).divide(java.math.BigDecimal.valueOf(20))
                    .stripTrailingZeros().toPlainString();
            graphics.drawString(font, Component.translatable(KEY + "batch", recipe.fuelCount(), recipe.outputCount(), seconds), 16, 91, 0xFFC5C6B7, false);
        });
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFC5C6B7, false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        updateStartButton();
        super.render(graphics, mouseX, mouseY, delta); renderTooltip(graphics, mouseX, mouseY);
    }
    @EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(AlchemyMenus.FURNACE.get(), AlchemyScreen::new); }
    }
}
