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
            boolean ticks = e.getKey().endsWith("ticks") || e.getKey().endsWith("cooldown");
            String value = ticks ? String.format(Locale.ROOT, "%.1fs", e.getValue() / 20)
                    : e.getKey().endsWith("radius") ? String.format(Locale.ROOT, "%.1f", e.getValue())
                    : e.getKey().endsWith("strength") ? String.format(Locale.ROOT, "%.2f", e.getValue())
                    : String.format(Locale.ROOT, "%+.0f%%", e.getValue() * 100);
            lines.add(Component.translatable("fate.myvillage.effect." + e.getKey()).append(": " + value));
        }
        return List.copyOf(lines);
    }
}
