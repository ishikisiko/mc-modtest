package com.example.myvillage.client.atlas;

import com.example.myvillage.network.AtlasCityTravelPayload;
import com.example.myvillage.worldplan.atlas.WorldAtlas;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.LoggerFactory;

/** A paper-and-ink confirmation over the same atlas instance; cancel never emits a travel packet. */
public final class AtlasCityTravelScreen extends Screen {
    private final WorldAtlasScreen atlasScreen;
    private final WorldAtlas.City city;
    private final String region, fingerprint;
    private int panelX, panelY, panelWidth;
    private boolean submitted;

    public AtlasCityTravelScreen(WorldAtlasScreen atlasScreen, WorldAtlas.City city, String region, String fingerprint) {
        super(tr("question",city.name()));
        this.atlasScreen=atlasScreen; this.city=city; this.region=region; this.fingerprint=fingerprint;
    }
    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.myvillage.atlas.travel."+key,args);
    }
    @Override protected void init() {
        panelWidth=Math.min(360,width-20); panelX=(width-panelWidth)/2; panelY=(height-178)/2;
        int buttonWidth=Math.min(120,(panelWidth-42)/2);
        inkButton(width/2-buttonWidth-8,panelY+139,buttonWidth,tr("confirm"),this::confirm);
        var cancel=inkButton(width/2+8,panelY+139,buttonWidth,tr("cancel"),this::onClose);
        setInitialFocus(cancel);
    }
    private Button inkButton(int x, int y, int w, Component text, Runnable action) {
        return addRenderableWidget(new Button(x,y,w,24,text,button -> action.run(),supplier -> supplier.get()) {
            @Override public void renderWidget(GuiGraphics g, int mx, int my, float tick) {
                int border=isHoveredOrFocused()?0xff9f4e38:0xff8b937f;
                g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),border);
                g.fill(getX()+1,getY()+1,getX()+getWidth()-1,getY()+getHeight()-1,0xffe5e4d4);
                g.drawCenteredString(AtlasCityTravelScreen.this.font,getMessage(),getX()+getWidth()/2,getY()+8,0xff465547);
            }
        });
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float tick) {
        atlasScreen.render(g,-1,-1,tick);
        g.fill(0,0,width,height,0xb3202821);
        g.fill(panelX,panelY,panelX+panelWidth,panelY+178,0xff4f5849);
        g.fill(panelX+1,panelY+1,panelX+panelWidth-1,panelY+177,0xffeeeadd);
        g.fill(panelX+12,panelY+12,panelX+15,panelY+34,0xff9f4e38);
        g.drawCenteredString(font,title,width/2,panelY+20,0xff3d493f);
        g.drawCenteredString(font,Component.literal(city.name()+" · "+region),width/2,panelY+49,0xff626c5b);
        g.drawCenteredString(font,tr("entry",city.x(),city.y(),city.z()),width/2,panelY+68,0xff626c5b);
        int y=panelY+92;
        for (var line : font.split(tr("safe_hint"),panelWidth-36)) {
            g.drawCenteredString(font,line,width/2,y,0xff626c5b); y+=font.lineHeight+2;
        }
        super.render(g,mouseX,mouseY,tick);
    }
    private void confirm() {
        if (submitted || minecraft==null || minecraft.player==null || minecraft.getConnection()==null) return;
        submitted=true;
        LoggerFactory.getLogger(AtlasCityTravelScreen.class).info("ATLAS_TRAVEL_INPUT action=confirm city={} plan={}",city.id(),fingerprint);
        PacketDistributor.sendToServer(new AtlasCityTravelPayload(city.id(),fingerprint));
        // Show the world and server chat feedback while the bounded loading request is pending.
        minecraft.setScreen(null);
    }
    @Override public void onClose() {
        if (submitted) return;
        LoggerFactory.getLogger(AtlasCityTravelScreen.class).info("ATLAS_TRAVEL_INPUT action=cancel city={} plan={}",city.id(),fingerprint);
        atlasScreen.travelCancelled(city);
        minecraft.setScreen(atlasScreen);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float tick) { }
}
