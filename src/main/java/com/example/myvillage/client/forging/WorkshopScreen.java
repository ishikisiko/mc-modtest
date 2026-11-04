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

/** Physical anvil finishing and existing equipment training. Preparation, heating and assembly have dedicated stations. */
public final class WorkshopScreen extends AbstractContainerScreen<WorkshopMenu> {
    private static final Style BOOK=Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath("myvillage","forge_detail"));
    private static final int INK=0xFF353B32,MUTED=0xFF756A52,CINNABAR=0xFF8C4031,PAPER=0xFFE1D6B9;
    private final List<InkButton> directions=new ArrayList<>();
    private InkButton quench,grind,repair;
    public WorkshopScreen(WorkshopMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=WorkshopMenu.WIDTH;imageHeight=WorkshopMenu.HEIGHT;}
    private void send(int action){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
    private InkButton button(int x,int y,int width,String label,int action){return addRenderableWidget(new InkButton(leftPos+x,topPos+y,width,18,Component.literal(label).withStyle(BOOK),b->send(action),()->false));}
    @Override protected void init(){
        super.init();directions.clear();quench=button(12,118,65,"淬火",WorkshopMenu.QUENCH);grind=button(83,118,65,"精磨",WorkshopMenu.GRIND);repair=button(154,118,65,"修补",WorkshopMenu.REPAIR);
        for(int i=1;i<=3;i++){final int d=i;directions.add(addRenderableWidget(new InkButton(leftPos+12+(i-1)*67,topPos+85,62,18,
                Component.translatable("workshop.myvillage.path."+i).withStyle(BOOK),b->send(WorkshopMenu.PATH_BASE+d),()->menu.direction()==d)));}
        update();
    }
    private void update(){
        quench.active=!menu.busy()&&menu.reason(WorkshopMenu.QUENCH)==0;grind.active=!menu.busy()&&menu.reason(WorkshopMenu.GRIND)==0;repair.active=!menu.busy()&&menu.reason(WorkshopMenu.REPAIR)==0;
        for(int i=0;i<directions.size();i++){var b=directions.get(i);b.visible=menu.piece()==null&&!menu.input().isEmpty();b.active=!menu.busy()&&menu.trainingDirection(i+1);}
        quench.setTooltip(Tooltip.create(Component.literal("成形完成且器坯有热度时，在相邻有水的淬火槽中淬火。")));
        grind.setTooltip(Tooltip.create(Component.literal("回火后的器坯消耗燧石进行精磨，再送到组装台。")));
    }
    @Override protected void containerTick(){super.containerTick();update();}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;g.fill(x,y,x+imageWidth,y+imageHeight,INK);g.fill(x+2,y+2,x+imageWidth-2,y+imageHeight-2,PAPER);
        for(int[] p:new int[][]{{94,104},{188,104}})slot(g,x+p[0],y+p[1]);
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)slot(g,x+144+col*18,y+151+row*18);
        for(int col=0;col<9;col++)slot(g,x+144+col*18,y+209);
        int heat=Math.min(menu.heat(),FoundryRules.get().heat().capacity());g.fill(x+12,y+66,x+304,y+71,0xFFB7A78B);g.fill(x+12,y+66,x+12+292*heat/FoundryRules.get().heat().capacity(),y+71,0xFFB06E3F);
    }
    private static void slot(GuiGraphics g,int x,int y){g.fill(x-1,y-1,x+17,y+17,0xFF99896D);g.fill(x,y,x+16,y+16,0xFFC9BFA5);}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,title,12,9,INK,false);g.drawString(font,"台面器物由右键放置，空手右键取回。",12,28,MUTED,false);
        var piece=menu.piece();String detail;
        if(piece==null)detail=menu.input().isEmpty()?"在普通炼器鼎起坯，再送入加热窑。":"持锻锤右键台面再锻；方向沿用器物原有成长规则。";
        else detail=switch(piece.stage()){case 0,1->"成形进度 "+piece.points()+" / "+WorkshopRules.get().formingPoints(piece.recipe())+"；持锤右键锻打。";
            case 2->"器坯已淬火，请送回加热窑进行回火。";case 3->"回火已完成，可以精磨。";default->"精磨已完成，请送到组装台装配。";};
        var lines=font.split(Component.literal(detail),292);for(int i=0;i<Math.min(2,lines.size());i++)g.drawString(font,lines.get(i),12,44+i*10,INK);
        g.drawString(font,"火候 "+menu.heat(),236,85,CINNABAR,false);
        g.drawString(font,Component.translatable("workshop.myvillage.status."+menu.status()),12,140,MUTED,false);
        if(!menu.input().isEmpty()){
            var craft=piece!=null?WorkshopRules.get().craft(piece.recipe()):WorkshopEquipment.craft(menu.input()).orElse(null);
            if(craft!=null){var record=FoundryEquipment.record(menu.input());g.drawString(font,"手工贡献",12,164,MUTED,false);g.drawString(font,WorkshopEquipment.amount(record.manualStrength(craft.forming_points())*100)+"%",12,178,INK,false);}
        }
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){super.render(g,mx,my,partial);renderTooltip(g,mx,my);}
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
