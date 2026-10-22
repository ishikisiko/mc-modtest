package com.example.myvillage.client.forging.array;

import com.example.myvillage.forging.*;
import com.example.myvillage.forging.array.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Compact Chinese blueprint and manual infusion screen. Structure and costs are never invented by the UI. */
public final class ForgingArrayScreen extends AbstractContainerScreen<ForgingArrayMenu> {
    private static final int PAPER=0xFFE0D6BB,INK=0xFF343C35,GOLD=0xFF82643B,JADE=0xFF347F74,MUTED=0xFF716E60;
    private static final String[] ROLE_NAMES={"留空","阵枢","阵基","鼎壁","炉心","阵柱"};
    private static final String[] SYMBOLS={"·","枢","基","壁","火","柱"};
    private static final int[] COLORS={0xFFCDC6B0,0xFFA88750,0xFF969889,0xFF64716D,0xFFB67A4C,0xFF4D9688};
    private static final String[] STATUS={"放入成品，选择专长后开炉","炉心炼灵中","阵体不完整，修复后续炼","成品槽占用，炼制暂停","炼灵完成，请取出成品","请放入尚未炼灵的成品","请先觉醒并获得炼器资质","背包或灵石袋材料不足","已有炉次正在进行","该器物不支持此专长","仅开炉者可以撤炉退料","炉次需恢复，请撤炉取回材料"};
    private Button start,cancel;private final List<Button> traits=new ArrayList<>();
    private int layer=-1;
    public ForgingArrayScreen(ForgingArrayMenu menu,Inventory player,Component title){super(menu,player,title);imageWidth=ForgingArrayMenu.WIDTH;imageHeight=ForgingArrayMenu.HEIGHT;}
    private void send(int action){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
    private Button button(int x,int y,int width,String text,Button.OnPress action){return addRenderableWidget(Button.builder(Component.literal(text),action).bounds(leftPos+x,topPos+y,width,18).build());}
    @Override protected void init(){
        super.init();traits.clear();
        button(10,26,22,"‹",b->layer=layer==-1?3:layer-1);button(111,26,22,"›",b->layer=layer==3?-1:layer+1);
        for(int i=0;i<ForgeMark.TRAITS.size();i++){
            int index=i;var b=button(144+i*55,94,52,"",v->send(10+index));traits.add(b);
        }
        start=button(144,119,84,"引火炼灵",b->send(0));cancel=button(232,119,74,"撤炉退料",b->send(1));
        cancel.setTooltip(Tooltip.create(Component.literal("仅开炉者可撤炉。原器物留在槽内，暂存材料退回背包；满背包则掉落。")));
        updateButtons();
    }
    private void updateButtons(){
        var p=EquipmentInfusion.prepare(menu.input(),ForgeMark.TRAITS.get(menu.traitIndex()),menu.qualityCap());
        start.active=!menu.active()&&menu.formed()&&menu.output().isEmpty()&&p.valid();cancel.active=menu.active();
        var costs=Component.literal("材料从当前操作者背包或灵石袋转入鼎内，完成后消耗。\n");
        if(p.valid())p.costs().forEach((key,n)->costs.append(ForgingIngredients.description(key)).append(" ×"+n+"\n"));
        start.setTooltip(Tooltip.create(costs));
        for(int i=0;i<traits.size();i++){
            String trait=ForgeMark.TRAITS.get(i);var b=traits.get(i);
            b.setMessage(Component.literal(menu.traitIndex()==i?"◆":"").append(Component.translatable("forging.myvillage.trait."+trait)));
            b.active=!menu.active()&&(menu.input().isEmpty()||EquipmentInfusion.prepare(menu.input(),trait,Math.max(1,menu.qualityCap())).valid());
            b.setTooltip(Tooltip.create(Component.translatable("screen.myvillage.forging.trait."+trait)));
        }
    }
    @Override protected void containerTick(){super.containerTick();updateButtons();}
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        int x=leftPos,y=topPos;
        g.fill(x,y,x+imageWidth,y+imageHeight,INK);g.fill(x+2,y+2,x+imageWidth-2,y+imageHeight-2,PAPER);
        g.fill(x+8,y+21,x+308,y+22,GOLD);g.fill(x+137,y+27,x+138,y+239,0xFFB3A78B);
        g.fill(x+144,y+28,x+307,y+90,0xFFCFCCB8);
        for(int[] slot:new int[][]{{150,51},{268,51}}){g.fill(x+slot[0]-1,y+slot[1]-1,x+slot[0]+17,y+slot[1]+17,INK);g.fill(x+slot[0],y+slot[1],x+slot[0]+16,y+slot[1]+16,0xFFBEBCA8);}
        g.fill(x+177,y+56,x+256,y+62,0xFF9A9A87);
        int width=menu.duration()>0?Math.min(79,79*menu.progress()/menu.duration()):0;
        g.fill(x+177,y+56,x+177+width,y+62,menu.active()?JADE:GOLD);
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)slot(g,x+144+col*18,y+166+row*18);
        for(int col=0;col<9;col++)slot(g,x+144+col*18,y+224);
        for(var c:ForgingArrayRules.get().cells()){
            if(c.y()!=layer)continue;int xx=x+26+(c.x()+2)*18,yy=y+48+c.z()*18;
            g.fill(xx,yy,xx+16,yy+16,COLORS[c.role().ordinal()]);
            if(ForgingArrayRules.get().cells().indexOf(c)==menu.mismatchIndex()){
                g.fill(xx,yy,xx+16,yy+1,0xFFB44433);g.fill(xx,yy+15,xx+16,yy+16,0xFFB44433);
                g.fill(xx,yy,xx+1,yy+16,0xFFB44433);g.fill(xx+15,yy,xx+16,yy+16,0xFFB44433);
            }
            g.drawCenteredString(font,SYMBOLS[c.role().ordinal()],xx+8,yy+4,0xFFF4EDDB);
        }
    }
    private static void slot(GuiGraphics g,int x,int y){g.fill(x-1,y-1,x+17,y+17,0xFF99937F);g.fill(x,y,x+16,y+16,0xFFC6C1A9);}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,title,10,8,INK,false);
        g.drawString(font,menu.formed()?"阵体已成":"缺失 / 不符 "+menu.mismatchCount()+" 格",200,8,menu.formed()?JADE:0xFF98452F,false);
        g.drawCenteredString(font,layer==-1?"底层 · 阵基":"第 "+(layer+1)+" 层",72,31,INK);
        g.drawCenteredString(font,"正面 / 阵枢侧 ↑",72,140,MUTED);
        int row=0;
        for(var role:ForgingArrayRules.Role.values()){
            if(role==ForgingArrayRules.Role.AIR)continue;
            long count=ForgingArrayRules.get().cells().stream().filter(c->c.role()==role).count();
            g.fill(14,155+row*13,21,162+row*13,COLORS[role.ordinal()]);
            g.drawString(font,ROLE_NAMES[role.ordinal()]+" × "+count,27,154+row*13,INK,false);row++;
        }
        g.drawString(font,"· 格留空；仅阵枢定朝向",10,224,MUTED,false);
        g.drawString(font,"阵枢 "+menu.origin().getX()+", "+menu.origin().getY()+", "+menu.origin().getZ(),10,236,MUTED,false);
        g.drawString(font,"成品入炉",145,35,INK,false);g.drawString(font,"炼灵产物",258,35,INK,false);
        int quality=menu.active()?menu.batchQuality():menu.qualityCap();
        String[] qualities={"未觉醒","下品","中品","上品","极品"};
        g.drawString(font,(menu.active()?"本炉品质：":"炼灵品质：")+qualities[Math.max(0,Math.min(4,quality))],145,77,JADE,false);
        String status=!menu.formed()&&!menu.active()?STATUS[2]:STATUS[Math.max(0,Math.min(STATUS.length-1,menu.status()))];
        var lines=font.split(Component.literal(status),162);
        for(int i=0;i<Math.min(2,lines.size());i++)g.drawString(font,lines.get(i),144,141+i*10,INK,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){super.render(g,mx,my,partial);renderTooltip(g,mx,my);
        int localX=mx-leftPos,localY=my-topPos;
        for(var c:ForgingArrayRules.get().cells())if(c.y()==layer){int x=26+(c.x()+2)*18,y=48+c.z()*18;
            if(localX>=x&&localX<x+16&&localY>=y&&localY<y+16){var p=ForgingArrayPattern.worldPos(menu.origin(),menu.facing(),c);
                g.renderTooltip(font,Component.literal(ROLE_NAMES[c.role().ordinal()]+"  "+p.getX()+", "+p.getY()+", "+p.getZ()),mx,my);break;}
        }
    }
}
