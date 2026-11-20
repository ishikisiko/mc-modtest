package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.client.cultivation.ClientCultivationIntentSender;
import com.example.myvillage.client.cultivation.ClientCultivationEvents;
import com.example.myvillage.cultivation.CultivationRecovery;
import com.example.myvillage.cultivation.CultivationStats;
import com.example.myvillage.cultivation.nascent.NascentRules;
import com.example.myvillage.cultivation.nascent.NascentTrial;
import com.example.myvillage.cultivation.network.MeditationIntentAction;
import com.example.myvillage.stats.runtime.ModStatsAttributes;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** 数值页只读服务器同步的档案和原版属性；按钮只提交有界的 正式引劫意图。 */
public final class NumericalStatsPage extends PanelPage {
    private final List<PanelButton> buttons = new ArrayList<>();
    private PanelButton refine, duel, reform, restore;
    @Override public int init(int x, int y, int width, int height, Consumer<AbstractWidget> widgets) {
        buttons.clear();
        var actions = new MeditationIntentAction[]{MeditationIntentAction.TRIAL_FOUNDATION_HEAVEN,
                MeditationIntentAction.TRIAL_GOLDEN_LOW, MeditationIntentAction.TRIAL_GOLDEN_MIDDLE, MeditationIntentAction.TRIAL_GOLDEN_HIGH};
        String[] labels = {"天道筑基引劫", "下品金丹引劫", "中品金丹引劫", "上品金丹引劫"};
        int dock = 48, cell = (width - 4) / 2;
        for (int i = 0; i < actions.length; i++) {
            var action = actions[i];
            var button = new PanelButton(PanelButton.Style.ACTION, PanelTheme.JADE, x + (i % 2) * (cell + 4),
                    y + height - dock + (i / 2) * 24, cell, 20, Component.literal(labels[i]),
                    pressed -> { ClientCultivationIntentSender.send(action); net.minecraft.client.Minecraft.getInstance().setScreen(null); });
            button.setInk(true);
            button.setTooltip(Tooltip.create(Component.literal(labels[i] + "：修为与稳定度达标后开始正式雷劫，承受所需次数命中并存活才结算。")));
            buttons.add(button); widgets.accept(button);
        }
        refine = button(widgets, x, y + height - dock, cell, "中品 · 炼手中妖丹",
                "主手持金丹境及以上、不凡及以上的妖丹。炼成时扣妖丹和半槽灵力，随后静坐凝婴。",
                MeditationIntentAction.NASCENT_REFINE);
        duel = button(widgets, x + cell + 4, y + height - dock, cell, "上品 · 引心魔",
                "单人斩心魔后自行碎丹。战败或中断损失四分之一修为与一半稳定度。真实死亡按掉境规则结算。",
                MeditationIntentAction.NASCENT_DUEL);
        reform = button(widgets, x, y + height - 24, cell, "重凝元婴",
                "独立运功重凝，完成后恢复一次护境。可以先服续婴丹加速。",
                MeditationIntentAction.NASCENT_REFORM);
        restore = button(widgets, x + cell + 4, y + height - 24, cell, "恢复原境界",
                "补满这一段修为，即可按记录恢复境界和品级。", MeditationIntentAction.START_BREAKTHROUGH);
        return dock;
    }

    private static PanelButton button(Consumer<AbstractWidget> widgets, int x, int y, int width, String label,
            String tooltip, MeditationIntentAction action) {
        var button = new PanelButton(PanelButton.Style.ACTION, PanelTheme.JADE, x, y, width, 20,
                Component.literal(label), pressed -> {
                    ClientCultivationIntentSender.send(action);
                    net.minecraft.client.Minecraft.getInstance().setScreen(null);
                });
        button.setInk(true);
        button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        widgets.accept(button);
        return button;
    }

    @Override public void setVisible(boolean visible) {
        buttons.forEach(b -> b.visible = visible);
        for (var b : List.of(refine, duel, reform, restore)) b.visible = visible;
    }
    @Override public void refresh(PanelContext c, boolean visible) {
        var p = c.profile();
        boolean restoring = p != null && CultivationRecovery.canRestore(c.registries(), p);
        boolean choosingNascent = p != null && NascentTrial.canChoose(c.registries(), p);
        var phase = ClientCultivationEvents.nascent().phase();
        boolean ready = p != null && c.progressFull() && c.currentStage().flatMap(s -> s.advancement())
                .map(a -> p.stability() >= a.requiredStability()).orElse(false)
                && phase == NascentTrial.Phase.INACTIVE
                && c.meditation() != null && !c.meditation().state().active()
                && !com.example.myvillage.client.tribulation.TribulationClient.active()
                && !com.example.myvillage.client.tribulation.TribulationClient.recovering();
        for (int i = 0; i < buttons.size(); i++) {
            boolean applicable = p != null && p.stageId().toString().equals(i == 0 ? "yuanxian:qi_refining_9" : "yuanxian:foundation_late");
            buttons.get(i).visible = visible && applicable && !restoring;
            buttons.get(i).active = buttons.get(i).visible && ready;
        }
        refine.visible = duel.visible = visible && choosingNascent && phase == NascentTrial.Phase.INACTIVE;
        refine.active = duel.active = refine.visible && ready;
        reform.visible = visible && p != null && CultivationRecovery.canReform(c.registries(), p);
        reform.active = reform.visible && c.meditation() != null && !c.meditation().state().active();
        restore.visible = visible && (restoring || phase == NascentTrial.Phase.READY);
        restore.active = restore.visible && (phase == NascentTrial.Phase.READY
                || c.progressFull() && c.meditation() != null && !c.meditation().state().active());
        restore.setMessage(Component.literal(phase == NascentTrial.Phase.READY ? "自行碎丹" : "恢复原境界"));
    }
    @Override public int render(GuiGraphics g, PanelContext c, int x, int y, int width, int viewportHeight) {
        var profile = c.profile();
        var player = c.minecraft() == null ? null : c.minecraft().player;
        var base = CultivationStats.find(profile).orElse(null);
        var rows = new ArrayList<String[]>();
        if (NascentTrial.canChoose(c.registries(), profile)) {
            rows.add(new String[]{"下品结婴", "手持凝婴丹长按右键"});
        } else if (CultivationRecovery.canRestore(c.registries(), profile)) {
            rows.add(new String[]{"复境", "补满当前修为后按 N"});
        }
        Collections.addAll(rows,
                new String[]{"生命", player == null ? "等待属性同步" : number(player.getHealth()) + " / " + number(player.getMaxHealth())},
                new String[]{"自身基础攻击", base == null ? "数值未配置" : number(base.attack())},
                new String[]{"当前攻击（含装备）", player == null ? "等待属性同步" : number(player.getAttributeValue(Attributes.ATTACK_DAMAGE))},
                new String[]{"肉身防御", player == null ? "等待属性同步" : number(player.getAttributeValue(ModStatsAttributes.BODY_DEFENSE))},
                new String[]{"固定破防", player == null ? "等待属性同步" : number(player.getAttributeValue(ModStatsAttributes.FLAT_DEFENSE_PENETRATION))},
                new String[]{"装备护甲 / 韧性", player == null ? "等待属性同步" : number(player.getAttributeValue(Attributes.ARMOR)) + " / " + number(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS))},
                new String[]{"灵力", c.spiritualPowerValue()},
                new String[]{"筑基来源", CultivationStats.foundationName(profile.foundationQuality())},
                new String[]{"金丹品质", CultivationStats.goldenName(profile.goldenCoreQuality())});
        var memory = profile.memory();
        if (memory.nascentQuality() > 0) {
            rows.add(new String[]{"元婴品级", NascentTrial.qualityName(memory.nascentQuality())});
            rows.add(new String[]{"元婴护境", memory.intact() ? "完整，可护境一次"
                    : "已碎，重凝 " + memory.reformation() + " / " + NascentRules.get().value("reformation_target")});
        }
        int height = InkPanelTheme.CARD_TITLE_HEIGHT + 5 + rows.size() * 14;
        InkPanelTheme.card(g, c.font(), x, y, width, height, Component.literal("基础属性与实际面板"));
        int cursor = y + InkPanelTheme.CARD_TITLE_HEIGHT + 2;
        for (var row : rows) {
            InkPanelTheme.pair(g, c.font(), row[0], row[1], x + 8, cursor, width - 16, InkPanelTheme.TEXT);
            cursor += 14;
        }
        return height;
    }
    private static String number(double value) { return String.format(Locale.ROOT, value == Math.rint(value) ? "%.0f" : "%.2f", value); }
}
