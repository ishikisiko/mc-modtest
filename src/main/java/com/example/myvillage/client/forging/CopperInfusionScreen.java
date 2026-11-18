package com.example.myvillage.client.forging;

import com.example.myvillage.client.cultivation.ClientCultivationState;
import com.example.myvillage.forging.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Inventory;

/** The same private infusion batch, presented as a page in the workshop. */
public final class CopperInfusionScreen extends AbstractContainerScreen<CopperInfusionMenu> {
    private static final String KEY="screen.yuanxian.infusion.";
    private static final Style BOOK=Style.EMPTY;
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
        StationPanel.paper(g,x,y,imageWidth,imageHeight);
        var state=com.example.myvillage.block.ModBlocks.FORGING_TABLE.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT,menu.running());
        StationPanel.station(g,x+10,y+30,72,91,state,delta);
        for(var slot:menu.slots)StationPanel.slot(g,x+slot.x,y+slot.y);
        int paid=Math.min(menu.paidQi(),InfusionRules.get().qiPerIngot());
        g.fill(x+101,y+82,x+281,y+85,0xFF928367);
        g.fill(x+101,y+82,x+101+180*paid/InfusionRules.get().qiPerIngot(),y+85,0xFFA65B3C);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,title,10,8,INK,false);
        g.drawString(font,text("batch",InfusionRules.get().maxBatch()),16,31,INK,false);
        long power=ClientCultivationState.latest().map(p->p.currentSpiritualPower()).orElse(0L);
        g.drawString(font,text("power",power),101,47,INK,false);
        int remaining=Math.max(0,InfusionRules.get().qiPerIngot()-menu.paidQi())*menu.count();
        var remainingText=text("remaining",remaining);g.drawString(font,remainingText,191-font.width(remainingText)/2,93,INK,false);
        var status=text("status."+menu.status());g.drawString(font,status,191-font.width(status)/2,109,INK,false);
        g.drawString(font,playerInventoryTitle.copy().withStyle(BOOK),10,158,INK,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        start.active=menu.ready()&&!menu.running();pause.active=menu.running();super.render(g,mx,my,delta);renderTooltip(g,mx,my);
    }
}
