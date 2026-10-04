package com.example.myvillage.client.cultivation.panel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A vanilla button drawn in the panel's theme: a rail tab or an accented action. */
public final class PanelButton extends Button {
    public enum Style {
        NAV,
        ACTION
    }

    private final Style style;
    private final int accent;
    private boolean selected;

    public PanelButton(
            Style style,
            int accent,
            int x,
            int y,
            int width,
            int height,
            Component message,
            OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.style = style;
        this.accent = accent;
    }

    /** Marks a rail tab as the open page. */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int right = x + getWidth();
        int bottom = y + getHeight();
        boolean lit = active && isHoveredOrFocused();
        int textColor;
        if (style == Style.NAV) {
            if (selected) {
                graphics.fillGradient(x, y, right, bottom, 0xFF2A3735, 0xFF212B2B);
                graphics.fill(x, y, x + 2, bottom, PanelTheme.GOLD_BRIGHT);
                graphics.fill(x + 2, y, right, y + 1, PanelTheme.GOLD_DIM);
                graphics.fill(x + 2, bottom - 1, right, bottom, PanelTheme.GOLD_DIM);
                textColor = PanelTheme.GOLD_BRIGHT;
            } else if (lit) {
                graphics.fill(x, y, right, bottom, 0xFF1B2426);
                graphics.fill(x, y, x + 2, bottom, PanelTheme.GOLD_DIM);
                textColor = PanelTheme.TEXT;
            } else {
                textColor = PanelTheme.MUTED;
            }
        } else if (!active) {
            graphics.fill(x, y, right, bottom, 0xFF161C1E);
            graphics.renderOutline(x, y, getWidth(), getHeight(), 0xFF2A3233);
            textColor = PanelTheme.FAINT;
        } else {
            graphics.fillGradient(
                    x, y, right, bottom, lit ? 0xFF34453F : 0xFF293634, lit ? 0xFF26322F : 0xFF1D2728);
            graphics.renderOutline(x, y, getWidth(), getHeight(), lit ? accent : PanelTheme.GOLD_DIM);
            graphics.fill(x + 1, y + 1, x + 3, bottom - 1, accent);
            textColor = lit ? PanelTheme.GOLD_BRIGHT : PanelTheme.TEXT;
        }
        String label = PanelTheme.fit(font, getMessage().getString(), getWidth() - 8);
        graphics.drawString(
                font,
                label,
                x + (getWidth() - font.width(label)) / 2,
                y + (getHeight() - 8) / 2,
                textColor,
                false);
    }
}
