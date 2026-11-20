package com.example.myvillage.client.commerce;

import com.example.myvillage.sim.runtime.economy.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** 琳琅阁与江湖榜的中文界面，显示服务器冻结的报价和奖励。 */
public final class EconomyScreen extends AbstractContainerScreen<EconomyMenu> {
    private final List<Button> actions = new ArrayList<>();
    private Button previous, next, claim;
    private int page, pending = -1;
    private static final int ROWS = 3;
    public EconomyScreen(EconomyMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); imageWidth = 340; imageHeight = 230; }
    @Override protected void init() {
        super.init(); actions.clear();
        for (int i = 0; i < ROWS; i++) { final int row = i; actions.add(addRenderableWidget(Button.builder(Component.empty(), b -> send(row)).bounds(leftPos + 267, topPos + 54 + i * 43, 62, 20).build())); }
        previous = addRenderableWidget(Button.builder(Component.literal("上一页"), b -> { page--; update(); }).bounds(leftPos + 170, topPos + 192, 55, 18).build());
        next = addRenderableWidget(Button.builder(Component.literal("下一页"), b -> { page++; update(); }).bounds(leftPos + 275, topPos + 192, 55, 18).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose()).bounds(leftPos + 10, topPos + 192, 55, 18).build());
        if (menu.auction()) {
            claim = addRenderableWidget(Button.builder(Component.literal("领取拍卖款物"), b -> sendIndex(menu.rows.size() - 1))
                    .bounds(leftPos + 71, topPos + 192, 89, 18).build());
            claim.setTooltip(Tooltip.create(Component.literal("先领取退回的灵石，再逐件领取成交拍品。")));
        }
        update();
    }
    private void send(int row) {
        sendIndex(page * ROWS + row);
    }
    private void sendIndex(int index) {
        if (index >= menu.rows.size() || pending == menu.sequence()) return;
        int quote = menu.auction() ? menu.auctionQuote(index).next() : 0;
        pending = menu.sequence();
        PacketDistributor.sendToServer(new EconomyIntentPayload(menu.containerId, menu.token, pending, index, quote));
        update();
    }
    private int visibleRows() { return menu.rows.size() - (menu.auction() ? 1 : 0); }
    private void update() {
        page = Math.clamp(page, 0, Math.max(0, (visibleRows() - 1) / ROWS));
        for (int i = 0; i < actions.size(); i++) {
            int index = page * ROWS + i; var button = actions.get(i); button.visible = index < visibleRows(); if (!button.visible) continue;
            var row = menu.rows.get(index); boolean expedition = !menu.linlang && row.id().startsWith("expedition/");
            if (menu.auction()) {
                var quote = menu.auctionQuote(index);
                button.setMessage(Component.literal(row.id().equals(TianhengAuctions.OPEN) ? "开场" : "出价"));
                button.active = pending != menu.sequence() && (quote.state() == TianhengAuctions.STARTABLE
                        || quote.state() == TianhengAuctions.LIVE && quote.leader() != TianhengAuctions.YOU && quote.next() > 0);
            } else {
                button.setMessage(Component.literal(menu.townStore() ? "购买" : expedition ? "办理远征"
                        : menu.linlang ? "兑换" : menu.activeTask() ? "交付事务" : "接取事务"));
                button.active = pending != menu.sequence() && (menu.townStore() || expedition
                        || (menu.linlang ? menu.contribution() >= row.cost() : menu.activeTask() ? menu.activeRow() == index : menu.canAccept()));
            }
            button.setTooltip(Tooltip.create(Component.literal(row.detail())));
        }
        if (previous != null) { previous.active = page > 0; next.active = (page + 1) * ROWS < visibleRows(); }
        if (claim != null) claim.active = pending != menu.sequence()
                && menu.auctionQuote(menu.rows.size() - 1).state() == TianhengAuctions.CLAIMABLE;
    }
    @Override protected void renderBg(GuiGraphics g, float delta, int mx, int my) {
        int x = leftPos, y = topPos; g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, 0xFF8C7850); g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF242C28);
        for (int i = 0; i < ROWS; i++) {
            int index = page * ROWS + i; if (index >= visibleRows()) break; var row = menu.rows.get(index); int yy = y + 47 + i * 43;
            g.fill(x + 7, yy - 2, x + imageWidth - 7, yy + 38, 0xFF333F37);
            if (!row.item().equals("minecraft:air")) { var stack = new ItemStack(com.example.myvillage.sim.runtime.commerce.CommerceInventory.item(row.item())); if (row.quality() > 1) stack.set(com.example.myvillage.item.ModDataComponents.PILL_QUALITY.get(), row.quality()); g.renderItem(stack, x + 11, yy + 3); }
            String name = row.name() + (menu.linlang && row.count() > 1 ? " ×" + row.count() : "");
            g.drawString(font, font.plainSubstrByWidth(name, 228), x + 32, yy + 1, 0xFFE2D3A8, false);
            int split = row.detail().indexOf(" 奖励：");
            String detail = split < 0 ? row.detail() : row.detail().substring(0, split);
            if (!menu.linlang && menu.activeRow() == index) detail = "进度 " + menu.progress() + "/" + row.count() + "；" + detail;
            g.drawString(font, font.plainSubstrByWidth(detail, 228), x + 32, yy + 13, 0xFFCCDEBF, false);
            if (split >= 0) g.drawString(font, font.plainSubstrByWidth(row.detail().substring(split + 1), 228), x + 32, yy + 25, 0xFFE2D3A8, false);
            if (menu.townStore()) g.drawString(font, "价格 " + row.cost() + " 下品灵石", x + 32, yy + 25, 0xFFE2D3A8, false);
            if (menu.auction()) g.drawString(font, font.plainSubstrByWidth(auctionLine(index), 228),
                    x + 32, yy + 25, 0xFFE2D3A8, false);
        }
    }
    private String auctionLine(int index) {
        var quote = menu.auctionQuote(index);
        String leader = switch (quote.leader()) {
            case TianhengAuctions.YOU -> "你";
            case TianhengAuctions.OTHER_PLAYER -> "另一位修士";
            default -> {
                int npc = quote.leader() - TianhengAuctions.NPC;
                yield npc >= 0 && npc < menu.bidders.size() ? menu.bidders.get(npc) : "";
            }
        };
        return switch (quote.state()) {
            case TianhengAuctions.PREVIEW -> "起拍 " + quote.next() + "，" + quote.seconds() + " 秒后开拍";
            case TianhengAuctions.LIVE -> (leader.isEmpty() ? "" : leader + "出价 " + quote.price() + "，")
                    + (quote.next() > 0 ? "下一口 " + quote.next() + "，" : "") + "剩 " + quote.seconds() + " 秒";
            case TianhengAuctions.SOLD -> leader + "以 " + quote.price() + " 成交";
            case TianhengAuctions.PASSED -> "流拍";
            case TianhengAuctions.STARTABLE -> "会场按顺序竞拍三件物品";
            default -> "";
        };
    }
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, font.plainSubstrByWidth(title.getString(), 320), 10, 9, 0xFFE2D3A8, false);
        String heading = menu.auction() ? (menu.bidders.isEmpty() ? "每座城每日开一场" : "受邀竞拍者 " + String.join("、", menu.bidders))
                : menu.townStore() ? "秘籍按州城及境界补货" : "宗门贡献：" + menu.contribution() + "    江湖声望：" + menu.reputation();
        g.drawString(font, font.plainSubstrByWidth(heading, 320), 10, 27, 0xFFCCDEBF, false);
        g.drawString(font, Component.translatable("screen.yuanxian.economy.status." + menu.status()), 10, 183, 0xFFE2D3A8, false);
        g.drawString(font, (page + 1) + "/" + Math.max(1, (visibleRows() + ROWS - 1) / ROWS), 237, 197, 0xFFCCDEBF, false);
        String note = menu.auction() ? "以灵石竞价。被超价后，可在此领回先前的出价。"
                : menu.townStore() ? "标价以下品灵石为单位，各品级可折算。"
                : menu.linlang ? "本宗弟子可用贡献兑换，部分商品要求内门身份。" : "日常事务每日一项；远征另行接取，通关后回榜领奖。";
        g.drawString(font, note, 10, 216, 0xFFA5B59C, false);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) { update(); super.render(g, mx, my, delta); renderTooltip(g, mx, my); }
}
