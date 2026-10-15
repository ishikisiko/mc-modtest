package com.example.myvillage.client.cultivation.panel;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Read-only innate aptitudes and the server's same-recipe product-quality ceilings. */
public final class CraftingPage extends PanelPage {
    private static final Logger LOGGER = LoggerFactory.getLogger(CraftingPage.class);
    private static final String KEY = "screen.myvillage.crafting.";
    private static final int CARD_HEIGHT = 52;
    private static final int GAP = 6;
    private static final int TWO_COLUMNS_AT = 340;
    private boolean visible;

    @Override public int init(int x, int y, int width, int height, Consumer<AbstractWidget> widgets) {
        LOGGER.info("CRAFTING_UI page=crafting x={} y={} w={} h={} controls=0", x, y, width, height);
        return 0;
    }

    @Override public void setVisible(boolean next) {
        if (next && !visible) LOGGER.info("CRAFTING_UI page=crafting visible=true controls=0");
        visible = next;
    }

    @Override public int render(GuiGraphics graphics, PanelContext context, int x, int y, int width, int viewportHeight) {
        int start = y;
        boolean initialized = context.crafting().initialized();
        y = text(graphics, context, Component.translatable(KEY + (initialized ? "meaning" : "awaken")),
                x, y, width, initialized ? PanelTheme.MUTED : PanelTheme.GOLD_BRIGHT);
        y += GAP;
        var rows = CraftingReadouts.of(context.crafting(), context.alchemyQualityCap(), context.forgingQualityCap());
        if (width >= TWO_COLUMNS_AT) {
            int cardWidth = (width - GAP) / 2;
            card(graphics, context, rows.get(0), x, y, cardWidth);
            card(graphics, context, rows.get(1), x + cardWidth + GAP, y, width - cardWidth - GAP);
            y += CARD_HEIGHT + GAP;
        } else {
            for (var row : rows) {
                card(graphics, context, row, x, y, width);
                y += CARD_HEIGHT + GAP;
            }
        }
        y = text(graphics, context, Component.translatable(KEY + "foundation"), x, y, width, PanelTheme.FAINT);
        return y - start;
    }

    private static void card(GuiGraphics graphics, PanelContext context, CraftingReadouts.Readout row,
            int x, int y, int width) {
        var font = context.font();
        PanelTheme.card(graphics, font, x, y, width, CARD_HEIGHT, Component.translatable(row.professionKey()));
        int pad = PanelTheme.CARD_PADDING, innerWidth = width - pad * 2;
        String value = row.initialized() ? row.score() + " / 100" : context.text(KEY + "pending");
        PanelTheme.meter(graphics, font, context.text(KEY + "aptitude"), value,
                x + pad, y + 16, innerWidth, row.initialized() ? row.score() / 100.0 : 0,
                PanelTheme.JADE, PanelTheme.JADE_DARK);
        PanelTheme.pair(graphics, font, context.text(KEY + "quality_cap"), context.text(row.qualityKey()),
                x + pad, y + 36, innerWidth, row.initialized() ? PanelTheme.GOLD_BRIGHT : PanelTheme.MUTED);
    }

    private static int text(GuiGraphics graphics, PanelContext context, Component text,
            int x, int y, int width, int color) {
        for (var line : context.font().split(text, Math.max(1, width))) {
            graphics.drawString(context.font(), line, x, y, color, false);
            y += PanelTheme.ROW;
        }
        return y;
    }
}
