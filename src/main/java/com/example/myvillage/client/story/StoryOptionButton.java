package com.example.myvillage.client.story;

import com.example.myvillage.client.cultivation.panel.CultivationInkTheme;
import com.example.myvillage.client.cultivation.panel.PanelFonts;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Wraps the complete option label inside its always-visible hit box, using the shared body font. */
final class StoryOptionButton extends Button {
    StoryOptionButton(StoryLayout.Rect box, String text, OnPress press) {
        super(box.x(), box.y(), box.width(), box.height(), CultivationInkTheme.book(text), press, DEFAULT_NARRATION);
    }
    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var font = PanelFonts.get();
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
                active && isHoveredOrFocused() ? 0xFFDEDCC3 : 0xFFEEE8D6);
        g.renderOutline(getX(), getY(), getWidth(), getHeight(),
                active && isHoveredOrFocused() ? CultivationInkTheme.JADE : CultivationInkTheme.RULE);
        float scale = 1;
        List<FormattedCharSequence> rows = font.split(getMessage(), getWidth() - 10);
        while (rows.size() * 11 * scale > getHeight() - 6 && scale > .35f) {
            scale -= .05f;
            rows = font.split(getMessage(), Math.max(1, (int)((getWidth() - 10) / scale)));
        }
        g.pose().pushPose();
        g.pose().translate(getX() + getWidth() / 2f, getY() + (getHeight() - rows.size() * 11 * scale) / 2f, 0);
        g.pose().scale(scale, scale, 1);
        int y = 0;
        for (var row : rows) {
            g.drawString(font, row, -font.width(row) / 2, y,
                    active ? CultivationInkTheme.INK : CultivationInkTheme.FAINT, false);
            y += 11;
        }
        g.pose().popPose();
    }
}
