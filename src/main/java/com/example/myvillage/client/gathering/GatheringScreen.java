package com.example.myvillage.client.gathering;

import com.example.myvillage.gathering.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class GatheringScreen extends AbstractContainerScreen<GatheringMenu> {
    private static final int PAPER=0xFFE5DECB, INK=0xFF283D33, GREEN=0xFF60816C, EDGE=0xFF978660;
    private Button start, stop;
    public GatheringScreen(GatheringMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth=GatheringMenu.WIDTH; imageHeight=GatheringMenu.HEIGHT;
        inventoryLabelX=69; inventoryLabelY=144;
    }
    private void send(int button) { if(minecraft!=null&&minecraft.gameMode!=null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,button); }
    @Override protected void init() {
        super.init();
        start=addRenderableWidget(Button.builder(Component.literal("启阵"),b->send(0)).bounds(leftPos+136,topPos+119,72,18).build());
        stop=addRenderableWidget(Button.builder(Component.literal("停阵"),b->send(1)).bounds(leftPos+214,topPos+119,72,18).build());
        start.setTooltip(Tooltip.create(Component.literal("完整成型并有灵石或阵能时启用。\n修炼沿用原按键；满修为固本不耗阵能。")));
        stop.setTooltip(Tooltip.create(Component.literal("停止提供加成，未使用的灵石与阵能保留。\n拆除阵心只退回尚未转化的灵石。")));
    }
    @Override protected void renderBg(GuiGraphics g,float delta,int mouseX,int mouseY) {
        int x=leftPos,y=topPos;
        g.fill(x-2,y-2,x+imageWidth+2,y+imageHeight+2,EDGE);
        g.fill(x,y,x+imageWidth,y+imageHeight,PAPER);
        g.fill(x+8,y+21,x+imageWidth-8,y+22,EDGE);
        g.fill(x+8,y+26,x+124,y+139,0xFFCDD3C0);
        int r=GatheringRules.get().radius();
        for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
            int px=x+15+(dx+r)*14,pz=y+34+(dz+r)*14;
            int color=Math.abs(dx)<=GatheringRules.get().innerRadius()&&Math.abs(dz)<=GatheringRules.get().innerRadius()?0xFF88A38A:0xFFB4BDAA;
            if(dx==0&&dz==0)color=INK;
            for(var node:GatheringPattern.nodes())if(node.getX()==dx&&node.getZ()==dz)color=EDGE;
            g.fill(px,pz,px+12,pz+12,color);
            if(menu.value(4)!=0&&menu.value(5)==dx&&menu.value(7)==dz) g.renderOutline(px-1,pz-1,14,14,0xFFB14E38);
        }
        for(var slot:menu.slots){
            g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,EDGE);
            g.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xFFBFC7B6);
        }
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY) {
        g.drawString(font,title,10,8,INK,false);
        g.drawString(font,menu.value(1)==1?"阵形完整":"阵形未成",218,8,menu.value(1)==1?GREEN:0xFF9B4935,false);
        g.drawString(font,"北",61,24,INK,false);
        g.drawString(font,"下品灵石",136,29,INK,false);
        g.drawString(font,"余能 "+menu.value(2)+" 次",164,45,INK,false);
        g.drawString(font,"1 枚 → "+menu.value(9)+" 次",164,57,GREEN,false);
        g.drawString(font,"修为 +"+menu.value(8)/100.0+"% · 取整",136,75,INK,false);
        g.drawString(font,(menu.value(0)==0?"已停阵":menu.value(1)==0?"缺件暂停":"已启阵")+" · 受益 "+menu.value(3)+" 人",136,89,INK,false);
        String[] issues={"阵心嵌地，阵枢在地面上", "缺北侧阵枢", "缺东侧阵枢", "缺南侧阵枢", "缺西侧阵枢", "地基需完整实体顶面", "中央需两格无障碍空间", "结构或归属区块未加载", "阵枢被另一阵心共享", "阵心无效"};
        int issue=Math.clamp(menu.value(4),0,issues.length-1);
        String status=issues[issue];
        if(issue>0)status+=" ("+menu.value(5)+","+menu.value(6)+","+menu.value(7)+")";
        g.drawString(font,status,10,144-1,issue>0?0xFF9B4935:INK,false);
        g.drawString(font,"仅修为受益；不叠阵",136,103,GREEN,false);
        g.drawString(font,"随身物品",10,162,INK,false);
        g.drawString(font,"空闲不耗能",10,180,GREEN,false);
        g.drawString(font,"固本不耗能",10,193,GREEN,false);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta){
        start.active=menu.value(0)==0&&menu.value(1)==1&&(menu.value(2)>0||!menu.getSlot(0).getItem().isEmpty());
        stop.active=menu.value(0)!=0;
        super.render(g,mouseX,mouseY,delta);renderTooltip(g,mouseX,mouseY);
    }
}
