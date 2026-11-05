package com.example.myvillage.client.talisman;

import com.example.myvillage.client.cultivation.panel.CultivationInkTheme;
import com.example.myvillage.talisman.TalismanRules;
import com.example.myvillage.talisman.TalismanTravelMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class TalismanTravelScreen extends AbstractContainerScreen<TalismanTravelMenu> {
    public TalismanTravelScreen(TalismanTravelMenu menu, Inventory inv, Component title) {
        super(menu, inv, title); imageWidth = TalismanTravelMenu.WIDTH; imageHeight = TalismanTravelMenu.HEIGHT;
    }
    private void send(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }
    @Override protected void init() {
        super.init();
        for (int i = 0; i < menu.entries().size(); i++) {
            int index = i; var row = menu.entries().get(i);
            String dimension = switch (row.dimension().toString()) {
                case "minecraft:overworld" -> "主世界";
                case "minecraft:the_nether" -> "下界";
                case "minecraft:the_end" -> "末地";
                default -> row.dimension().toString();
            };
            String label = row.name() + "  " + dimension + " " + row.pos().getX() + ", " + row.pos().getZ();
            addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(label, imageWidth - 40)), b -> send(index))
                    .bounds(leftPos + 10, topPos + 36 + i * 25, imageWidth - 20, 23)
                    .tooltip(Tooltip.create(Component.literal(row.name() + "\n" + dimension + "\n" + row.pos().toShortString()))).build());
        }
        var previous = addRenderableWidget(Button.builder(Component.literal("上一页"), b -> send(TalismanTravelMenu.PREVIOUS))
                .bounds(leftPos + 10, topPos + 193, 78, 20).build());
        previous.active = menu.page() > 0;
        var next = addRenderableWidget(Button.builder(Component.literal("下一页"), b -> send(TalismanTravelMenu.NEXT))
                .bounds(leftPos + imageWidth - 88, topPos + 193, 78, 20).build());
        next.active = menu.page() + 1 < menu.pages();
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
                .bounds(leftPos + imageWidth / 2 - 39, topPos + 219, 78, 20).build());
    }
    @Override protected void renderBg(GuiGraphics g, float delta, int mx, int my) {
        CultivationInkTheme.paper(g, leftPos, topPos, imageWidth, imageHeight);
    }
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        CultivationInkTheme.title(g, font, "回城符：选择石碑", 10, 9, imageWidth - 20);
        CultivationInkTheme.text(g, font, "点击目的地传送，消耗一张符和 " + TalismanRules.get().use("activation_power") + " 点灵力", 10, 23, imageWidth - 20, CultivationInkTheme.MUTED);
        g.drawCenteredString(font, (menu.page() + 1) + " / " + menu.pages(), imageWidth / 2, 199, CultivationInkTheme.INK);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta); renderTooltip(g, mx, my);
    }
}
