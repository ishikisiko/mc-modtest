package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.client.story.ClientStory;
import com.example.myvillage.client.story.JourneyView;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/** H -> 行迹. Text scrolls independently of the fixed, server-validated realm travel button. */
public final class JourneyPage extends PanelPage {
    private PanelButton travel;
    private PanelButton questStatus,questTravel;
    private boolean questRealm(){var mc=Minecraft.getInstance();return mc.level!=null&&mc.level.dimension().location().toString().startsWith("myvillage:expedition/");}
    private static void command(String command){var mc=Minecraft.getInstance();if(mc.getConnection()!=null)mc.getConnection().sendCommand(command);if(mc.screen!=null)mc.screen.onClose();}
    private JourneyView.Travel reported;
    private JourneyView model() {
        var mc=Minecraft.getInstance();
        return JourneyView.of(ClientStory.snapshot(),mc.player==null?null:mc.player.getUUID(),
                mc.level==null?"":mc.level.dimension().location().toString());
    }
    @Override public boolean requiresProfile(){return false;}
    @Override public int init(int x,int y,int width,int height,Consumer<AbstractWidget> widgets) {
        travel=new PanelButton(PanelButton.Style.ACTION,CultivationInkTheme.JADE,x,y+height-22,width,20,
                Component.empty(),button -> {
                    if(questRealm()){command("expedition finish");return;}
                    // Re-read the current world/snapshot at click time, including after a dimension change.
                    var action=model().travel();var mc=Minecraft.getInstance();
                    if(action==JourneyView.Travel.NONE || mc.getConnection()==null)return;
                    mc.getConnection().sendCommand(action.command);
                    if(mc.screen!=null)mc.screen.onClose();
                });
        widgets.accept(travel);
        questStatus=new PanelButton(PanelButton.Style.ACTION,CultivationInkTheme.JADE,x,y+height-46,width/2-3,20,Component.literal("远征状态"),b->command("expedition status"));
        questTravel=new PanelButton(PanelButton.Style.ACTION,CultivationInkTheme.JADE,x+width/2+3,y+height-46,width/2-3,20,Component.literal("进入远征"),b->command(questRealm()?"expedition leave":"expedition enter"));
        widgets.accept(questStatus);widgets.accept(questTravel);
        return 52;
    }
    @Override public void setVisible(boolean value) {
        if(!value)reported=null;
        if(travel!=null)travel.visible=value && model().travel()!=JourneyView.Travel.NONE;
        if(questStatus!=null){questStatus.visible=value;questTravel.visible=value;}
    }
    @Override public void refresh(PanelContext context,boolean showing) {
        if(travel==null)return;
        var action=model().travel();travel.visible=showing && action!=JourneyView.Travel.NONE;
        travel.active=travel.visible;travel.setMessage(Component.literal(action.label));
        questStatus.visible=showing;questTravel.visible=showing;questTravel.setMessage(Component.literal(questRealm()?"暂离远征":"进入远征"));
        if(questRealm()){travel.visible=showing;travel.active=showing;travel.setMessage(Component.literal("出口验核"));}
        if(showing && reported!=action) {
            reported=action;var mc=Minecraft.getInstance();
            double sx=mc.getWindow().getWidth()/(double)mc.getWindow().getGuiScaledWidth();
            double sy=mc.getWindow().getHeight()/(double)mc.getWindow().getGuiScaledHeight();
            org.slf4j.LoggerFactory.getLogger(JourneyPage.class).info(
                    "QIANZHEN_JOURNAL action={} x={} y={} w={} h={} page=H_JOURNEY",
                    action.name().toLowerCase(java.util.Locale.ROOT),Math.round(travel.getX()*sx),Math.round(travel.getY()*sy),
                    Math.round(travel.getWidth()*sx),Math.round(travel.getHeight()*sy));
        }
    }
    @Override public int render(GuiGraphics g,PanelContext c,int x,int y,int width,int viewportHeight) {
        int start=y;var data=model();
        if(questRealm()){
            CultivationInkTheme.title(g,c.font(),"委托远征",x+3,y,width-6);y+=21;
            y=text(g,c,"清除沿途守敌后，前往出口磁石验核。上方进度条显示必经守敌的清剿进度。",x+3,y,width-6,CultivationInkTheme.INK)+9;
            y=text(g,c,"暂离需要停留6秒，移动和受伤会中断。暂离与重登保留守敌境界、生命和击败进度，空置24小时后回收。",x+3,y,width-6,CultivationInkTheme.MUTED)+9;
            y=text(g,c,"通关后回千机阁江湖榜领取报酬。“远征状态”可查看签发地、境界和救援余量。",x+3,y,width-6,CultivationInkTheme.INK);
            return y-start+5;
        }
        CultivationInkTheme.title(g,c.font(),data.title(),x+3,y,width-6);y+=21;
        y=text(g,c,data.status(),x+3,y,width-6,CultivationInkTheme.JADE)+8;
        y=text(g,c,data.objective(),x+3,y,width-6,CultivationInkTheme.INK)+5;
        if(!data.hint().isBlank())y=text(g,c,data.hint(),x+3,y,width-6,CultivationInkTheme.MUTED)+8;
        g.fill(x+3,y,x+width-3,y+1,CultivationInkTheme.RULE);y+=9;
        y=text(g,c,"已记之事",x+3,y,width-6,CultivationInkTheme.INK)+5;
        if(data.journal().isEmpty())y=text(g,c,"尚无记录。",x+3,y,width-6,CultivationInkTheme.MUTED);
        else for(String entry:data.journal())y=text(g,c,entry,x+3,y,width-6,CultivationInkTheme.INK)+7;
        return y-start+5;
    }
    private static int text(GuiGraphics g,PanelContext c,String text,int x,int y,int width,int color) {
        for(var line:c.font().split(Component.literal(text),Math.max(1,width))) {
            g.drawString(c.font(),line,x,y,color,false);y+=12;
        }
        return y;
    }
}
