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
                    // Re-read the current world/snapshot at click time, including after a dimension change.
                    var action=model().travel();var mc=Minecraft.getInstance();
                    if(action==JourneyView.Travel.NONE || mc.getConnection()==null)return;
                    mc.getConnection().sendCommand(action.command);
                    if(mc.screen!=null)mc.screen.onClose();
                });
        widgets.accept(travel);
        return 28;
    }
    @Override public void setVisible(boolean value) {
        if(!value)reported=null;
        if(travel!=null)travel.visible=value && model().travel()!=JourneyView.Travel.NONE;
    }
    @Override public void refresh(PanelContext context,boolean showing) {
        if(travel==null)return;
        var action=model().travel();travel.visible=showing && action!=JourneyView.Travel.NONE;
        travel.active=travel.visible;travel.setMessage(Component.literal(action.label));
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
