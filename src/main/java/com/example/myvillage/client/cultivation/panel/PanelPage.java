package com.example.myvillage.client.cultivation.panel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.function.Consumer;

/**
 * One page of the cultivation panel. The screen owns the frame, the rail, and scrolling; a page
 * draws its body and may dock fixed widgets under it. A new system joins the panel by adding a
 * page and a rail entry.
 */
public abstract class PanelPage {
    /**
     * Adds the page's fixed widgets, laid out against the bottom of the page rectangle, and
     * returns the height they take from it. The body scrolls in what remains.
     */
    public int init(int x, int y, int width, int height, Consumer<AbstractWidget> widgets) {
        return 0;
    }

    /** Shows or hides the fixed widgets when the open page changes. */
    public void setVisible(boolean visible) {
    }

    /** Updates advisory widget state from the latest caches; runs every tick and frame. */
    public void refresh(PanelContext context, boolean visible) {
    }

    /**
     * Draws the body with its top-left at {@code (x, y)} and returns its full height. The profile
     * is never null here. {@code viewportHeight} is the visible height, which a page may use to
     * stretch short content.
     */
    public abstract int render(
            GuiGraphics graphics, PanelContext context, int x, int y, int width, int viewportHeight);
}
