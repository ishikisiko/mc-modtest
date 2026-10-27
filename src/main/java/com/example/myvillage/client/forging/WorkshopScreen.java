package com.example.myvillage.client.forging;

import com.example.myvillage.forging.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Native paper-and-iron workbench: object first, compact instructions, no timing score. */
public final class WorkshopScreen extends AbstractContainerScreen<WorkshopMenu> {
    private static final String KEY="workshop.myvillage.";
    private static final Style BOOK=Style.EMPTY.withFont(id("forge_detail"));
    private static final Style BRUSH=Style.EMPTY.withFont(id("boss_title"));
    private static final int INK=0xFF353B32,MUTED=0xFF756A52,CINNABAR=0xFF8C4031;
    private final List<InkButton> recipeButtons=new ArrayList<>(),optionButtons=new ArrayList<>();
    private InkButton heat,primary,repair;
    private int recipePage,lastRecipe=-1;
    private InkButton recipeNext;
    private static final int RECIPES_PER_PAGE=6;
    private int clientTicks,lastHit=-1,impactTick=-100;
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("myvillage",path);}
    private static Component text(String key,Object...args){return Component.translatable(KEY+key,args).withStyle(BOOK);}
    public WorkshopScreen(WorkshopMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=WorkshopMenu.WIDTH;imageHeight=WorkshopMenu.HEIGHT;}
    private void send(int action){if(minecraft!=null&&minecraft.gameMode!=null){minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}}
    private Component recipeName(int i){return BuiltInRegistries.ITEM.get(ResourceLocation.parse(ForgingRules.get().recipe(WorkshopRules.get().crafts().get(i).recipe()).orElseThrow().output())).getDescription().copy().withStyle(BOOK);}
    private InkButton button(int x,int y,int width,int height,Component label,Button.OnPress action,BooleanSupplier selected){
        var button=new InkButton(leftPos+x,topPos+y,width,height,label,action,selected);return addRenderableWidget(button);
    }
    @Override protected void init(){
        super.init();recipeButtons.clear();optionButtons.clear();
        for(int i=0;i<WorkshopRules.get().crafts().size();i++){
            int index=i;var b=button(10,42+(i%RECIPES_PER_PAGE)*14,71,13,recipeName(i),v->send(WorkshopMenu.RECIPE_BASE+index),()->menu.recipeIndex()==index);
            b.setTooltip(Tooltip.create(recipeName(i)));recipeButtons.add(b);
        }
        recipeNext=button(10,129,71,13,Component.empty(),b->recipePage=(recipePage+1)%((recipeButtons.size()+RECIPES_PER_PAGE-1)/RECIPES_PER_PAGE),()->false);
        heat=button(90,126,48,18,text("action.heat"),b->send(WorkshopMenu.HEAT),()->false);
        primary=button(218,120,88,20,text("action.start"),b->send(primaryAction()),()->false);
        for(int i=0;i<3;i++){
            int index=i;optionButtons.add(button(218,49+i*23,88,20,Component.empty(),b->selectOption(index),()->optionSelected(index)));
        }
        repair=button(10,210,124,18,text("action.repair"),b->send(WorkshopMenu.REPAIR),()->false);
        updateButtons();
    }
    private int primaryAction(){
        if(menu.input().isEmpty())return WorkshopMenu.START;
        var p=menu.piece();if(p!=null)return switch(p.stage()){case 0,1->WorkshopMenu.QUENCH;case 2->WorkshopMenu.TEMPER;case 3->WorkshopMenu.GRIND;default->WorkshopMenu.ASSEMBLE;};
        return WorkshopMenu.STRIKE;
    }
    private void selectOption(int index){
        if(menu.piece()!=null||menu.input().isEmpty())send(WorkshopMenu.TREATMENT_BASE+index);
        else send(WorkshopMenu.PATH_BASE+index+1);
    }
    private boolean optionSelected(int index){
        if(menu.piece()!=null||menu.input().isEmpty())return menu.treatment()==index;
        return menu.direction()==index+1;
    }
    private void updateButtons(){
        if(heat==null)return;
        if(lastRecipe!=menu.recipeIndex()){lastRecipe=menu.recipeIndex();recipePage=lastRecipe/RECIPES_PER_PAGE;}
        recipeNext.visible=recipeButtons.size()>RECIPES_PER_PAGE;
        recipeNext.setMessage(text("recipe_page",recipePage+1,(recipeButtons.size()+RECIPES_PER_PAGE-1)/RECIPES_PER_PAGE));
        for(int i=0;i<recipeButtons.size();i++){
            var b=recipeButtons.get(i);b.visible=i/RECIPES_PER_PAGE==recipePage;b.active=!menu.busy()&&menu.input().isEmpty();
            b.setTooltip(Tooltip.create(menu.knows(i)?recipeName(i):text("status.8")));
        }
        heat.active=!menu.busy()&&menu.reason(WorkshopMenu.HEAT)==0;
        repair.active=!menu.busy()&&menu.reason(WorkshopMenu.REPAIR)==0;
        primary.setMessage(actionLabel(primaryAction()));
        primary.active=!menu.busy()&&menu.reason(primaryAction())==0;
        primary.visible=primaryAction()!=WorkshopMenu.STRIKE;
        var p=menu.piece();boolean treatment=p!=null||menu.input().isEmpty();
        for(int i=0;i<optionButtons.size();i++){
            var b=optionButtons.get(i);
            b.setMessage(treatment?text("treatment."+i):text("path."+(i+1)));
            b.active=!menu.busy()&&(treatment?menu.craft().treatment(i)&&(p==null||p.stage()<2):menu.trainingDirection(i+1));
            b.setTooltip(Tooltip.create(treatment?text("treatment_hint."+i):text("path_hint."+(i+1))));
        }
        heat.setTooltip(costTooltip(WorkshopMenu.HEAT));primary.setTooltip(costTooltip(primaryAction()));repair.setTooltip(costTooltip(WorkshopMenu.REPAIR));
    }
    private Component actionLabel(int action){
        String name=WorkshopMenu.actionName(action);
        return text("action."+(menu.craft().armor()&&(action==WorkshopMenu.ASSEMBLE||action==WorkshopMenu.GRIND)?"armor.":"")+name);
    }
    private Tooltip costTooltip(int action){
        var label=Component.empty().withStyle(BOOK).append(actionLabel(action)).append("\n");
        if(menu.reason(action)!=0)label.append(text("status."+menu.reason(action))).append("\n");
        if(action==WorkshopMenu.STRIKE){
            label.append(text("hold")).append("\n");
            if(menu.piece()==null&&!menu.input().isEmpty()&&menu.temperRecord().strikes()<menu.craft().max_strikes()){
                var rules=WorkshopRules.get();
                label.append(text("per_strike",rules.experienceCost(menu.temperRecord().strikes()))).append("\n");
                String gain=menu.direction()==2?"durability":menu.direction()==3?"qi":menu.craft().weapon()?"attack":"speed";
                double amount=switch(gain){case "durability"->rules.durability_per_strike()*100;case "qi"->rules.qi_discount_per_strike()*100;case "attack"->rules.attack_per_strike();default->rules.speed_per_strike()*100;};
                label.append(text("strike_gain."+gain,WorkshopEquipment.amount(amount))).append("\n");
            }
        }
        label.append(text("costs")).append("\n");var costs=menu.costs(action);
        if(action==WorkshopMenu.QUENCH)label.append(text("trough.cost"));
        else if(costs.isEmpty())label.append(text("no_cost"));
        else costs.forEach((key,n)->label.append(ForgingIngredients.description(key)).append(" ×"+n+"\n"));
        return Tooltip.create(label);
    }
    @Override protected void containerTick(){
        super.containerTick();clientTicks++;
        if(menu.hitSequence()!=lastHit){if(lastHit>=0)impactTick=clientTicks;lastHit=menu.hitSequence();}
    }
    @Override protected void renderBg(GuiGraphics g,float delta,int mx,int my){
        int x=leftPos,y=topPos;
        g.blit(id("textures/gui/workshop_panel.png"),x,y,0,0,imageWidth,imageHeight,imageWidth,imageHeight);
        // Sparse brush rules; no dashboard cards or glowing borders.
        g.fill(x+83,y+19,x+305,y+20,0xFF887956);g.fill(x+85,y+20,x+267,y+21,0x66887956);
        g.fill(x+62,y+7,x+76,y+21,CINNABAR);g.renderOutline(x+64,y+9,10,10,0xFFCEB88B);
        for(var slot:menu.slots){int sx=x+slot.x,sy=y+slot.y;
            g.fill(sx-1,sy-1,sx+17,sy+17,slot.index<2?0xFF8F7956:0xFF7D7962);
            g.fill(sx,sy,sx+16,sy+16,slot.index<2?0xFFB6A47D:0xFF353B33);
            g.fill(sx,sy,sx+16,sy+1,0x55312B21);
        }
        drawForge(g,x,y,delta);
        int heatWidth=68*Math.min(menu.heat(),WorkshopRules.get().heat_ticks())/WorkshopRules.get().heat_ticks();
        g.fill(x+115,y+118,x+183,y+120,0xFF978767);g.fill(x+115,y+118,x+115+heatWidth,y+120,0xFFAA5733);
        if(menu.busy()){
            int w=(int)(88*(1-menu.remaining()/(double)menu.duration(menu.action())));
            g.fill(x+218,y+141,x+306,y+143,0xFFB3A17C);g.fill(x+218,y+141,x+218+w,y+143,CINNABAR);
        }
    }
    private void drawForge(GuiGraphics g,int x,int y,float delta){
        g.pose().pushPose();g.pose().translate(x+111,y+70,0);g.pose().scale(2.25F,1.1F,1);g.blit(id("textures/gui/workshop_anvil.png"),0,0,0,0,32,32,32,32);g.pose().popPose();
        ItemStack shown=menu.input();
        if(shown.isEmpty())shown=menu.output();
        if(shown.isEmpty())shown=WorkshopItems.blank(new Workpiece(menu.recipe().id(),0,0,0));
        float shake=clientTicks-impactTick<2?1:0;
        if(menu.heat()>0)g.fill(x+127,y+75,x+169,y+79,0x50A34B26);
        g.pose().pushPose();g.pose().translate(x+128+shake,y+54,0);g.pose().scale(2.5F,2.5F,1);g.renderItem(shown,0,0);g.pose().popPose();
        if(clientTicks-impactTick<4){int t=clientTicks-impactTick;
            for(int i=0;i<4;i++){int sx=x+146+(i-2)*(3+t*2),sy=y+76-Math.abs(i-2)*(2+t);g.fill(sx,sy,sx+1,sy+2,0xFFE4AC58);}}
        if(menu.action()==WorkshopMenu.QUENCH){int t=menu.duration(menu.action())-menu.remaining();for(int i=0;i<4;i++)g.fill(x+132+i*7,y+66-(t+i)%12,x+135+i*7,y+72-(t+i)%12,0x668F9789);}
    }
    private void line(GuiGraphics g,Component label,int x,int y,int width,int color){g.drawString(font,fit(label,width),x,y,color,false);}
    private Component fit(Component label,int width){
        if(font.width(label)<=width)return label;
        return Component.literal(font.getSplitter().plainHeadByWidth(label.getString(),Math.max(0,width-font.width(Component.literal("…").withStyle(BOOK))),label.getStyle())+"…").withStyle(label.getStyle());
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,text("title").copy().withStyle(BRUSH),12,8,INK,false);
        g.drawString(font,text("seal"),65,10,0xFFE6D4AD,false);
        line(g,text("patterns"),12,29,65,MUTED);
        line(g,text("manual.panel_hint"),142,132,62,MUTED);
        var piece=menu.piece();var record=menu.temperRecord();
        Component stage=!menu.output().isEmpty()?text("take"):piece!=null?text("stage."+piece.stage()):menu.input().isEmpty()?text("heat.none"):text("stage.5");
        line(g,stage,94,29,110,INK);
        line(g,text(piece!=null||menu.input().isEmpty()?"treatment":"directions"),218,31,88,MUTED);
        line(g,text(menu.input().isEmpty()?"heat.none":menu.heat()>0?"heat.hot":"heat.cold"),115,106,68,CINNABAR);
        line(g,text("input"),91,94,26,MUTED);line(g,text("output"),184,94,28,MUTED);
        int status=!menu.output().isEmpty()?1:menu.status();
        var statusLines=font.split(text("status."+status),123);
        for(int i=0;i<Math.min(2,statusLines.size());i++)g.drawString(font,statusLines.get(i),10,153+i*13,INK,false);
        if(piece!=null)line(g,text("formed",piece.blows(),WorkshopRules.get().forming_strikes()),10,190,124,CINNABAR);
        else if(!menu.input().isEmpty()){
            line(g,text("experience",record.experience(),menu.available()),10,184,124,INK);
            line(g,text("tempering",record.strikes(),menu.craft().max_strikes()),10,198,124,CINNABAR);
        }else line(g,menu.knows(menu.recipeIndex())?recipeName(menu.recipeIndex()):text("status.8"),10,190,124,MUTED);

    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        updateButtons();super.render(g,mx,my,delta);renderTooltip(g,mx,my);
        if(mx>=leftPos+10&&mx<leftPos+135&&my>=topPos+151&&my<topPos+182)
            g.renderTooltip(font,text("status."+(!menu.output().isEmpty()?1:menu.status())),mx,my);
        if(mx>=leftPos+10&&mx<leftPos+135&&my>=topPos+182&&my<topPos+210&&!menu.input().isEmpty()&&menu.piece()==null){
            var lines=new ArrayList<Component>();WorkshopEquipment.tooltip(menu.input(),lines);lines.add(text(menu.craft().armor()?"armor_earned_hint":"earned_hint"));g.renderComponentTooltip(font,lines,mx,my);
        }
    }
    static final class InkButton extends Button {
        private final BooleanSupplier selected;
        InkButton(int x,int y,int width,int height,Component text,OnPress press,BooleanSupplier selected){super(x,y,width,height,text,press,DEFAULT_NARRATION);this.selected=selected;}
        @Override public void playDownSound(SoundManager manager){} // The committed tool contact supplies the sound.
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){
            int x=getX(),y=getY();boolean current=selected.getAsBoolean();
            int background=current?0xFFBFA57B:isHoveredOrFocused()&&active?0xFFD9C399:0xFFD2BF96;
            g.fill(x,y,x+width,y+height,background);g.fill(x,y+height-1,x+width,y+height,active?0xFF98805C:0xFFB19E79);
            if(current)g.fill(x,y+2,x+2,y+height-2,CINNABAR);
            var font=Minecraft.getInstance().font;var message=getMessage();
            if(font.width(message)>width-7)message=Component.literal(font.getSplitter().plainHeadByWidth(message.getString(),width-13,BOOK)+"…").withStyle(BOOK);
            g.drawString(font,message,x+(width-font.width(message))/2,y+(height-10)/2,active?INK:0xFF827458,false);
        }
    }
}
