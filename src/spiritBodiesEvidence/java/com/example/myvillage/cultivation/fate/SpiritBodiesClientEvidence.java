package com.example.myvillage.cultivation.fate;

import com.example.myvillage.client.cultivation.ClientSpiritBodies;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** 仅采集真实客户端缓存与网络重连；操作输入仍由外部映射按键提供。 */
@EventBusSubscriber(modid="myvillage",value=Dist.CLIENT)
public final class SpiritBodiesClientEvidence {
    private static int tick,reconnectAt=-1,sequence=-1;
    private static final List<Map<String,Object>> HISTORY=new ArrayList<>();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        String destination=System.getenv("MYVILLAGE_SPIRIT_CLIENT_OUT");if(destination==null)return;
        try {
            var mc=Minecraft.getInstance();var out=Path.of(destination);Files.createDirectories(out);tick++;
            var control=out.resolve("control.json");
            if(Files.exists(control)) {
                var command=JsonParser.parseString(Files.readString(control)).getAsJsonObject();int next=command.get("sequence").getAsInt();
                if(next!=sequence) {
                    sequence=next;
                    if(command.get("action").getAsString().equals("reconnect")) {
                        if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen());reconnectAt=tick+command.get("delay_ticks").getAsInt();
                    }
                }
            }
            if(reconnectAt>=0&&tick>=reconnectAt) {
                reconnectAt=-1;String address=System.getenv().getOrDefault("MYVILLAGE_SPIRIT_SERVER","127.0.0.1:25565");
                ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(address),new ServerData("灵体验收",address,ServerData.Type.OTHER),false,null);
            }
            if(tick%5!=0)return;
            var row=new LinkedHashMap<String,Object>();row.put("tick",tick);row.put("screen",mc.screen==null?"":mc.screen.getClass().getSimpleName());row.put("connected",mc.player!=null&&mc.level!=null);
            if(mc.player!=null&&mc.level!=null) {
                row.put("name",mc.player.getGameProfile().getName());row.put("position",List.of(mc.player.getX(),mc.player.getY(),mc.player.getZ()));row.put("health",mc.player.getHealth());
                var status=ClientSpiritBodies.status();row.put("spirit",status);row.put("ember_self",ClientSpiritBodies.ember(mc.player));
                var visible=new ArrayList<String>();for(var player:mc.level.players())if(ClientSpiritBodies.ember(player))visible.add(player.getGameProfile().getName());row.put("visible_embers",visible);
                if(status!=null&&status.mode()!=0){HISTORY.add(row);if(HISTORY.size()>1000)HISTORY.remove(0);}
            }
            var gson=new GsonBuilder().setPrettyPrinting().create();Files.writeString(out.resolve("state.json"),gson.toJson(row));Files.writeString(out.resolve("history.json"),gson.toJson(HISTORY));
        }catch(Throwable e){e.printStackTrace();}
    }
}
