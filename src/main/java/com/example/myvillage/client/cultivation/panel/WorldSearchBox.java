package com.example.myvillage.client.cultivation.panel;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * The 天下 page's search field in the panel's theme: a card-coloured box with a thin border that
 * turns gold while focused. The vanilla field is unbordered and laid over the box's text line; the
 * whole box still takes clicks and hover.
 */
public final class WorldSearchBox extends EditBox {
    private static final int INSET = 4;

    private final int boxX;
    private final int boxY;
    private final int boxWidth;
    private final int boxHeight;

    public WorldSearchBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x + INSET, y + (height - 8) / 2, Math.max(1, width - INSET * 2), 9, message);
        this.boxX = x;
        this.boxY = y;
        this.boxWidth = width;
        this.boxHeight = height;
        setBordered(false);
        setTextColor(PanelTheme.TEXT);
        setTextShadow(false);
    }

    /** Sets the hint in the panel's faint colour. */
    public void setPanelHint(Component hint) {
        setHint(hint.copy().withStyle(style -> style.withColor(PanelTheme.FAINT & 0xFFFFFF)));
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (isVisible()) {
            graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, PanelTheme.CARD);
            graphics.renderOutline(boxX, boxY, boxWidth, boxHeight, isFocused() ? PanelTheme.GOLD : PanelTheme.CARD_BORDER);
        }
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        return active && visible && insideBox(mouseX, mouseY);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return active && visible && insideBox(mouseX, mouseY);
    }

    private boolean insideBox(double mouseX, double mouseY) {
        return mouseX >= boxX && mouseX < boxX + boxWidth && mouseY >= boxY && mouseY < boxY + boxHeight;
    }
}
