package com.example.myvillage.client.production;

import com.example.myvillage.gathering.GatheringRules;
import com.example.myvillage.gathering.GatheringTier;
import com.example.myvillage.production.ProductionKind;
import com.example.myvillage.production.ProductionMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ProductionScreen extends AbstractContainerScreen<ProductionMenu> {
    private static final int PAPER = 0xFFE5DECB, INK = 0xFF263B34, EDGE = 0xFF9D8A65, FILLED = 0xFF537D68, WARN = 0xFF994631;
    private Button toggle, mode, layerButton, targetDown, targetUp, reserveDown, reserveUp, mediumDown, mediumUp;
    private int layer = 1;
    private ProductionKind drawnKind;
    private Direction drawnDirection;
    private List<ProductionMenu.BlueprintCell> cells = List.of();

    public ProductionScreen(ProductionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = ProductionMenu.WIDTH;
        imageHeight = ProductionMenu.HEIGHT;
    }

    @Override protected void init() {
        super.init();
        toggle = button(114, 93, 55, ProductionMenu.TOGGLE, "启动");
        mode = button(173, 93, 134, ProductionMenu.CHANGE_MODE, "切换模式");
        targetDown = button(263, 112, 20, ProductionMenu.TARGET_DOWN, "−");
        targetUp = button(287, 112, 20, ProductionMenu.TARGET_UP, "+");
        reserveDown = button(263, 130, 20, ProductionMenu.RESERVE_DOWN, "−");
        reserveUp = button(287, 130, 20, ProductionMenu.RESERVE_UP, "+");
        mediumDown = button(263, 130, 20, ProductionMenu.MEDIUM_DOWN, "−");
        mediumUp = button(287, 130, 20, ProductionMenu.MEDIUM_UP, "+");
        layerButton = addRenderableWidget(Button.builder(Component.empty(), b -> layer = layer % (menu.kind().mining() ? 3 : 2) + 1)
                .bounds(leftPos + 14, topPos + 130, 85, 16).build());
    }

    private Button button(int x, int y, int width, int id, String label) {
        return addRenderableWidget(Button.builder(Component.literal(label), b -> {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }).bounds(leftPos + x, topPos + y, width, 16).build());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        var kind = menu.kind();
        boolean mining = kind.mining(), supply = kind == ProductionKind.HIGH && menu.mode() == 3;
        if (kind != drawnKind || menu.facing() != drawnDirection) {
            drawnKind = kind;
            drawnDirection = menu.facing();
            layer = 1;
            cells = menu.blueprint();
        }
        layerButton.setMessage(Component.literal("第 " + layer + " 层"));
        toggle.setMessage(Component.literal(menu.enabled() ? "暂停" : "启动"));
        toggle.active = menu.enabled() || !menu.status().blocked();
        mode.visible = kind.hasModes();
        mode.setMessage(modeLabel(kind));
        targetDown.visible = targetUp.visible = mining || supply;
        reserveDown.visible = reserveUp.visible = mining;
        mediumDown.visible = mediumUp.visible = supply;
        super.render(graphics, mouseX, mouseY, delta);
        renderTooltip(graphics, mouseX, mouseY);
        drawBlueprintTooltip(graphics, mouseX, mouseY);
    }

    private Component modeLabel(ProductionKind kind) {
        if (kind.mining()) return Component.literal(menu.mode() == 1 ? "赶工采掘" : "常速采掘");
        if (menu.mode() == 3) return Component.literal("优先备货，再凝上品");
        return Component.literal("专产").append(stone(menu.mode()));
    }

    private Component stone(int grade) { return BuiltInRegistries.ITEM.get(menu.stoneId(grade)).getDescription(); }

    @Override protected void renderBg(GuiGraphics g, float delta, int mouseX, int mouseY) {
        g.fill(leftPos - 2, topPos - 2, leftPos + imageWidth + 2, topPos + imageHeight + 2, EDGE);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PAPER);
        g.fill(leftPos + 8, topPos + 20, leftPos + imageWidth - 8, topPos + 21, EDGE);
        g.fill(leftPos + 8, topPos + 26, leftPos + 107, topPos + 146, 0xFFD0D5C3);
        for (var slot : menu.slots) if (slot.isActive()) {
            int x = leftPos + slot.x, y = topPos + slot.y;
            g.fill(x - 1, y - 1, x + 17, y + 17, EDGE);
            g.fill(x, y, x + 16, y + 16, 0xFFBCC6B4);
        }
        g.fill(leftPos + 114, topPos + 72, leftPos + 307, topPos + 78, 0xFFCBD2BE);
        g.fill(leftPos + 114, topPos + 72, leftPos + 114 + 193 * menu.progress() / 100, topPos + 78, FILLED);
        drawBlueprint(g);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 9, 8, INK, false);
        g.drawString(font, playerInventoryTitle, 78, 151, INK, false);
        g.drawString(font, "北", 53, 29, INK, false);
        if (menu.kind().mining()) g.drawString(font, "矿镐", 152, 34, INK, false);
        g.drawString(font, "矿仓", 270, 34, INK, false);
        var status = menu.status();
        var detail = status.label().copy();
        if (status == ProductionMenu.Status.PART) detail.append(" ").append(menu.expectedPart());
        if (status.blocked()) detail.append(" ").append(coords(menu.issue()));
        g.drawString(font, detail, 114, 56, status.blocked() ? WARN : INK, false);
        var batch = menu.kind().mining() ? Component.literal(menu.oreType() == 1 ? "粗玄铁" : "粗铁") : stone(menu.oreType());
        g.drawString(font, batch.copy().append(" " + menu.progress() + "%"), 114, 81, INK, false);
        if (menu.kind().mining()) {
            g.drawString(font, "备货 " + menu.target() + " 份", 114, 116, INK, false);
            g.drawString(font, "预留 " + menu.reserveFloor() + " · 矿眼 " + menu.reserve() + "/" + menu.capacity(), 114, 134, INK, false);
        } else if (menu.kind() == ProductionKind.HIGH && menu.mode() == 3) {
            g.drawString(font, "下品备货 " + menu.target() + " 枚", 114, 116, INK, false);
            g.drawString(font, "中品备货 " + menu.mediumTarget() + " 枚", 114, 134, INK, false);
        }
    }

    private int radius() { return switch (menu.kind()) { case MINE -> 2; case LOW -> 5; case MEDIUM -> 6; case HIGH -> 7; }; }
    private int cellSize() { return 91 / (radius() * 2 + 1); }
    private int leftGrid() { return leftPos + 57 - (radius() * 2 + 1) * cellSize() / 2; }
    private int topGrid() { return topPos + 82 - (radius() * 2 + 1) * cellSize() / 2; }

    private void drawBlueprint(GuiGraphics g) {
        int r = radius(), s = cellSize(), left = leftGrid(), top = topGrid();
        int old = menu.kind().mining() ? 1 : GatheringRules.get().tier(GatheringTier.fromWire(menu.kind().ordinal() - 1)).radius();
        for (int x = -old; x <= old; x++) for (int z = -old; z <= old; z++) {
            int color = x == 0 && z == 0 ? INK : 0xFFABB8A4;
            tile(g, left, top, r, s, x, z, color);
        }
        for (var cell : cells) if (cell.offset().getY() == layer) {
            var p = cell.offset();
            tile(g, left, top, r, s, p.getX(), p.getZ(), cell.facing() == null ? FILLED : 0xFF9E764B);
        }
        if (menu.status().blocked() && (menu.issue().getY() == layer || menu.issue().getY() == 0)) {
            var p = menu.issue();
            if (Math.abs(p.getX()) <= r && Math.abs(p.getZ()) <= r) {
                g.renderOutline(left + (p.getX() + r) * s, top + (p.getZ() + r) * s, s, s, WARN);
            }
        }
    }

    private static void tile(GuiGraphics g, int left, int top, int r, int s, int x, int z, int color) {
        int px = left + (x + r) * s, pz = top + (z + r) * s;
        g.fill(px, pz, px + s - 1, pz + s - 1, color);
    }

    private void drawBlueprintTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int dx = mouseX - leftGrid(), dz = mouseY - topGrid(), r = radius(), s = cellSize();
        if (dx < 0 || dz < 0 || dx >= (r * 2 + 1) * s || dz >= (r * 2 + 1) * s) return;
        BlockPos pos = new BlockPos(dx / s - r, layer, dz / s - r);
        for (var cell : cells) if (cell.offset().equals(pos)) {
            var label = cell.name().copy().append(" " + coords(pos));
            if (cell.facing() != null) label.append(" 朝" + cardinal(cell.facing()));
            g.renderTooltip(font, label, mouseX, mouseY);
            return;
        }
        if (pos.getX() == 0 && pos.getZ() == 0) g.renderTooltip(font, Component.literal(menu.kind().mining() ? "天然矿眼在地面层" : "原阵心在地面层"), mouseX, mouseY);
    }

    private static String coords(BlockPos pos) { return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")"; }
    private static String cardinal(Direction dir) {
        return switch (dir) { case NORTH -> "北"; case EAST -> "东"; case SOUTH -> "南"; case WEST -> "西"; default -> "上"; };
    }
}
