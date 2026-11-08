package com.example.myvillage.aptitude;

import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import net.minecraft.server.level.ServerPlayer;

/** 兵器流派的学习门槛使用同一份点数规则，在开始研读与每次结算时复查。 */
public final class AptitudeLearning {
    private AptitudeLearning() { }
    public static boolean accepts(ServerPlayer player, TechniqueDefinition technique, boolean notify) {
        if (technique == null) return false;
        int minimum = AptitudeRules.get().minimumForGrade(technique.grade());
        for (var element : technique.elements()) {
            int required = Math.max(minimum, technique.requirements().minimumElementAptitude().getOrDefault(element, 0));
            int value = AptitudeRuntime.score(player, element.getPath());
            if (value < required) {
                if (notify) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("学习这门功法需要"
                        + AptitudeRuntime.name(element.getPath()) + " " + required + "，当前 " + value));
                return false;
            }
        }
        String family = technique.school().flatMap(id -> ModCultivationRegistries.school(player.registryAccess(), id))
                .flatMap(school -> school.weaponFamily()).orElse("");
        if (!AptitudeRules.get().weapons().contains(family)) return true;
        int required = AptitudeRules.get().minimumForGrade(technique.grade());
        boolean accepted = AptitudeRuntime.score(player, family) >= required;
        if (!accepted && notify) player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "学习这门功法需要" + AptitudeRuntime.name(family) + "资质 " + required + "，当前 " + AptitudeRuntime.score(player, family)));
        return accepted;
    }
}
