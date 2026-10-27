package com.example.myvillage.client.forging;

import com.example.myvillage.client.cultivation.ClientCultivationState;
import com.example.myvillage.forging.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** The same private infusion batch, presented as a page in the workshop. */
public final class CopperInfusionScreen extends AbstractContainerScreen<CopperInfusionMenu> {
    private static final String KEY="screen.myvillage.infusion.";
    private static final Style BOOK=Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath("myvillage","forge_detail"));
    private static final int INK=0xFF353B32;
    private Button start,pause;
    public CopperInfusionScreen(CopperInfusionMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title);imageWidth=ForgingMenu.WIDTH;imageHeight=ForgingMenu.HEIGHT;
    }
    private static Component text(String key,Object...args){return Component.translatable(KEY+key,args).withStyle(BOOK);}
    private void send(int action){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
    private Button button(int x,int y,int width,int action,String key){return addRenderableWidget(new WorkshopScreen.InkButton(leftPos+x,topPos+y,width,18,text(key),b->send(action),()->false));}
    @Override protected void init(){
        super.init();button(204,6,88,CopperInfusionMenu.BACK,"back");
        start=button(70,128,100,CopperInfusionMenu.START,"start");pause=button(178,128,100,CopperInfusionMenu.PAUSE,"pause");
    }
    @Override protected void renderBg(GuiGraphics g,float delta,int mx,int my){
        int x=leftPos,y=topPos;
        g.fill(x-2,y-2,x+imageWidth+2,y+imageHeight+2,0xFF554E3E);g.fill(x,y,x+imageWidth,y+imageHeight,0xFFDCCFB2);
        g.renderOutline(x+2,y+2,imageWidth-4,imageHeight-4,0xFF9D855C);
        g.fill(x+8,y+27,x+296,y+123,0xFFC5B590);g.fill(x+67,y+151,x+284,y+233,0xFF353B33);
        for(var slot:menu.slots){g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xFF8B826C);g.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xFF343B32);}
        int paid=Math.min(menu.paidQi(),InfusionRules.get().qiPerIngot());
        if(menu.count()>0){
            int stage=paid*6/InfusionRules.get().qiPerIngot();
            var texture=ResourceLocation.fromNamespaceAndPath("myvillage","textures/gui/copper_infusion_"+stage+".png");
            g.blit(texture,x+136,y+43,0,0,32,32,32,32);
        }
        g.fill(x+88,y+82,x+216,y+85,0xFF928367);g.fill(x+88,y+82,x+88+128*paid/InfusionRules.get().qiPerIngot(),y+85,0xFFA65B3C);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,title.copy().withStyle(Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath("myvillage","boss_title"))),10,8,INK,false);
        g.drawString(font,text("batch",InfusionRules.get().maxBatch()),16,31,INK,false);
        long power=ClientCultivationState.latest().map(p->p.currentSpiritualPower()).orElse(0L);
        g.drawString(font,text("power",power),16,47,INK,false);
        int remaining=Math.max(0,InfusionRules.get().qiPerIngot()-menu.paidQi())*menu.count();
        var remainingText=text("remaining",remaining);g.drawString(font,remainingText,152-font.width(remainingText)/2,93,INK,false);
        var status=text("status."+menu.status());g.drawString(font,status,152-font.width(status)/2,109,INK,false);
        g.drawString(font,playerInventoryTitle.copy().withStyle(BOOK),10,158,INK,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        start.active=menu.ready()&&!menu.running();pause.active=menu.running();super.render(g,mx,my,delta);renderTooltip(g,mx,my);
    }
}
