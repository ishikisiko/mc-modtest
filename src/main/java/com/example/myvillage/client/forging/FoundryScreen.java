package com.example.myvillage.client.forging;

import com.example.myvillage.forging.*;
import com.example.myvillage.item.ModDataComponents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Paper/bronze panels show actual requirements and preview the same server-owned assembly plan. */
public final class FoundryScreen extends AbstractContainerScreen<FoundryMenu> {
    private static final int INK=0xFF353B32,PAPER=0xFFE1D6B9,GOLD=0xFF9B7B49,JADE=0xFF397C6F,MUTED=0xFF766B55;
    private Button first,second,cancel;
    public FoundryScreen(FoundryMenu menu,Inventory player,Component title){super(menu,player,title);imageWidth=FoundryMenu.WIDTH;imageHeight=FoundryMenu.HEIGHT;}
    private void send(int action){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
    private Button button(int x,int y,int width,String label,int action){return addRenderableWidget(new WorkshopScreen.InkButton(leftPos+x,topPos+y,width,18,Component.literal(label),b->send(action),()->false));}
    @Override protected void init(){
        super.init();first=second=cancel=null;
        switch(menu.kind()){
            case PREPARATION->{
                addRenderableWidget(new WorkshopScreen.InkButton(leftPos+10,topPos+28,22,18,Component.literal("‹"),b->send(20+Math.floorMod(menu.recipeIndex()-1,WorkshopRules.get().crafts().size())),()->false));
                addRenderableWidget(new WorkshopScreen.InkButton(leftPos+123,topPos+28,22,18,Component.literal("›"),b->send(20+(menu.recipeIndex()+1)%WorkshopRules.get().crafts().size()),()->false));
                first=button(153,28,57,"只起坯",0);second=button(217,28,72,"自动制作",1);
                button(10,57,72,"赤铜灌灵",2);cancel=button(217,57,72,"撤炉退料",3);
            }
            case ASSEMBLY->{first=button(118,117,78,"组装 / 换柄",0);cancel=button(205,117,78,"取消",1);}
            case KILN->{}
        }
        updateButtons();
    }
    private void updateButtons(){
        if(first!=null)first.active=!menu.active()&&menu.output().isEmpty()&&(menu.kind()!=FoundryMenu.Kind.PREPARATION||menu.input().isEmpty()&&menu.knows(menu.recipeIndex()));
        if(second!=null)second.active=!menu.active()&&menu.output().isEmpty()&&menu.knows(menu.recipeIndex());
        if(cancel!=null)cancel.active=menu.active();
        if(menu.kind()==FoundryMenu.Kind.PREPARATION){
            var craft=WorkshopRules.get().crafts().get(menu.recipeIndex());var recipe=ForgingRules.get().recipe(craft.recipe()).orElseThrow();
            first.setTooltip(costs(WorkshopRules.get().blankCosts(recipe),"得到器坯，可取出后进行手工加工。"));
            var piece=WorkshopItems.valid(menu.input())?menu.input().get(ModDataComponents.WORKPIECE.get()):new Workpiece(craft.recipe(),0,0,craft.armor()?2:0);
            second.setTooltip(costs(PreparationBlockEntity.automaticCosts(piece,menu.input().isEmpty()),"自动完成标准器物，使用普通木柄；用水后返还空桶。已有手工贡献保留。"));
        }else if(menu.kind()==FoundryMenu.Kind.ASSEMBLY&&first!=null){
            var plan=AssemblyPlan.prepare(menu.input(),menu.material(),1);first.active=first.active&&plan.valid();
            first.setTooltip(Tooltip.create(Component.literal(plan.valid()?"保留品质、锻造增益、炼灵、附魔和损伤比例。换柄后从右槽返还旧柄。":assemblyStatus(plan.reason()))));
        }
    }
    private Tooltip costs(Map<String,Integer> costs,String detail){
        var text=Component.literal(detail+"\n从背包消耗：\n");costs.forEach((key,n)->text.append(ForgingIngredients.description(key)).append(" ×"+n+"\n"));return Tooltip.create(text);
    }
    @Override protected void containerTick(){super.containerTick();updateButtons();}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;g.fill(x,y,x+imageWidth,y+imageHeight,INK);g.fill(x+2,y+2,x+imageWidth-2,y+imageHeight-2,PAPER);g.fill(x+9,y+21,x+291,y+22,GOLD);
        for(int[] p:new int[][]{{32,92},{96,92},{202,92},{258,92}})slot(g,x+p[0],y+p[1]);
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)slot(g,x+69+col*18,y+151+row*18);
        for(int col=0;col<9;col++)slot(g,x+69+col*18,y+209);
        if(menu.kind()==FoundryMenu.Kind.KILN){
            g.fill(x+32,y+61,x+278,y+69,0xFFB4A58C);
            g.fill(x+32,y+61,x+32+246*Math.min(menu.heat(),FoundryRules.get().heat().capacity())/FoundryRules.get().heat().capacity(),y+69,0xFFB66F3F);
        }else{
            g.fill(x+132,y+96,x+186,y+102,0xFFB4A58C);int width=menu.duration()>0?Math.min(54,54*menu.progress()/menu.duration()):0;g.fill(x+132,y+96,x+132+width,y+102,JADE);
        }
        if(menu.kind()==FoundryMenu.Kind.ASSEMBLY&&menu.output().isEmpty()){
            var plan=AssemblyPlan.prepare(menu.input(),menu.material(),1);if(plan.valid())g.renderItem(plan.result(),x+202,y+92);
        }
    }
    private static void slot(GuiGraphics g,int x,int y){g.fill(x-1,y-1,x+17,y+17,0xFF99896D);g.fill(x,y,x+16,y+16,0xFFC9BFA5);}
    private static String assemblyStatus(int status){return switch(status){case 1->"放入精磨器坯，或需要换柄的成品。";case 2->"器坯尚未完成精磨。";case 3->"放入柄材；护甲装配使用皮革。";case 4->"当前已经使用这种柄材。";case 5->"先取走成品和返还物。";case 6->"正在组装。";case 7->"装配状态异常，请取消并取回原件。";case 8->"组装完成。";default->"放入器坯与柄材，确认预览后组装。";};}
    private String status(){return switch(menu.kind()){
        case ASSEMBLY->assemblyStatus(menu.status());
        case KILN->switch(menu.status()){case 1->"正在加热。";case 2->"火候已足，窑中继续保温。";case 3->"回火完成，可以取出精磨。";case 4->"燃料不足，器坯仍留在窑中。";default->"放入器坯与燃料，自动加热。";};
        case PREPARATION->switch(menu.status()){case 1->"先取走器坯。";case 2->"先取走产物和返还物。";case 3->"请放入有效器坯。";case 4->"尚未学会这张器谱。";case 5->"背包中的材料不足。";case 6->"起坯完成，可取出手工加工。";case 7->"只有开炉者可以撤炉。";case 8->"正在自动制作，关闭界面后仍会继续。";case 9->"炉次异常，请撤炉取回材料。";case 10->"器物制作完成。";default->"只起坯供手工加工，或自动完成标准成品。";};
    };}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,title,10,8,INK,false);
        if(menu.kind()==FoundryMenu.Kind.PREPARATION){
            var craft=WorkshopRules.get().crafts().get(menu.recipeIndex());var recipe=ForgingRules.get().recipe(craft.recipe()).orElseThrow();
            var name=BuiltInRegistries.ITEM.get(ResourceLocation.parse(recipe.output())).getDescription();
            g.drawCenteredString(font,name,77,33,menu.knows(menu.recipeIndex())?INK:0xFF9B4A36);
            g.drawString(font,"器坯",29,80,INK,false);g.drawString(font,"标准成品",190,80,INK,false);g.drawString(font,"返还物",250,80,INK,false);
        }else if(menu.kind()==FoundryMenu.Kind.KILN){
            g.drawString(font,"火候 "+menu.heat()+" / "+FoundryRules.get().heat().capacity(),32,42,INK,false);
            g.drawString(font,"器坯",29,80,INK,false);g.drawString(font,"燃料",93,80,INK,false);
            g.drawString(font,"右键取放；持锤可与相邻锻台转移。",16,119,MUTED,false);
        }else{
            g.drawString(font,"器坯 / 成品",15,80,INK,false);g.drawString(font,"柄 / 皮革",85,80,INK,false);g.drawString(font,"预览 / 成品",186,80,INK,false);g.drawString(font,"旧柄",255,80,INK,false);
            var plan=AssemblyPlan.prepare(menu.input(),menu.material(),1);
            if(plan.valid()){
                var before=baseline();var after=plan.result();var craft=WorkshopEquipment.craft(after).orElseThrow();
                g.drawString(font,craft.armor()?"护甲装配":FoundryEquipment.handle(after).name(),12,29,JADE,false);
                g.drawString(font,"伤害 "+number(attack(before))+" → "+number(attack(after))+"　速度 ×"+number(FoundryEquipment.speed(before))+" → ×"+number(FoundryEquipment.speed(after)),12,43,INK,false);
                g.drawString(font,"耐久 "+before.getMaxDamage()+" → "+after.getMaxDamage()+"　耗灵 "+ForgedEquipment.qiCost(before,100)+" → "+ForgedEquipment.qiCost(after,100),12,56,INK,false);
                if(craft.armor())g.drawString(font,"护甲防护随手工成形与辅助火改善。",12,68,MUTED,false);
                else g.drawString(font,"悬停右侧预览器物，查看柄材效果与完整属性。",12,68,MUTED,false);
            }
        }
        var lines=font.split(Component.literal(status()),278);if(!lines.isEmpty())g.drawString(font,lines.getFirst(),10,138,INK);
        g.drawString(font,"熟练度",11,158,MUTED,false);g.drawString(font,menu.skill()+" 级",16,171,JADE,false);
        g.drawString(font,"炼器资质",8,193,MUTED,false);g.drawString(font,Integer.toString(menu.aptitude()),12,207,INK,false);
    }
    private static String number(double value){return java.math.BigDecimal.valueOf(value).setScale(2,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();}
    private static double attack(ItemStack stack){return 1+stack.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers().stream()
            .filter(e->e.attribute().equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)&&e.modifier().operation()==net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE).mapToDouble(e->e.modifier().amount()).sum();}
    private ItemStack baseline(){
        if(!WorkshopItems.valid(menu.input()))return menu.input();var piece=menu.input().get(ModDataComponents.WORKPIECE.get());
        return PreparationBlockEntity.standard(WorkshopItems.blank(new Workpiece(piece.recipe(),0,0,WorkshopRules.get().craft(piece.recipe()).armor()?2:0)),1);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        super.render(g,mx,my,partial);renderTooltip(g,mx,my);
        if(menu.kind()==FoundryMenu.Kind.ASSEMBLY&&menu.output().isEmpty()&&isHovering(202,92,16,16,mx,my)){
            var plan=AssemblyPlan.prepare(menu.input(),menu.material(),1);
            if(plan.valid()){
                if(WorkshopItems.valid(menu.input()))g.renderTooltip(font,Component.literal("成败与品质在开始装配时按炼器资质随机判定。"),mx,my);
                else g.renderTooltip(font,plan.result(),mx,my);
            }
        }
    }
}
