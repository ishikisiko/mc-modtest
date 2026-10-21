package com.example.myvillage.client.alchemy;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.alchemy.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import java.util.ArrayList;
import java.util.List;

/** Per-viewer learned recipes and server-owned transfers, carried by vanilla menu button intents. */
public final class AlchemyScreen extends AbstractContainerScreen<AlchemyMenu> {
    private static final String KEY = "screen.myvillage.alchemy.";
    private Button startButton, previousPage, nextPage;
    private int page;
    private final List<Button> recipes = new ArrayList<>();
    public AlchemyScreen(AlchemyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = AlchemyMenu.WIDTH; imageHeight = AlchemyMenu.HEIGHT;
        inventoryLabelX = 70; inventoryLabelY = 128;
    }
    private void send(int button) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }
    @Override protected void init() {
        super.init(); recipes.clear();
        var definitions = AlchemyRules.get().recipes();
        for (int i = 0; i < definitions.size(); i++) {
            final int index = i;
            var recipe = definitions.get(i);
            var button = Button.builder(pillName(i), ignored -> send(index + 1))
                    .bounds(leftPos + 8, topPos + 40, 92, 20).build();
            button.setTooltip(Tooltip.create(RecipeText.tooltip(recipe)));
            recipes.add(addRenderableWidget(button));
        }
        startButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "start"), button -> send(0))
                .bounds(leftPos + 112, topPos + 104, 176, 18).build());
        previousPage=addRenderableWidget(Button.builder(Component.literal("‹"),b->page=Math.max(0,page-1)).bounds(leftPos+42,topPos+108,28,18).build());
        nextPage=addRenderableWidget(Button.builder(Component.literal("›"),b->page++).bounds(leftPos+72,topPos+108,28,18).build());
        updateButtons();
    }
    private Component pillName(int index) {
        return RecipeText.name(AlchemyRules.get().recipes().get(index));
    }
    private void updateButtons() {
        if (startButton == null) return;
        startButton.active = !menu.active() && menu.status() == 5 && menu.knows(menu.selectedRecipe());
        startButton.setMessage(Component.translatable(KEY + (menu.active() ? "locked" : "start")));
        int count=0;for(int i=0;i<recipes.size();i++)if(menu.knows(i))count++;
        page=Math.min(page,Math.max(0,(count-1)/3));previousPage.active=page>0;nextPage.active=(page+1)*3<count;
        int row = 0;
        for (int i = 0; i < recipes.size(); i++) {
            var button = recipes.get(i);
            button.visible = menu.knows(i) && row/3==page; button.active = !menu.active();
            if (button.visible) button.setY(topPos + 40 + (row%3) * 22);
            if(menu.knows(i))row++;
            button.setMessage(Component.literal(menu.selectedRecipe() == i ? "• " : "").append(pillName(i)));
        }
    }
    @Override protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, 0xFF786346);
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF252823);
        graphics.fill(x + 106, y + 24, x + imageWidth - 8, y + 122, 0xFF343A31);
        for (var slot : menu.slots) if (slot.isActive()) {
            graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF8B826C);
            graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFF171D18);
        }
        graphics.fill(x + 112, y + 60, x + 288, y + 65, 0xFF171D18);
        int progress = menu.duration() == 0 ? 0 : 176 * menu.progress() / menu.duration();
        graphics.fill(x + 112, y + 60, x + 112 + progress, y + 65, 0xFFD9B568);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 10, 8, 0xFFE5D5AF, false);
        graphics.drawString(font, Component.translatable(KEY + "learned"), 8, 26, 0xFFD9B568, false);
        if (recipes.stream().noneMatch(button -> button.visible))
            graphics.drawWordWrap(font, Component.translatable(KEY + "learn_hint"), 8, 42, 92, 0xFFC5C6B7);

        String[] labels = {"herbs", "fuel", "output"}; int[] xs = {116, 204, 256};
        for (int i = 0; i < labels.length; i++) graphics.drawString(font, Component.translatable(KEY + labels[i]), xs[i], 26, 0xFFC5C6B7, false);
        Component status = !menu.active() && menu.selectedRecipe() >= 0 && !menu.knows(menu.selectedRecipe())
                ? Component.translatable(KEY + "not_learned") : Component.translatable(KEY + "status." + menu.status());
        graphics.drawString(font, status, 112, 69, 0xFFE5D5AF, false);
        Component ceiling = menu.active() && menu.qualityCap() >= 1 && menu.qualityCap() <= 4
                ? Component.translatable(KEY + "ceiling", menu.aptitude(), PillQuality.name(menu.qualityCap()))
                : menu.selectedRecipe()>=0 && AlchemyRules.get().recipes().get(menu.selectedRecipe()).fixedQuality()>0
                    ? Component.literal("要求炼丹资质 ≥ "+AlchemyRules.get().recipes().get(menu.selectedRecipe()).minimumAptitude()) : Component.translatable(KEY + "ceiling_pending");
        graphics.drawString(font, ceiling, 112, 80, 0xFFD9B568, false);
        int selected = menu.selectedRecipe(); var definitions = AlchemyRules.get().recipes();
        if (selected >= 0 && selected < definitions.size()) {
            var recipe = definitions.get(selected);
            String seconds = java.math.BigDecimal.valueOf(recipe.durationTicks()).divide(java.math.BigDecimal.valueOf(20))
                    .stripTrailingZeros().toPlainString();
            graphics.drawString(font, Component.translatable(KEY + "batch", recipe.fuelCount(), recipe.outputCount(), seconds), 112, 91, 0xFFC5C6B7, false);
        }
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFC5C6B7, false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        updateButtons(); super.render(graphics, mouseX, mouseY, delta); renderTooltip(graphics, mouseX, mouseY);
    }
    @EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(AlchemyMenus.FURNACE.get(), AlchemyScreen::new); }
    }
}
