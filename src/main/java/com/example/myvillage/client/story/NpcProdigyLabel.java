package com.example.myvillage.client.story;

import com.example.myvillage.client.cultivation.panel.CultivationInkTheme;
import com.example.myvillage.entity.npc.NpcEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** 对话中的天骄字样只读取服务器同步的人物身份。 */
public final class NpcProdigyLabel {
    public static final int HEIGHT = 14;
    private NpcProdigyLabel() { }
    public static boolean visible(int entityId) {
        var level = Minecraft.getInstance().level;
        return level != null && level.getEntity(entityId) instanceof NpcEntity npc && npc.prodigy();
    }
    /** 返回称号可开始绘制的位置，普通人物保留原布局。 */
    public static int draw(GuiGraphics g, Font font, int entityId, int x, int y) {
        if (!visible(entityId)) return x;
        var text = CultivationInkTheme.brush("天骄");
        g.drawString(font, text, x, y, CultivationInkTheme.GOLD, false);
        return x + font.width(text) + 8;
    }
}
