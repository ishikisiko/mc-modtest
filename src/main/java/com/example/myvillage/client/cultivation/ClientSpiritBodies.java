package com.example.myvillage.client.cultivation;

import com.example.myvillage.cultivation.fate.AdaptationCategory;
import com.example.myvillage.cultivation.fate.PhoenixFormPayload;
import com.example.myvillage.cultivation.fate.SpiritBodyStatusPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** 客户端仅显示服务器快照；形态记录同时核对实体 UUID，避免实体编号复用。 */
@EventBusSubscriber(modid="myvillage",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClientSpiritBodies {
    private static ClientLevel world;
    private static SpiritBodyStatusPayload status;
    private static boolean forcedPose;
    private static final Map<Integer,UUID> EMBERS=new HashMap<>();
    private ClientSpiritBodies() { }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(()->{
            SpiritBodyStatusPayload.installReceiver(p->{ensureWorld();status=p;});
            PhoenixFormPayload.installReceiver(p->{ensureWorld();EMBERS.entrySet().removeIf(e->e.getValue().equals(p.owner()));if(p.ember())EMBERS.put(p.entityId(),p.owner());});
            NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Pre e)->{if(ember(e.getEntity()))e.setCanceled(true);});
            NeoForge.EVENT_BUS.addListener((RenderHandEvent e)->{if(ember(Minecraft.getInstance().player))e.setCanceled(true);});
            NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e)->clear());
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Opening e)->{
                var current=status();if(current!=null&&current.mode()==3&&e.getNewScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)e.setCanceled(true);
            });
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Pre e)->{
                var mc=Minecraft.getInstance();var current=status();if(mc.player==null)return;
                if(current!=null&&current.mode()==3){mc.player.setForcedPose(net.minecraft.world.entity.Pose.SWIMMING);mc.player.setSprinting(false);mc.options.keySprint.setDown(false);forcedPose=true;}
                else if(forcedPose){mc.player.setForcedPose(null);forcedPose=false;}
            });
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.MovementInputUpdateEvent e)->{var current=status();if(current!=null&&current.mode()==3)e.getInput().jumping=false;});
        });
    }
    @SubscribeEvent public static void layers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR,ResourceLocation.parse("myvillage:spirit_body"),ClientSpiritBodies::render);
    }
    private static void ensureWorld(){if(world!=Minecraft.getInstance().level){clear();world=Minecraft.getInstance().level;}}
    private static void clear(){world=null;status=null;EMBERS.clear();forcedPose=false;}
    public static SpiritBodyStatusPayload status(){ensureWorld();return status;}
    public static boolean ember(Entity entity) {
        ensureWorld();if(entity==null)return false;
        return entity.getUUID().equals(EMBERS.get(entity.getId()))
                ||entity==Minecraft.getInstance().player&&status!=null&&status.mode()==3;
    }
    public static List<Component> details() {
        var p=status();if(p==null||p.mode()==0)return List.of();
        var lines=new ArrayList<Component>();
        if(p.mode()==1) {
            double total=p.adaptation().stream().mapToDouble(Double::doubleValue).sum();
            lines.add(Component.literal(String.format(Locale.ROOT,"适应容量 %.1f / %.0f",total,p.adaptationCapacity())));
            for(var c:AdaptationCategory.values()) {
                double points=p.adaptation().get(c.ordinal());if(points<=0)continue;
                lines.add(Component.literal(String.format(Locale.ROOT,"%s适应 %.1f，对应肉身防御 +%.2f",c.label(),points,points*p.bodyDefense()*p.defensePerPoint())));
            }
            if(total<=0)lines.add(Component.literal("承受敌方有效攻击后，肉身逐渐形成适应。"));
        } else if(p.mode()==2) {
            lines.add(Component.literal(String.format(Locale.ROOT,"火种 %.1f / %.0f，%s",p.reserve(),p.reserveCapacity(),p.reserve()>=p.rebirthThreshold()?"可以涅槃":"正在温养")));
            lines.add(Component.literal(String.format(Locale.ROOT,"自动自愈保留 %.0f 点火种。",p.rebirthThreshold())));
        } else {
            lines.add(Component.literal(String.format(Locale.ROOT,"余烬耐久 %.1f / %.1f",p.emberHealth(),p.emberMaximum())));
            lines.add(Component.literal(String.format(Locale.ROOT,"还需凝聚 %.1f 秒，可以缓慢移动。",p.remaining()/20.0)));
        }
        return List.copyOf(lines);
    }
    private static void render(GuiGraphics g,DeltaTracker delta) {
        var mc=Minecraft.getInstance();var p=status();
        if(p==null||p.mode()==0||mc.player==null||mc.options.hideGui||mc.player.isSpectator())return;
        int x=12,y=g.guiHeight()-106,w=190,color=p.mode()==1?0xffe8c275:0xffefab77;
        if(p.mode()==1) {
            var categories=new ArrayList<AdaptationCategory>(List.of(AdaptationCategory.values()));
            categories.sort(Comparator.comparingDouble((AdaptationCategory c)->p.adaptation().get(c.ordinal())).reversed());
            int row=0;
            for(var c:categories.subList(0,2)) {
                double points=p.adaptation().get(c.ordinal());if(points<=0)continue;
                g.drawString(mc.font,String.format(Locale.ROOT,"%s适应 %.1f  防御 +%.2f",c.label(),points,points*p.bodyDefense()*p.defensePerPoint()),x,y+row++*11,color,true);
            }
        } else {
            String label=p.mode()==3?String.format(Locale.ROOT,"凝聚剩余 %.1f 秒",p.remaining()/20.0):p.reserve()>=p.rebirthThreshold()?"火种充足，可以涅槃":"火种温养中";
            String value=p.mode()==3?String.format(Locale.ROOT,"%.1f / %.1f",p.emberHealth(),p.emberMaximum()):String.format(Locale.ROOT,"%.1f / %.0f",p.reserve(),p.reserveCapacity());
            g.drawString(mc.font,label,x,y,color,true);g.drawString(mc.font,value,x+w-mc.font.width(value),y,color,true);
            g.fill(x,y+12,x+w,y+18,0xc0201c19);
            double ratio=p.mode()==3?(p.emberMaximum()<=0?0:p.emberHealth()/p.emberMaximum()):p.reserve()/p.reserveCapacity();
            g.fill(x,y+13,x+(int)(w*Math.max(0,Math.min(1,ratio))),y+17,color);
            if(p.mode()==3)g.drawString(mc.font,"只能缓慢移动，浸水会熄灭火种。",x,y+23,0xfff3d9c0,true);
        }
    }
}
