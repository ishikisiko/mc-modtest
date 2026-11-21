package com.example.myvillage.cultivation.technique;

import com.example.myvillage.cultivation.data.HeartMethod.Defense;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.util.List;

/** The manual and the learned technique show the same fixed defense bonuses. */
public final class DefenseHeartText {
    private DefenseHeartText() { }

    public static void appendBonuses(List<Component> lines, Defense defense) {
        var attributes = defense.attributes();
        bonus(lines, "常驻生命上限", defense.maxHealth(), " 点");
        bonus(lines, "常驻肉身防御", defense.defense(), " 点");
        bonus(lines, "常驻护甲", attributes.armor(), " 点");
        bonus(lines, "常驻护甲韧性", attributes.armorToughness(), " 点");
        bonus(lines, "常驻物理抗性", percentage(attributes.physicalResistance()), " 个百分点");
        bonus(lines, "常驻击退抗性", percentage(attributes.knockbackResistance()), " 个百分点");
        defense.shield().ifPresent(shield -> {
            String threshold = number(percentage(shield.healthThreshold())) + "%";
            lines.add(Component.literal("敌方命中使生命从高于 " + threshold + " 降至 " + threshold + " 或以下时"));
            lines.add(Component.literal("存活则获得 " + number(shield.amount()) + " 点护盾，持续 "
                    + number(shield.durationTicks() / 20.0) + " 秒，冷却 "
                    + number(shield.cooldownTicks() / 20.0) + " 秒"));
        });
    }

    private static void bonus(List<Component> lines, String label, double value, String unit) {
        if (value > 0) {
            lines.add(Component.literal(label + " +" + number(value) + unit));
        }
    }

    private static double percentage(double fraction) {
        return BigDecimal.valueOf(fraction).movePointRight(2).doubleValue();
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
