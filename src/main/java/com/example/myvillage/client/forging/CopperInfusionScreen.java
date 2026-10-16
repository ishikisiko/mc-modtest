package com.example.myvillage.client.forging;

import com.example.myvillage.client.cultivation.ClientCultivationState;
import com.example.myvillage.forging.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Same inventory coordinates as forging, with an explicit per-batch progress and cost view. */
public final class CopperInfusionScreen extends AbstractContainerScreen<CopperInfusionMenu> {
    private static final String KEY="screen.myvillage.infusion.";
    private Button start,pause;
    public CopperInfusionScreen(CopperInfusionMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title);imageWidth=ForgingMenu.WIDTH;imageHeight=ForgingMenu.HEIGHT;
    }
    private void send(int action){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
    @Override protected void init(){
        super.init();
        addRenderableWidget(Button.builder(Component.translatable(KEY+"back"),b->send(CopperInfusionMenu.BACK)).bounds(leftPos+204,topPos+4,88,18).build());
        start=addRenderableWidget(Button.builder(Component.translatable(KEY+"start"),b->send(CopperInfusionMenu.START)).bounds(leftPos+70,topPos+128,100,18).build());
        pause=addRenderableWidget(Button.builder(Component.translatable(KEY+"pause"),b->send(CopperInfusionMenu.PAUSE)).bounds(leftPos+178,topPos+128,100,18).build());
    }
    @Override protected void renderBg(GuiGraphics g,float delta,int mx,int my){
        int x=leftPos,y=topPos;
        g.fill(x-2,y-2,x+imageWidth+2,y+imageHeight+2,0xFF786346);g.fill(x,y,x+imageWidth,y+imageHeight,0xFF252823);
        g.fill(x+8,y+26,x+296,y+124,0xFF343A31);
        for(var s:menu.slots){g.fill(x+s.x-1,y+s.y-1,x+s.x+17,y+s.y+17,0xFF8B826C);g.fill(x+s.x,y+s.y,x+s.x+16,y+s.y+16,0xFF171D18);}
        int paid=Math.min(menu.paidQi(),InfusionRules.get().qiPerIngot());
        if(menu.count()>0){
            int stage=paid*6/InfusionRules.get().qiPerIngot();
            var texture=ResourceLocation.fromNamespaceAndPath("myvillage","textures/gui/copper_infusion_"+stage+".png");
            g.blit(texture,x+136,y+43,0,0,32,32,32,32);
        }
        g.fill(x+88,y+82,x+216,y+87,0xFF171D18);
        g.fill(x+88,y+82,x+88+128*paid/InfusionRules.get().qiPerIngot(),y+87,0xFFD59164);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,title,10,8,0xFFE5D5AF,false);
        g.drawString(font,Component.translatable(KEY+"batch",InfusionRules.get().maxBatch()),16,31,0xFFC5C6B7,false);
        long power=ClientCultivationState.latest().map(p->p.currentSpiritualPower()).orElse(0L);
        g.drawString(font,Component.translatable(KEY+"power",power),16,47,0xFFACC9BF,false);
        int remaining=Math.max(0,InfusionRules.get().qiPerIngot()-menu.paidQi())*menu.count();
        g.drawCenteredString(font,Component.translatable(KEY+"remaining",remaining),152,93,0xFFE5D5AF);
        g.drawCenteredString(font,Component.translatable(KEY+"status."+menu.status()),152,109,0xFFC5C6B7);
        g.drawString(font,playerInventoryTitle,8,160,0xFFC5C6B7,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        start.active=menu.ready()&&!menu.running();pause.active=menu.running();
        super.render(g,mx,my,delta);renderTooltip(g,mx,my);
    }
}
