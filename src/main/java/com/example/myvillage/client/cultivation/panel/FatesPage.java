package com.example.myvillage.client.cultivation.panel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** H only inspects confirmed results. Drafts belong to the testing stele. */
public final class FatesPage extends PanelPage {
    @Override public int render(GuiGraphics g, PanelContext c, int x, int y, int width, int viewportHeight) {
        int top = y;
        if (!c.fates().confirmed()) {
            return text(g, c, Component.translatable("awakening.myvillage.visit_stele"), x, y, width, PanelTheme.MUTED) - top;
        }
        y = text(g, c, Component.translatable("fate.myvillage.fixed"), x, y, width, PanelTheme.GOLD_BRIGHT);
        var definitions = c.fateDefinitions();
        for (String id : c.fates().selected()) {
            var def = definitions.get(id);
            if (def == null || c.profile() == null || !def.eligible(c.profile().spiritualRoot())) {
                y = text(g, c, c.unavailable(ResourceLocation.parse(id)), x, y, width, PanelTheme.MUTED);
                continue;
            }
            g.fill(x, y, x + 2, y + 10, FatePresentation.color(def.tier()));
            boolean title = true;
            for (Component line : FatePresentation.description(def)) {
                y = text(g, c, line, x + 8, y, width - 8,
                        title ? FatePresentation.color(def.tier()) : PanelTheme.MUTED);
                title = false;
            }
            y += 6;
        }
        return y - top;
    }
    private static int text(GuiGraphics g, PanelContext c, Component text, int x, int y, int width, int color) {
        for (var part : c.font().split(text, width)) {
            g.drawString(c.font(), part, x, y, color, false); y += 11;
        }
        return y + 4;
    }
}
