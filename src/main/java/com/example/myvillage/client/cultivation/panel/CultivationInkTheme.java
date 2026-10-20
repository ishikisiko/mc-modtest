package com.example.myvillage.client.cultivation.panel;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/** Scoped to the approved meditation page; other H pages retain their established theme. */
public final class CultivationInkTheme {
    public static final int PAPER = 0xFFE9E2CE;
    public static final int INK = 0xFF293D33;
    public static final int MUTED = 0xFF65755D;
    public static final int FAINT = 0xFF90947B;
    public static final int JADE = 0xFF3F6B57;
    public static final int GOLD = 0xFF9C793C;
    public static final int RED = 0xFF974F3F;
    public static final int RULE = 0xFFBCB89E;
    private static final Style BOOK = Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath("myvillage", "boss_detail"));
    private static final Style BRUSH = Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath("myvillage", "boss_title"));

    private CultivationInkTheme() {}

    public static Component book(String text) { return Component.literal(text).withStyle(BOOK); }
    public static Component brush(String text) { return Component.literal(text).withStyle(BRUSH); }
    public static String tr(String suffix, Object... args) {
        return Component.translatable("screen.myvillage.cultivation.ink." + suffix, args).getString();
    }

    public static Component fit(Font font, String text, int width, boolean title) {
        Component whole = title ? brush(text) : book(text);
        if (font.width(whole) <= width) return whole;
        String out = text;
        while (!out.isEmpty()) {
            out = out.substring(0, out.offsetByCodePoints(out.length(), -1));
            Component candidate = title ? brush(out + "…") : book(out + "…");
            if (font.width(candidate) <= width) return candidate;
        }
        return Component.empty();
    }

    public static void text(GuiGraphics g, Font f, String text, int x, int y, int width, int color) {
        g.drawString(f, fit(f, text, width, false), x, y, color, false);
    }

    public static void title(GuiGraphics g, Font f, String text, int x, int y, int width) {
        g.drawString(f, fit(f, text, width, true), x, y, INK, false);
    }

    public static void centered(GuiGraphics g, Font f, String text, int x, int y, int width, boolean title, int color) {
        Component fitted = fit(f, text, width, title);
        g.drawString(f, fitted, x + (width - f.width(fitted)) / 2, y, color, false);
    }

    public static void pair(GuiGraphics g, Font f, String label, String value, int x, int y, int width, int color) {
        Component right = fit(f, value, Math.max(20, width * 2 / 3), false);
        int rightWidth = f.width(right);
        text(g, f, label, x, y, width - rightWidth - 5, color);
        g.drawString(f, right, x + width - rightWidth, y, color, false);
    }

    public static void paper(GuiGraphics g, int x, int y, int width, int height) {
        g.fillGradient(x, y, x + width, y + height, 0xFFF0EAD9, PAPER);
        // Quiet deterministic paper fibres, not an animated fullscreen shader.
        for (int i = 4; i < width - 3; i += 7) {
            g.fill(x + i, y + 2, x + i + 1, y + height - 2, 0x036A623E);
        }
        g.renderOutline(x, y, width, height, 0xFF948463);
        g.renderOutline(x + 3, y + 3, width - 6, height - 6, 0xFFCDC4A7);
    }

    public static void bar(GuiGraphics g, int x, int y, int width, double fraction, int color) {
        g.fill(x, y, x + width, y + 3, 0xFFD1D1BA);
        int filled = (int) Math.round(width * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) g.fill(x, y, x + filled, y + 3, color);
    }
}
