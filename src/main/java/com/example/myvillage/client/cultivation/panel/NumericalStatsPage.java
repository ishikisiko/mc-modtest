package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.client.cultivation.ClientCultivationIntentSender;
import com.example.myvillage.cultivation.CultivationStats;
import com.example.myvillage.cultivation.network.MeditationIntentAction;
import com.example.myvillage.stats.runtime.ModStatsAttributes;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** 数值页只读服务器同步的档案和原版属性；按钮只提交有界的 MOCK 品质意图。 */
public final class NumericalStatsPage extends PanelPage {
    private final List<PanelButton> buttons = new ArrayList<>();
    @Override public int init(int x, int y, int width, int height, Consumer<AbstractWidget> widgets) {
        buttons.clear();
        var actions = new MeditationIntentAction[]{MeditationIntentAction.MOCK_FOUNDATION_HEAVEN,
                MeditationIntentAction.MOCK_GOLDEN_LOW, MeditationIntentAction.MOCK_GOLDEN_MIDDLE, MeditationIntentAction.MOCK_GOLDEN_HIGH};
        String[] labels = {"MOCK天道筑基", "MOCK下品金丹", "MOCK中品金丹", "MOCK上品金丹"};
        int dock = 48, cell = (width - 4) / 2;
        for (int i = 0; i < actions.length; i++) {
            var action = actions[i];
            var button = new PanelButton(PanelButton.Style.ACTION, PanelTheme.JADE, x + (i % 2) * (cell + 4),
                    y + height - dock + (i / 2) * 24, cell, 20, Component.literal(labels[i]),
                    pressed -> ClientCultivationIntentSender.send(action));
            button.setInk(true);
            button.setTooltip(Tooltip.create(Component.literal(labels[i] + "：修为与稳定度达标后立即结算，当前不运行真实雷劫。")));
            buttons.add(button); widgets.accept(button);
        }
        return dock;
    }
    @Override public void setVisible(boolean visible) { buttons.forEach(b -> b.visible = visible); }
    @Override public void refresh(PanelContext c, boolean visible) {
        var p = c.profile();
        boolean ready = p != null && c.progressFull() && c.currentStage().flatMap(s -> s.advancement())
                .map(a -> p.stability() >= a.requiredStability()).orElse(false)
                && c.meditation() != null && !c.meditation().state().active()
                && !com.example.myvillage.client.tribulation.TribulationClient.active()
                && !com.example.myvillage.client.tribulation.TribulationClient.recovering();
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).visible = visible;
            buttons.get(i).active = visible && ready && com.example.myvillage.stats.CultivationStatsRules.bundled().mockTribulation()
                    && p.stageId().toString().equals(i == 0 ? "myvillage:qi_refining_9" : "myvillage:foundation_late");
        }
    }
    @Override public int render(GuiGraphics g, PanelContext c, int x, int y, int width, int viewportHeight) {
        int height = 139;
        InkPanelTheme.card(g, c.font(), x, y, width, height, Component.literal("基础属性与实际面板"));
        var player = c.minecraft() == null ? null : c.minecraft().player;
        var base = CultivationStats.find(c.profile()).orElse(null);
        String[][] rows = {
                {"生命", player == null ? "等待属性同步" : number(player.getHealth()) + " / " + number(player.getMaxHealth())},
                {"自身基础攻击", base == null ? "数值未配置" : number(base.attack())},
                {"当前攻击（含装备）", player == null ? "等待属性同步" : number(player.getAttributeValue(Attributes.ATTACK_DAMAGE))},
                {"肉身防御", player == null ? "等待属性同步" : number(player.getAttributeValue(ModStatsAttributes.BODY_DEFENSE))},
                {"装备护甲 / 韧性", player == null ? "等待属性同步" : number(player.getAttributeValue(Attributes.ARMOR)) + " / " + number(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS))},
                {"灵力", c.spiritualPowerValue()},
                {"筑基来源", CultivationStats.foundationName(c.profile().foundationQuality())},
                {"金丹品质", CultivationStats.goldenName(c.profile().goldenCoreQuality())}
        };
        int cursor = y + InkPanelTheme.CARD_TITLE_HEIGHT + 2;
        for (var row : rows) {
            InkPanelTheme.pair(g, c.font(), row[0], row[1], x + 8, cursor, width - 16, InkPanelTheme.TEXT);
            cursor += 14;
        }
        return height;
    }
    private static String number(double value) { return String.format(Locale.ROOT, value == Math.rint(value) ? "%.0f" : "%.2f", value); }
}
