package com.example.myvillage.client.story;

import com.example.myvillage.client.cultivation.CultivationProfileScreen;
import com.example.myvillage.client.cultivation.panel.NpcFateLayout;
import com.example.myvillage.client.cultivation.panel.WorldPage;
import com.example.myvillage.client.sim.ClientWorldSimState;
import com.example.myvillage.sim.fate.NpcFateRoll;
import com.example.myvillage.sim.runtime.net.WorldSimQuery;
import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** 直接准备页面后的真实客户端绘制检查，不宣称映射输入或玩家验收。 */
@EventBusSubscriber(modid="myvillage",value=Dist.CLIENT)
public final class NpcFateUiClientEvidence {
    private static int stage,ticks,personId;
    private static JsonObject fixture;
    private static final List<String> images=new ArrayList<>();
    private static Path out;
    private static Object field(Class<?> type,Object value,String name)throws Exception {
        var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(value);
    }
    private static void set(Class<?> type,Object value,String name,Object replacement)throws Exception {
        var f=type.getDeclaredField(name);f.setAccessible(true);f.set(value,replacement);
    }
    private static void capture(Minecraft mc,String name)throws Exception {
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(out.resolve(name+".png"));}
        images.add(name+".png");
    }
    private static void mouse(Minecraft mc,int x,int y) {
        var window=mc.getWindow();
        GLFW.glfwSetCursorPos(window.getWindow(),x*(double)window.getScreenWidth()/window.getGuiScaledWidth(),
                y*(double)window.getScreenHeight()/window.getGuiScaledHeight());
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception {
        String target=System.getenv("MYVILLAGE_NPC_FATE_UI_OUT");
        if(target==null||stage==99)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        out=Path.of(target);if(!Files.exists(out.resolve("fixture.json")))return;
        if(fixture==null){fixture=JsonParser.parseString(Files.readString(out.resolve("fixture.json"))).getAsJsonObject();personId=fixture.get("world_person_id").getAsInt();}
        if(++ticks<12)return;ticks=0;
        try {
            if(stage==0){
                if(!(mc.screen instanceof NpcDialogueScreen screen))return;
                var actual=(NpcFateRoll)field(NpcDialogueScreen.class,screen,"fates");
                if(actual==null||!actual.toJson().equals(fixture.get("npc_fates")))throw new IllegalStateException("NPC 交谈词条同步与出生记录不符");
                capture(mc,"npc-dialogue");
                var body=screen.layout.body();var rect=NpcFateLayout.card(body.x(),body.y(),body.width(),0);
                mouse(mc,rect.x()+20,rect.y()+15);stage=1;
            }else if(stage==1){
                capture(mc,"npc-dialogue-tooltip");
                mc.screen.onClose();
                var screen=new CultivationProfileScreen();
                var view=Class.forName(CultivationProfileScreen.class.getName()+"$View");
                set(CultivationProfileScreen.class,screen,"view",Enum.valueOf((Class)view,"WORLD"));
                var type=Class.forName(WorldPage.class.getName()+"$Target");var ctor=type.getDeclaredConstructor(boolean.class,int.class);ctor.setAccessible(true);
                set(WorldPage.class,null,"detail",ctor.newInstance(false,personId));
                mc.setScreen(screen);ClientWorldSimState.request(WorldSimQuery.person(personId));stage=2;
            }else if(stage==2){
                var snapshot=ClientWorldSimState.latest(WorldSimQuery.person(personId)).orElse(null);
                if(snapshot==null)return;
                if(snapshot.person()==null||!snapshot.person().fates().toJson().equals(fixture.get("world_fates")))throw new IllegalStateException("命簿词条同步与人物出生记录不符");
                capture(mc,"ledger-person");stage=3;
            }else if(stage==3||stage==4){
                if(stage==4)capture(mc,"ledger-person-scroll-180");
                var screen=(CultivationProfileScreen)mc.screen;
                var scrolls=(Map)field(CultivationProfileScreen.class,screen,"scrolls");
                var view=field(CultivationProfileScreen.class,screen,"view");
                scrolls.put(view,stage==3?180:300);stage++;
            }else if(stage==5){
                capture(mc,"ledger-person-scrolled");
                var result=new JsonObject();result.addProperty("pass",true);result.addProperty("npc_payload_matches_birth",true);result.addProperty("ledger_payload_matches_birth",true);
                result.addProperty("mapped_input","not_verified");result.addProperty("player_acceptance","not_verified");result.addProperty("preparation","developer fixture and direct page selection");
                result.add("images",new Gson().toJsonTree(images));Files.writeString(out.resolve("client-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
                stage=99;mc.stop();
            }
        }catch(Throwable failure){
            Files.writeString(out.resolve("client-failure.txt"),failure.toString());stage=99;throw failure;
        }
    }
}
