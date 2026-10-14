package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.client.cultivation.ClientCultivationIntentSender;
import com.example.myvillage.cultivation.fate.FateDefinition;
import com.example.myvillage.cultivation.fate.FateSelection;
import com.example.myvillage.cultivation.fate.FateState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Read-only view of the synced draft. Only slot/mask intent crosses to the server. */
public final class FatesPage extends PanelPage {
    private final List<PanelButton> buttons = new ArrayList<>();
    private long revision = -1;
    private int selection;
    private boolean confirming;
    private int pointerX = -1, pointerY = -1;
    @Override public void pointer(int x, int y) { pointerX = x; pointerY = y; }
    @Override public boolean mouseClicked(double x, double y, int button) {
        for (PanelButton candidate : buttons) if (candidate.mouseClicked(x, y, button)) return true;
        return false;
    }
    private void button(GuiGraphics g, int x, int y, int width, String key, boolean enabled, Runnable action) {
        PanelButton b = new PanelButton(PanelButton.Style.ACTION, PanelTheme.JADE, x, y, width, 16,
                Component.translatable(key), ignored -> action.run());
        b.active = enabled; b.render(g, pointerX, pointerY, 0); buttons.add(b);
    }
    private static int text(GuiGraphics g, PanelContext c, Component text, int x, int y, int width, int color) {
        int line = y;
        for (var part : c.font().split(text, width)) { g.drawString(c.font(), part, x, line, color, false); line += 11; }
        return line + 4;
    }
    @Override public int render(GuiGraphics g, PanelContext c, int x, int y, int width, int viewportHeight) {
        buttons.clear(); int top = y;
        FateState state = c.fates();
        if (revision != state.revision()) { revision = state.revision(); selection = 0; confirming = false; }
        var all = c.fateDefinitions(); Map<String, FateDefinition> eligible = new TreeMap<>();
        all.forEach((id, d) -> { if (d.eligible(c.profile().spiritualRoot())) eligible.put(id, d); });
        y = text(g, c, Component.translatable("fate.myvillage.page_intro", c.fateBudget()), x, y, width, PanelTheme.MUTED);
        if (c.profile().spiritualRoot().isEmpty()) {
            return text(g, c, Component.translatable("fate.myvillage.error.root"), x, y, width, PanelTheme.GOLD_BRIGHT) - top;
        }
        List<String> shown = state.confirmed() ? state.selected() : state.candidates();
        List<String> picks = new ArrayList<>();
        for (int i = 0; i < state.candidates().size(); i++) if ((selection & (1 << i)) != 0) picks.add(state.candidates().get(i));
        int cost = picks.stream().map(all::get).filter(Objects::nonNull).mapToInt(FateDefinition::cost).sum();
        if (!state.confirmed()) {
            y = text(g, c, Component.translatable("fate.myvillage.selection", picks.size(), cost, c.fateBudget()), x, y, width, PanelTheme.GOLD_BRIGHT);
            int half = (width - 4) / 2;
            int yy = y;
            button(g, x, yy, half, "fate.myvillage.roll", true, () -> ClientCultivationIntentSender.sendFate(0, state.revision(), 0));
            boolean valid = FateSelection.validate(picks, eligible, c.fateBudget(), true).isEmpty();
            button(g, x + half + 4, yy, half, confirming ? "fate.myvillage.confirm_final" : "fate.myvillage.confirm", valid, () -> {
                if (!confirming) confirming = true;
                else ClientCultivationIntentSender.sendFate(2, state.revision(), selection);
            });
            y += 21;
            if (confirming) y = text(g, c, Component.translatable("fate.myvillage.permanent"), x, y, width, PanelTheme.GOLD_BRIGHT);
            if (!state.locked().isEmpty()) {
                button(g, x, y, width, "fate.myvillage.unlock", true, () -> ClientCultivationIntentSender.sendFate(1, state.revision(), 255)); y += 21;
            }
        } else y = text(g, c, Component.translatable("fate.myvillage.fixed"), x, y, width, PanelTheme.GOLD_BRIGHT);
        if (shown.isEmpty()) y = text(g, c, Component.translatable("fate.myvillage.empty"), x, y, width, PanelTheme.MUTED);
        for (String id : shown) {
            FateDefinition def = all.get(id);
            if (def == null || !eligible.containsKey(id)) { y = text(g, c, c.unavailable(net.minecraft.resources.ResourceLocation.parse(id)), x, y, width, PanelTheme.MUTED); continue; }
            int start = y; int inner = width - 12;
            y += 5;
            y = text(g, c, Component.translatable(def.translationKey()).append(Component.literal(" · " + def.cost()))
                    .append(Component.translatable("fate.myvillage.points")), x + 6, y, inner, color(def.tier()));
            y = text(g, c, Component.translatable(def.descriptionKey()), x + 6, y, inner, PanelTheme.MUTED);
            for (var e : new TreeMap<>(def.effects()).entrySet()) {
                boolean ticks = e.getKey().endsWith("ticks") || e.getKey().endsWith("cooldown");
                String value = ticks ? String.format(Locale.ROOT, "%.1fs", e.getValue() / 20) : String.format(Locale.ROOT, "%+.0f%%", e.getValue() * 100);
                y = text(g, c, Component.translatable("fate.myvillage.effect." + e.getKey()).append(": " + value), x + 6, y, inner, PanelTheme.MUTED);
            }
            if (!state.confirmed()) {
                int slot = state.candidates().indexOf(id); boolean picked = (selection & (1 << slot)) != 0;
                int half = (inner - 4) / 2;
                button(g, x + 6, y, half, picked ? "fate.myvillage.unpick" : "fate.myvillage.pick", true, () -> {
                    selection ^= 1 << slot; confirming = false;
                });
                button(g, x + half + 10, y, half, state.locked().equals(id) ? "fate.myvillage.locked" : "fate.myvillage.lock",
                        state.locked().isEmpty() && eligible.containsKey(id), () -> ClientCultivationIntentSender.sendFate(1, state.revision(), slot));
                y += 20;
            }
            y += 5; g.renderOutline(x, start, width, y - start, PanelTheme.CARD_BORDER); y += 5;
        }
        return y - top;
    }
    private static int color(int tier) { return switch (tier) { case 1 -> 0xff78ba94; case 2 -> 0xff79aade; case 3 -> 0xffbd9ce2; case 4 -> 0xffecad66; case 5 -> 0xffee8886; default -> 0xffdddddd; }; }
}
