package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.fate.FateDefinition;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/** Full, unshortened descriptions shared by result rows and awakening tooltips. */
public final class FatePresentation {
    private FatePresentation() { }
    public static int color(int tier) {
        return switch (tier) {
            case 1 -> 0xff78ba94; case 2 -> 0xff79aade; case 3 -> 0xffbd9ce2;
            case 4 -> 0xffecad66; case 5 -> 0xffee8886; default -> PanelTheme.TEXT;
        };
    }
    public static List<Component> description(FateDefinition def) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(def.translationKey()));
        lines.add(Component.translatable(def.descriptionKey()));
        for (var e : new TreeMap<>(def.effects()).entrySet()) {
            if(e.getKey().equals("jingang_adaptation")||e.getKey().equals("phoenix")||e.getKey().equals("everlasting")) {
                lines.add(Component.translatable("fate.myvillage.effect."+e.getKey()).append("：已启用"));continue;
            }
            boolean ticks = e.getKey().endsWith("ticks") || e.getKey().endsWith("cooldown");
            String value = ticks ? String.format(Locale.ROOT, "%.1fs", e.getValue() / 20)
                    : (e.getKey().endsWith("stacks") || e.getKey().endsWith("hits") || e.getKey().equals("air_dodge")) ? String.format(Locale.ROOT, "%.0f", e.getValue())
                    : e.getKey().endsWith("radius") ? String.format(Locale.ROOT, "%.1f", e.getValue())
                    : e.getKey().endsWith("strength") ? String.format(Locale.ROOT, "%.2f", e.getValue())
                    : String.format(Locale.ROOT, "%+.0f%%", e.getValue() * 100);
            lines.add(Component.translatable("fate.myvillage.effect." + e.getKey()).append(": " + value));
        }
        return List.copyOf(lines);
    }
    /** 与觉醒选择页相同的完整说明，长文本会在视口内换行并缩放。 */
    public static void tooltip(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font,
            List<Component> content, int mouseX, int mouseY, int width, int height) {
        if (content.isEmpty()) return;
        int wrap = com.example.myvillage.client.cultivation.AwakeningTooltipLayout.wrapWidth(width);
        var lines = font.split(join(content), wrap);
        int textHeight = lines.size() * 10 + 3;
        if (textHeight + 2 * com.example.myvillage.client.cultivation.AwakeningTooltipLayout.PADDING > height - 2 * com.example.myvillage.client.cultivation.AwakeningTooltipLayout.MARGIN) {
            lines = font.split(join(content), com.example.myvillage.client.cultivation.AwakeningTooltipLayout.widestWrap(width));
            textHeight = lines.size() * 10 + 3;
        }
        int textWidth = lines.stream().mapToInt(font::width).max().orElse(1);
        var box = com.example.myvillage.client.cultivation.AwakeningTooltipLayout.place(width, height, mouseX, mouseY, textWidth, textHeight);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(box.x(), box.y(), box.right(), box.bottom(), 0xFE10191B);
        g.renderOutline(box.x(), box.y(), box.width(), box.height(), PanelTheme.JADE_DARK);
        g.pose().translate(box.x() + com.example.myvillage.client.cultivation.AwakeningTooltipLayout.PADDING, box.y() + com.example.myvillage.client.cultivation.AwakeningTooltipLayout.PADDING, 0);
        g.pose().scale((float)box.textScale(), (float)box.textScale(), 1);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), 0, i * 10 + (i == 0 ? 0 : 3),
                i == 0 ? PanelTheme.GOLD_BRIGHT : PanelTheme.TEXT, false);
        g.pose().popPose();
    }
    private static Component join(List<Component> lines) {
        var result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) result.append("\n");
            result.append(lines.get(i));
        }
        return result;
    }

}
