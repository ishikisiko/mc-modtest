package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.fate.FateService;
import com.example.myvillage.sim.fate.NpcFateRoll;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

/** 每帧只读服务器提供的出生词条，不读取玩家灵根或提供选择操作。 */
public final class NpcFateCards {
    private List<Component> hovered = List.of();
    public void reset() { hovered = List.of(); }
    public void draw(GuiGraphics g, Font font, RegistryAccess registries, NpcFateRoll fates,
            int x, int y, int width, int mouseX, int mouseY) {
        reset();
        if (fates == null) return;
        g.drawString(font, "天赋 · 已用 " + fates.spent() + " / " + fates.budget() + " 点",
                x, y + 2, CultivationInkTheme.MUTED, false);
        var definitions = FateService.definitions(registries);
        for (int i = 0; i < fates.selected().size(); i++) {
            String id = fates.selected().get(i);
            var definition = definitions.get(id);
            boolean available = definition != null && definition.enabled();
            Component name = definition == null ? Component.literal(id) : Component.translatable(definition.translationKey());
            var rect = NpcFateLayout.card(x, y, width, i);
            boolean over = rect.contains(mouseX, mouseY);
            FateCardRenderer.draw(g, font, rect.x(), rect.y(), rect.width(), rect.height(),
                    definition, name, available, available, false, over, false);
            if (over) {
                var lines = new ArrayList<Component>();
                if (definition == null) lines.add(name);
                else lines.addAll(FatePresentation.description(definition));
                if (!available) lines.add(Component.translatable("fate.myvillage.error.unavailable"));
                hovered = List.copyOf(lines);
            }
        }
    }
    public void tooltip(GuiGraphics g, Font font, int mouseX, int mouseY, int width, int height) {
        FatePresentation.tooltip(g, font, hovered, mouseX, mouseY, width, height);
    }
}
