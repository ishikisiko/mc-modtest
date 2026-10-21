package com.example.myvillage.client.alchemy.array;

import com.example.myvillage.alchemy.AlchemyRules;
import com.example.myvillage.alchemy.PillQuality;
import com.example.myvillage.alchemy.array.AlchemyArrayMenu;
import com.example.myvillage.alchemy.array.AlchemyArrayPattern;
import com.example.myvillage.alchemy.array.AlchemyArrayRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Chinese, server-owned production controls and an interactive, orientation-aware building plan. */
public final class AlchemyArrayScreen extends AbstractContainerScreen<AlchemyArrayMenu> {
    private static final int INK = 0xFF232C27, PANEL = 0xFF303D32, COPPER = 0xFF907650;
    private static final int PAPER = 0xFFE6D8B7, MUTED = 0xFFBAC4AF, GOLD = 0xFFD7B66F;
    private final List<Button> recipeButtons = new ArrayList<>();
    private final List<Button> layerButtons = new ArrayList<>();
    private Button startButton, stopButton, recipesTab, blueprintTab, previousPage, nextPage;
    private int page;
    private boolean blueprint;
    private int layer;

    public AlchemyArrayScreen(AlchemyArrayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = AlchemyArrayMenu.WIDTH;
        imageHeight = AlchemyArrayMenu.HEIGHT;
        inventoryLabelX = 100;
        inventoryLabelY = 156;
    }

    private void send(int button) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override protected void init() {
        super.init();
        recipeButtons.clear(); layerButtons.clear();
        recipesTab = addRenderableWidget(Button.builder(Component.literal("已学丹方"), b -> blueprint = false)
                .bounds(leftPos + 8, topPos + 25, 60, 18).build());
        blueprintTab = addRenderableWidget(Button.builder(Component.literal("搭建蓝图"), b -> blueprint = true)
                .bounds(leftPos + 70, topPos + 25, 60, 18).build());
        var recipes = AlchemyRules.get().recipes();
        for (int i = 0; i < recipes.size(); i++) {
            final int index = i;
            var recipe = recipes.get(i);
            var button = Button.builder(pillName(i), b -> send(2 + index))
                    .bounds(leftPos + 8, topPos + 50, 122, 20).build();
            button.setTooltip(Tooltip.create(com.example.myvillage.client.alchemy.RecipeText.tooltip(recipe)));
            recipeButtons.add(addRenderableWidget(button));
        }
        previousPage=addRenderableWidget(Button.builder(Component.literal("上一页"),b->page=Math.max(0,page-1)).bounds(leftPos+8,topPos+122,58,18).build());
        nextPage=addRenderableWidget(Button.builder(Component.literal("下一页"),b->page++).bounds(leftPos+72,topPos+122,58,18).build());
        String[] layers = {"底层", "中层", "顶层"};
        for (int i = 0; i < 3; i++) {
            final int selected = i - 1;
            layerButtons.add(addRenderableWidget(Button.builder(Component.literal(layers[i]), b -> layer = selected)
                    .bounds(leftPos + 8 + i * 42, topPos + 48, 40, 18).build()));
        }
        startButton = addRenderableWidget(Button.builder(Component.literal("启动连续炼制"), b -> send(0))
                .bounds(leftPos + 140, topPos + 140, 106, 16).build());
        startButton.setTooltip(Tooltip.create(Component.literal("选择已学丹方、补齐药材与灵石后启动。\n基础丹药按资质随机品质；筑基丹、补基丹按丹方固定品级。")));
        stopButton = addRenderableWidget(Button.builder(Component.literal("停止后续批次"), b -> send(1))
                .bounds(leftPos + 252, topPos + 140, 106, 16).build());
        stopButton.setTooltip(Tooltip.create(Component.literal("当前批次保留并继续炼完；不再开启下一批。\n结构损坏或出丹口堵塞时，当前批次会暂停。")));
        updateButtons();
    }

    private void updateButtons() {
        if (startButton == null) return;
        recipesTab.active = blueprint; blueprintTab.active = !blueprint;
        int count=0;for(int i=0;i<recipeButtons.size();i++)if(menu.knows(i))count++;
        page=Math.min(page,Math.max(0,(count-1)/3));previousPage.visible=nextPage.visible=!blueprint;previousPage.active=page>0;nextPage.active=(page+1)*3<count;
        int row = 0;
        for (int i = 0; i < recipeButtons.size(); i++) {
            var button = recipeButtons.get(i);
            button.visible = !blueprint && menu.knows(i) && row/3==page;
            button.active = !menu.active() && !menu.enabled();
            if (button.visible) button.setY(topPos + 50 + (row%3) * 23);
            if(menu.knows(i))row++;
            button.setMessage(Component.literal(menu.selectedRecipe() == i ? "• " : "").append(pillName(i)));
        }
        for (int i = 0; i < layerButtons.size(); i++) {
            layerButtons.get(i).visible = blueprint;
            layerButtons.get(i).active = layer != i - 1;
        }
        startButton.active = !menu.active() && !menu.enabled() && menu.formed() && menu.status() == 5 &&
                menu.selectedRecipe() >= 0 && menu.knows(menu.selectedRecipe());
        startButton.setMessage(Component.literal(menu.enabled() ? "连续炼制已启用" : menu.active() ? "等待本批完成" : "启动连续炼制"));
        stopButton.active = menu.enabled();
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }
    private Component pillName(int index) { return com.example.myvillage.client.alchemy.RecipeText.name(AlchemyRules.get().recipes().get(index)); }

    @Override protected void renderBg(GuiGraphics g, float delta, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, COPPER);
        g.fill(x, y, x + imageWidth, y + imageHeight, INK);
        g.fill(x + 4, y + 21, x + 134, y + 154, PANEL);
        g.fill(x + 136, y + 21, x + imageWidth - 6, y + 158, PANEL);
        g.fill(x + 8, y + 19, x + imageWidth - 8, y + 20, COPPER);
        for (var slot : menu.slots) {
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, COPPER);
            g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFF141F18);
        }
        g.fill(x + 140, y + 62, x + 260, y + 68, 0xFF121D16);
        int progress = menu.duration() <= 0 ? 0 : Math.clamp(120 * menu.progress() / menu.duration(), 0, 120);
        g.fill(x + 140, y + 62, x + 140 + progress, y + 68, GOLD);
        if (blueprint) drawBlueprint(g);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "青铜丹炉阵", 10, 8, PAPER, false);
        g.drawString(font, "炉面朝" + direction(menu.facing()), 270, 8, MUTED, false);
        g.drawString(font, "药材", 154, 26, MUTED, false);
        g.drawString(font, "灵石", 222, 26, MUTED, false);
        g.drawString(font, "成品 · 四档共存", 276, 62, MUTED, false);
        String[] statuses = {"请选择丹方", "待补药材", "待补灵石", "出丹口已满 · 暂停", "正在炼制", "等待启动", "结构未成 · 暂停", "炼完本批后停机"};
        int status = menu.status();
        String statusText = statuses[Math.clamp(status, 0, statuses.length - 1)];
        if (menu.active() && menu.duration() > 0)
            statusText += " " + Math.clamp(100 * menu.progress() / menu.duration(), 0, 100) + "%";
        g.drawString(font, statusText, 140, 77,
                status == 3 || status == 6 ? 0xFFE6A17D : PAPER, false);
        if (menu.qualityCap() >= 1 && menu.qualityCap() <= 4) {
            g.drawString(font, Component.literal("授权资质 " + menu.aptitude() + " · 品质上限 ")
                    .append(PillQuality.name(menu.qualityCap())), 140, 96, GOLD, false);
        } else g.drawString(font, "启动后锁定授权资质与品质上限", 140, 96, GOLD, false);
        int selected = menu.selectedRecipe();
        if (selected >= 0 && selected < AlchemyRules.get().recipes().size()) {
            var recipe = AlchemyRules.get().recipes().get(selected);
            String seconds = java.math.BigDecimal.valueOf(recipe.durationTicks()).divide(java.math.BigDecimal.valueOf(20))
                    .stripTrailingZeros().toPlainString();
            g.drawString(font, "每批：灵石" + recipe.fuelCount() + " · 成丹" + recipe.outputCount() + " · " + seconds + "秒", 140, 108, MUTED, false);
        }
        var mismatch = mismatch();
        if (menu.formed()) {
            g.drawString(font, "结构完整 · 中心通畅", 140, 120, 0xFF9ED0A2, false);
            g.drawString(font, "进药 / 灵石 / 出丹口可接漏斗", 140, 130, MUTED, false);
        } else if (mismatch != null) {
            g.drawString(font, "缺件/堵塞 " + menu.mismatchCount() + "处 · " + roleName(mismatch.role()) + " " + local(mismatch), 140, 119, 0xFFE6A17D, false);
            g.drawString(font, "世界 " + coordinates(world(mismatch)) + " · 详见蓝图", 140, 129, MUTED, false);
        } else {
            g.drawString(font, "结构尚未成型 / 区块未齐", 140, 120, 0xFFE6A17D, false);
        }
        if (!blueprint) {
            if (recipeButtons.stream().noneMatch(b -> b.visible))
                g.drawWordWrap(font, Component.literal("尚未学会丹方。\n手持丹方使用，学会后可在此选择。"), 10, 52, 116, MUTED);

        }
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
    }

    private void drawBlueprint(GuiGraphics g) {
        // Looking down with the front below: local +x is screen-left, and the rear is above.
        var cells = AlchemyArrayRules.get().cells();
        for (int i = 0; i < cells.size(); i++) {
            var cell = cells.get(i);
            if (cell.y() != layer) continue;
            int x = leftPos + 29 + (1 - cell.x()) * 26, y = topPos + 75 + (2 - cell.z()) * 26;
            int border = i == menu.mismatchIndex() ? 0xFFE88D65 : COPPER;
            g.fill(x, y, x + 24, y + 24, border);
            g.fill(x + 1, y + 1, x + 23, y + 23, 0xFF17241D);
            if (cell.role() == AlchemyArrayRules.Role.AIR)
                g.drawCenteredString(font, "空", x + 12, y + 8, MUTED);
            else g.renderItem(roleItem(cell.role()), x + 4, y + 4);
        }
        g.drawString(font, "↓ 正面 / 炉心", leftPos + 29, topPos + 155, GOLD, false);
        g.drawString(font, "材料总计", leftPos + 8, topPos + 168, MUTED, false);
        var roles = materialRoles();
        for (int i = 0; i < roles.length; i++) {
            var role = roles[i];
            int x = leftPos + 8 + i % 2 * 43, y = topPos + 181 + i / 2 * 21;
            g.renderItem(roleItem(role), x, y);
            long count = cells.stream().filter(c -> c.role() == role).count();
            g.drawString(font, "×" + count, x + 17, y + 5, PAPER, false);
        }
    }

    private AlchemyArrayRules.Cell mismatch() {
        int i = menu.mismatchIndex();
        var cells = AlchemyArrayRules.get().cells();
        return i >= 0 && i < cells.size() ? cells.get(i) : null;
    }
    private BlockPos world(AlchemyArrayRules.Cell cell) {
        return AlchemyArrayPattern.worldPos(menu.controllerPos(), menu.facing(), cell);
    }
    private static String coordinates(BlockPos pos) { return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")"; }
    private static String local(AlchemyArrayRules.Cell cell) { return "(" + cell.x() + "," + cell.y() + "," + cell.z() + ")"; }
    private static String direction(Direction direction) {
        return switch (direction) { case NORTH -> "北"; case SOUTH -> "南"; case EAST -> "东"; case WEST -> "西"; default -> "?"; };
    }
    private static AlchemyArrayRules.Role[] materialRoles() {
        return new AlchemyArrayRules.Role[]{AlchemyArrayRules.Role.CASING, AlchemyArrayRules.Role.CONTROLLER,
                AlchemyArrayRules.Role.INPUT, AlchemyArrayRules.Role.OUTPUT, AlchemyArrayRules.Role.FUEL, AlchemyArrayRules.Role.VENT};
    }
    private static String roleName(AlchemyArrayRules.Role role) {
        return switch (role) {
            case AIR -> "保留空气"; case CASING -> "青铜炉壁"; case CONTROLLER -> "炉心控制器";
            case INPUT -> "进药口"; case OUTPUT -> "出丹口"; case FUEL -> "灵石口"; case VENT -> "排气盖";
        };
    }
    private static ItemStack roleItem(AlchemyArrayRules.Role role) {
        return item("myvillage:alchemy_array_" + switch (role) {
            case CONTROLLER -> "controller"; case CASING -> "casing"; case INPUT -> "input";
            case OUTPUT -> "output"; case FUEL -> "fuel"; case VENT -> "vent"; case AIR -> "casing";
        });
    }
    private void blueprintTooltip(GuiGraphics g, int mouseX, int mouseY) {
        for (var cell : AlchemyArrayRules.get().cells()) {
            if (cell.y() != layer) continue;
            int x = leftPos + 29 + (1 - cell.x()) * 26, y = topPos + 75 + (2 - cell.z()) * 26;
            if (inside(mouseX, mouseY, x, y, 24, 24)) {
                g.renderComponentTooltip(font, List.of(Component.literal(roleName(cell.role())),
                        Component.literal("局部 " + local(cell) + " · 世界 " + coordinates(world(cell))),
                        Component.literal(installation(cell))), mouseX, mouseY);
                return;
            }
        }
        var roles = materialRoles();
        for (int i = 0; i < roles.length; i++) {
            if (!inside(mouseX, mouseY, leftPos + 8 + i % 2 * 43, topPos + 181 + i / 2 * 21, 40, 18)) continue;
            var role = roles[i];
            long count = AlchemyArrayRules.get().cells().stream().filter(c -> c.role() == role).count();
            g.renderComponentTooltip(font, List.of(Component.literal(roleName(role) + " ×" + count),
                    Component.literal("完整结构所需总数；控制器为局部坐标原点。")), mouseX, mouseY);
            return;
        }
    }
    private String installation(AlchemyArrayRules.Cell cell) {
        return switch (cell.role()) {
            case AIR -> "中心必须留空；勿放任何方块。";
            case VENT -> "放在顶层中央；铜环位于此处。";
            case INPUT, OUTPUT, FUEL -> "接口朝" + direction(AlchemyArrayPattern.outward(menu.facing(), cell.role())) +
                    (cell.role() == AlchemyArrayRules.Role.OUTPUT ? "；底层左侧，漏斗接在下方。" : "；漏斗接在接口外侧。" );
            case CONTROLLER -> "控制器正面朝" + direction(menu.facing()) + "；此处为局部坐标原点。";
            case CASING -> "青铜炉壁；按层逐块安装。";
        };
    }
    private static boolean inside(int mx, int my, int x, int y, int width, int height) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        updateButtons();
        super.render(g, mouseX, mouseY, delta);
        renderTooltip(g, mouseX, mouseY);
        if (blueprint) blueprintTooltip(g, mouseX, mouseY);
    }
}
