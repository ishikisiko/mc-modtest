package com.example.myvillage.client.cultivation.panel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Small native widgets: paper insight slips, an ink action, and quiet header links. */
final class TechniqueScrollButton extends Button {
    enum Kind { LINK, PRACTICE, INSIGHT }
    private final Kind kind;
    private String label, effectLabel = "", effectValue = "";
    private boolean chosen;
    private double insightAge = 99;

    TechniqueScrollButton(Kind kind, int x, int y, int width, int height, String label, OnPress press) {
        super(x, y, width, height, Component.literal(label), press, DEFAULT_NARRATION);
        this.kind = kind; this.label = label;
    }
    void label(String label) { this.label = label; setMessage(Component.literal(label)); }
    void resize(int width, int height) { this.width = width; this.height = height; }
    void insightFeedback(double age) { insightAge = age; }
    void insight(String name, String effectLabel, String effectValue, boolean chosen) {
        label = name; this.effectLabel = effectLabel; this.effectValue = effectValue; this.chosen = chosen;
        setMessage(Component.literal((chosen ? "已悟·" : "参悟·") + name + "，" + effectLabel + effectValue));
    }
    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var font = PanelFonts.get();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = active && isHoveredOrFocused();
        if (kind == Kind.LINK) {
            CultivationInkTheme.centered(g, font, label, x, y + (h - 9) / 2, w, false,
                    hover ? InkPanelTheme.RED : InkPanelTheme.MUTED);
            if (hover) g.fill(x + 3, y + h - 2, x + w - 3, y + h - 1, InkPanelTheme.GOLD);
            return;
        }
        if (kind == Kind.PRACTICE) {
            int color = !active ? 0xFF858B70 : hover ? 0xFF536C4B : 0xFF3D5945;
            g.fill(x + 2, y + 1, x + w - 2, y + h - 1, color);
            g.fill(x, y + 3, x + w, y + h - 4, color);
            g.fill(x + 5, y, x + w - 5, y + 2, color);
            g.fill(x + 1, y + h - 4, x + w - 4, y + h, color);
            CultivationInkTheme.centered(g, font, label, x + 3, y + (h - 9) / 2, w - 6, true, 0xFFF4E7C9);
            if (isFocused()) g.renderOutline(x, y, w, h, InkPanelTheme.GOLD);
            return;
        }
        int lift = hover ? 2 : 0;
        y -= lift;
        g.fillGradient(x, y, x + w, y + h - 4, chosen ? 0xFFF3E0BC : 0xFFF3E7CD,
                chosen ? 0xFFE8CFA4 : 0xFFE6D7B5);
        for (int i = 0; i < 4; i++) g.fill(x + i * w / 8, y + h - 4 + i,
                x + w - i * w / 8, y + h - 3 + i, chosen ? 0xFFE8CFA4 : 0xFFE6D7B5);
        g.fill(x, y, x + w, y + 1, chosen ? InkPanelTheme.RED : InkPanelTheme.GOLD_DIM);
        int color = chosen ? InkPanelTheme.RED : active ? InkPanelTheme.TEXT : InkPanelTheme.FAINT;
        int[] letters = label.codePoints().toArray();
        for (int i = 0; i < Math.min(2, letters.length); i++) {
            CultivationInkTheme.centered(g, font, new String(Character.toChars(letters[i])),
                    x + 1, y + 6 + i * 11, w - 2, true, color);
        }
        if (h >= 52) CultivationInkTheme.centered(g, font, effectLabel, x + 1, y + h - 24, w - 2, false, InkPanelTheme.MUTED);
        CultivationInkTheme.centered(g, font, chosen ? "已悟" : effectValue, x + 1, y + h - 13, w - 2, false,
                chosen ? InkPanelTheme.RED : InkPanelTheme.MUTED);
        if (isFocused()) g.renderOutline(x, y, w, h - 3, InkPanelTheme.GOLD);
        if (chosen && insightAge < 1.4) {
            float settle = (float) Math.max(0, 1 - insightAge / .35);
            g.pose().pushPose();
            g.pose().translate(x + w - 4, y + 4, 0);
            g.pose().scale(1 + settle * .6f, 1 + settle * .6f, 1);
            g.fill(-5, -5, 7, 7, 0xFFF1E0BC);
            g.renderOutline(-5, -5, 12, 12, InkPanelTheme.RED);
            g.drawString(font, CultivationInkTheme.brush("悟"), -3, -3, InkPanelTheme.RED, false);
            g.pose().popPose();
        }
    }
}
