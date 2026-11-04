package com.example.myvillage.forging;

import com.example.myvillage.item.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.util.List;

/** Material identity belongs to its registered item; players never submit a free-form material id. */
public final class FoundryMaterialItem extends Item {
    private final String material;
    private final boolean flame;
    public FoundryMaterialItem(Properties properties,String material,boolean flame){
        super(flame?properties.stacksTo(1).component(ModDataComponents.FIRE_SEED.get(),new FireSeed(material,0,"",0)):properties);
        this.material=material;this.flame=flame;
    }
    public String material(){return material;}
    public boolean flame(){return flame;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        super.appendHoverText(stack,context,lines,flag);
        if(flame){
            var seed=stack.get(ModDataComponents.FIRE_SEED.get());
            if(seed!=null)lines.add(Component.literal("成熟度："+seed.maturity()+" / "+FoundryRules.get().fire().max_maturity()).withStyle(ChatFormatting.AQUA));
            lines.add(Component.literal("装入玄枢多方块炼器鼎作为辅助火。可在鼎中温养。").withStyle(ChatFormatting.GRAY));
            if(material.equals("earth"))lines.add(Component.literal("地脉灵火须在原泉眼附近使用。").withStyle(ChatFormatting.GRAY));
        }else{
            var h=FoundryRules.get().handle(material);
            lines.add(Component.literal("伤害 ×"+WorkshopEquipment.amount(h.attack())+"　速度 ×"+WorkshopEquipment.amount(h.speed())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal("耐久 ×"+WorkshopEquipment.amount(h.durability())+"　耗灵 ×"+WorkshopEquipment.amount(h.qi())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal(effectDescription(h.effect())).withStyle(ChatFormatting.AQUA));
        }
    }
    public static String effectDescription(String effect){return switch(effect){
        case "light"->"手持时小幅提高移动速度。";
        case "nurture"->"脱离战斗后，消耗少量灵力温养耐久。";
        case "followup"->"重击命中后，强化下一次轻击。";
        case "steady"->"手持时提高抗击退能力。";
        case "spell_followup"->"灵力技能命中后，强化下一次普通攻击。";
        default->"普通柄材，无额外特性。";
    };}
}
