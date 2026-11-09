package com.example.myvillage.combat.runtime;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.technique.TechniqueRequirementEvaluator;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.entity.npc.NpcSkillRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** 主体适配。玩家档案仍由 CultivationService 修改，普通测试 NPC 保存自己的配置。 */
public final class CombatCaster {
    private CombatCaster() { }
    public static ServerLevel level(LivingEntity actor) { return (ServerLevel) actor.level(); }
    public static int slot(LivingEntity actor) { return actor instanceof ServerPlayer p ? p.getInventory().selected : -1; }
    public static String name(LivingEntity actor) { return actor.getName().getString(); }
    public static boolean inventoryFree(LivingEntity actor) { return !(actor instanceof ServerPlayer p) || p.containerMenu == p.inventoryMenu; }
    public static void message(LivingEntity actor, Component text) { if (actor instanceof ServerPlayer p) p.displayClientMessage(text, true); }
    public static void message(LivingEntity actor, Component text, boolean overlay) { if (actor instanceof ServerPlayer p) p.displayClientMessage(text, overlay); }
    public static boolean active(LivingEntity actor) {
        return actor instanceof ServerPlayer p ? p.isAlive() && !p.isRemoved() && !p.isSpectator()
                && com.example.myvillage.combat.CombatService.getMode(p) == com.example.myvillage.combat.CombatMode.CULTIVATION
                : actor instanceof NpcEntity npc && com.example.myvillage.entity.npc.NpcCombatRuntime.canAct(npc);
    }
    public static CultivationProfile profile(LivingEntity actor) {
        if (actor instanceof ServerPlayer p) return CultivationService.getProfile(p);
        if (actor instanceof NpcEntity npc) return NpcSkillRuntime.profile(npc);
        throw new IllegalArgumentException("此身体没有修炼档案");
    }
    public static boolean eligible(LivingEntity actor, TechniqueDefinition technique) {
        return actor instanceof NpcEntity npc
                ? TechniqueRequirementEvaluator.evaluate(profile(npc), technique, id -> NpcSkillRuntime.realm(npc, id)).satisfied()
                : TechniqueRequirementEvaluator.evaluate(actor.registryAccess(), profile(actor), technique).satisfied();
    }
    public static long power(LivingEntity actor) {
        return actor instanceof ServerPlayer p ? CultivationService.getProfile(p).currentSpiritualPower()
                : com.example.myvillage.entity.npc.NpcCultivationRuntime.current((NpcEntity) actor);
    }
    public static long capacity(LivingEntity actor) {
        return actor instanceof ServerPlayer p ? CultivationService.spiritualPowerCap(p)
                : com.example.myvillage.entity.npc.NpcCultivationRuntime.cap((NpcEntity) actor);
    }
    public static boolean spend(LivingEntity actor, long amount) {
        return com.example.myvillage.cultivation.qi.SpiritualPowerRuntime.spend(actor, amount);
    }
    public static boolean recover(LivingEntity actor, long amount) {
        return com.example.myvillage.cultivation.fate.FateActors.recoverPower(actor, amount);
    }
}
