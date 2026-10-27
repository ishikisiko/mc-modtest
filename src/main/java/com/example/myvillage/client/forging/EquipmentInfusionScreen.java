package com.example.myvillage.client.forging;

import com.example.myvillage.forging.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import java.util.ArrayList;
import java.util.List;

/** Dedicated ding page: only compatible affinities, with full-height paging controls. */
public final class EquipmentInfusionScreen extends AbstractContainerScreen<EquipmentInfusionMenu> {
    private static final Style BOOK=Style.EMPTY.withFont(id("forge_detail"));
    private static final Style BRUSH=Style.EMPTY.withFont(id("boss_title"));
    private static final int INK=0xFF353B32,MUTED=0xFF756A52,RED=0xFF8C4031,PER_PAGE=6;
    private final List<WorkshopScreen.InkButton> traits=new ArrayList<>();
    private WorkshopScreen.InkButton infuse,next,copper;
    private int page,lastRecipe=-1;
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("myvillage",path);}
    private static Component text(String key,Object...args){return Component.translatable("workshop.myvillage."+key,args).withStyle(BOOK);}
    public EquipmentInfusionScreen(EquipmentInfusionMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title);imageWidth=WorkshopMenu.WIDTH;imageHeight=WorkshopMenu.HEIGHT;
    }
    private void send(int action){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
    private WorkshopScreen.InkButton button(int x,int y,int width,int height,Component label,net.minecraft.client.gui.components.Button.OnPress action,java.util.function.BooleanSupplier selected){
        return addRenderableWidget(new WorkshopScreen.InkButton(leftPos+x,topPos+y,width,height,label,action,selected));
    }
    private List<String> options(){return menu.input().isEmpty()?List.of():menu.recipe().traits();}
    @Override protected void init(){
        super.init();traits.clear();
        for(int i=0;i<PER_PAGE;i++){
            int cell=i;
            traits.add(button(144+(i%2)*82,48+(i/2)*24,76,20,Component.empty(),b->{
                int index=page*PER_PAGE+cell;if(index<options().size())send(WorkshopMenu.TRAIT_BASE+ForgeMark.TRAITS.indexOf(options().get(index)));
            },()->{int index=page*PER_PAGE+cell;return index<options().size()&&menu.trait().equals(options().get(index));}));
        }
        next=button(144,122,158,18,Component.empty(),b->page=(page+1)%pages(),()->false);
        infuse=button(10,122,124,20,text("action.infuse"),b->send(WorkshopMenu.INFUSE),()->false);
        copper=button(10,210,124,18,text("action.copper"),b->send(WorkshopMenu.COPPER),()->false);
        updateButtons();
    }
    private int pages(){return Math.max(1,(options().size()+PER_PAGE-1)/PER_PAGE);}
    private void updateButtons(){
        if(infuse==null)return;
        if(lastRecipe!=menu.recipeIndex()){lastRecipe=menu.recipeIndex();page=0;}
        page=Math.min(page,pages()-1);
        for(int i=0;i<traits.size();i++){
            var button=traits.get(i);int index=page*PER_PAGE+i;button.visible=index<options().size();button.active=!menu.busy();
            if(button.visible){String trait=options().get(index);button.setMessage(Component.translatable("forging.myvillage.trait."+trait).withStyle(BOOK));
                button.setTooltip(Tooltip.create(Component.translatable("screen.myvillage.forging.trait."+trait).withStyle(BOOK)));}
        }
        next.visible=pages()>1;next.setMessage(text("infusion.page",page+1,pages()));
        infuse.active=!menu.busy()&&menu.reason(WorkshopMenu.INFUSE)==0;copper.active=!menu.busy();
        var hint=Component.empty().withStyle(BOOK);
        if(menu.reason(WorkshopMenu.INFUSE)!=0)hint.append(text("status."+menu.reason(WorkshopMenu.INFUSE))).append("\n");
        hint.append(text("costs"));
        if(!menu.input().isEmpty())menu.costs(WorkshopMenu.INFUSE).forEach((key,n)->hint.append("\n").append(ForgingIngredients.description(key)).append(" ×"+n));
        infuse.setTooltip(Tooltip.create(hint));
    }
    @Override protected void renderBg(GuiGraphics g,float delta,int mx,int my){
        int x=leftPos,y=topPos;
        g.blit(id("textures/gui/equipment_infusion_panel.png"),x,y,0,0,imageWidth,imageHeight,imageWidth,imageHeight);
        for(var slot:menu.slots){int sx=x+slot.x,sy=y+slot.y;g.fill(sx-1,sy-1,sx+17,sy+17,0xFF8F7956);g.fill(sx,sy,sx+16,sy+16,slot.index<2?0xFFB6A47D:0xFF353B33);}
        var shown=menu.input().isEmpty()?menu.output():menu.input();
        if(!shown.isEmpty()){g.pose().pushPose();g.pose().translate(x+54,y+43,0);g.pose().scale(2,2,1);g.renderItem(shown,0,0);g.pose().popPose();}
        if(menu.busy()){int w=(int)(124*(1-menu.remaining()/(double)menu.duration(menu.action())));g.fill(x+10,y+143,x+10+w,y+145,RED);}
    }
    private void line(GuiGraphics g,Component text,int x,int y,int width,int color){
        if(font.width(text)>width)text=Component.literal(font.getSplitter().plainHeadByWidth(text.getString(),width-10,BOOK)+"…").withStyle(BOOK);
        g.drawString(font,text,x,y,color,false);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,text("infusion.title").copy().withStyle(BRUSH),12,8,INK,false);
        line(g,text("infusion.equipment"),10,29,124,MUTED);line(g,text("traits"),144,29,158,MUTED);
        line(g,text("input"),26,84,40,MUTED);line(g,text("output"),100,84,34,MUTED);
        line(g,Component.literal("→").withStyle(BOOK),68,102,20,MUTED);
        if(menu.input().isEmpty())line(g,text("infusion.empty_traits"),144,52,158,MUTED);
        line(g,menu.qualityCap()>0?text("cap",ForgingQualityDisplay.name(menu.qualityCap()).getString()):text("cap_locked"),10,153,124,RED);
        Component status=!menu.output().isEmpty()?text("take"):menu.busy()?text("infusion.working"):menu.status()==0?text("infusion.hint"):text("status."+menu.status());
        var lines=font.split(status,124);for(int i=0;i<Math.min(3,lines.size());i++)g.drawString(font,lines.get(i),10,170+i*13,INK,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){updateButtons();super.render(g,mx,my,delta);renderTooltip(g,mx,my);}
}
