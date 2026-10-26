package com.example.myvillage.client.talisman;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.client.cultivation.panel.CultivationInkTheme;
import com.example.myvillage.talisman.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.lwjgl.glfw.GLFW;

/** 按住仅发送短租期心跳；纸面朱线完全按服务器脉冲绘制。 */
public final class TalismanScreen extends AbstractContainerScreen<TalismanMenu> {
    public static final int HOLD_X = 110, HOLD_Y = 111, HOLD_W = 178, HOLD_H = 22;
    private static final String[] NAMES = {"引雷符", "镇步符", "护身符"};
    private final List<InkButton> recipes = new ArrayList<>();
    private InkButton hold;
    private boolean holding;
    private int heartbeat;
    public TalismanScreen(TalismanMenu m, Inventory i, Component t) {
        super(m, i, t); imageWidth = TalismanMenu.WIDTH; imageHeight = TalismanMenu.HEIGHT;
        inventoryLabelX = 70; inventoryLabelY = 144;
    }
    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
    @Override protected void init() {
        super.init(); recipes.clear(); holding = false;
        for (int i = 0; i < NAMES.length; i++) {
            final int id = i;
            recipes.add(addRenderableWidget(new InkButton(leftPos + 8, topPos + 30 + i * 24, 88, 20, NAMES[i], () -> send(id))));
        }
        hold = addRenderableWidget(new InkButton(leftPos + HOLD_X, topPos + HOLD_Y, HOLD_W, HOLD_H, "按住 · 注灵绘制", () -> {
            holding = true; heartbeat = 0; send(10);
        }));
    }
    private void stop() { if (holding) send(11); holding = false; }
    @Override protected void containerTick() {
        super.containerTick();
        if (!holding) return;
        if (minecraft == null || !minecraft.isWindowActive()
                || GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            stop(); return;
        }
        if (++heartbeat >= TalismanRules.get().draw("heartbeat_ticks")) { heartbeat = 0; send(10); }
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (holding && !hold.isMouseOver(x, y)) stop();
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public void mouseMoved(double x, double y) { if (holding && !hold.isMouseOver(x, y)) stop(); super.mouseMoved(x, y); }
    @Override public boolean mouseReleased(double x, double y, int button) { if (button == 0) stop(); return super.mouseReleased(x, y, button); }
    @Override public void onClose() { stop(); super.onClose(); }
    @Override public void removed() { stop(); super.removed(); }
    /** 完成展示读取同步的真实产物，不把已经清零的批次误显示为未完成。 */
    private ItemStack completedOutput() {
        ItemStack output = menu.getSlot(2).getItem();
        return !menu.active() && !output.isEmpty() && output.getItem() instanceof TalismanItem ? output : ItemStack.EMPTY;
    }
    @Override protected void renderBg(GuiGraphics g, float delta, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x - 3, y - 3, x + imageWidth + 3, y + imageHeight + 3, 0xFF433A30);
        CultivationInkTheme.paper(g, x, y, imageWidth, imageHeight);
        g.fill(x + 103, y + 24, x + 292, y + 105, 0x189C793C);
        for (var slot : menu.slots) {
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF9C793C);
            g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFFDBD2B6);
        }
        int paperX = x + 194, paperY = y + 29;
        g.fill(paperX, paperY, paperX + 45, paperY + 39, 0xFFF6E5B7);
        ItemStack completed = completedOutput();
        ResourceLocation icon;
        if (!completed.isEmpty()) {
            ResourceLocation item = BuiltInRegistries.ITEM.getKey(completed.getItem());
            icon = ResourceLocation.fromNamespaceAndPath(item.getNamespace(), "textures/item/" + item.getPath() + ".png");
        } else icon = ResourceLocation.parse("myvillage:textures/item/talisman_" + new String[]{"lightning", "snare", "ward"}[menu.selected()] + ".png");
        double fraction = !completed.isEmpty() ? 1 : menu.required() <= 0 ? 0 : (double) menu.progress() / menu.required();
        int height = (int) (32 * fraction);
        if (height > 0) g.blit(icon, paperX + 7, paperY + 3, 0, 0, 32, height, 32, 32);
        else g.renderOutline(paperX + 10, paperY + 4, 25, 30, 0x35A94635);
        CultivationInkTheme.bar(g, x + 110, y + 100, 178, fraction, CultivationInkTheme.RED);
    }
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        CultivationInkTheme.title(g, font, "朱纹制符案 · 一纸承灵", 9, 9, 282);
        CultivationInkTheme.text(g, font, "符式", 9, 20, 88, CultivationInkTheme.MUTED);
        CultivationInkTheme.text(g, font, "符纸", 114, 22, 34, CultivationInkTheme.MUTED);
        CultivationInkTheme.text(g, font, "灵墨", 150, 22, 34, CultivationInkTheme.MUTED);
        CultivationInkTheme.text(g, font, "成符", 258, 22, 34, CultivationInkTheme.MUTED);
        CultivationInkTheme.text(g, font, "松开即停", 10, 108, 88, CultivationInkTheme.INK);
        CultivationInkTheme.text(g, font, "关案封存符稿", 10, 121, 88, CultivationInkTheme.MUTED);
        String[] statuses = {"已暂停 · 按住继续", "朱纹渐成 · 持续注灵", "缺少符纸或朱砂灵墨", "输出满或品质不符 · 请取出", "灵力不足 · 已暂停", "尚未觉醒 · 无法注灵", "符稿属于另一位制符者", "符稿异常 · 保留待诊断", "成符 · 松开后可再绘"};
        int status = Math.max(0, Math.min(statuses.length - 1, menu.status()));
        ItemStack completed = completedOutput();
        String statusText = completed.isEmpty() ? statuses[status] : "成符已入槽 · " + switch (status) {
            case 2 -> "缺少符纸或灵墨";
            case 3 -> "输出满或不兼容";
            case 4 -> "灵力不足";
            case 5 -> "尚未觉醒";
            default -> "请取用";
        };
        CultivationInkTheme.text(g, font, statusText, 110, 72, 178, status >= 2 && status <= 7 ? CultivationInkTheme.RED : CultivationInkTheme.INK);
        String quality = menu.active() && menu.quality() >= 1 && menu.quality() <= 4 ? "冻结资质 " + menu.aptitude() + " · " + TalismanQuality.name(menu.quality()).getString() : "一纸一墨 · 灵力逐次支付";
        String readout = completed.isEmpty() ? quality + "  " + menu.progress() + "/" + menu.required()
                : "成品 " + TalismanQuality.name(TalismanQuality.get(completed)).getString() + " ×" + completed.getCount()
                        + " · " + menu.required() + "/" + menu.required();
        CultivationInkTheme.text(g, font, readout, 110, 86, 178, CultivationInkTheme.MUTED);
        CultivationInkTheme.text(g, font, "物品栏", inventoryLabelX, inventoryLabelY, 160, CultivationInkTheme.MUTED);
        CultivationInkTheme.text(g, font, "符式初学即用；品质由独立制符资质决定", 9, 135, 280, CultivationInkTheme.MUTED);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        for (int i = 0; i < recipes.size(); i++) {
            recipes.get(i).active = !menu.active();
            recipes.get(i).setMessage(CultivationInkTheme.book((menu.selected() == i ? "◆ " : "") + NAMES[i]));
        }
        if (hold != null) hold.setMessage(CultivationInkTheme.book(!completedOutput().isEmpty()
                ? holding ? "已成符 · 松开后可再绘" : "按住 · 再绘一张"
                : holding ? "持续注灵中 · 松开暂停" : "按住 · 注灵绘制"));
        super.render(g, mx, my, delta); renderTooltip(g, mx, my);
    }
    private final class InkButton extends Button {
        InkButton(int x, int y, int w, int h, String label, Runnable action) {
            super(x, y, w, h, CultivationInkTheme.book(label), ignored -> action.run(), DEFAULT_NARRATION);
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
            g.fill(getX(), getY(), getX() + width, getY() + height, isHoveredOrFocused() ? 0xFFE1D2AA : 0xFFE8DFC5);
            g.renderOutline(getX(), getY(), width, height, active ? CultivationInkTheme.GOLD : CultivationInkTheme.RULE);
            g.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2,
                    active ? CultivationInkTheme.INK : CultivationInkTheme.FAINT);
        }
    }
    @EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent public static void screens(RegisterMenuScreensEvent e) { e.register(TalismanContent.MENU.get(), TalismanScreen::new); }
    }
}
