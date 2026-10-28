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
    /** Independent views, such as realm travel, can render before the cultivation snapshot arrives. */
    public boolean requiresProfile() { return true; }
    /** Fixed filters/header above the scrolling body; unlike the bottom dock, this never scrolls. */
    public int topInset() { return 0; }
    /** A detail view may reclaim a dock reserved by its catalogue without rebuilding the screen. */
    public int dockHeight(int initializedHeight) { return initializedHeight; }
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
     * Where the mouse is, in screen coordinates, before the page is drawn each frame; -1, -1 when
     * it is outside the body viewport. A page with clickable rows uses it for hover feedback.
     */
    public void pointer(int mouseX, int mouseY) {
    }

    /**
     * A click inside the body viewport, in screen coordinates. Returns true when the page used it;
     * otherwise the screen handles the click as usual.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * Returns true once when the page's content has changed enough that the body should scroll
     * back to the top (the screen asks before drawing the page).
     */
    public boolean takeScrollToTop() {
        return false;
    }

    /** Draw transient read-only tooltips after the hub has removed the body scissor. */
    public void renderOverlay(GuiGraphics graphics, PanelContext context, int mouseX, int mouseY) {
    }

    /**
     * Draws the body with its top-left at {@code (x, y)} and returns its full height. The profile
     * may be null only when requiresProfile() is false. {@code viewportHeight} is the visible height, which a page may use to
     * stretch short content.
     */
    public abstract int render(
            GuiGraphics graphics, PanelContext context, int x, int y, int width, int viewportHeight);
}
