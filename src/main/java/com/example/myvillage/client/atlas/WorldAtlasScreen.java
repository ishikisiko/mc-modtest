package com.example.myvillage.client.atlas;

import com.example.myvillage.client.sim.ClientWorldSimState;
import com.example.myvillage.sim.runtime.net.WorldSimQuery;
import com.example.myvillage.worldplan.atlas.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** Native, read-only ink atlas. It never looks up a chunk or reconstructs the server seed. */
public final class WorldAtlasScreen extends Screen {
    private final Screen parent;
    private final AtlasViewport viewport=new AtlasViewport();
    private float scale,left,top;
    private WorldAtlas atlas;
    private byte[] cells;
    private boolean dragging;
    public WorldAtlasScreen(Screen parent){super(tr("title"));this.parent=parent;}
    private static Component tr(String key){return Component.translatable("screen.myvillage.atlas."+key);}
    @Override protected void init(){
        scale=Math.min((width-8f)/AtlasInkPainter.WIDTH,(height-8f)/AtlasInkPainter.HEIGHT);
        left=(width-AtlasInkPainter.WIDTH*scale)/2;top=(height-AtlasInkPainter.HEIGHT*scale)/2;
        addInk(1088,755,145,45,"locate",()->{if(ready()&&overworld())viewport.locate(minecraft.player.getX(),minecraft.player.getZ(),atlas.radius());});
        addInk(1088,809,145,45,"overview",viewport::reset);
        addInk(1098,879,48,49,"zoom_out",()->viewport.scale(1/1.3,.5,.5));
        addInk(1177,879,48,49,"zoom_in",()->viewport.scale(1.3,.5,.5));
        addInk(1220,917,105,43,"close",this::onClose);
        org.slf4j.LoggerFactory.getLogger(WorldAtlasScreen.class).info("ATLAS_OPEN width={} height={} scale={}",width,height,scale);
        ClientWorldSimState.request(WorldSimQuery.atlas());
    }
    private void addInk(int x,int y,int w,int h,String title,Runnable action){
        addRenderableWidget(new Button(Math.round(left+x*scale),Math.round(top+y*scale),Math.max(9,Math.round(w*scale)),Math.max(9,Math.round(h*scale)),tr(title),b->{action.run();report(title);},supplier->supplier.get()){
            @Override public void renderWidget(GuiGraphics g,int mx,int my,float tick){if(isHoveredOrFocused())g.fill(getX()+2,getY()+height-2,getX()+width-2,getY()+height-1,0xff9f4e38);}
        });
    }
    private void report(String action){org.slf4j.LoggerFactory.getLogger(WorldAtlasScreen.class).info("ATLAS_INPUT action={} zoom={} dx={} dy={}",action,viewport.zoom(),viewport.dx(),viewport.dy());}
    private boolean ready(){return atlas!=null&&atlas.status()==WorldAtlas.Status.READY;}
    private boolean overworld(){return minecraft!=null&&minecraft.level!=null&&minecraft.player!=null&&minecraft.level.dimension().equals(Level.OVERWORLD);}
    @Override public void tick(){
        var latest=ClientWorldSimState.latest(WorldSimQuery.atlas());
        if(latest.isPresent()&&latest.get().atlas()!=null&&latest.get().atlas()!=atlas){atlas=latest.get().atlas();cells=ready()?atlas.cells():null;}
        if(atlas==null||atlas.status()==WorldAtlas.Status.LOADING)ClientWorldSimState.request(WorldSimQuery.atlas());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float tick){
        g.fillGradient(0,0,width,height,0xdd171d19,0xee111713);
        if(!ready()){
            g.fill(Math.round(left+24*scale),Math.round(top+24*scale),Math.round(left+1410*scale),Math.round(top+1000*scale),0xffeeeadd);
            String key=atlas==null?"loading":switch(atlas.status()){case UNAVAILABLE->"unavailable";case FAILED->"failed";default->"loading";};
            g.drawCenteredString(font,tr(key),width/2,height/2-5,0xff4b584b);
            g.drawCenteredString(font,tr("close_hint"),width/2,Math.round(top+970*scale),0xff7c8274);super.render(g,mx,my,tick);return;
        }
        boolean chinese=minecraft.getLanguageManager().getSelected().startsWith("zh_");
        var textures=AtlasTextures.get(atlas,chinese);
        if(textures==null){g.drawCenteredString(font,tr(AtlasTextures.failed()?"failed":"painting"),width/2,height/2-5,0xffeeeadd);return;}
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.pose().pushPose();g.pose().translate(left,top,0);g.pose().scale(scale,scale,1);
        g.blit(textures.paper(),0,0,0,0,1440,1024,1440,1024);
        g.pose().popPose();
        int mapLeft=Math.round(left+AtlasInkPainter.MAP_X*scale),mapTop=Math.round(top+AtlasInkPainter.MAP_Y*scale),mapSize=Math.round(AtlasInkPainter.MAP_SIZE*scale);
        g.enableScissor(mapLeft,mapTop,mapLeft+mapSize,mapTop+mapSize);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.pose().pushPose();g.pose().translate(mapLeft+viewport.dx()*mapSize,mapTop+viewport.dy()*mapSize,0);g.pose().scale((float)(mapSize/880.0*viewport.zoom()),(float)(mapSize/880.0*viewport.zoom()),1);
        g.blit(textures.map(),0,0,0,0,880,880,880,880);g.pose().popPose();
        int name=0;
        if(overworld()){
            double x=minecraft.player.getX(),z=minecraft.player.getZ();
            if(Math.abs(x)<=atlas.radius()&&Math.abs(z)<=atlas.radius()){
                int ix=(int)Math.round((x+atlas.radius())/(2*atlas.radius())*255),iz=(int)Math.round((z+atlas.radius())/(2*atlas.radius())*255);name=cells[iz*256+ix]&127;
                double px=mapLeft+viewport.mapX(x,atlas.radius())*mapSize,pz=mapTop+viewport.mapZ(z,atlas.radius())*mapSize;
                g.pose().pushPose();g.pose().translate(px,pz,0);g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-26));
                g.fill(-1,-10,1,2,0xff465e51);g.fill(-3,1,4,2,0xff343e37);g.fill(0,2,1,6,0xff343e37);g.fill(1,6,2,9,0xff9f4e38);g.pose().popPose();
            }
        }else name=textures.names().size()-1;
        g.disableScissor();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.pose().pushPose();g.pose().translate(left,top,0);g.pose().scale(scale,scale,1);
        g.blit(textures.names().get(name),1050,663,0,0,220,60,220,60);
        g.blit(textures.scale(),97,903,0,0,220,60,220,60);g.pose().popPose();
        if(!overworld()||(minecraft.player!=null&&(Math.abs(minecraft.player.getX())>atlas.radius()||Math.abs(minecraft.player.getZ())>atlas.radius())))
            g.drawCenteredString(font,tr(overworld()?"outside":"other_dimension"),width/2,Math.max(1,Math.round(top+48*scale)),0xff7c8274);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        super.render(g,mx,my,tick);
    }
    private boolean inMap(double x,double y){return x>=left+83*scale&&x<left+963*scale&&y>=top+93*scale&&y<top+973*scale;}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==0&&inMap(x,y)&&ready()){dragging=true;return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseReleased(double x,double y,int button){if(dragging)report("pan");dragging=false;return super.mouseReleased(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(dragging&&button==0){viewport.pan(dx/(880*scale),dy/(880*scale));return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(inMap(x,y)&&ready()){viewport.scale(vertical>0?1.12:1/1.12,(x-left-83*scale)/(880*scale),(y-top-93*scale)/(880*scale));report("wheel");return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(com.example.myvillage.client.cultivation.ClientCultivationKeyMappings.OPEN_PROFILE.matches(key,scan)){onClose();return true;}return super.keyPressed(key,scan,mods);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float tick){}
}
