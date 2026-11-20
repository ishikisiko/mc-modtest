package com.example.myvillage.client.forging;

import com.example.myvillage.forging.ForgingIngredients;
import com.example.myvillage.storagebag.StorageBagForgeMenu;
import com.example.myvillage.storagebag.StorageBagRules.Kind;
import com.example.myvillage.storagebag.StorageBagRules.Station;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.example.myvillage.client.forging.StorageBagScreen.TEXT;

public final class StorageBagForgeScreen extends AbstractContainerScreen<StorageBagForgeMenu> {
    private static final int INK = 0xff353b32;
    private final List<Button> kinds = new ArrayList<>();
    private Button start;

    public StorageBagForgeScreen(StorageBagForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = StorageBagForgeMenu.WIDTH;
        imageHeight = StorageBagForgeMenu.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        kinds.clear();
        for (var kind : Kind.values()) {
            kinds.add(button(11 + kind.ordinal() * 70, 26, 68, Component.translatable(TEXT + "kind." + kind.id()),
                    StorageBagForgeMenu.SELECT_KIND + kind.ordinal(), () -> menu.selectedKind() == kind));
        }
        start = button(168, 91, 51, Component.translatable(TEXT + "forge_start"), StorageBagForgeMenu.START, () -> false);
        updateButtons();
    }

    private Button button(int x, int y, int width, Component label, int action, java.util.function.BooleanSupplier chosen) {
        return addRenderableWidget(new WorkshopScreen.InkButton(leftPos + x, topPos + y, width, 17, label,
                ignored -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
                    }
                }, chosen));
    }

    private void updateButtons() {
        for (var button : kinds) {
            button.visible = menu.stationKind() == Station.DING && menu.input().isEmpty();
            button.active = menu.output().isEmpty();
        }
        start.active = menu.output().isEmpty() && menu.recipe().isPresent();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        StationPanel.paper(g, leftPos, topPos, imageWidth, imageHeight);
        for (var slot : menu.slots) StationPanel.slot(g, leftPos + slot.x, topPos + slot.y);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 10, 8, INK, false);
        g.drawString(font, Component.translatable(TEXT + "original"), StorageBagForgeMenu.INPUT_X - 1, 46, INK, false);
        g.drawString(font, Component.translatable(TEXT + "result"), StorageBagForgeMenu.OUTPUT_X - 1, 46, INK, false);
        var recipe = menu.recipe().orElse(null);
        if (recipe != null && menu.output().isEmpty()) {
            var tier = recipe.tier();
            var name = Component.translatable("item.yuanxian." + tier.itemId(recipe.kind()));
            g.drawCenteredString(font, Component.translatable(TEXT + "recipe_result", name, tier.capacity()), 115, 56, INK);
            g.drawCenteredString(font, Component.translatable(TEXT + "requirement", tier.name()), 115, 68, INK);
            int y = 79;
            for (var cost : tier.costs().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                var line = Component.empty().append(ForgingIngredients.description(cost.getKey())).append(" ×" + cost.getValue());
                g.drawString(font, line, 14, y, INK, false);
                y += 10;
            }
        } else if (recipe == null && !menu.input().isEmpty() && menu.output().isEmpty()) {
            g.drawCenteredString(font, Component.translatable(TEXT + "max"), 115, 61, INK);
        }
        int status = menu.status();
        if (status == 0 && recipe == null && menu.input().isEmpty()) status = 6;
        g.drawString(font, Component.translatable(TEXT + "status." + status), 14, 121, INK, false);
        g.drawString(font, playerInventoryTitle, StorageBagForgeMenu.INVENTORY_X, 128, INK, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }
}
