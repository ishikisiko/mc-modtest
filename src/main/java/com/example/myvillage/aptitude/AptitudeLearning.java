package com.example.myvillage.aptitude;

import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import java.util.Optional;
import java.util.function.ToIntFunction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** 兵器流派的学习门槛使用同一份点数规则，在开始研读与每次结算时复查。 */
public final class AptitudeLearning {
    private AptitudeLearning() { }

    public record Shortfall(String id, int required, int actual, boolean weapon) {
        public Component message() {
            return Component.literal("学习这门功法需要" + AptitudeRuntime.name(id) + (weapon ? "资质 " : " ")
                    + required + "，当前 " + actual);
        }
    }

    public static boolean accepts(ServerPlayer player, TechniqueDefinition technique, boolean notify) {
        if (technique == null) return false;
        var shortfall = shortfall(player.registryAccess(), technique, id -> AptitudeRuntime.score(player, id));
        if (notify) shortfall.ifPresent(missing -> player.sendSystemMessage(missing.message()));
        return shortfall.isEmpty();
    }

    public static Optional<Shortfall> shortfall(RegistryAccess registries, TechniqueDefinition technique,
                                                ToIntFunction<String> score) {
        int minimum = AptitudeRules.get().minimumForGrade(technique.grade());
        for (var element : technique.elements()) {
            int required = Math.max(minimum, technique.requirements().minimumElementAptitude().getOrDefault(element, 0));
            int actual = score.applyAsInt(element.getPath());
            if (actual < required) return Optional.of(new Shortfall(element.getPath(), required, actual, false));
        }
        String family = technique.school().flatMap(id -> ModCultivationRegistries.school(registries, id))
                .flatMap(school -> school.weaponFamily()).orElse("");
        if (!AptitudeRules.get().weapons().contains(family)) return Optional.empty();
        int actual = score.applyAsInt(family);
        return actual < minimum ? Optional.of(new Shortfall(family, minimum, actual, true)) : Optional.empty();
    }
}
