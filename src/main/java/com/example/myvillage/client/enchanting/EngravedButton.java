package com.example.myvillage.client.enchanting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class EngravedButton extends Button {
    public EngravedButton(int x, int y, int width, int height, Component text, OnPress press) {
        super(x, y, width, height, text, press, DEFAULT_NARRATION);
    }

    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        int x = getX(), y = getY();
        boolean hover = active && (isHovered() || isFocused());
        g.fill(x, y, x + width, y + height, !active ? 0xffd9d2bc : hover ? 0xffceddc3 : 0xffd0c4a5);
        g.fill(x, y, x + width, y + 1, hover ? 0xff68917e : 0xff9e8b63);
        g.fill(x, y + height - 1, x + width, y + height, 0xff938161);
        var font = Minecraft.getInstance().font;
        String label = font.plainSubstrByWidth(getMessage().getString(), width - 4);
        g.drawString(font, label, x + (width - font.width(label)) / 2, y + (height - 8) / 2,
                active ? 0xff243932 : 0xff756e5d, false);
    }
}
