package com.example.myvillage.cultivation.fate;

import com.example.myvillage.cultivation.CultivationAttachments;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.time.CultivationTimeRuntime;
import com.example.myvillage.stats.runtime.ModStatsAttributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** 由个人寿元的有效 tick 驱动；无离线追补、世界时间或客户端计数入口。 */
public final class EverlastingRuntime {
    private static final String FATE = "myvillage:everlasting_embryo";
    public static final ResourceLocation HEALTH = ResourceLocation.parse("myvillage:everlasting_health");
    public static final ResourceLocation DEFENSE = ResourceLocation.parse("myvillage:everlasting_defense");
    public static final ResourceLocation ATTACK = ResourceLocation.parse("myvillage:everlasting_attack");
    private EverlastingRuntime() { }
    public static EverlastingState state(ServerPlayer player) { return player.getData(CultivationAttachments.EVERLASTING.get()); }
    public static EverlastingState state(net.minecraft.world.entity.LivingEntity actor) {
        if(actor instanceof ServerPlayer player)return state(player);
        if(!(actor instanceof com.example.myvillage.entity.npc.NpcEntity npc))return EverlastingState.EMPTY;
        var extras=com.example.myvillage.entity.npc.NpcFateRuntime.extras(npc);
        return extras.has("everlasting")?EverlastingState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,extras.get("everlasting")).getOrThrow():EverlastingState.EMPTY;
    }
    public static boolean active(ServerPlayer player) {
        var fates = FateService.state(player);
        return fates.confirmed() && fates.selected().contains(FATE) && FateService.effect(player, "everlasting") > 0;
    }
    /** 在本 tick 的寿元扣除之前调用，最后一 tick 仍可正常成长。 */
    public static void eligibleTick(ServerPlayer player) {
        if (!CultivationTimeRuntime.isEligibleForPersonalLifespan(player) || !active(player)) return;
        var rules = EverlastingRules.get();
        var annual = rules.at(CultivationService.getProfile(player).realmId().toString());
        if (annual.empty()) return;
        var time = CultivationTimeRuntime.statusFor(player);
        if (!time.realmResolved() || time.exhausted()) return;
        var previous = state(player);
        var next = previous.advance(annual, time.ticksPerYear(), 1);
        CultivationService.replaceEverlasting(player, next, !previous.sameCredits(next));
    }
    public static void refresh(ServerPlayer player) {
        var value = state(player); double step = active(player) ? EverlastingRules.get().creditStep() : 0;
        SpiritBodyAttributes.addition(player.getAttribute(Attributes.MAX_HEALTH), HEALTH, value.health().points(step));
        SpiritBodyAttributes.addition(player.getAttribute(ModStatsAttributes.BODY_DEFENSE), DEFENSE, value.defense().points(step));
        SpiritBodyAttributes.addition(player.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK, value.attack().points(step));
    }
    public static void tickNpc(com.example.myvillage.entity.npc.NpcEntity npc) {
        if(FateService.effect(npc,"everlasting")<=0)return;
        var realm=com.example.myvillage.stats.runtime.StatsRuntime.assignedRealm(npc).orElseThrow();
        var annual=EverlastingRules.get().at(realm.realmId());if(annual.empty())return;
        var scale=com.example.myvillage.cultivation.time.CultivationServerConfig.scale();
        var next=state(npc).advance(annual,scale.ticksPerYear(),1);
        var extras=com.example.myvillage.entity.npc.NpcFateRuntime.extras(npc);
        extras.add("everlasting",EverlastingState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,next).getOrThrow());
        com.example.myvillage.entity.npc.NpcFateRuntime.extras(npc,extras);
    }
    public static void refresh(net.minecraft.world.entity.LivingEntity actor) {
        if(actor instanceof ServerPlayer player){refresh(player);return;}
        if(!FateActors.supported(actor))return;
        var value=state(actor);double step=FateService.effect(actor,"everlasting")>0?EverlastingRules.get().creditStep():0;
        SpiritBodyAttributes.addition(actor.getAttribute(Attributes.MAX_HEALTH),HEALTH,value.health().points(step));
        SpiritBodyAttributes.addition(actor.getAttribute(ModStatsAttributes.BODY_DEFENSE),DEFENSE,value.defense().points(step));
        SpiritBodyAttributes.addition(actor.getAttribute(Attributes.ATTACK_DAMAGE),ATTACK,value.attack().points(step));
    }
}
