package com.example.myvillage.client.combat;

import com.example.myvillage.client.item.QualityPickerScreen;
import com.example.myvillage.client.item.TestUnitScreen;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.network.TestUnitPayload;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 真实客户端的数据包、配置页面和保存回执；界面由开发者直接准备。 */
@EventBusSubscriber(modid="myvillage",value=Dist.CLIENT)
public final class NpcSkillClientEvidence {
    private static int stage,ticks;
    private static TestUnitScreen screen;
    private static Path out;
    private static JsonObject fixture;
    private static String pending;
    private static boolean artReceived,waveReceived;
    private static final List<String> images=new ArrayList<>();
    private static Object get(Class<?> type,Object value,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static void set(Class<?> type,Object value,String name,Object next)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);f.set(value,next);}
    private static void invoke(String name)throws Exception{var m=TestUnitScreen.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(screen);}
    private static void check(boolean ok,String text){if(!ok)throw new IllegalStateException(text);}
    @SubscribeEvent public static void rendered(ScreenEvent.Render.Post event)throws Exception{
        if(pending==null||event.getScreen()!=screen)return;
        for(var child:screen.children())if(child instanceof AbstractWidget widget)
            check(widget.getX()>=0&&widget.getY()>=0&&widget.getX()+widget.getWidth()<=screen.width&&widget.getY()+widget.getHeight()<=screen.height,
                    "控件超出窗口："+widget.getMessage().getString());
        event.getGuiGraphics().flush();var mc=Minecraft.getInstance();
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(out.resolve(pending+".png"));}
        images.add(pending+".png");pending=null;
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        String dir=System.getenv("MYVILLAGE_NPC_SKILLS_OUT");if(dir==null||stage==99)return;out=Path.of(dir);
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        var arts=(Map<Integer,?>)get(ClientAdvancedArts.class,null,"STAMPS");
        artReceived|=arts.keySet().stream().anyMatch(id->mc.level.getEntity(id) instanceof NpcEntity);
        var waves=(Map<Integer,?>)get(ClientSwordQi.class,null,"CHARGES");
        waveReceived|=waves.keySet().stream().anyMatch(id->mc.level.getEntity(id) instanceof NpcEntity);
        if(Files.exists(out.resolve("server-failure.json"))){stage=99;mc.stop();return;}
        if(!Files.exists(out.resolve("server-result.json"))||pending!=null||++ticks<16)return;ticks=0;
        try{
            if(stage==0){
                fixture=JsonParser.parseString(Files.readString(out.resolve("fixture.json"))).getAsJsonObject();
                mc.options.guiScale().set(2);mc.resizeDisplay();screen=new TestUnitScreen(new QualityPickerScreen());mc.setScreen(screen);stage=1;return;
            }
            if(stage==1){
                var data=new JsonObject();data.addProperty("unit",fixture.get("saved_id").getAsString());
                PacketDistributor.sendToServer(new TestUnitPayload("inspect_skills",data.toString()));stage=2;return;
            }
            if(stage==2){
                if(get(TestUnitScreen.class,screen,"skillUnit")==null)return;
                var rows=(Map<String,Object>)get(TestUnitScreen.class,screen,"skillDrafts");var wave=rows.get("myvillage:jianqi_jue");
                check(wave!=null&&get(wave.getClass(),wave,"mastery").equals("1200"),"服务器技能读取未正确同步");
                set(TestUnitScreen.class,screen,"selectedSkill","myvillage:jianqi_jue");invoke("rebuild");pending="npc-skills-loaded";stage=3;return;
            }
            if(stage==3){
                var rows=(Map<String,Object>)get(TestUnitScreen.class,screen,"skillDrafts");var wave=rows.get("myvillage:jianqi_jue");
                set(wave.getClass(),wave,"mastery","1400");set(wave.getClass(),wave,"priority","7");set(wave.getClass(),wave,"automatic",false);
                invoke("applySkills");stage=4;return;
            }
            if(stage==4){
                if(!((String)get(TestUnitScreen.class,screen,"message")).startsWith("已应用"))return;
                var rows=(Map<String,Object>)get(TestUnitScreen.class,screen,"skillDrafts");var wave=rows.get("myvillage:jianqi_jue");
                check(get(wave.getClass(),wave,"mastery").equals("1400")&&get(wave.getClass(),wave,"priority").equals("7")
                        &&Boolean.FALSE.equals(get(wave.getClass(),wave,"automatic")),"技能修改没有按服务器回执落定");
                pending="npc-skills-applied";stage=5;return;
            }
            if(stage==5){
                var rows=(Map<String,Object>)get(TestUnitScreen.class,screen,"skillDrafts");
                Class<?> type=Class.forName(TestUnitScreen.class.getName()+"$SkillDraft");var ctor=type.getDeclaredConstructor(boolean.class);ctor.setAccessible(true);
                var core=ctor.newInstance(false);set(type,core,"mastery","1800");rows.put("myvillage:qingmu_changchun_gong",core);
                set(TestUnitScreen.class,screen,"runningCore","myvillage:qingmu_changchun_gong");set(TestUnitScreen.class,screen,"selectedSkill","myvillage:qingmu_changchun_gong");
                invoke("applySkills");stage=6;return;
            }
            if(stage==6){
                if(!((String)get(TestUnitScreen.class,screen,"message")).startsWith("已应用"))return;
                check(get(TestUnitScreen.class,screen,"runningCore").equals("myvillage:qingmu_changchun_gong"),"运行心法没有正确回执");
                invoke("rebuild");pending="npc-skills-core";stage=7;return;
            }
            if(stage==7){org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),960,720);mc.options.guiScale().set(3);mc.resizeDisplay();stage=8;return;}
            if(stage==8){invoke("rebuild");pending="npc-skills-core-narrow";stage=9;return;}
            if(stage==9){set(TestUnitScreen.class,screen,"selectedSkill","myvillage:jianqi_jue");invoke("rebuild");pending="npc-skills-narrow";stage=10;return;}
            if(stage==10){
                check(artReceived&&waveReceived,"未观察到 NPC 武技和剑气的正式表现数据包");
                check(Files.exists(out.resolve("applied-result.json")),"服务器没有确认已写入技能配置");
                var data=new JsonObject();data.addProperty("pass",true);data.addProperty("npc_art_payload",artReceived);data.addProperty("npc_wave_payload",waveReceived);
                data.addProperty("server_config_read_write",true);data.addProperty("mapped_input","not_verified");data.addProperty("player_acceptance","not_verified");
                data.add("images",new Gson().toJsonTree(images));Files.writeString(out.resolve("client-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(data));stage=99;mc.stop();
            }
        }catch(Throwable failure){Files.writeString(out.resolve("client-failure.txt"),failure.toString());stage=99;throw failure;}
    }
}
