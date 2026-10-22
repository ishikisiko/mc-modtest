package com.example.myvillage.client.cultivation.panel;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Colors and drawing primitives shared by the cultivation panel and its pages. Everything is
 * drawn over the shared paper surface. Other game menus keep their original theme.
 */
public final class InkPanelTheme {
    public static final int BACKDROP_TOP = 0xB80A0E10;
    public static final int BACKDROP_BOTTOM = 0xD4050708;
    public static final int PANEL = 0xFFF0EAD9;
    public static final int HEADER_TOP = 0xFFF0EAD9;
    public static final int HEADER_BOTTOM = 0xFFE9E2CE;
    public static final int RAIL = 0xFFE9E2CE;
    public static final int CARD = 0x35F8F4E7;
    public static final int CARD_BORDER = 0xFFBCB89E;
    public static final int GOLD = 0xFF9C793C;
    public static final int GOLD_BRIGHT = 0xFF7B5F2E;
    public static final int GOLD_DIM = 0xFFAAA17D;
    public static final int JADE = 0xFF3F6B57;
    public static final int JADE_DARK = 0xFF3F6B57;
    public static final int AMBER = 0xFFA47737;
    public static final int AMBER_DARK = 0xFF85602E;
    public static final int RED = 0xFF974F3F;
    public static final int TEXT = 0xFF293D33;
    public static final int MUTED = 0xFF65755D;
    public static final int FAINT = 0xFF828871;
    public static final int DIVIDER = 0xFFBCB89E;
    public static final int BAR_TRACK = 0xFFD1D1BA;
    public static final int FALLBACK_ELEMENT = 0xFF718477;

    /** Height of a card's title row; card content starts below it. */
    public static final int CARD_TITLE_HEIGHT = 14;
    /** Distance between two text rows. */
    public static final int ROW = 10;
    /** Height of {@link #meter}: a label row with a bar under it. */
    public static final int METER_HEIGHT = 15;
    public static final int CARD_PADDING = 6;
    public static final int CHIP_HEIGHT = 12;

    private InkPanelTheme() {
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** The panel body: ink ground, a double gold border, and bracket ornaments at the corners. */
    public static void frame(GuiGraphics graphics, int x, int y, int width, int height) {
        CultivationInkTheme.paper(graphics, x, y, width, height);
    }

    private static void corner(GuiGraphics graphics, int x, int y, int arm, int dirX, int dirY) {
        int endX = x + dirX * arm;
        int endY = y + dirY * arm;
        graphics.fill(Math.min(x, endX), y, Math.max(x, endX) + 1, y + 1, GOLD_BRIGHT);
        graphics.fill(x, Math.min(y, endY), x + 1, Math.max(y, endY) + 1, GOLD_BRIGHT);
    }

    /** A titled content card; content starts at {@code y + CARD_TITLE_HEIGHT}. */
    public static void card(GuiGraphics graphics, Font font, int x, int y, int width, int height, Component title) {
        graphics.fill(x, y, x + width, y + height, CARD);
        graphics.fill(x, y + CARD_TITLE_HEIGHT - 1, x + width, y + CARD_TITLE_HEIGHT, CARD_BORDER);
        diamond(graphics, x + 8, y + 7, 2, GOLD);
        String text = fit(font, title.getString(), width - 22);
        graphics.drawString(font, text, x + 14, y + 3, GOLD_BRIGHT, false);
        int ruleX = x + 14 + font.width(text) + 5;
        if (ruleX < x + width - CARD_PADDING) {
            graphics.fill(ruleX, y + 7, x + width - CARD_PADDING, y + 8, DIVIDER);
        }
    }

    public static void diamond(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int offset = -radius; offset <= radius; offset++) {
            int half = radius - Math.abs(offset);
            graphics.fill(centerX - half, centerY + offset, centerX + half + 1, centerY + offset + 1, color);
        }
    }

    public static void hollowDiamond(
            GuiGraphics graphics, int centerX, int centerY, int radius, int color, int inside) {
        diamond(graphics, centerX, centerY, radius, color);
        if (radius > 0) {
            diamond(graphics, centerX, centerY, radius - 1, inside);
        }
    }

    /** A bordered bar filled to {@code fraction} with a vertical gradient. */
    public static void bar(
            GuiGraphics graphics, int x, int y, int width, int height, double fraction, int top, int bottom) {
        graphics.fill(x, y, x + width, y + height, BAR_TRACK);
        graphics.renderOutline(x, y, width, height, CARD_BORDER);
        int inner = Math.max(0, width - 2);
        int filled = (int) Math.round(inner * Math.max(0.0D, Math.min(1.0D, fraction)));
        if (filled > 0) {
            graphics.fillGradient(x + 1, y + 1, x + 1 + filled, y + height - 1, top, bottom);
        }
        for (int quarter = 1; quarter < 4; quarter++) {
            int tickX = x + 1 + inner * quarter / 4;
            graphics.fill(tickX, y + 1, tickX + 1, y + height - 1, 0x50000000);
        }
    }

    /** Label on the left, value on the right, and a bar under both. */
    public static void meter(
            GuiGraphics graphics,
            Font font,
            String label,
            String value,
            int x,
            int y,
            int width,
            double fraction,
            int top,
            int bottom) {
        pair(graphics, font, label, value, x, y, width, TEXT);
        bar(graphics, x, y + ROW, width, 5, fraction, top, bottom);
    }

    /** Muted label on the left, value right-aligned to {@code x + width}. */
    public static void pair(
            GuiGraphics graphics, Font font, String label, String value, int x, int y, int width, int valueColor) {
        // The value is the information: it may take up to three fifths before the label, and
        // whatever a short label leaves free.
        int available = Math.max(1, width - 6);
        int valueWidth = Math.min(font.width(value), available * 3 / 5);
        int labelWidth = Math.min(font.width(label), available - valueWidth);
        String fittedValue = fit(font, value, Math.max(1, available - labelWidth));
        graphics.drawString(font, fit(font, label, labelWidth), x, y, MUTED, false);
        graphics.drawString(font, fittedValue, x + width - font.width(fittedValue), y, valueColor, false);
    }

    /** A condition row: a filled jade diamond when met, a hollow one otherwise. */
    public static void condition(
            GuiGraphics graphics, Font font, boolean met, String label, String value, int x, int y, int width) {
        if (met) {
            diamond(graphics, x + 2, y + 3, 2, JADE);
        } else {
            hollowDiamond(graphics, x + 2, y + 3, 2, FAINT, CARD);
        }
        pair(graphics, font, label, value, x + 8, y, width - 8, met ? JADE : TEXT);
    }

    /** A small outlined tag; returns its width. */
    public static int chip(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        int width = font.width(text) + 7;
        graphics.fill(x, y, x + width, y + CHIP_HEIGHT, withAlpha(color, 0x30));
        graphics.renderOutline(x, y, width, CHIP_HEIGHT, withAlpha(color, 0xB0));
        graphics.drawString(font, text, x + 4, y + 2, color, false);
        return width;
    }

    public static int chipWidth(Font font, String text) {
        return font.width(text) + 7;
    }

    public static String fit(Font font, String value, int maxWidth) {
        if (font.width(value) <= maxWidth) {
            return value;
        }
        String suffix = "...";
        int available = Math.max(1, maxWidth - font.width(suffix));
        return font.plainSubstrByWidth(value, available) + suffix;
    }
}
