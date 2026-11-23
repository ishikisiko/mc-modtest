package com.example.myvillage.client.kitchen;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.kitchen.KitchenContent;
import com.example.myvillage.kitchen.KitchenMenu;
import com.example.myvillage.kitchen.KitchenRules;
import com.example.myvillage.kitchen.KitchenStoveBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import static com.example.myvillage.kitchen.KitchenStoveBlockEntity.*;

public final class KitchenScreen extends AbstractContainerScreen<KitchenMenu> {
    private static final int INK = 0xFF33281B;
    private static final int MUTED = 0xFF64533F;
    private Button cook;

    public KitchenScreen(KitchenMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = KitchenMenu.WIDTH;
        imageHeight = KitchenMenu.HEIGHT;
        inventoryLabelX = 36;
        inventoryLabelY = 133;
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("‹"), button -> cycle(-1))
                .bounds(leftPos + 9, topPos + 22, 19, 19).build());
        addRenderableWidget(Button.builder(Component.literal("›"), button -> cycle(1))
                .bounds(leftPos + imageWidth - 28, topPos + 22, 19, 19).build());
        cook = addRenderableWidget(Button.builder(text("start"), button -> send(KitchenMenu.TOGGLE_COOK))
                .bounds(leftPos + 99, topPos + 93, 66, 20).build());
    }

    private void cycle(int step) {
        send(Math.floorMod(menu.value(SELECTED) + step, KitchenRules.get().stove.size()));
    }

    private void send(int button) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("gui.yuanxian.kitchen." + key, args);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        cook.setMessage(text(menu.value(MANUAL) == 1 ? "pause" : "start"));
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF514332);
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFFECE0C1);
        graphics.fill(leftPos + 7, topPos + 44, leftPos + imageWidth - 7, topPos + 119, 0xFFDCCCAD);
        for (var slot : menu.slots) {
            int x = leftPos + slot.x, y = topPos + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF917C5D);
            graphics.fill(x, y, x + 16, y + 16, 0xFFF1E6CC);
        }
        var recipe = menu.recipe();
        for (int slot = 0; slot < INPUTS; slot++) {
            var ingredient = KitchenStoveBlockEntity.material(recipe, slot);
            if (ingredient != null) ghost(graphics, slot, ingredient.stack());
        }
        if (recipe.servedInBowl()) ghost(graphics, BOWLS, Items.BOWL.getDefaultInstance());
        ghost(graphics, FUEL, Items.CHARCOAL.getDefaultInstance());
        ghost(graphics, PRODUCT, recipe.result());
        int progress = Math.clamp(menu.value(PROGRESS), 0, recipe.ticks());
        graphics.fill(leftPos + 117, topPos + 69, leftPos + 146, topPos + 74, 0xFF9A876A);
        graphics.fill(leftPos + 117, topPos + 69, leftPos + 117 + 29 * progress / recipe.ticks(), topPos + 74, 0xFF9C5129);
    }

    private void ghost(GuiGraphics graphics, int index, ItemStack display) {
        var slot = menu.getSlot(index);
        int x = leftPos + slot.x, y = topPos + slot.y;
        if (slot.hasItem()) return;
        graphics.renderFakeItem(display, x, y);
        graphics.fill(x, y, x + 16, y + 16, 0x72E9DFC2);
        if (display.getCount() > 1)
            graphics.drawString(font, Integer.toString(display.getCount()), x + 9, y + 8, INK, false);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, INK, false);
        var recipe = menu.recipe();
        graphics.drawCenteredString(font, recipe.result().getHoverName(), imageWidth / 2, 23, INK);
        graphics.drawCenteredString(font, Component.literal(recipe.method()), imageWidth / 2, 34, MUTED);
        graphics.drawString(font, text("input"), 15, 50, MUTED, false);
        graphics.drawString(font, text("output"), 156, 50, MUTED, false);
        graphics.drawString(font, text("bowls"), 17, 84, MUTED, false);
        graphics.drawString(font, text("fuel"), 58, 84, MUTED, false);
        graphics.drawString(font, text("status." + menu.value(STATUS)), 13, 121, MUTED, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, INK, false);
    }

    @EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
    public static final class ClientEvents {
        @SubscribeEvent public static void register(RegisterMenuScreensEvent event) {
            event.register(KitchenContent.MENU.get(), KitchenScreen::new);
        }
    }
}
