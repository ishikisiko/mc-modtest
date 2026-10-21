package com.example.myvillage.client.item;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.forging.ForgeMark;
import com.example.myvillage.item.AlchemyPillItem;
import com.example.myvillage.item.QualityDisplay;
import com.example.myvillage.item.QualityPicker;
import com.example.myvillage.network.QualityPickerPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class QualityPickerScreen extends Screen {
    private static final String KEY = "screen.myvillage.quality_picker.";
    private static final int ROWS = 6;
    private final List<Item> catalogue = QualityPicker.catalogue().stream()
            .sorted(Comparator.<Item>comparingInt(item -> QualityPicker.allows(item, 4) ? 0 : 1)
                    .thenComparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).toList();
    private List<Item> filtered = catalogue;
    private final List<Button> rows = new ArrayList<>(), qualities = new ArrayList<>(), traits = new ArrayList<>();
    private Item selected = catalogue.stream().filter(item -> BuiltInRegistries.ITEM.getKey(item).getPath().equals("pill_huiling"))
            .findFirst().orElse(catalogue.getFirst());
    private int quality = QualityPicker.defaultQuality(selected), trait, page, left, top;
    private Button previous, next;
    private EditBox search;
    private ItemStack preview = ItemStack.EMPTY;
    private boolean requested;

    public QualityPickerScreen() { super(Component.translatable("item.myvillage.quality_picker")); }
    private Component text(String id) { return Component.translatable(KEY + id); }

    @Override protected void init() {
        left = (width - 304) / 2; top = (height - 220) / 2;
        rows.clear(); qualities.clear(); traits.clear();
        search = new EditBox(font, left + 8, top + 28, 288, 18, text("search"));
        search.setMaxLength(64); search.setHint(text("search"));
        search.setResponder(value -> {
            String query = value.strip().toLowerCase(Locale.ROOT);
            filtered = catalogue.stream().filter(item -> item.getDescription().getString().toLowerCase(Locale.ROOT).contains(query)
                    || BuiltInRegistries.ITEM.getKey(item).toString().contains(query)).toList();
            page = 0; refresh();
        });
        addRenderableWidget(search);
        for (int i = 0; i < ROWS; i++) {
            final int row = i;
            rows.add(addRenderableWidget(Button.builder(Component.empty(), button -> {
                int index = page * ROWS + row;
                if (index < filtered.size()) {
                    selected = filtered.get(index);
                    if (!QualityPicker.allows(selected, quality)) quality = QualityPicker.defaultQuality(selected);
                    trait = 0; refresh();
                }
            }).bounds(left + 8, top + 52 + i * 22, 142, 20).build()));
        }
        previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; refresh(); })
                .bounds(left + 8, top + 190, 30, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; refresh(); })
                .bounds(left + 120, top + 190, 30, 20).build());
        for (int q = 0; q <= 4; q++) {
            final int value = q;
            var button = addRenderableWidget(Button.builder(text("quality." + q), b -> {
                quality = value; trait = 0; refresh();
            }).bounds(left + 158 + q * 28, top + 52, 26, 20).build());
            button.setTooltip(Tooltip.create(text("quality_hint." + q))); qualities.add(button);
        }
        for (int t = 0; t < ForgeMark.TRAITS.size(); t++) {
            final int value = t;
            traits.add(addRenderableWidget(Button.builder(Component.translatable("forging.myvillage.trait." + ForgeMark.TRAITS.get(t)),
                    b -> { trait = value; refresh(); }).bounds(left + 158 + t * 47, top + 80, 44, 20).build()));
        }
        addRenderableWidget(Button.builder(text("one"), b -> give(false)).bounds(left + 158, top + 170, 64, 20).build());
        addRenderableWidget(Button.builder(text("stack"), b -> give(true)).bounds(left + 228, top + 170, 68, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + 158, top + 194, 138, 20).build());
        refresh();
    }

    private void refresh() {
        requested = false;
        for (int i = 0; i < rows.size(); i++) {
            int index = page * ROWS + i; var button = rows.get(i); button.visible = index < filtered.size();
            if (button.visible) {
                Item item = filtered.get(index);
                String label = (item == selected ? "• " : "") + item.getDescription().getString();
                button.setMessage(Component.literal(font.plainSubstrByWidth(label, 134)));
                button.setTooltip(Tooltip.create(item.getDescription().copy().append("\n" + BuiltInRegistries.ITEM.getKey(item))));
            }
        }
        if (previous != null) { previous.active = page > 0; next.active = (page + 1) * ROWS < filtered.size(); }
        for (int q = 0; q < qualities.size(); q++) {
            var button = qualities.get(q); button.active = QualityPicker.allows(selected, q);
            final int value = q;
            button.setMessage(text("quality." + q).copy().withStyle(style -> style.withColor(QualityDisplay.color(value))
                    .withUnderlined(quality == value)));
        }
        for (int t = 0; t < traits.size(); t++) {
            String id = ForgeMark.TRAITS.get(t); var button = traits.get(t);
            button.active = quality > 0 && QualityPicker.traits(selected).contains(id);
            button.setMessage(Component.translatable("forging.myvillage.trait." + id).withStyle(style -> style.withUnderlined(id.equals(ForgeMark.TRAITS.get(trait)) && button.active)));
        }
        preview = QualityPicker.create(selected, quality, trait, false);
    }

    private void give(boolean stack) {
        if (!preview.isEmpty()) {
            PacketDistributor.sendToServer(new QualityPickerPayload(BuiltInRegistries.ITEM.getKey(selected), quality, trait, stack));
            requested = true;
        }
    }

    @Override public void tick() {
        if (minecraft == null || minecraft.player == null || !minecraft.player.isAlive() || !QualityPicker.held(minecraft.player)) onClose();
    }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);
        g.fill(left - 2, top - 2, left + 306, top + 222, 0xFF786346);
        g.fill(left, top, left + 304, top + 220, 0xFF252823);
        g.drawString(font, title, left + 10, top + 10, 0xFFE5D5AF, false);
        g.drawString(font, Component.literal((page + 1) + "/" + Math.max(1, (filtered.size() + ROWS - 1) / ROWS)), left + 53, top + 196, 0xFFC5C6B7, false);
        if (filtered.isEmpty()) g.drawWordWrap(font, text("empty"), left + 12, top + 60, 130, 0xFFC5C6B7);
        if (!preview.isEmpty()) {
            g.renderItem(preview, left + 216, top + 110);
            g.renderItemDecorations(font, preview, left + 216, top + 110);
            g.drawWordWrap(font, preview.getHoverName(), left + 158, top + 136, 138, 0xFFE5D5AF);
        }
        if (requested) g.drawString(font, text("requested"), left + 158, top + 158, 0xFFC5C6B7, false);
        super.render(g, mouseX, mouseY, delta);
        if (!preview.isEmpty() && mouseX >= left + 212 && mouseX < left + 238 && mouseY >= top + 106 && mouseY < top + 130)
            g.renderTooltip(font, preview, mouseX, mouseY);
    }

    @EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
    public static final class Input {
        @SubscribeEvent public static void use(InputEvent.InteractionKeyMappingTriggered event) {
            var mc = Minecraft.getInstance();
            if (event.isUseItem() && mc.screen == null && mc.player != null && QualityPicker.held(mc.player)) {
                event.setCanceled(true); event.setSwingHand(false);
                mc.setScreen(new QualityPickerScreen());
            }
        }
    }
}
