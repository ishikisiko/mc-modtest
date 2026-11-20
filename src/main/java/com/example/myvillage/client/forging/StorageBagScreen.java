package com.example.myvillage.client.forging;

import com.example.myvillage.aptitude.AptitudeLearning;
import com.example.myvillage.aptitude.AptitudeRules;
import com.example.myvillage.client.cultivation.ClientCultivationState;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.study.StudyMessages;
import com.example.myvillage.cultivation.study.StudyStart;
import com.example.myvillage.item.TechniqueManualItem;
import com.example.myvillage.storagebag.StorageBagMenu;
import com.example.myvillage.storagebag.StorageBagRules.Kind;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class StorageBagScreen extends AbstractContainerScreen<StorageBagMenu> {
    static final String TEXT = "screen.yuanxian.storage_bag.";
    private static final int INK = 0xff353b32;
    private Button previous, next, automatic;

    public StorageBagScreen(StorageBagMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = StorageBagMenu.WIDTH;
        imageHeight = menu.inventoryY() + 80;
    }

    @Override
    protected void init() {
        super.init();
        if (menu.pages() > 1) {
            previous = button(18, 17, 18, Component.literal("‹"), StorageBagMenu.PREVIOUS_PAGE);
            next = button(66, 17, 18, Component.literal("›"), StorageBagMenu.NEXT_PAGE);
        }
        if (menu.kind() == Kind.MATERIAL) {
            automatic = button(119, 16, 59, Component.empty(), StorageBagMenu.TOGGLE_PICKUP);
        }
        updateButtons();
    }

    private Button button(int x, int y, int width, Component label, int action) {
        return addRenderableWidget(new WorkshopScreen.InkButton(leftPos + x, topPos + y, width, 16, label,
                ignored -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
                    }
                }, () -> false));
    }

    private void updateButtons() {
        if (previous != null) previous.active = menu.page() > 0 && menu.getCarried().isEmpty();
        if (next != null) next.active = menu.page() + 1 < menu.pages() && menu.getCarried().isEmpty();
        if (automatic != null) automatic.setMessage(Component.translatable(TEXT + (menu.automatic() ? "auto_on" : "auto_off")));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        StationPanel.paper(g, leftPos, topPos, imageWidth, imageHeight);
        for (var slot : menu.slots) {
            if (slot.isActive()) StationPanel.slot(g, leftPos + slot.x, topPos + slot.y);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 10, 6, INK, false);
        if (menu.pages() > 1) {
            g.drawCenteredString(font, Component.translatable(TEXT + "page", menu.page() + 1, menu.pages()), 51, 21, INK);
        }
        g.drawString(font, playerInventoryTitle, StorageBagMenu.GRID_X, menu.inventoryY() - 10, INK, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        var tooltip = super.getTooltipFromContainerItem(stack);
        if (menu.kind() != Kind.MANUAL || hoveredSlot == null || hoveredSlot.index >= menu.bagSlots()
                || minecraft == null || minecraft.level == null) return tooltip;
        var snapshot = ClientCultivationState.snapshot().orElse(null);
        if (snapshot == null) return tooltip;
        var registries = minecraft.level.registryAccess();
        var techniques = registries.registry(ModCultivationRegistries.TECHNIQUES).orElse(null);
        var realms = registries.registry(ModCultivationRegistries.REALMS).orElse(null);
        var elements = registries.registry(ModCultivationRegistries.SPIRITUAL_ELEMENTS).orElse(null);
        if (techniques == null || realms == null || elements == null) return tooltip;
        var id = TechniqueManualItem.technique(stack, registries).orElse(null);
        var definition = id == null ? null : techniques.get(id);
        if (definition == null) return tooltip;

        var lines = new ArrayList<>(tooltip);
        var refusal = StudyStart.learnRefusal(id, snapshot.profile(), techniques::get, realms::get, elements::containsKey);
        if (refusal.isPresent()) {
            var reason = refusal.get();
            var subject = switch (reason) {
                case PREVIOUS_REQUIRED, PREVIOUS_MASTERY_REQUIRED, PREVIOUS_INSIGHT_REQUIRED ->
                        definition.previous().map(techniques::get).orElse(definition);
                default -> definition;
            };
            var color = reason == StudyStart.Refusal.ALREADY_LEARNED ? ChatFormatting.GRAY : ChatFormatting.RED;
            lines.add(Component.translatable(StudyMessages.key(reason),
                    Component.translatable(subject.translationKey())).withStyle(color));
            return lines;
        }
        var shortfall = AptitudeLearning.shortfall(registries, definition, key -> {
            if (AptitudeRules.get().elements().contains(key)) {
                return snapshot.profile().spiritualRoot().map(root -> root.points()
                        .getOrDefault(ResourceLocation.fromNamespaceAndPath("yuanxian", key), 0)).orElse(0);
            }
            return snapshot.aptitudes().scores().get(key);
        });
        lines.add(shortfall.map(missing -> missing.message().copy().withStyle(ChatFormatting.RED))
                .orElseGet(() -> Component.translatable(TEXT + "ready_to_study").withStyle(ChatFormatting.GREEN)));
        return lines;
    }
}
