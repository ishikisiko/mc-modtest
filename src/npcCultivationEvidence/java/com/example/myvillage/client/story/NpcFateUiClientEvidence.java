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
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/** 直接准备页面后的真实客户端绘制检查，不宣称映射输入或玩家验收。 */
@EventBusSubscriber(modid="myvillage",value=Dist.CLIENT)
public final class NpcFateUiClientEvidence {
    private static int stage,ticks,personId;
    private static JsonObject fixture;
    private static final List<String> images=new ArrayList<>();
    private static Path out;
    private static String pending;
    private static final List<String> capturedScreens=new ArrayList<>();
    private static Object field(Class<?> type,Object value,String name)throws Exception {
        var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(value);
    }
    private static void set(Class<?> type,Object value,String name,Object replacement)throws Exception {
        var f=type.getDeclaredField(name);f.setAccessible(true);f.set(value,replacement);
    }
    private static void capture(String name) { pending=name; }
    @SubscribeEvent public static void rendered(ScreenEvent.Render.Post event)throws Exception {
        if(pending==null)return;
        boolean npc=pending.startsWith("npc-");
        if(npc&&!(event.getScreen() instanceof NpcDialogueScreen)||!npc&&!(event.getScreen() instanceof CultivationProfileScreen))return;
        if(pending.equals("npc-dialogue-tooltip")) {
            var cards=field(NpcDialogueScreen.class,event.getScreen(),"fateCards");
            if(((List<?>)field(com.example.myvillage.client.cultivation.panel.NpcFateCards.class,cards,"hovered")).isEmpty())return;
        }
        event.getGuiGraphics().flush();
        var mc=Minecraft.getInstance();
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(out.resolve(pending+".png"));}
        images.add(pending+".png");capturedScreens.add(event.getScreen().getClass().getSimpleName());pending=null;
    }
    private static void mouse(Minecraft mc,int x,int y)throws Exception {
        var window=mc.getWindow();
        double px=x*(double)window.getScreenWidth()/window.getGuiScaledWidth(),py=y*(double)window.getScreenHeight()/window.getGuiScaledHeight();
        GLFW.glfwSetCursorPos(window.getWindow(),px,py);
        // 开发者准备鼠标坐标，不将此方法计作玩家映射输入。
        set(net.minecraft.client.MouseHandler.class,mc.mouseHandler,"xpos",px);
        set(net.minecraft.client.MouseHandler.class,mc.mouseHandler,"ypos",py);
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception {
        String target=System.getenv("MYVILLAGE_NPC_FATE_UI_OUT");
        if(target==null||stage==99)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        out=Path.of(target);if(!Files.exists(out.resolve("fixture.json")))return;
        if(fixture==null){fixture=JsonParser.parseString(Files.readString(out.resolve("fixture.json"))).getAsJsonObject();personId=fixture.get("world_person_id").getAsInt();}
        if(pending!=null||++ticks<12)return;ticks=0;
        try {
            if(stage==0){
                if(!(mc.screen instanceof NpcDialogueScreen screen))return;
                var actual=(NpcFateRoll)field(NpcDialogueScreen.class,screen,"fates");
                if(actual==null||!actual.toJson().equals(fixture.get("npc_fates")))throw new IllegalStateException("NPC 交谈词条同步与出生记录不符");
                capture("npc-dialogue");stage=1;
            }else if(stage==1){
                var screen=(NpcDialogueScreen)mc.screen;var body=screen.layout.body();
                var rect=NpcFateLayout.card(body.x(),body.y(),body.width(),0);mouse(mc,rect.x()+20,rect.y()+15);stage=2;
            }else if(stage==2){
                capture("npc-dialogue-tooltip");stage=3;
            }else if(stage==3){
                mc.screen.onClose();
                var screen=new CultivationProfileScreen();
                var view=Class.forName(CultivationProfileScreen.class.getName()+"$View");
                set(CultivationProfileScreen.class,screen,"view",Enum.valueOf((Class)view,"WORLD"));
                var type=Class.forName(WorldPage.class.getName()+"$Target");var ctor=type.getDeclaredConstructor(boolean.class,int.class);ctor.setAccessible(true);
                set(WorldPage.class,null,"detail",ctor.newInstance(false,personId));
                mc.setScreen(screen);ClientWorldSimState.request(WorldSimQuery.person(personId));stage=4;
            }else if(stage==4){
                var snapshot=ClientWorldSimState.latest(WorldSimQuery.person(personId)).orElse(null);
                if(snapshot==null)return;
                if(snapshot.person()==null||!snapshot.person().fates().toJson().equals(fixture.get("world_fates")))throw new IllegalStateException("命簿词条同步与人物出生记录不符");
                capture("ledger-person");stage=5;
            }else if(stage==5||stage==7){
                var screen=(CultivationProfileScreen)mc.screen;
                var scrolls=(Map)field(CultivationProfileScreen.class,screen,"scrolls");
                var view=field(CultivationProfileScreen.class,screen,"view");
                scrolls.put(view,stage==5?180:300);stage++;
            }else if(stage==6){capture("ledger-person-scroll-180");stage=7;
            }else if(stage==8){capture("ledger-person-scrolled");stage=9;
            }else if(stage==9){
                if(images.size()!=5)throw new IllegalStateException("原生页面截图数量不完整");
                var result=new JsonObject();result.addProperty("pass",true);result.addProperty("npc_payload_matches_birth",true);result.addProperty("ledger_payload_matches_birth",true);
                result.addProperty("mapped_input","not_verified");result.addProperty("player_acceptance","not_verified");result.addProperty("preparation","developer fixture and direct page/pointer preparation; capture after screen render");
                result.add("images",new Gson().toJsonTree(images));result.add("captured_screens",new Gson().toJsonTree(capturedScreens));Files.writeString(out.resolve("client-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
                stage=99;mc.stop();
            }
        }catch(Throwable failure){
            Files.writeString(out.resolve("client-failure.txt"),failure.toString());stage=99;throw failure;
        }
    }
}
