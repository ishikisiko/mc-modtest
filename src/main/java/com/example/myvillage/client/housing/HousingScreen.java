package com.example.myvillage.client.housing;

import com.example.myvillage.housing.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** 从真实施工模板绘制平面，选房和自由留白共用同一块宅地。 */
public final class HousingScreen extends Screen {
    private HousingOffer offer;private final List<HousingTemplates.Info> houses=HousingTemplates.players();
    private int selected,turn,x,z,left,top,panelWidth,panelHeight,listWidth,cut=3;private boolean waiting;
    private Map<Long,Integer> plan=Map.of();private Button build;
    HousingScreen(HousingOffer offer){super(Component.literal("宅地与住宅"));this.offer=offer;center();restorePlacement();}
    public String site(){return offer.site();}
    public void update(HousingOffer value){offer=value;waiting=false;restorePlacement();rebuildWidgets();}
    private void restorePlacement(){
        if(offer.currentHouse().isEmpty())return;
        for(int i=0;i<houses.size();i++)if(houses.get(i).id().equals(offer.currentHouse())){selected=i;turn=offer.currentTurn();x=offer.currentX();z=offer.currentZ();clamp();return;}
    }
    private HousingTemplates.Info selected(){return houses.get(selected);}
    private int houseWidth(){return turn%2==0?selected().width():selected().depth();}
    private int houseDepth(){return turn%2==0?selected().depth():selected().width();}
    private void center(){x=(offer.width()-houseWidth())/2;z=Math.max(0,(offer.depth()-houseDepth())/2);}
    private void clamp(){x=Math.max(0,Math.min(offer.width()-houseWidth(),x));z=Math.max(0,Math.min(offer.depth()-houseDepth(),z));}
    @Override protected void init(){
        panelWidth=Math.min(width-12,548);panelHeight=Math.min(height-12,336);left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        int list=Math.min(148,Math.max(112,panelWidth*3/10));listWidth=list;int cy=top+30;
        for(int i=0;i<houses.size();i++){
            final int index=i;var info=houses.get(i);
            addRenderableWidget(Button.builder(Component.literal(info.name()+" "+info.width()+"×"+info.depth()),b->{selected=index;turn=0;center();refreshPlan();}).bounds(left+8,cy+i*22,list,20).build());
        }
        int rx=left+list+20,controls=top+panelHeight-70;
        addRenderableWidget(Button.builder(Component.literal("旋转 90°"),b->{turn=(turn+1)%4;clamp();refreshPlan();}).bounds(rx,controls,66,20).build());
        addRenderableWidget(Button.builder(Component.literal("居中"),b->{center();}).bounds(rx+70,controls,42,20).build());
        addRenderableWidget(Button.builder(Component.literal("楼层"),b->{cut=cut==3?9:3;refreshPlan();}).bounds(rx+116,controls,42,20).build());
        int row=controls+22;
        for(int i=0;i<4;i++){
            final int direction=i;
            addRenderableWidget(Button.builder(Component.literal(new String[]{"←","→","↑","↓"}[i]),b->{if(direction==0)x--;if(direction==1)x++;if(direction==2)z--;if(direction==3)z++;clamp();}).bounds(rx+i*24,row,22,20).build());
        }
        build=addRenderableWidget(Button.builder(Component.literal(action()),b->submit()).bounds(rx+100,row,panelWidth-list-128,20).build());
        build.active=!waiting&&(offer.status().equals("unclaimed")||offer.owner()&&Set.of("empty","blocked").contains(offer.status()));
        addRenderableWidget(Button.builder(Component.literal("暂留空地 / 关闭"),b->onClose()).bounds(left+8,top+panelHeight-26,list,20).build());
        refreshPlan();
    }
    private String action(){return offer.status().equals("unclaimed")?"领取宅地":offer.status().equals("blocked")?"继续施工":"确认建造";}
    private void submit(){
        waiting=true;build.active=false;PacketDistributor.sendToServer(new HousingIntent(offer.nonce(),offer.status().equals("unclaimed"),selected().id(),turn,x,z));
    }
    private void refreshPlan(){
        var template=HousingTemplates.get(selected().id());Map<Long,Integer> colors=new HashMap<>();Map<Long,Integer> levels=new HashMap<>();
        for(var entry:template.blocks().entrySet()){
            var p=entry.getKey();if(p.getY()>cut||p.getY()<-1)continue;
            long key=((long)p.getX()<<32)|(p.getZ()&0xffffffffL);
            if(levels.getOrDefault(key,-3)>p.getY())continue;
            String id=BuiltInRegistries.BLOCK.getKey(entry.getValue().getBlock()).getPath();
            int color=id.contains("water")?0xff568d99:id.contains("leaves")||id.contains("grass")||id.contains("bamboo")?0xff66865d:
                id.contains("bed")?0xffa85649:id.contains("plank")||id.contains("log")||id.contains("door")?0xff92704d:
                id.contains("calcite")||id.contains("wall")||id.contains("sandstone")?0xffdbd1b5:0xffa39d88;
            colors.put(key,color);levels.put(key,p.getY());
        }
        plan=colors;
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){
        super.renderBackground(g,mx,my,delta);
        g.fill(left,top,left+panelWidth,top+panelHeight,0xf224292b);g.fill(left+2,top+2,left+panelWidth-2,top+panelHeight-2,0xffe8dfc8);
        g.drawString(font,title,left+10,top+10,0xff302d28,false);
        int rx=left+listWidth+20,ry=top+31;
        int column=Math.max(1,(panelWidth-listWidth-42)/2);
        int previewX=rx+column+12;
        int previewSize=Math.max(1,Math.min(column,panelHeight-148));
        g.drawString(font,selected().name()+" · "+(cut==3?"一层平面":"上层平面"),rx,ry,0xff302d28,false);
        g.drawString(font,"立体预览",previewX,ry,0xff302d28,false);
        double scale=Math.min((double)column/offer.width(),(double)previewSize/offer.depth());int ox=rx,oz=ry+15;
        g.fill(previewX-1,oz-1,previewX+previewSize+1,oz+previewSize+1,0xff776c57);
        var preview=ResourceLocation.fromNamespaceAndPath("yuanxian","textures/gui/housing/"+selected().id()+".png");
        g.blit(preview,previewX,oz,previewSize,previewSize,0,0,256,256,256,256);
        int pw=(int)Math.ceil(offer.width()*scale),pd=(int)Math.ceil(offer.depth()*scale);
        g.fill(ox-1,oz-1,ox+pw+1,oz+pd+1,0xff776c57);g.fill(ox,oz,ox+pw,oz+pd,0xff94a17b);
        for(var entry:plan.entrySet()){
            int bx=(int)(entry.getKey()>>32),bz=(int)(long)entry.getKey();BlockPos p=HousingTemplates.rotate(new BlockPos(bx,0,bz),selected().width(),selected().depth(),turn);
            int a=(int)Math.floor((p.getX()+x)*scale),b=(int)Math.floor((p.getZ()+z)*scale),c=(int)Math.ceil((p.getX()+x+1)*scale),d=(int)Math.ceil((p.getZ()+z+1)*scale);
            g.fill(ox+a,oz+b,ox+c,oz+d,entry.getValue());
        }
        var entry=HousingTemplates.rotate(selected().entry(),selected().width(),selected().depth(),turn);
        int ex=ox+(int)((entry.getX()+x+.5)*scale),ez=oz+(int)((entry.getZ()+z+.5)*scale);g.fill(ex-2,ez-2,ex+3,ez+3,0xffbf7738);
        g.drawString(font,"宅地 "+offer.width()+"×"+offer.depth()+"，橙色为入口",rx,oz+pd+5,0xff554c3c,false);
        String status=switch(offer.status()){case "unclaimed"->"尚未领取";case "empty"->offer.owner()?"你的宅地，尚未建房":"已由其他玩家领取";case "preparing"->"正在检查施工范围";case "building"->"正在施工";case "built"->"房屋已建好，可自由改建";case "blocked"->"施工暂停，可修复后继续";default->"宅地状态待确认";};
        int ty=top+168;g.drawWordWrap(font,Component.literal(status),left+10,ty,listWidth-4,0xff433c32);
        int claim=offer.claimCost(),cost=offer.buildCost();String currencyName=Component.translatable(offer.currency()).getString();String price=offer.status().equals("unclaimed")?"领取费用："+(claim==0?"免费":claim+" 枚"+currencyName):"建房费用："+(cost==0?"免费":cost+" 枚"+currencyName);
        if(panelHeight>260)g.drawWordWrap(font,Component.literal(selected().description()),left+10,ty+26,listWidth-4,0xff635b4b);
        g.drawString(font,price,rx,top+panelHeight-83,0xff554c3c,false);
        if(!offer.message().isEmpty()){
            var lines=font.split(Component.literal(offer.message()),panelWidth-listWidth-32);
            for(int i=0;i<Math.min(2,lines.size());i++)g.drawString(font,lines.get(i),rx,top+panelHeight-22+i*9,0xff6c3d28,false);
        }
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        super.render(g,mx,my,delta);
        if(!offer.message().isEmpty()&&mx>=left+listWidth+20&&my>=top+panelHeight-22)
            g.renderTooltip(font,Component.literal(offer.message()),mx,my);
    }
    @Override public boolean isPauseScreen(){return false;}
}
