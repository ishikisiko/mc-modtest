package com.example.myvillage.client.combat;

import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.client.cultivation.panel.PanelAssets;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import net.minecraft.network.chat.Component;
import com.example.myvillage.combat.network.CombatHotbarPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;
import static com.example.myvillage.combat.network.CombatHotbarPayload.*;

/** One visual vocabulary for advanced arts, the active wave and movement techniques. */
final class CombatHotbarHud {
    private static final CombatHotbarPayload.Slot EMPTY=new CombatHotbarPayload.Slot(Optional.empty(),LOCKED,false,0,0,0);
    private CombatHotbarHud(){}
    static void render(GuiGraphics g) {
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || !mc.player.isAlive() || mc.options.hideGui || mc.player.isSpectator()
                || ClientCombatState.mode()!=CombatMode.CULTIVATION || !ArtHotbarLayout.fits(g.guiWidth(),g.guiHeight()))return;
        var snapshot=ClientCombatHotbar.latest();
        boolean owner=snapshot!=null && snapshot.entityId()==mc.player.getId()
                && snapshot.dimension().equals(mc.level.dimension().location());
        boolean held=owner && snapshot.weapon().equals(BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()));
        for(int index=0;index<SLOT_COUNT;index++) {
            var supplied=owner?snapshot.slots().get(index):EMPTY;
            if(index>=4 && supplied.technique().isEmpty())continue;
            var kind=supplied.technique().map(ClientSkillBindings::kind).orElse(com.example.myvillage.combat.SkillBindingRules.Kind.NONE);
            var value=owner && (held || kind==com.example.myvillage.combat.SkillBindingRules.Kind.MOVEMENT)?supplied:EMPTY;
            var box=ArtHotbarLayout.slot(g.guiWidth(),g.guiHeight(),index);
            int x=box.x(),y=box.y(),size=box.size();
            int state=value.state(),remaining=ClientCombatHotbar.remaining(value);
            if(remaining==0 && (state==COOLDOWN || state==RECOVERING))state=READY;
            int edge=state==LOCKED || state==BLOCKED?0xFF5E645D:!value.power()?0xFF995C50:
                    state==CHARGING || state==RECOVERING?0xFFE2C779:state==COOLDOWN?0xFF777064:0xFFBBA77A;
            g.fill(x-1,y-1,x+size+1,y+size+1,0xB5000000);
            g.fill(x,y,x+size,y+size,edge);g.fill(x+1,y+1,x+size-1,y+size-1,0xFF15221F);
            var elemental=value.technique().flatMap(com.example.myvillage.combat.art.ArtDefinition::find).flatMap(a->a.elemental());
            if(elemental.isPresent()) {
                var id=value.technique().orElseThrow();
                var palette=PanelAssets.grade(ModCultivationRegistries.technique(mc.level.registryAccess(),id).map(d->d.grade()).orElse(1));
                g.fill(x+2,y+2,x+size-2,y+size-2,palette.wash());
                PanelAssets.draw(g,PanelAssets.technique(id,TechniqueCategory.ACTIVE),x+2,y+2,size-4);
                g.drawString(mc.font,Component.translatable("combat.myvillage.elemental."+elemental.get().element().id()+".symbol"),
                        x+size-10,y+size-10,palette.text(),false);
            } else {
            var texture=kind==com.example.myvillage.combat.SkillBindingRules.Kind.WAVE || index==2?icon("wave"):
                    kind==com.example.myvillage.combat.SkillBindingRules.Kind.MOVEMENT || index==3?icon("dodge"):
                    value.technique().filter(id->com.example.myvillage.combat.art.ArtDefinition.find(id).filter(a->a.lock().isEmpty()).isPresent())
                            .map(id->ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"textures/gui/advanced_arts/"+id.getPath()+".png"))
                            .orElseGet(()->icon(kind==com.example.myvillage.combat.SkillBindingRules.Kind.ART?"wave":"unavailable"));
            g.pose().pushPose();g.pose().translate(x+2,y+2,0);g.pose().scale((size-4)/32F,(size-4)/32F,1);
            g.flush();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            g.blit(texture,0,0,0,0,32,32,32,32);RenderSystem.disableBlend();g.pose().popPose();
            }
            if(state==LOCKED) {
                g.fill(x+1,y+1,x+size-1,y+size-1,0xB8181C1B);
                g.fill(x+9,y+10,x+15,y+16,0xFFABA898);g.fill(x+10,y+7,x+14,y+11,0xFFABA898);
                g.fill(x+11,y+8,x+13,y+11,0xFF202824);
            } else if(state==BLOCKED) {
                g.fill(x+1,y+1,x+size-1,y+size-1,0x88181C1B);
                g.fill(x+9,y+13,x+15,y+15,0xFFAAA89A);
            } else {
                if(!value.power())g.fill(x+1,y+1,x+size-1,y+size-1,0x80551F1B);
                if(state==CHARGING) {
                    double charge=kind==com.example.myvillage.combat.SkillBindingRules.Kind.WAVE?ClientSwordQi.localCharge():-1;
                    int percent=charge>=0?(int)Math.round(charge*100):value.charge();
                    int fill=(size-2)*percent/100;
                    g.fill(x+1,y+size-1-fill,x+size-1,y+size-1,0x802DAB9B);
                    center(g,percent+"%",x,y,size,0xFFFFFFDF);
                } else if(remaining>0) {
                    int cover=ArtHotbarLayout.coverHeight(remaining,value.total(),size-2);
                    g.fill(x+1,y+1,x+size-1,y+1+cover,0xBA08100F);
                    center(g,ArtHotbarLayout.cooldownText(remaining),x,y,size,0xFFFFF1D1);
                } else if(!value.power())g.drawString(mc.font,"!",x+size-7,y+size-11,0xFFFFA18A,true);
            }
            var key=index==2?mc.options.keyUse:index==3?ClientCombatKeyMappings.DODGE:
                    ClientCombatKeyMappings.SKILL_KEYS.get(index<2?index:index-2);
            String label=mc.font.plainSubstrByWidth(keyName(key),25);
            float scale=.75F;int keyWidth=(int)Math.ceil(mc.font.width(label)*scale);
            g.fill(x+1,y+1,x+keyWidth+4,y+8,0xE0101816);
            g.pose().pushPose();g.pose().translate(x+2,y+1,0);g.pose().scale(scale,scale,1);
            g.drawString(mc.font,label,0,0,0xFFF2E6CB,false);g.pose().popPose();
        }
    }
    private static ResourceLocation icon(String name){return ResourceLocation.parse("myvillage:textures/gui/advanced_arts/"+name+".png");}
    private static String keyName(KeyMapping key){
        return switch(key.getKey().getName()) {
            case "key.mouse.right"->"右键";case "key.mouse.left"->"左键";case "key.mouse.middle"->"中键";
            case "key.keyboard.left.alt"->"Alt";case "key.keyboard.right.alt"->"RAlt";
            default->key.getTranslatedKeyMessage().getString();
        };
    }
    private static void center(GuiGraphics g,String text,int x,int y,int size,int color){
        var font=Minecraft.getInstance().font;float scale=Math.min(1,(size-4F)/Math.max(1,font.width(text)));
        g.pose().pushPose();g.pose().translate(x+(size-font.width(text)*scale)/2,y+9,0);g.pose().scale(scale,scale,1);
        g.drawString(font,text,0,0,color,true);g.pose().popPose();
    }
}
