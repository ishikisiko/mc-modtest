package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.*;
import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.network.*;
import com.example.myvillage.combat.session.CombatSessionManager;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Saved loadout edits and casts are both resolved against current server-owned learning. */
public final class SkillBindingService {
    private static final Map<UUID,Long> LAST_EDIT=new HashMap<>();
    private SkillBindingService(){}
    public static SkillBindingRules.Kind kind(ServerPlayer p,ResourceLocation id) {
        return SkillBindingRules.kind(id,ModCultivationRegistries.technique(p.registryAccess(),id).orElse(null));
    }
    public static boolean learned(ServerPlayer p,ResourceLocation id){return CultivationService.getProfile(p).learnedTechniques().containsKey(id);}
    public static void edit(ServerPlayer p,SkillBindingPayload request) {
        long now=p.serverLevel().getServer().overworld().getGameTime();Long last=LAST_EDIT.get(p.getUUID());
        if(last!=null && now-last<2)return;
        LAST_EDIT.put(p.getUUID(),now);
        var id=request.technique();var old=CombatService.getPreference(p);
        if(!p.isAlive() || p.isSpectator()) {sync(p);return;}
        if(!id.equals(SkillBindings.AUTO) && !id.equals(SkillBindings.EMPTY)
                && !SkillBindingRules.assignable(id,CultivationService.getProfile(p).learnedTechniques().keySet(),
                        ModCultivationRegistries.technique(p.registryAccess(),id).orElse(null))){sync(p);return;}
        var next=request.slot()==SkillBindings.SIZE?SkillBindings.defaults():old.skillBindings().with(request.slot(),id);
        p.setData(CombatAttachments.PREFERENCE.get(),new CombatPreference(old.combatMode(),next));
        CombatHotbarService.forget(p.getUUID());sync(p);
    }
    public static Optional<ResourceLocation> resolve(ServerPlayer p,int slot) {
        var id=CombatService.getPreference(p).skillBindings().slots().get(slot);
        if(id.equals(SkillBindings.EMPTY))return Optional.empty();
        if(id.equals(SkillBindings.AUTO))return CombatStyles.bundled().weapon(p.getMainHandItem()).flatMap(w->w.family())
                .flatMap(f->ArtDefinition.forSlot(f,slot)).map(ArtDefinition::id);
        return Optional.of(id);
    }
    public static boolean cast(ServerPlayer p,BoundSkillIntentPayload intent) {
        var selected=resolve(p,intent.slot());if(selected.isEmpty() || !learned(p,selected.get()))return false;
        var id=selected.get();
        return switch(kind(p,id)) {
            case ART -> ArtDefinition.find(id).filter(a->CombatStyles.bundled().weapon(p.getMainHandItem()).flatMap(w->w.family())
                    .equals(Optional.of(a.family()))).map(a->CombatSessionManager.handleArtIntent(p,a.id())).orElse(false);
            case WAVE -> CombatSessionManager.handleChargeIntent(p,id);
            case MOVEMENT -> CombatDodgeService.handleIntent(p,intent.direction(),id);
            default -> false;
        };
    }
    public static void sync(ServerPlayer p){PacketDistributor.sendToPlayer(p,new SkillBindingsSnapshotPayload(
            p.getId(),p.level().dimension().location(),CombatService.getPreference(p).skillBindings()));}
    public static void forget(UUID id){LAST_EDIT.remove(id);}
    public static void clear(){LAST_EDIT.clear();}
}
