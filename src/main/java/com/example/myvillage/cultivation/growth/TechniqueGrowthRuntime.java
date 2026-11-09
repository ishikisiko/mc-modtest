package com.example.myvillage.cultivation.growth;

import com.example.myvillage.combat.session.CombatSessionManager;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.meditation.InventoryBatchRemoval;
import com.example.myvillage.cultivation.meditation.MeditationManager;
import com.example.myvillage.cultivation.technique.TechniqueRequirementEvaluator;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.Function;

/** Bounded player intentions and committed combat outcomes enter the existing profile service here. */
public final class TechniqueGrowthRuntime {
    private TechniqueGrowthRuntime() { }
    public static Function<ResourceLocation, TechniqueDefinition> lookup(ServerPlayer player) {
        return id -> ModCultivationRegistries.technique(player.registryAccess(), id).orElse(null);
    }
    public static boolean eligible(ServerPlayer player, ResourceLocation id) {
        if (!player.isAlive() || player.isRemoved() || !player.gameMode.isSurvival()) return false;
        var profile = CultivationService.getProfile(player);
        var definition = lookup(player).apply(id);
        return profile.awakened() && profile.learnedTechniques().containsKey(id)
                && TechniqueGrowth.definition(TechniqueGrowthRules.bundled(), id, lookup(player)).isPresent()
                && TechniqueRequirementEvaluator.evaluate(profile, definition, MeditationManager.realmLookup(player),
                        MeditationManager.elementLookup(player)).status() == TechniqueRequirementEvaluator.Status.SATISFIED;
    }
    public static void intent(ServerPlayer player, ResourceLocation id, int action, long revision) {
        if (!eligible(player, id)) { message(player, "当前不能修习这门功法，请检查已学功法和修习条件。"); return; }
        var profile = CultivationService.getProfile(player);
        if (profile.learnedTechniques().get(id).revision() != revision) { message(player, "功法状态已更新，请重新选择。"); return; }
        if (action == 0) { MeditationManager.requestPractice(player, id); return; }
        if (action < 1 || action > 2 || MeditationManager.active(player) || CombatSessionManager.hudBusy(player)
                || player.containerMenu != player.inventoryMenu) {
            message(player, "请结束当前修习、战斗动作或容器操作后再参悟。"); return;
        }
        var rules = TechniqueGrowthRules.bundled();
        var choice = TechniqueGrowth.choose(rules, id, action - 1, revision, profile, lookup(player)).orElse(null);
        if (choice == null) { message(player, "参悟条件不足、状态已更新，或已选择此方向。"); return; }
        if (!InventoryBatchRemoval.payForProfile(player, choice.stoneCost(), choice.replacement())) {
            message(player, "下品灵石不足或提交失败，本次参悟未生效。"); return;
        }
        message(player, "已参悟「" + rules.find(id).orElseThrow().insights().get(action - 1).name() + "」，同门其他参悟方向已替换。");
    }
    public static void hit(ServerPlayer player, ResourceLocation id) {
        var definition=lookup(player).apply(id);var profile=CultivationService.getProfile(player);
        if (!player.isAlive() || player.isRemoved() || !player.gameMode.isSurvival() || !profile.awakened()
                || definition==null || definition.category()!=com.example.myvillage.cultivation.data.TechniqueCategory.ACTIVE
                || !profile.learnedTechniques().containsKey(id)
                || TechniqueRequirementEvaluator.evaluate(profile,definition,MeditationManager.realmLookup(player),
                        MeditationManager.elementLookup(player)).status()!=TechniqueRequirementEvaluator.Status.SATISFIED) return;
        var rules = TechniqueGrowthRules.bundled();
        var spec = rules.find(id).orElse(null);
        int amount=spec==null?rules.defaultActiveHitGain():spec.hitGain();
        if (amount <= 0) return;
        CultivationService.updateProfile(player, current -> com.example.myvillage.cultivation.fate.SchoolInsightRuntime.boost(player,current,
                current.withTechniqueProgress(id,spec==null
                        ? com.example.myvillage.cultivation.fate.SchoolInsight.gain(current.learnedTechniques().get(id),amount,0,Long.MAX_VALUE)
                        : TechniqueGrowth.gain(spec, current.learnedTechniques().get(id), amount))));
    }
    public static int multiplier(ServerPlayer player, ResourceLocation id, TechniqueGrowthRules.Effect effect) {
        if (TechniqueGrowth.definition(TechniqueGrowthRules.bundled(), id, lookup(player)).isEmpty()) return TechniqueGrowthRules.UNIT;
        return TechniqueGrowth.multiplier(TechniqueGrowthRules.bundled(), id, CultivationService.getProfile(player), effect);
    }
    public static int multiplier(net.minecraft.world.entity.LivingEntity actor, ResourceLocation id, TechniqueGrowthRules.Effect effect) {
        return actor instanceof ServerPlayer player ? multiplier(player,id,effect)
                : actor instanceof com.example.myvillage.entity.npc.NpcEntity npc
                    ? com.example.myvillage.entity.npc.NpcSkillRuntime.multiplier(npc,id,effect) : TechniqueGrowthRules.UNIT;
    }
    public static void hit(net.minecraft.world.entity.LivingEntity actor, ResourceLocation id) {
        if (actor instanceof ServerPlayer player) hit(player,id);
        else if(actor instanceof com.example.myvillage.entity.npc.NpcEntity npc)
            com.example.myvillage.entity.npc.NpcSkillRuntime.hit(npc,id);
    }
    public static void message(ServerPlayer player, String text) { player.sendSystemMessage(Component.literal(text)); }
}
