package com.example.myvillage.client.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Optional read-only native UI evidence. Actions still use mapped keyboard/mouse, never this observer. */
@EventBusSubscriber(modid="myvillage",value=Dist.CLIENT)
public final class WorldUiEvidence {
    private static final boolean ENABLED=Boolean.getBoolean("myvillage.uiEvidence");
    private static String last="";
    @SubscribeEvent public static void rendered(ScreenEvent.Render.Post event) {
        if(!ENABLED)return;
        var screen=event.getScreen();String name=screen.getClass().getSimpleName();
        if(!java.util.Set.of("CreateWorldScreen","CultivationProfileScreen","PauseScreen","TitleScreen",
                "SelectWorldScreen","JoinMultiplayerScreen","ConfirmScreen").contains(name))return;
        JsonObject row=new JsonObject();row.addProperty("screen",name);row.addProperty("width",screen.width);row.addProperty("height",screen.height);
        JsonArray widgets=new JsonArray();walk(screen,widgets);row.add("widgets",widgets);
        if(screen instanceof CreateWorldScreen creation) {
            var state=creation.getUiState();var preset=state.getWorldType().preset();
            row.addProperty("preset",preset==null?"":preset.unwrapKey().map(k->k.location().toString()).orElse(""));
            row.addProperty("generator",state.getSettings().selectedDimensions().overworld().getClass().getName());
        }
        String encoded=row.toString();if(encoded.equals(last))return;last=encoded;
        org.slf4j.LoggerFactory.getLogger(WorldUiEvidence.class).info("WORLD_UI_EVIDENCE {}",encoded);
    }
    private static void walk(GuiEventListener node,JsonArray out) {
        if(node instanceof AbstractWidget widget && widget.visible) {
            JsonObject v=new JsonObject();v.addProperty("text",widget.getMessage().getString());v.addProperty("active",widget.active);
            v.addProperty("x",widget.getX());v.addProperty("y",widget.getY());v.addProperty("width",widget.getWidth());v.addProperty("height",widget.getHeight());out.add(v);
        }
        if(node instanceof ContainerEventHandler parent)for(var child:parent.children())walk(child,out);
    }
    private WorldUiEvidence(){}
}
