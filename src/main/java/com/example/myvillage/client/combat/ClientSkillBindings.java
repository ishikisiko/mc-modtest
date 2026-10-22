package com.example.myvillage.client.combat;

import com.example.myvillage.combat.*;
import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.network.*;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Physical keys are client options. The saved skill choices come only from the server. */
public final class ClientSkillBindings {
    private static SkillBindingsSnapshotPayload snapshot;
    private static SkillBindings pending;
    private static long requestedAt;
    private static int waveKey=-1;
    private static String feedback="";
    private ClientSkillBindings(){}
    static void install(){SkillBindingsReceiver.install(ClientSkillBindings::receive);}
    private static void receive(SkillBindingsSnapshotPayload value) {
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || value.entityId()!=mc.player.getId()
                || !value.dimension().equals(mc.level.dimension().location()))return;
        snapshot=value;
        if(pending!=null && pending.equals(value.bindings())){pending=null;feedback="已保存";}
    }
    public static SkillBindings current() {
        var mc=Minecraft.getInstance();
        return snapshot!=null && mc.player!=null && mc.level!=null && snapshot.entityId()==mc.player.getId()
                && snapshot.dimension().equals(mc.level.dimension().location())?snapshot.bindings():null;
    }
    public static boolean pending(){
        if(pending!=null && System.currentTimeMillis()-requestedAt>2500){pending=null;feedback="未能保存，请重试";}
        return pending!=null;
    }
    public static String feedback(){return pending()?"保存中…":feedback;}
    public static void assign(int slot,ResourceLocation id) {
        var old=current();if(old==null || pending())return;
        pending=slot==SkillBindings.SIZE?SkillBindings.defaults():old.with(slot,id);
        requestedAt=System.currentTimeMillis();feedback="";
        PacketDistributor.sendToServer(new SkillBindingPayload(slot,id));
    }
    public static SkillBindingRules.Kind kind(ResourceLocation id) {
        var mc=Minecraft.getInstance();
        return mc.level==null?SkillBindingRules.Kind.NONE:SkillBindingRules.kind(id,
                ModCultivationRegistries.technique(mc.level.registryAccess(),id).orElse(null));
    }
    public static Optional<ResourceLocation> resolve(int slot) {
        var config=current();var mc=Minecraft.getInstance();if(config==null || mc.player==null)return Optional.empty();
        var id=config.slots().get(slot);
        if(id.equals(SkillBindings.EMPTY))return Optional.empty();
        if(id.equals(SkillBindings.AUTO))return CombatStyles.bundled().weapon(mc.player.getMainHandItem()).flatMap(w->w.family())
                .flatMap(f->ArtDefinition.forSlot(f,slot)).map(ArtDefinition::id);
        return Optional.of(id);
    }
    /** Conflicting mappings are surfaced in H; do not fire two actions for a shared physical key. */
    public static List<String> conflicts(int slot) {
        var mc=Minecraft.getInstance();var key=ClientCombatKeyMappings.SKILL_KEYS.get(slot);
        if(key.isUnbound())return List.of();
        return Arrays.stream(mc.options.keyMappings).filter(other->other!=key && !other.isUnbound() && key.same(other))
                .map(k->net.minecraft.network.chat.Component.translatable(k.getName()).getString()).distinct().toList();
    }
    static void tick() {
        var mc=Minecraft.getInstance();
        boolean input=mc.player!=null && mc.level!=null && mc.player.isAlive() && !mc.player.isSpectator()
                && mc.screen==null && !mc.isPaused() && mc.isWindowActive() && ClientCombatState.mode()==CombatMode.CULTIVATION;
        if(waveKey>=0) {
            if(!input) {PacketDistributor.sendToServer(CombatCancelChargeIntentPayload.INSTANCE);waveKey=-1;}
            else if(!ClientCombatKeyMappings.SKILL_KEYS.get(waveKey).isDown()) {
                PacketDistributor.sendToServer(CombatReleaseIntentPayload.INSTANCE);waveKey=-1;
            }
        }
        for(int slot=0;slot<SkillBindings.SIZE;slot++) {
            var key=ClientCombatKeyMappings.SKILL_KEYS.get(slot);
            while(key.consumeClick()) {
                if(!input || waveKey>=0 || ClientSwordQi.chargingLocal() || !ClientAdvancedArts.canStartBoundSkill()
                        || !conflicts(slot).isEmpty())continue;
                var selected=resolve(slot);if(selected.isEmpty())continue;
                var kind=kind(selected.get());if(kind==SkillBindingRules.Kind.NONE)continue;
                PacketDistributor.sendToServer(new BoundSkillIntentPayload(slot,
                        DodgeDirection.fromInput(mc.player.input.forwardImpulse,mc.player.input.leftImpulse)));
                if(kind==SkillBindingRules.Kind.WAVE)waveKey=slot;
                if(kind==SkillBindingRules.Kind.ART)ClientAdvancedArts.boundSkillPending();
            }
        }
    }
    static void clear(){snapshot=null;pending=null;waveKey=-1;feedback="";}
}
