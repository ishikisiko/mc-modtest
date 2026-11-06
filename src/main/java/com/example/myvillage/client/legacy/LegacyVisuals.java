package com.example.myvillage.client.legacy;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.legacy.LegacyProjectionEntity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Presentation is inferred only from tracked server projections; never hides inventory items. */
@EventBusSubscriber(modid=MyVillageMod.MOD_ID,value=Dist.CLIENT)
public final class LegacyVisuals {
    private static final Map<Integer,Item> DEPLOYED=new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private LegacyVisuals(){}
    public static boolean deployed(LivingEntity e){return DEPLOYED.get(e.getId())==e.getMainHandItem().getItem();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.level!=world){DEPLOYED.clear();world=mc.level;}
        LegacyVfx.tick(mc);
        if(mc.level==null||mc.player==null){DEPLOYED.clear();return;}
        if(mc.player.tickCount%4!=0)return;
        DEPLOYED.clear();
        for(var e:mc.level.getEntitiesOfClass(LegacyProjectionEntity.class,mc.player.getBoundingBox().inflate(48),e->!e.isRemoved()))
            if(e.kind()==LegacyProjectionEntity.SWORD||e.kind()==LegacyProjectionEntity.RULER)DEPLOYED.put(e.ownerId(),e.visualStack().getItem());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hand(RenderHandEvent e){
        var p=Minecraft.getInstance().player;
        if(p!=null&&e.getHand()==InteractionHand.MAIN_HAND&&deployed(p))e.setCanceled(true);
    }
}
