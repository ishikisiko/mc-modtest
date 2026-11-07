package com.example.myvillage.cultivation.fate;

import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.CultivationStats;
import com.example.myvillage.stats.runtime.ModStatsAttributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** 新灵体只添加境界/根基对应的贡献，不放大装备和其他天命，也不被旧百分比二次放大。 */
public final class SpiritBodyAttributes {
    public static final ResourceLocation HEALTH=ResourceLocation.parse("myvillage:spirit_body_health");
    public static final ResourceLocation DEFENSE=ResourceLocation.parse("myvillage:spirit_body_defense");
    private SpiritBodyAttributes() { }
    public static void refresh(ServerPlayer player) {
        var body=CultivationStats.find(CultivationService.getProfile(player));
        addition(player.getAttribute(Attributes.MAX_HEALTH),HEALTH,body.map(b->b.health()*FateService.effect(player,"body_health_bonus")).orElse(0.0));
        addition(player.getAttribute(ModStatsAttributes.BODY_DEFENSE),DEFENSE,body.map(b->b.defense()*FateService.effect(player,"body_defense_bonus")).orElse(0.0));
    }
    public static void addition(AttributeInstance attribute,ResourceLocation id,double finalContribution) {
        if(attribute==null)return;
        double baseScale=1,totalScale=1;
        for(var modifier:attribute.getModifiers()) {
            if(modifier.operation()==AttributeModifier.Operation.ADD_MULTIPLIED_BASE)baseScale+=modifier.amount();
            if(modifier.operation()==AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)totalScale*=1+modifier.amount();
        }
        double amount=isolatedAddition(finalContribution,baseScale,totalScale);
        var previous=attribute.getModifier(id);
        if(previous!=null&&previous.amount()==amount&&previous.operation()==AttributeModifier.Operation.ADD_VALUE)return;
        attribute.removeModifier(id);
        if(amount!=0)attribute.addPermanentModifier(new AttributeModifier(id,amount,AttributeModifier.Operation.ADD_VALUE));
    }
    public static double isolatedAddition(double contribution,double baseScale,double totalScale) {
        if(!Double.isFinite(contribution)||contribution<0||!Double.isFinite(baseScale)||!Double.isFinite(totalScale))
            throw new IllegalArgumentException("非法独立灵体贡献");
        return baseScale>0&&totalScale>0?contribution/baseScale/totalScale:0;
    }
}
