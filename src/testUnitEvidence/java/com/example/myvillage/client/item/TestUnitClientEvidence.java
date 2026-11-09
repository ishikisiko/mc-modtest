package com.example.myvillage.client.item;

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

/** 原生页面绘制证据；直接准备页面，不计作玩家映射输入。 */
@EventBusSubscriber(modid="myvillage",value=Dist.CLIENT)
public final class TestUnitClientEvidence {
    private static int ticks,stage;
    private static String pending;
    private static TestUnitScreen screen;
    private static final List<String> images=new ArrayList<>();
    private static Path out;
    private static void set(String field,Object value)throws Exception {
        var f=TestUnitScreen.class.getDeclaredField(field);f.setAccessible(true);f.set(screen,value);
    }
    private static void refresh() throws Exception {
        var method=TestUnitScreen.class.getDeclaredMethod("rebuild");method.setAccessible(true);method.invoke(screen);
    }
    @SubscribeEvent public static void render(ScreenEvent.Render.Post event)throws Exception {
        if(pending==null||event.getScreen()!=screen)return;
        for(var child:screen.children())if(child instanceof AbstractWidget w)
            if(w.getX()<0||w.getY()<0||w.getX()+w.getWidth()>screen.width||w.getY()+w.getHeight()>screen.height)
                throw new IllegalStateException("组件超出窗口："+w.getMessage().getString());
        event.getGuiGraphics().flush();var mc=Minecraft.getInstance();
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(out.resolve(pending+".png"));}
        images.add(pending+".png");pending=null;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception {
        String target=System.getenv("MYVILLAGE_TEST_UNIT_OUT");if(target==null||stage==99)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;out=Path.of(target);
        if(Files.exists(out.resolve("server-failure.txt"))){mc.stop();stage=99;return;}
        if(!Files.exists(out.resolve("server-result.json"))||pending!=null||++ticks<16)return;ticks=0;
        try {
            if(stage==0){mc.options.guiScale().set(2);mc.resizeDisplay();screen=new TestUnitScreen(new QualityPickerScreen());mc.setScreen(screen);stage++;return;}
            if(stage>=1&&stage<=5){set("tab",stage-1);refresh();pending="test-unit-tab-"+(stage-1);stage++;return;}
            if(stage==6){
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),960,720);
                mc.options.guiScale().set(3);mc.resizeDisplay();stage++;return;
            }
            if(stage>=7&&stage<=11){set("tab",stage-7);refresh();pending="test-unit-narrow-tab-"+(stage-7);stage++;return;}
            var data=new JsonObject();data.addProperty("pass",images.size()==10);data.add("images",new Gson().toJsonTree(images));
            data.addProperty("mapped_input","not_verified");data.addProperty("player_acceptance","not_verified");
            data.addProperty("width",screen.width);data.addProperty("height",screen.height);
            Files.writeString(out.resolve("client-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(data));stage=99;mc.stop();
        }catch(Throwable failure){Files.writeString(out.resolve("client-failure.txt"),failure.toString());stage=99;throw failure;}
    }
}
