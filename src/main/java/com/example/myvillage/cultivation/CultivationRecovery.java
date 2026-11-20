package com.example.myvillage.cultivation;

import com.example.myvillage.cultivation.CultivationMemory.Stage;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmDefinition;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CultivationRecovery {
    private CultivationRecovery() { }

    public static Stage stage(CultivationProfile profile) {
        return new Stage(profile.realmId(), profile.stageId());
    }

    public static boolean atLeastNascent(RegistryAccess access, CultivationProfile profile) {
        var registry = access.registry(ModCultivationRegistries.REALMS).orElse(null);
        if (registry == null) return false;
        var realm = registry.get(profile.realmId());
        var nascent = registry.get(ModCultivationRegistries.NASCENT_SOUL_REALM_ID);
        return realm != null && nascent != null && realm.sortOrder() >= nascent.sortOrder();
    }

    public static boolean canRestore(RegistryAccess access, CultivationProfile profile) {
        var registry = access.registry(ModCultivationRegistries.REALMS).orElse(null);
        return registry != null && profile.memory().highest().filter(highest -> compare(registry, stage(profile), highest) < 0).isPresent();
    }

    public static boolean canReform(RegistryAccess access, CultivationProfile profile) {
        return profile.memory().nascentQuality() > 0 && !profile.memory().intact() && atLeastNascent(access, profile);
    }

    public static CultivationProfile remember(RegistryAccess access, CultivationProfile profile) {
        var registry = access.registry(ModCultivationRegistries.REALMS).orElse(null);
        if (registry == null || order(registry, stage(profile)) < 0) return profile;
        var memory = profile.memory();
        var best = memory.highest().orElse(null);
        if (best == null || compare(registry, stage(profile), best) > 0) memory = memory.reach(stage(profile));
        if (memory.nascentQuality() == 0 && atLeastNascent(access, profile)) memory = memory.form(1);
        return memory.equals(profile.memory()) ? profile : profile.withMemory(memory);
    }

    /** A drop never erases awakening or the record of previously earned qualities. */
    public static CultivationProfile death(RegistryAccess access, CultivationProfile profile) {
        if (!profile.awakened()) return profile;
        profile = remember(access, profile);
        if (atLeastNascent(access, profile) && profile.memory().intact()) {
            return profile.withMemory(profile.memory().shatter());
        }
        var registry = access.registry(ModCultivationRegistries.REALMS).orElse(null);
        if (registry == null) return profile;
        List<Stage> stages = new ArrayList<>();
        registry.entrySet().stream().sorted(Comparator.comparingInt(entry -> entry.getValue().sortOrder()))
                .forEach(entry -> entry.getValue().stages().stream()
                        .filter(stage -> stage.cultivationCap().isPresent() || stage.consolidationCap().isPresent())
                        .forEach(stage -> stages.add(new Stage(entry.getKey().location(), stage.id()))));
        return drop(profile, stages);
    }

    static CultivationProfile drop(CultivationProfile profile, List<Stage> stages) {
        int index = stages.indexOf(stage(profile));
        if (index < 0) return profile;
        Stage target = stages.get(Math.max(0, index - 2));
        return profile.withRealmAndStage(target.realm(), target.id()).withCultivationProgress(0).withStability(0);
    }

    public static boolean covers(RegistryAccess access, CultivationProfile profile, Stage target) {
        var registry = access.registry(ModCultivationRegistries.REALMS).orElse(null);
        if (registry == null) return false;
        long required = order(registry, target);
        return required >= 0 && profile.memory().highest().filter(highest -> order(registry, highest) >= required).isPresent();
    }

    private static int compare(Registry<RealmDefinition> registry, Stage left, Stage right) {
        long a = order(registry, left);
        long b = order(registry, right);
        return a < 0 || b < 0 ? 0 : Long.compare(a, b);
    }

    private static long order(Registry<RealmDefinition> registry, Stage stage) {
        RealmDefinition realm = registry.get(stage.realm());
        if (realm == null) return -1;
        return realm.stages().stream().filter(value -> value.id().equals(stage.id())).findFirst()
                .map(value -> ((long) realm.sortOrder() << 32) | value.sortOrder()).orElse(-1L);
    }
}
