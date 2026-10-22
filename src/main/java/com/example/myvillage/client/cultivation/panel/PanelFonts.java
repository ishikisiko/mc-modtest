package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.mixin.PanelFontAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/** A scoped default font: drawing, wrapping and measuring resolve exactly the same glyphs. */
public final class PanelFonts {
    public static final ResourceLocation BODY = ResourceLocation.fromNamespaceAndPath("myvillage", "panel_body");
    public static final ResourceLocation TITLE = ResourceLocation.fromNamespaceAndPath("myvillage", "panel_title");
    private static Font vanilla;
    private static Font panel;

    private PanelFonts() {}

    public static Font get() {
        Font current = Minecraft.getInstance().font;
        if (panel == null || vanilla != current) {
            vanilla = current;
            var sets = ((PanelFontAccess) current).myvillage$fontSets();
            // The function reads the manager on every lookup and therefore survives resource reloads.
            panel = new Font(id -> sets.apply(Style.DEFAULT_FONT.equals(id) ? BODY : id), false);
        }
        return panel;
    }
}
