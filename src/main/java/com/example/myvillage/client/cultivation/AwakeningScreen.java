package com.example.myvillage.client.cultivation;

import com.example.myvillage.client.cultivation.panel.FatePresentation;
import com.example.myvillage.client.cultivation.panel.PanelButton;
import com.example.myvillage.client.cultivation.panel.PanelContext;
import com.example.myvillage.client.cultivation.panel.PanelTheme;
import com.example.myvillage.cultivation.fate.FateDefinition;
import com.example.myvillage.cultivation.fate.FateSelection;
import com.example.myvillage.cultivation.fate.FateState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** A stele-owned root reveal followed by a compact, non-scrolling fate comparison. */
public final class AwakeningScreen extends Screen {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(AwakeningScreen.class);
    private final BlockPos stelePos;
    private final long sessionToken;
    private ResourceKey<Level> dimension;
    private AwakeningLayout layout;
    private final List<CandidateButton> cards = new ArrayList<>();
    private PanelButton reroll, lock, confirm, proceed;
    private boolean rootStep = true, confirming, closed, awaiting;
    private long revision = -1;
    private int selection, focused = -1;
    private int pendingTicks;
    private String loggedState = "";

    public AwakeningScreen(BlockPos stelePos, long sessionToken) {
        super(Component.translatable("awakening.myvillage.title"));
        this.stelePos = stelePos.immutable();
        this.sessionToken = sessionToken;
    }
    public long sessionToken() { return sessionToken; }

    @Override protected void init() {
        layout = AwakeningLayout.at(width, height);
        if (dimension == null && minecraft.level != null) dimension = minecraft.level.dimension();
        cards.clear();
        for (int i = 0; i < 6; i++) cards.add(addRenderableWidget(new CandidateButton(i, layout.card(i))));
        reroll = action(layout.reroll(), "fate.myvillage.roll", () -> send(0, 0));
        lock = action(layout.lock(), "fate.myvillage.lock", this::toggleLock);
        confirm = action(layout.confirm(), "fate.myvillage.confirm", () -> {
            if (confirming) send(2, selection);
            else confirming = true;
        });
        proceed = action(new AwakeningLayout.Rect(layout.root().x(), layout.confirm().y(),
                layout.root().width(), layout.confirm().height()), "awakening.myvillage.continue", () -> {
            if (rootStep) rootStep = false;
            else onClose();
        });
        action(layout.close(), "awakening.myvillage.close", this::onClose);
        refresh();
    }
    private PanelButton action(AwakeningLayout.Rect rect, String key, Runnable run) {
        return addRenderableWidget(new PanelButton(PanelButton.Style.ACTION, PanelTheme.JADE,
                rect.x(), rect.y(), rect.width(), rect.height(), Component.translatable(key), ignored -> run.run()));
    }
    private PanelContext context() { return PanelContext.capture(minecraft, font); }
    private Map<String, FateDefinition> eligible(PanelContext c) {
        Map<String, FateDefinition> result = new TreeMap<>();
        if (c.profile() != null) c.fateDefinitions().forEach((id, def) -> {
            if (def.eligible(c.profile().spiritualRoot())) result.put(id, def);
        });
        return result;
    }
    private List<String> picks(FateState state) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < state.candidates().size(); i++)
            if ((selection & (1 << i)) != 0) result.add(state.candidates().get(i));
        return result;
    }
    private void send(int action, int value) {
        if (closed || awaiting || rootStep) return;
        if (ClientCultivationIntentSender.sendFate(action, revision, value, sessionToken)) {
            awaiting = true;
            pendingTicks = 40;
            confirming = false;
        }
    }
    private void toggleLock() {
        var state = ClientCultivationState.fates();
        if (!state.locked().isEmpty()) send(1, 255);
        else if (focused >= 0) send(1, focused);
    }
    private void select(int slot) {
        if (awaiting || rootStep || ClientCultivationState.fates().confirmed()) return;
        focused = slot;
        selection ^= 1 << slot;
        confirming = false;
        refresh();
    }
    private void refresh() {
        if (layout == null) return;
        var c = context();
        var state = c.fates();
        if (state.revision() != revision) {
            revision = state.revision(); selection = 0; focused = -1; confirming = false; awaiting = false;
        }
        var definitions = c.fateDefinitions();
        var eligible = eligible(c);
        List<String> shown = state.confirmed() ? state.selected() : state.candidates();
        for (int i = 0; i < cards.size(); i++) {
            CandidateButton card = cards.get(i);
            String id = i < shown.size() ? shown.get(i) : "";
            card.definition = definitions.get(id);
            card.visible = !rootStep && !id.isEmpty();
            card.available = eligible.containsKey(id);
            card.active = !awaiting && !state.confirmed() && card.available;
            card.chosen = card.available && (state.confirmed() || (selection & (1 << i)) != 0);
            card.locked = state.locked().equals(id) && !id.isEmpty();
            card.setMessage(card.definition == null ? Component.literal(id) : Component.translatable(card.definition.translationKey()));
            List<Component> tooltip = new ArrayList<>();
            if (card.definition != null) tooltip.addAll(FatePresentation.description(card.definition));
            if (!card.available) tooltip.add(Component.translatable("fate.myvillage.error.unavailable"));
            card.tooltipLines = List.copyOf(tooltip);
        }
        boolean editing = !rootStep && !state.confirmed();
        reroll.visible = lock.visible = confirm.visible = editing;
        reroll.active = editing && !awaiting && c.profile() != null && c.profile().spiritualRoot().isPresent();
        lock.active = editing && !awaiting && (!state.locked().isEmpty() || focused >= 0);
        lock.setMessage(Component.translatable(state.locked().isEmpty() ? "fate.myvillage.lock" : "fate.myvillage.unlock"));
        confirm.active = editing && !awaiting && FateSelection.validate(picks(state), eligible, c.fateBudget(), true).isEmpty();
        confirm.setMessage(Component.translatable(confirming ? "fate.myvillage.confirm_final" : "fate.myvillage.confirm"));
        proceed.visible = rootStep || state.confirmed();
        proceed.active = c.profile() != null && c.profile().spiritualRoot().isPresent();
        proceed.setMessage(Component.translatable(rootStep ? "awakening.myvillage.continue" : "awakening.myvillage.done"));
        if (!rootStep && state.confirmed()) {
            // A confirmed screen uses its single full-width action as a close button.
            proceed.setMessage(Component.translatable("awakening.myvillage.done"));
        }
        String current = width + "x" + height + ":" + rootStep + ":" + revision + ":" + selection + ":" + confirming;
        if (!current.equals(loggedState)) {
            loggedState = current;
            LOGGER.info("AWAKENING_UI gui={}x{} step={} revision={} selection={} confirming={} confirmed={} token={}",
                    width, height, rootStep ? "root" : "fates", revision, selection, confirming, state.confirmed(), sessionToken);
            if (rootStep) logRect("continue", new AwakeningLayout.Rect(layout.root().x(), layout.confirm().y(), layout.root().width(), layout.confirm().height()));
            else {
                for (int i = 0; i < shown.size(); i++) logRect("card_" + i, layout.card(i));
                logRect("reroll", layout.reroll()); logRect("lock", layout.lock()); logRect("confirm", layout.confirm());
            }
            logRect("close", layout.close());
        }
    }
    private static void logRect(String control, AwakeningLayout.Rect r) {
        LOGGER.info("AWAKENING_UI control={} x={} y={} w={} h={}", control, r.x(), r.y(), r.width(), r.height());
    }
    @Override public void tick() {
        if (minecraft.player == null || minecraft.level == null || !minecraft.player.isAlive()
                || !minecraft.level.dimension().equals(dimension)
                || !minecraft.player.canInteractWithBlock(stelePos, 0)
                || !minecraft.level.hasChunkAt(stelePos)
                || !minecraft.level.getBlockState(stelePos).is(com.example.myvillage.block.ModBlocks.SPIRIT_TESTING_STELE.get())) {
            onClose(); return;
        }
        // Rejected intents need not change the snapshot revision. Permit a bounded retry.
        if (awaiting && --pendingTicks <= 0) awaiting = false;
        refresh();
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        refresh();
        g.fillGradient(0, 0, width, height, 0xB8091012, 0xDB05090B);
        var f = layout.frame();
        g.fill(f.x(), f.y(), f.right(), f.bottom(), 0xF4141C1E);
        g.renderOutline(f.x(), f.y(), f.width(), f.height(), 0xFF43554E);
        g.fill(f.x() + 12, f.y(), f.x() + 56, f.y() + 2, PanelTheme.JADE);
        g.drawString(font, title, f.x() + 12, f.y() + 13, PanelTheme.GOLD_BRIGHT, false);
        PanelContext c = context();
        if (rootStep) renderRootReveal(g, c);
        else {
            renderRootBand(g, c);
            var state = c.fates();
            Component heading = Component.translatable(state.confirmed() ? "fate.myvillage.fixed"
                    : confirming ? "awakening.myvillage.permanent_short" : "awakening.myvillage.choose");
            g.drawString(font, PanelTheme.fit(font, heading.getString(), layout.grid().width()),
                    layout.grid().x(), layout.grid().y() - 14, confirming ? PanelTheme.GOLD_BRIGHT : PanelTheme.MUTED, false);
            var picks = state.confirmed() ? state.selected() : picks(state);
            int cost = picks.stream().map(c.fateDefinitions()::get).filter(java.util.Objects::nonNull).mapToInt(FateDefinition::cost).sum();
            Component summary = Component.translatable("fate.myvillage.selection", picks.size(), cost, c.fateBudget());
            if (!state.confirmed() && picks.size() == 3) {
                String error = FateSelection.validate(picks, eligible(c), c.fateBudget(), true);
                if (!error.isEmpty()) summary = Component.translatable("fate.myvillage.error." + error);
            }
            if (awaiting) summary = Component.translatable("awakening.myvillage.waiting");
            g.drawString(font, PanelTheme.fit(font, summary.getString(), layout.summary().width()),
                    layout.summary().x(), layout.summary().y(), PanelTheme.GOLD_BRIGHT, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
        renderSafeTooltip(g, c, mouseX, mouseY);
    }
    private List<Map.Entry<ResourceLocation, Integer>> affinities(PanelContext c) {
        if (c.profile() == null || c.profile().spiritualRoot().isEmpty()) return List.of();
        return c.profile().spiritualRoot().get().affinitiesBasisPoints().entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<ResourceLocation, Integer> e) -> c.elementSortOrder(e.getKey()))
                        .thenComparing(e -> e.getKey().toString())).toList();
    }
    private Component rootTitle(PanelContext c) {
        if (c.profile() == null || c.profile().spiritualRoot().isEmpty())
            return Component.translatable("awakening.myvillage.waiting");
        long count = affinities(c).stream().filter(e -> e.getValue() > 0).count();
        return Component.translatable("awakening.myvillage.root", count);
    }
    private void renderRootReveal(GuiGraphics g, PanelContext c) {
        var f = layout.frame();
        g.drawCenteredString(font, rootTitle(c), f.x() + f.width() / 2, f.y() + 44, PanelTheme.GOLD_BRIGHT);
        var entries = affinities(c);
        int y = f.y() + 65;
        int row = Math.min(20, Math.max(12, (layout.summary().y() - y - 16) / Math.max(1, entries.size())));
        for (var e : entries) {
            String name = c.elementName(e.getKey()).getString();
            String share = String.format(Locale.ROOT, "%.1f%%", e.getValue() / 100.0);
            int x = layout.root().x() + 12, w = layout.root().width() - 24;
            g.drawString(font, PanelTheme.fit(font, name, w - 55), x, y, c.elementColor(e.getKey()), false);
            g.drawString(font, share, x + w - font.width(share), y, PanelTheme.TEXT, false);
            g.fill(x, y + 10, x + w, y + 12, PanelTheme.BAR_TRACK);
            g.fill(x, y + 10, x + (int)(w * e.getValue() / 10000.0), y + 12, c.elementColor(e.getKey()));
            y += row;
        }
        String note = c.profile() != null && c.profile().spiritualRoot().isPresent()
                ? "awakening.myvillage.root_saved" : "awakening.myvillage.waiting";
        g.drawString(font, PanelTheme.fit(font, Component.translatable(note).getString(), layout.summary().width()),
                layout.summary().x(), layout.summary().y(), PanelTheme.MUTED, false);
    }
    private void renderRootBand(GuiGraphics g, PanelContext c) {
        var r = layout.root();
        g.drawString(font, rootTitle(c), r.x(), r.y(), PanelTheme.JADE, false);
        var entries = affinities(c);
        int cell = r.width() / Math.max(1, entries.size());
        for (int i = 0; i < entries.size(); i++) {
            var e = entries.get(i);
            Component label = c.elementName(e.getKey()).copy().append(String.format(Locale.ROOT, " %.1f%%", e.getValue() / 100.0));
            int x = r.x() + i * cell;
            g.drawString(font, PanelTheme.fit(font, label.getString(), cell - 4), x, r.y() + 15, c.elementColor(e.getKey()), false);
            g.fill(x, r.y() + 27, x + cell - 5, r.y() + 29, PanelTheme.BAR_TRACK);
            g.fill(x, r.y() + 27, x + (int)((cell - 5) * e.getValue() / 10000.0), r.y() + 29, c.elementColor(e.getKey()));
        }
    }
    private List<Component> lockTooltip(PanelContext c) {
        List<Component> lines = new ArrayList<>();
        String target = c.fates().locked();
        if (target.isEmpty() && focused >= 0 && focused < c.fates().candidates().size())
            target = c.fates().candidates().get(focused);
        FateDefinition def = c.fateDefinitions().get(target);
        if (def != null) lines.add(Component.translatable("awakening.myvillage.lock_target",
                Component.translatable(def.translationKey())));
        lines.add(Component.translatable("awakening.myvillage.lock_hint"));
        return lines;
    }
    private void renderSafeTooltip(GuiGraphics g, PanelContext c, int mouseX, int mouseY) {
        if (rootStep) return;
        List<Component> content = List.of();
        // Pointer targets take precedence over the previously keyboard-focused control.
        for (int i = 0; i < cards.size(); i++) if (cards.get(i).visible && layout.card(i).contains(mouseX, mouseY)) {
            content = cards.get(i).tooltipLines; break;
        }
        if (content.isEmpty() && lock.visible && layout.lock().contains(mouseX, mouseY)) content = lockTooltip(c);
        if (content.isEmpty() && confirm.visible && layout.confirm().contains(mouseX, mouseY))
            content = List.of(Component.translatable("awakening.myvillage.permanent"));
        if (content.isEmpty() && reroll.visible && layout.reroll().contains(mouseX, mouseY))
            content = List.of(Component.translatable("awakening.myvillage.reroll_hint"));
        if (content.isEmpty() && layout.root().contains(mouseX, mouseY)) {
            content = affinities(c).stream().map(e -> (Component)c.elementName(e.getKey()).copy()
                    .append(String.format(Locale.ROOT, " %.1f%%", e.getValue() / 100.0))).toList();
        }
        if (content.isEmpty() && minecraft.getLastInputType().isKeyboard()) {
            for (CandidateButton card : cards) if (card.visible && card == getFocused()) content = card.tooltipLines;
            if (lock.visible && getFocused() == lock) content = lockTooltip(c);
            if (confirm.visible && getFocused() == confirm) content = List.of(Component.translatable("awakening.myvillage.permanent"));
            if (reroll.visible && getFocused() == reroll) content = List.of(Component.translatable("awakening.myvillage.reroll_hint"));
        }
        if (content.isEmpty()) return;
        int wrap = AwakeningTooltipLayout.wrapWidth(width);
        var lines = font.split(join(content), wrap);
        int textHeight = lines.size() * 10 + 3;
        if (textHeight + 2 * AwakeningTooltipLayout.PADDING > height - 2 * AwakeningTooltipLayout.MARGIN) {
            lines = font.split(join(content), AwakeningTooltipLayout.widestWrap(width));
            textHeight = lines.size() * 10 + 3;
        }
        int textWidth = lines.stream().mapToInt(font::width).max().orElse(1);
        var box = AwakeningTooltipLayout.place(width, height, mouseX, mouseY, textWidth, textHeight);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(box.x(), box.y(), box.right(), box.bottom(), 0xFE10191B);
        g.renderOutline(box.x(), box.y(), box.width(), box.height(), PanelTheme.JADE_DARK);
        g.pose().translate(box.x() + AwakeningTooltipLayout.PADDING, box.y() + AwakeningTooltipLayout.PADDING, 0);
        g.pose().scale((float)box.textScale(), (float)box.textScale(), 1);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), 0, i * 10 + (i == 0 ? 0 : 3),
                i == 0 ? PanelTheme.GOLD_BRIGHT : PanelTheme.TEXT, false);
        g.pose().popPose();
    }
    private static Component join(List<Component> lines) {
        var result = Component.empty();
        for (int i = 0; i < lines.size(); i++) { if (i > 0) result.append("\n"); result.append(lines.get(i)); }
        return result;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) { }
    @Override public void onClose() { closeSession(); super.onClose(); }
    @Override public void removed() { closeSession(); super.removed(); }
    private void closeSession() {
        if (!closed) { closed = true; ClientCultivationIntentSender.sendAwakeningClose(sessionToken); }
    }

    private final class CandidateButton extends Button {
        private final int slot;
        private FateDefinition definition;
        private boolean chosen, locked, available;
        private List<Component> tooltipLines = List.of();
        CandidateButton(int slot, AwakeningLayout.Rect r) {
            super(r.x(), r.y(), r.width(), r.height(), Component.empty(), ignored -> select(slot), DEFAULT_NARRATION);
            this.slot = slot;
        }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            int accent = definition == null || !available ? PanelTheme.MUTED : FatePresentation.color(definition.tier());
            g.fill(x, y, x + w, y + h, chosen ? 0xFF233B35 : isHoveredOrFocused() ? 0xFF25312F : 0xFF192325);
            g.renderOutline(x, y, w, h, chosen ? PanelTheme.JADE : PanelTheme.CARD_BORDER);
            g.fill(x + 1, y + 1, x + 3, y + h - 1, accent);
            String cost = definition == null ? "?" : Component.translatable("awakening.myvillage.cost", definition.cost()).getString();
            int costWidth = font.width(cost);
            g.drawString(font, PanelTheme.fit(font, getMessage().getString(), w - 24), x + 7, y + 4, accent, false);
            if (chosen) PanelTheme.diamond(g, x + w - 8, y + 7, 2, PanelTheme.JADE);
            if (definition != null && h >= 40) {
                var lines = font.split(Component.translatable(definition.descriptionKey()), w - 14);
                int count = Math.min(lines.size(), (h - 30) / 10);
                for (int i = 0; i < count; i++)
                    g.drawString(font, lines.get(i), x + 7, y + 18 + i * 10, PanelTheme.MUTED, false);
            }
            String status = Component.translatable(!available ? "screen.myvillage.cultivation.unavailable"
                    : chosen ? "awakening.myvillage.selected_short" : "awakening.myvillage.unselected_short").getString();
            if (locked) status += " · " + Component.translatable("awakening.myvillage.locked_short").getString();
            g.drawString(font, PanelTheme.fit(font, status, w - costWidth - 17), x + 7, y + h - 12,
                    chosen ? PanelTheme.JADE : PanelTheme.MUTED, false);
            g.drawString(font, cost, x + w - costWidth - 5, y + h - 12, PanelTheme.GOLD_BRIGHT, false);
        }
    }
}
