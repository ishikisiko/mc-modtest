package com.example.myvillage.item;

import com.example.myvillage.alchemy.*;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.meditation.*;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Consumes one dose only after the server has accepted a breakthrough or a better foundation. */
public final class FoundationPillItem extends Item {
    private final boolean repair;
    public FoundationPillItem(Properties props,boolean repair){super(props.stacksTo(16));this.repair=repair;}
    public boolean allows(int q){return q>=(repair?2:1)&&q<=3;}
    private String rejection(ServerPlayer player,int q){
        if(!allows(q))return "此丹品质不适用";
        if(!player.isAlive()||player.isSpectator()||!CultivationService.getProfile(player).awakened())return "需存活并已觉醒灵根";
        if(MeditationManager.active(player))return "请先结束当前修炼或突破";
        var state=MedicineRuntime.state(player);
        if(state.cooldownTicks()>0||state.effectTicks()>0)return "尚未达到下次服药时间";
        var p=CultivationService.getProfile(player);var rules=FoundationRules.get();
        if(repair)return rules.canRepair(p,q)?null:"需已筑基，且补基丹品质高于当前根基；无境界上限";
        return rules.source(p)?null:"筑基丹仅用于炼气九层突破筑基";
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(player instanceof ServerPlayer server){String why=rejection(server,PillQuality.get(stack));if(why!=null){server.displayClientMessage(Component.literal(why),true);return InteractionResultHolder.fail(stack);}}
        player.startUsingItem(hand);return InteractionResultHolder.consume(stack);
    }
    @Override public int getUseDuration(ItemStack stack,LivingEntity entity){return AlchemyRules.get().useTicks();}
    @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.EAT;}
    @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity entity){
        if(!(entity instanceof ServerPlayer p)||stack.isEmpty())return stack;
        int q=PillQuality.get(stack);String why=rejection(p,q);
        if(why!=null){p.displayClientMessage(Component.literal(why),true);return stack;}
        p.stopUsingItem();
        boolean accepted;
        if(repair){accepted=CultivationService.replaceProfile(p,CultivationService.getProfile(p).withFoundationQuality(q)).success();}
        else {accepted=MeditationManager.requestFoundationAdvancement(p,q).reason()==MeditationStopReason.ADVANCEMENT_ACCEPTED;}
        if(accepted){if(!p.getAbilities().instabuild)stack.shrink(1);MedicineRuntime.beginFoundationCooldown(p);
            p.displayClientMessage(Component.literal(repair?"根基已补全至":"服丹开始筑基：").append(PillQuality.name(q)),false);}
        return stack;
    }
    @Override public Component getName(ItemStack stack){int q=Math.min(3,PillQuality.get(stack));return PillQuality.name(q).copy().append(super.getName(stack)).withColor(QualityDisplay.color(q));}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        int q=Math.min(3,PillQuality.get(stack));var rules=FoundationRules.get();
        lines.add(Component.literal(repair?"补全至对应根基，无境界上限；同档或降档不消耗":"筑基成功率："+rules.successPercent(q)+"%；服丹后保持静止突破"));
        lines.add(Component.literal("根基加成：基础生命、攻击、灵力上限 +"+rules.bonusPercent(q)+"%（仅补差额）"));
        lines.add(Component.literal(repair?"只补根基，不增加修为，不回满生命或灵力":"失败消耗丹药，修为保留、稳定度减半；中断不返还丹药"));
        lines.add(Component.literal("共用服药间隔："+AlchemyRules.get().sharedCooldownTicks()/20+" 秒"));
    }
}
