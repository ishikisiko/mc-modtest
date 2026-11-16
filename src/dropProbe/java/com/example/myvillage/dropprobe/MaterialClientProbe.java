package com.example.myvillage.dropprobe;

import com.example.myvillage.client.commerce.CommerceScreen;
import com.example.myvillage.sim.commerce.CommerceRules;
import com.example.myvillage.sim.runtime.commerce.CommerceMenu;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Render fixture only: opens the real screen; subsequent filter changes use mapped mouse input. */
@EventBusSubscriber(modid="yuanxian_drop_probe",value=Dist.CLIENT)
public final class MaterialClientProbe {
    private static int ticks;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(++ticks%10!=0)return;var mc=Minecraft.getInstance();var request=Path.of("material-preview.request");
        if(mc.player==null||mc.level==null||!Files.isRegularFile(request))return;
        try {
            String action=Files.readString(request).trim();
            if(action.equals("open")) {
                CommerceRules rules;
                try(var stream=com.example.myvillage.resources.BuiltinResources.open("data/yuanxian/world_sim/commerce_rules.json")) {
                    rules=CommerceRules.parse("client-render-fixture",JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject());
                }
                var menu=new CommerceMenu(123,mc.player.getInventory(),null,123L,rules.currency(),rules.stockLimit(),rules.offers("smith"));
                for(int i=0;i<menu.offers().size();i++)menu.setData(i+2,menu.offers().get(i).recycling()?-1:16);
                mc.setScreen(new CommerceScreen(menu,mc.player.getInventory(),Component.literal("城镇铁匠 · 妖兽材料回收")));
            }
            if(!(mc.screen instanceof CommerceScreen))return;
            var result=new JsonObject();result.addProperty("scale",mc.getWindow().getGuiScale());
            result.addProperty("width",mc.getWindow().getWidth());result.addProperty("height",mc.getWindow().getHeight());
            var buttons=new JsonArray();
            for(var child:mc.screen.children())if(child instanceof AbstractWidget widget) {
                var b=new JsonObject();b.addProperty("label",widget.getMessage().getString());b.addProperty("x",widget.getX());b.addProperty("y",widget.getY());
                b.addProperty("width",widget.getWidth());b.addProperty("height",widget.getHeight());b.addProperty("visible",widget.visible);b.addProperty("active",widget.active);buttons.add(b);
            }
            result.add("widgets",buttons);var temporary=Path.of("material-preview-state.tmp");Files.writeString(temporary,result.toString());
            Files.move(temporary,Path.of("material-preview-state.json"),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);Files.delete(request);
        }catch(Exception error){throw new IllegalStateException("材料菜单预览夹具失败",error);}
    }
    private MaterialClientProbe(){}
}
