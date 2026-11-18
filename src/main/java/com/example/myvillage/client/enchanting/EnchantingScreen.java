package com.example.myvillage.client.enchanting;

import static com.example.myvillage.enchanting.EnchantingMenu.*;
import static com.example.myvillage.enchanting.EnchantingRules.RULES;

import com.example.myvillage.enchanting.EnchantingMenu;
import com.example.myvillage.enchanting.EnchantingRules;
import com.example.myvillage.enchanting.RuneStorage;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class EnchantingScreen extends AbstractContainerScreen<EnchantingMenu> {
    private static final int INK = 0xff273b35;
    private static final int MUTED = 0xff605b4b;
    private static final int PAPER = 0xffe5dbc1;
    private static final int GOLD = 0xffa98d59;
    private static final int FULL_LIGHT = 0xF000F0;
    private final List<Control> controls = new ArrayList<>();
    private static final int RUNES_PER_PAGE = 6;
    private final List<Button> catalogue = new ArrayList<>();
    private Mode layout;
    private boolean choosing;
    private int runePage;

    private record Control(Button widget, Supplier<String> caption, BooleanSupplier active) {}

    public EnchantingScreen(EnchantingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
    }

    @Override protected void init() {
        super.init();
        layout = menu.mode();
        controls.clear();
        catalogue.clear();
        choosing = false;
        switch (layout) {
            case TABLE, SEAL -> table();
            case LIBRARY -> library();
            case STONE -> stone();
        }
        if (layout != Mode.STONE) catalogue();
    }

    private void send(int action) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
    }

    private void button(int x, int y, int width, Supplier<String> caption, int action, BooleanSupplier enabled, String tooltip) {
        var button = new EngravedButton(leftPos + x, topPos + y, width, 15, Component.literal(caption.get()), b -> send(action));
        if (!tooltip.isEmpty()) button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        controls.add(new Control(addRenderableWidget(button), caption, enabled));
    }

    private void button(int x, int y, int width, String caption, int action, BooleanSupplier enabled) {
        button(x, y, width, () -> caption, action, enabled, "");
    }

    private void table() {
        for (int i = 0; i < 4; i++) {
            final int row = i;
            int y = 35 + 17 * i;
            button(136, y, 91, () -> candidate(row), SELECT + i, () -> menu.level(row) > 0, "选中这条附魔");
            button(229, y, 14, "−", LOWER + i, () -> menu.level(row) > 1);
            button(243, y, 14, "＋", RAISE + i, () -> menu.level(row) > 0);
            button(261, y, 34, () -> menu.heldIndex() == row ? "已留" : "留纹", HOLD + i,
                    () -> menu.level(row) > 0 && menu.canHold(), "成阵且强度达到 12 时，下一次抽选保留这条");
        }
        button(136, 104, 48, "青金石", LAPIS, () -> !menu.input().isEmpty());
        button(187, 104, 49, "经验 ×3", XP, () -> !menu.input().isEmpty());
        button(239, 104, 56, () -> "刻写 " + menu.cost() + "墨", WRITE, () -> menu.mask() != 0, "");
        button(136, 123, 16, "‹", PREVIOUS, () -> !menu.catalogue().isEmpty());
        button(279, 123, 16, "›", NEXT, () -> !menu.catalogue().isEmpty());
        chooser(123);
        button(136, 140, 51, () -> menu.preferred() ? "已定向" : "定向", FOCUS, menu::focused, "定向书架会提高这一词条的抽取权重");
        button(190, 140, 47, "升一级", UPGRADE, () -> menu.existing() > 0);
        button(240, 140, 55, "洗去", WASH, () -> menu.existing() > 0);
    }

    private void library() {
        button(136, 34, 16, "‹", PREVIOUS, () -> !menu.catalogue().isEmpty());
        button(279, 34, 16, "›", NEXT, () -> !menu.catalogue().isEmpty());
        chooser(34);
        button(136, 65, 17, "−", LEVEL_DOWN, () -> menu.targetLevel() > 1);
        button(174, 65, 17, "＋", LEVEL_UP, () -> menu.targetLevel() < menu.recorded());
        button(194, 65, 47, "印成书", PRINT, () -> menu.recorded() > 0);
        button(245, 65, 50, "收录书", DEPOSIT, () -> menu.input().is(net.minecraft.world.item.Items.ENCHANTED_BOOK));
        button(136, 99, 17, "−", DRAFT_DOWN, () -> menu.formed() && menu.draftLevel() > 0);
        button(174, 99, 17, "＋", DRAFT_UP, () -> menu.formed() && menu.draftLevel() < Math.max(menu.recorded(), menu.existing()));
        button(194, 99, 47, "还原", RESET, menu::formed);
        button(245, 99, 50, "换装", REPLACE, menu::feasible);
        button(136, 135, 53, () -> "配法 " + menu.preset(), PRESET, () -> true, "");
        button(193, 135, 49, "存玉简", SAVE_SLIP, menu::formed);
        button(246, 135, 49, "读玉简", LOAD_SLIP, menu::formed);
    }

    private void stone() {
        button(136, 35, 18, "‹", PREVIOUS, () -> true);
        button(277, 35, 18, "›", NEXT, () -> true);
        button(238, 103, 57, "开炉", WRITE, () -> !menu.input().isEmpty());
    }

    private void chooser(int y) {
        var button = new EngravedButton(leftPos + 155, topPos + y, 120, 15, Component.empty(), b -> {
            if (menu.catalogue().isEmpty()) return;
            choosing = true;
            runePage = menu.selection() / RUNES_PER_PAGE;
        });
        button.setTooltip(Tooltip.create(Component.literal("选择附魔词条")));
        controls.add(new Control(addRenderableWidget(button), this::selectedRune, () -> !menu.catalogue().isEmpty()));
    }

    private String selectedRune() {
        return menu.catalogue().isEmpty() ? "" : menu.current().value().description().getString()
                + (layout != Mode.LIBRARY && menu.existing() > 0 ? " " + roman(menu.existing()) : "");
    }

    private void catalogue() {
        for (int i = 0; i < RUNES_PER_PAGE; i++) {
            int row = i;
            catalogue.add(addRenderableWidget(new EngravedButton(leftPos + 136, topPos + 33 + 17 * i, 159, 15,
                    Component.empty(), button -> {
                        int index = runePage * RUNES_PER_PAGE + row;
                        if (index < menu.catalogue().size()) send(CATALOGUE_SELECT + index);
                        choosing = false;
                    })));
        }
        catalogue.add(addRenderableWidget(new EngravedButton(leftPos + 136, topPos + 136, 26, 15,
                Component.literal("‹"), b -> runePage = Math.max(0, runePage - 1))));
        catalogue.add(addRenderableWidget(new EngravedButton(leftPos + 165, topPos + 136, 101, 15,
                Component.literal("返回"), b -> choosing = false)));
        catalogue.add(addRenderableWidget(new EngravedButton(leftPos + 269, topPos + 136, 26, 15,
                Component.literal("›"), b -> {
                    if ((runePage + 1) * RUNES_PER_PAGE < menu.catalogue().size()) runePage++;
                })));
        catalogue.forEach(button -> button.visible = false);
    }

    private String candidate(int index) {
        var candidates = RuneStorage.candidates(menu.input());
        if (index >= candidates.size() || menu.level(index) == 0) return "—";
        String id = candidates.get(index);
        var name = menu.catalogue().stream().filter(e -> e.unwrapKey().orElseThrow().location().toString().equals(id))
                .findFirst().map(e -> e.value().description().getString()).orElse(id);
        return ((menu.mask() & (1 << index)) != 0 ? "◆ " : "") + name + " " + roman(menu.level(index));
    }

    private static String roman(int level) {
        return switch (level) {
            case 0 -> "—";
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(level);
        };
    }

    @Override protected void containerTick() {
        super.containerTick();
        if (layout != menu.mode()) {
            clearWidgets();
            init();
        }
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xff273734);
        g.renderOutline(x + 2, y + 2, imageWidth - 4, imageHeight - 4, GOLD);
        g.fill(x + 4, y + 4, x + imageWidth - 4, y + imageHeight - 4, PAPER);
        g.fill(x + 10, y + 25, x + 294, y + 26, GOLD);
        g.fill(x + 10, y + 31, x + 123, y + 95, 0xff233634);
        g.renderOutline(x + 10, y + 31, 113, 64, 0xff798b72);
        for (var slot : menu.slots) {
            if (slot.index < 5 && !visibleSlot(slot.index)) continue;
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xff9e8d69);
            g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xffc5baa1);
        }
        scene(g, x, y, partial);
        if (choosing) {
            g.fill(x + 133, y + 29, x + 297, y + 153, 0xffd9cdb0);
            g.renderOutline(x + 133, y + 29, 164, 124, 0xff9e8b63);
        }
    }

    private boolean visibleSlot(int slot) {
        return switch (layout) {
            case TABLE, SEAL -> slot <= 2;
            case STONE -> slot == 0 || slot == 4;
            case LIBRARY -> true;
        };
    }

    private void scene(GuiGraphics g, int x, int y, float partial) {
        if (minecraft == null || minecraft.level == null) return;
        float time = minecraft.level.getGameTime() % 72000 + partial;
        var pose = g.pose();
        g.enableScissor(x + 12, y + 33, x + 121, y + 93);
        pose.pushPose();
        pose.translate(x + 65, y + 84, 90);
        float scale = layout == Mode.LIBRARY ? 4.15f : 6.2f;
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(24));
        pose.mulPose(Axis.YP.rotationDegrees(time * .38f + 30));
        var buffers = g.bufferSource();
        if (layout == Mode.LIBRARY) {
            EnchantingRenderer.box(pose, buffers, -4.8f, 0, -4.5f, 9.6f, 1.85f, 9, "slate", FULL_LIGHT);
            for (int side : new int[]{-1, 1})
                EnchantingRenderer.box(pose, buffers, side * 3.55f - .3f, 1.85f, -.35f, .6f, 6.4f, .7f, "bronze", FULL_LIGHT);
            EnchantingRenderer.library(pose, buffers, FULL_LIGHT, time);
        } else if (layout == Mode.SEAL) {
            EnchantingRenderer.box(pose, buffers, -4.5f, -.19f, -4.5f, 9, .19f, 9, "slate", FULL_LIGHT);
            for (int a : new int[]{-4, 4}) for (int b : new int[]{-4, 4})
                EnchantingRenderer.box(pose, buffers, a - .34f, 0, b - .34f, .68f, 3.65f, .68f, "jade", FULL_LIGHT);
            EnchantingRenderer.box(pose, buffers, -.47f, 0, -.47f, .94f, .76f, .94f, "rose", FULL_LIGHT);
            EnchantingRenderer.seal(pose, buffers, FULL_LIGHT, menu.mask() == 0 ? 1 : 2, time);
        } else {
            EnchantingRenderer.box(pose, buffers, -2.5f, -.3f, -2.5f, 5, .3f, 5, "slate", FULL_LIGHT);
            EnchantingRenderer.box(pose, buffers, -.5f, 0, -.5f, 1, .75f, 1, layout == Mode.STONE ? "bronze" : "rose", FULL_LIGHT);
            EnchantingRenderer.box(pose, buffers, -.30f, .75f, -.20f, .60f, .08f, .40f, "ivory", FULL_LIGHT);
        }
        g.flush();
        pose.popPose();
        g.disableScissor();
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 10, 11, INK, false);
        String info = switch (layout) {
            case TABLE -> "附魔强度 " + menu.strength();
            case SEAL -> (menu.formed() ? "成阵" : "未成阵") + "   强度 " + menu.strength()
                    + (menu.formed() && menu.strength() >= RULES.double_strength() ? "   可同刻两条" : "");
            case LIBRARY -> menu.formed() ? "阵枢已接通" : "藏纹匣 · 存取附魔书";
            case STONE -> "普通炼器鼎";
        };
        print(g, info, 293 - font.width(info), 11, MUTED);
        print(g, menu.status(), 10, 144, MUTED);
        print(g, "行囊", 10, 164, MUTED);
        print(g, layout == Mode.STONE ? "阵材" : "器物", 24, 100, MUTED);
        if (layout != Mode.STONE) {
            print(g, "灵墨", 50, 100, MUTED);
            print(g, layout == Mode.LIBRARY ? "空书" : "青金石", 76, 100, MUTED);
        }
        if (layout == Mode.LIBRARY) {
            print(g, "玉简", 102, 100, MUTED);
            print(g, "出书", 78, 138, MUTED);
            if (choosing) return;
            print(g, "库存 " + menu.points() + "  收录 " + roman(menu.recorded()), 136, 52, INK);
            print(g, roman(menu.targetLevel()), 157, 69, INK);
            print(g, "装备词条  " + roman(menu.existing()) + " → " + roman(menu.draftLevel()), 136, 85, INK);
            print(g, roman(menu.draftLevel()), 157, 103, INK);
            print(g, "收回 " + menu.returned() + "  取用 " + menu.taken() + "  灵墨 " + menu.cost(), 136, 119, MUTED);
        } else if (layout == Mode.STONE) {
            var stone = RULES.stones().get(menu.selection());
            var id = EnchantingRules.id(stone.id());
            print(g, Component.translatable("item." + id.getNamespace() + "." + id.getPath()).getString(), 161, 39, INK);
            print(g, "开放强度 " + stone.strength(), 136, 56, INK);
            print(g, "需炼气 " + stone.stage().substring(stone.stage().lastIndexOf('_') + 1) + " 层", 136, 69, MUTED);
            print(g, "修为 " + stone.progress() + "   紫水晶 ×" + stone.count(), 136, 82, MUTED);
        }
    }

    private void print(GuiGraphics g, String text, int x, int y, int color) { g.drawString(font, text, x, y, color, false); }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        for (var control : controls) {
            control.widget.setMessage(Component.literal(control.caption.get()));
            control.widget.visible = !choosing;
            control.widget.active = control.active.getAsBoolean();
        }
        for (int i = 0; i < catalogue.size(); i++) {
            var widget = catalogue.get(i);
            int index = runePage * RUNES_PER_PAGE + i;
            widget.visible = choosing && (i >= RUNES_PER_PAGE || index < menu.catalogue().size());
            if (i < RUNES_PER_PAGE && widget.visible)
                widget.setMessage(menu.catalogue().get(index).value().description());
        }
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
    }
}
