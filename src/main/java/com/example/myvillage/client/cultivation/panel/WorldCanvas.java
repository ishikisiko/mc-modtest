package com.example.myvillage.client.cultivation.panel;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * One frame of drawing for the 天下 page. Every call can run "dry" to measure a card before its
 * background is drawn, so a card is sized by the same code that fills it. Clickable rows record
 * their hit boxes in screen coordinates and take the hover tint when the pointer is over them.
 */
final class WorldCanvas {
    static final int HOVER = PanelTheme.withAlpha(PanelTheme.GOLD, 0x18);
    static final int PAD = PanelTheme.CARD_PADDING;
    static final int ROW = PanelTheme.ROW;
    /** Space under a card's last row. */
    static final int CARD_BOTTOM = 4;

    /** A clickable rectangle in screen coordinates. */
    record Hot(int left, int top, int right, int bottom, Runnable action) {
        boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    /** A card's content: draws from {@code (x, y)} within {@code width} and returns the height used. */
    @FunctionalInterface
    interface Body {
        int draw(int x, int y, int width);
    }

    final GuiGraphics graphics;
    final Font font;
    private final int pointerX;
    private final int pointerY;
    private final List<Hot> hots = new ArrayList<>();
    private boolean dry;

    WorldCanvas(GuiGraphics graphics, Font font, int pointerX, int pointerY) {
        this.graphics = graphics;
        this.font = font;
        this.pointerX = pointerX;
        this.pointerY = pointerY;
    }

    List<Hot> hots() {
        return hots;
    }

    /** Runs {@code body} without drawing or recording hit boxes and returns what it measured. */
    int measure(IntSupplier body) {
        boolean was = dry;
        dry = true;
        try {
            return body.getAsInt();
        } finally {
            dry = was;
        }
    }

    /** True while measuring: nothing is drawn. */
    boolean measuring() {
        return dry;
    }

    /** A titled card around {@code body}, at least {@code minHeight} tall; returns its height. */
    int card(int x, int y, int width, int minHeight, Component title, Body body) {
        int innerX = x + PAD;
        int innerWidth = Math.max(1, width - PAD * 2);
        int top = y + PanelTheme.CARD_TITLE_HEIGHT + 1;
        int content = measure(() -> body.draw(innerX, top, innerWidth));
        int height = Math.max(minHeight, PanelTheme.CARD_TITLE_HEIGHT + 1 + content + CARD_BOTTOM);
        if (!dry) {
            PanelTheme.card(graphics, font, x, y, width, height, title);
            body.draw(innerX, top, innerWidth);
        }
        return height;
    }

    int card(int x, int y, int width, Component title, Body body) {
        return card(x, y, width, 0, title, body);
    }

    /**
     * Makes a rectangle clickable: records it and, when the pointer is over it, lays the hover tint
     * under whatever is drawn next. Call it before drawing the row's text.
     */
    void link(int left, int top, int right, int bottom, Runnable action) {
        if (dry || action == null || right <= left || bottom <= top) {
            return;
        }
        hots.add(new Hot(left, top, right, bottom, action));
        if (pointerX >= left && pointerX < right && pointerY >= top && pointerY < bottom) {
            graphics.fill(left, top, right, bottom, HOVER);
        }
    }

    void fill(int left, int top, int right, int bottom, int color) {
        if (!dry) {
            graphics.fill(left, top, right, bottom, color);
        }
    }

    /** Draws {@code text} cut to {@code width}; returns the drawn width. */
    int text(String text, int x, int y, int width, int color) {
        if (width <= 0) {
            return 0;
        }
        String fitted = PanelTheme.fit(font, text, width);
        if (!dry) {
            graphics.drawString(font, fitted, x, y, color, false);
        }
        return font.width(fitted);
    }

    /** Draws {@code text} cut to {@code width}, right-aligned to {@code right}; returns its width. */
    int textRight(String text, int right, int y, int width, int color) {
        if (width <= 0) {
            return 0;
        }
        String fitted = PanelTheme.fit(font, text, width);
        int drawn = font.width(fitted);
        if (!dry) {
            graphics.drawString(font, fitted, right - drawn, y, color, false);
        }
        return drawn;
    }

    /** Wraps {@code text} to {@code width} and draws every line; returns the height used. */
    int wrapped(Component text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = font.split(text, Math.max(1, width));
        if (!dry) {
            int lineY = y;
            for (FormattedCharSequence line : lines) {
                graphics.drawString(font, line, x, lineY, color, false);
                lineY += ROW;
            }
        }
        return Math.max(1, lines.size()) * ROW;
    }

    void pair(String label, String value, int x, int y, int width, int valueColor) {
        if (!dry) {
            PanelTheme.pair(graphics, font, label, value, x, y, width, valueColor);
        }
    }

    /** A chip cut so it never passes {@code maxWidth}; returns its width (0 when nothing fits). */
    int chip(String text, int x, int y, int maxWidth, int color) {
        if (maxWidth < 12) {
            return 0;
        }
        String fitted = PanelTheme.chipWidth(font, text) <= maxWidth ? text : PanelTheme.fit(font, text, maxWidth - 7);
        int width = PanelTheme.chipWidth(font, fitted);
        if (!dry) {
            PanelTheme.chip(graphics, font, fitted, x, y, color);
        }
        return width;
    }

    int chipWidth(String text) {
        return PanelTheme.chipWidth(font, text);
    }

    void bar(int x, int y, int width, int height, double fraction, int top, int bottom) {
        if (!dry && width > 2) {
            PanelTheme.bar(graphics, x, y, width, height, fraction, top, bottom);
        }
    }

    void diamond(int centerX, int centerY, int radius, int color) {
        if (!dry) {
            PanelTheme.diamond(graphics, centerX, centerY, radius, color);
        }
    }

    int width(String text) {
        return font.width(text);
    }
}
