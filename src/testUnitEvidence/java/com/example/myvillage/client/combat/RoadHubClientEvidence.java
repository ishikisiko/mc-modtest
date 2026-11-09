package com.example.myvillage.client.combat;

import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** 真实客户端画面采集；传送与视角属于 fixture 准备。 */
@EventBusSubscriber(modid="myvillage",value=Dist.CLIENT)
public final class RoadHubClientEvidence {
    private static String pending,last="";private static int ticks;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception {
        String folder=System.getenv("MYVILLAGE_ROAD_HUB_OUT");if(folder==null)return;var path=Path.of(folder).resolve("capture-request.json");
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||!Files.isRegularFile(path))return;
        var request=JsonParser.parseString(Files.readString(path)).getAsJsonObject();String name=request.get("name").getAsString();
        if(name.equals(last))return;
        if(++ticks<100)return;ticks=0;last=name;pending=name;
    }
    @SubscribeEvent public static void rendered(RenderGuiEvent.Post event)throws Exception {
        if(pending==null)return;var mc=Minecraft.getInstance();var out=Path.of(System.getenv("MYVILLAGE_ROAD_HUB_OUT"));
        event.getGuiGraphics().flush();
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(out.resolve(pending+".png"));}
        var data=new JsonObject();data.addProperty("status","captured_for_review");data.addProperty("width",mc.getWindow().getWidth());data.addProperty("height",mc.getWindow().getHeight());
        data.addProperty("x",mc.player.getX());data.addProperty("y",mc.player.getY());data.addProperty("z",mc.player.getZ());
        Files.writeString(out.resolve(pending+".json"),data.toString());pending=null;
    }
}
