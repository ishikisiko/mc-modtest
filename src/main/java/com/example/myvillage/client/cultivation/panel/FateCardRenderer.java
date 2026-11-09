package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.fate.FateDefinition;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 玩家选择与 NPC 只读词条共用的卡片绘制。 */
public final class FateCardRenderer {
    private FateCardRenderer() { }
    public static void draw(GuiGraphics g, Font font, int x, int y, int w, int h,
            FateDefinition definition, Component name, boolean available, boolean chosen,
            boolean locked, boolean hovered, boolean school) {
        int accent = school ? PanelTheme.JADE : definition == null || !available ? PanelTheme.MUTED : FatePresentation.color(definition.tier());
        g.fill(x, y, x + w, y + h, chosen ? 0xFF233B35 : hovered ? 0xFF25312F : 0xFF192325);
        g.renderOutline(x, y, w, h, chosen ? PanelTheme.JADE : PanelTheme.CARD_BORDER);
        g.fill(x + 1, y + 1, x + 3, y + h - 1, accent);
        String cost = school ? "" : definition == null ? "?" : Component.translatable("awakening.myvillage.cost", definition.cost()).getString();
        int costWidth = font.width(cost);
        g.drawString(font, PanelTheme.fit(font, name.getString(), w - 24), x + 7, y + 4, accent, false);
        if (chosen) PanelTheme.diamond(g, x + w - 8, y + 7, 2, PanelTheme.JADE);
        if (definition != null && h >= 40) {
            var lines = font.split(Component.translatable(definition.descriptionKey()), w - 14);
            int count = Math.min(lines.size(), (h - 30) / 10);
            for (int i = 0; i < count; i++)
                g.drawString(font, lines.get(i), x + 7, y + 18 + i * 10, PanelTheme.MUTED, false);
        }
        String status = Component.translatable(!available ? "screen.myvillage.cultivation.unavailable"
                : chosen ? "awakening.myvillage.selected_short" : "awakening.myvillage.unselected_short").getString();
        if (locked) status += " · " + Component.translatable("awakening.myvillage.locked_short").getString();
        g.drawString(font, PanelTheme.fit(font, status, w - costWidth - 17), x + 7, y + h - 12,
                chosen ? PanelTheme.JADE : PanelTheme.MUTED, false);
        g.drawString(font, cost, x + w - costWidth - 5, y + h - 12, PanelTheme.GOLD_BRIGHT, false);
    }
}
