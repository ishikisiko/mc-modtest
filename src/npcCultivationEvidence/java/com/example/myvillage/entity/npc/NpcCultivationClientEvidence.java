package com.example.myvillage.entity.npc;

import com.example.myvillage.client.cultivation.panel.CultivationInkTheme;
import com.example.myvillage.client.story.NpcProdigyLabel;
import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** 采集器只读客户端收到的身份与当前屏幕；打开页面仍须映射右键输入。 */
@EventBusSubscriber(modid="myvillage",value=Dist.CLIENT)
public final class NpcCultivationClientEvidence {
    private static int ticks;
    @SubscribeEvent public static void tick(ClientTickEvent.Post e)throws Exception{
        if(++ticks%10!=0)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        var out=Path.of(System.getenv().getOrDefault("MYVILLAGE_NPC_CULTIVATION_OUT","../reports/npc-native"));Files.createDirectories(out);
        List<Map<String,Object>> people=new ArrayList<>();
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof NpcEntity npc&&people.size()<64)
            people.add(Map.of("name",npc.getName().getString(),"entity_id",npc.getId(),"prodigy",npc.prodigy(),"badge_visible",NpcProdigyLabel.visible(npc.getId())));
        var state=Map.of("screen",mc.screen==null?"none":mc.screen.getClass().getName(),"npcs",people,
            "brush_width",mc.font.width(CultivationInkTheme.brush("天骄")),"gui_scale",mc.getWindow().getGuiScale());
        Files.writeString(out.resolve("client-screen.json"),new Gson().toJson(state));
    }
}
