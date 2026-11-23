package com.example.myvillage.client.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Compact status icons anchored to the shared health bar. */
public final class HudStatusIcons {
    private static final int SIZE = 14;
    private static final int STEP = 16;

    private HudStatusIcons() {}

    /** Returns the extra space needed by the vanilla HUD layers drawn above the icons. */
    public static int render(GuiGraphics graphics, List<ItemStack> icons, int x, int bottom, int width) {
        int columns = Math.max(1, width / STEP);
        for (int index = 0; index < icons.size(); index++) {
            int left = x + index % columns * STEP;
            int top = bottom - SIZE - index / columns * STEP;
            graphics.fill(left, top, left + SIZE, top + SIZE, 0xCC181C18);
            graphics.renderOutline(left, top, SIZE, SIZE, 0xC09D875E);
            graphics.pose().pushPose();
            graphics.pose().translate(left + 1, top + 1, 0);
            graphics.pose().scale(.75F, .75F, 1);
            graphics.renderFakeItem(icons.get(index), 0, 0);
            graphics.pose().popPose();
        }
        return Math.ceilDiv(icons.size(), columns) * STEP;
    }
}
