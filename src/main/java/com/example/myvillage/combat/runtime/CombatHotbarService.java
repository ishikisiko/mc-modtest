package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.*;
import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.combat.network.CombatHotbarPayload;
import com.example.myvillage.combat.network.CombatHotbarPayload.Slot;
import com.example.myvillage.combat.session.*;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.fate.WindStepRuntime;
import com.example.myvillage.cultivation.fate.ability.FateAbilityDefinition;
import com.example.myvillage.cultivation.fate.ability.FateAbilityRuntime;
import com.example.myvillage.combat.foundation.ControlService;
import com.example.myvillage.cultivation.meditation.MeditationManager;
import com.example.myvillage.forging.ForgedEquipment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static com.example.myvillage.combat.network.CombatHotbarPayload.*;

/** Reuses actual server selectors/timers; no second combat executor or client-side cooldown guess. */
public final class CombatHotbarService {
    private static final Map<UUID,CombatHotbarPayload> LAST=new HashMap<>();
    private CombatHotbarService(){}
    public static void tick(MinecraftServer server) {
        if(server.getTickCount()%4!=0)return;
        for(var p:server.getPlayerList().getPlayers()) {
            // H is available in both combat modes, and a late level must receive its loadout.
            if(server.getTickCount()%20==0)SkillBindingService.sync(p);
            if(CombatService.getMode(p)!=CombatMode.CULTIVATION){forget(p.getUUID());continue;}
            var next=snapshot(p);
            if(!next.equals(LAST.put(p.getUUID(),next)) || server.getTickCount()%20==0)PacketDistributor.sendToPlayer(p,next);
        }
    }
    public static CombatHotbarPayload snapshot(ServerPlayer p) {
        List<Slot> slots=new ArrayList<>(Collections.nCopies(SLOT_COUNT,locked(Optional.empty())));
        for(int i=0;i<SkillBindings.SIZE;i++)slots.set(SkillBindings.hudIndex(i),readout(p,SkillBindingService.resolve(p,i)));
        slots.set(2,readout(p,CombatSkillActions.choice(p).map(c->c.technique())));
        slots.set(3,readout(p,CombatDodgeService.currentChoice(p).map(c->c.id())));
        return new CombatHotbarPayload(p.getId(),p.level().dimension().location(),
                BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()),slots);
    }
    private static Slot readout(ServerPlayer p,Optional<ResourceLocation> technique) {
        if(technique.isEmpty())return locked(technique);
        var id=technique.get();if(!SkillBindingService.available(p,id))return locked(technique);
        long power=CultivationService.getProfile(p).currentSpiritualPower();
        boolean blocked=!p.isAlive() || p.isSpectator() || p.isSleeping() || p.isPassenger() || p.isUsingItem()
                || p.containerMenu!=p.inventoryMenu || MeditationManager.status(p).state().active();
        boolean busy=blocked || CombatSessionManager.hudBusy(p) || CombatDodgeService.isDodging(p);
        switch(SkillBindingService.kind(p,id)) {
            case FATE -> {
                var definition=FateAbilityDefinition.find(id).orElseThrow();
                int remaining=FateAbilityRuntime.cooldownRemaining(p,id);
                return slot(technique,state(true,remaining,FateAbilityRuntime.blocked(p)),
                        power>=FateAbilityRuntime.cost(p,id),remaining,definition.cooldownTicks(),0);
            }
            case ART -> {
                var a=ArtDefinition.find(id).orElseThrow();int remaining=CombatArtActions.cooldownRemaining(p,a);
                return slot(technique,state(CombatArtActions.authorized(p,a),remaining,busy || (a.requiresGround() && !p.onGround())),
                        power>=CombatArtActions.cost(p,a),remaining,a.cooldownTicks(),0);
            }
            case WAVE -> {
                var choice=CombatSkillActions.choice(p,id);if(choice.isEmpty())return locked(technique);
                var view=CombatSkillActions.presentation(p,choice.get());
                int state=view.charge()>=0?CHARGING:view.remaining()>0?RECOVERING:busy?BLOCKED:READY;
                return slot(technique,state,power>=ForgedEquipment.qiCost(p.getMainHandItem(),view.cost()),
                        view.remaining(),view.total(),Math.max(0,view.charge()));
            }
            case MOVEMENT -> {
                var choice=CombatDodgeService.currentChoice(p,id);if(choice.isEmpty())return locked(technique);
                var c=choice.get();boolean wind=WindStepRuntime.enabled(p);
                boolean ground=wind?WindStepRuntime.grounded(p):CombatDodgeService.supported(p);
                int remaining=bounded(CombatDodgeService.cooldownRemaining(p));
                boolean unavailable=blocked || ControlService.isRooted(p) || !CombatSessionManager.canDodgeCancel(p)
                        || (!ground && !(wind && WindStepRuntime.airChargeAvailable(p)));
                return slot(technique,state(true,remaining,unavailable),ground || power>=c.movement().qiCost(),
                        remaining,c.movement().cooldownTicks(),0);
            }
            default -> {return locked(technique);}
        }
    }
    static int state(boolean learned,int remaining,boolean blocked) {
        return !learned?LOCKED:remaining>0?COOLDOWN:blocked?BLOCKED:READY;
    }
    private static Slot locked(Optional<ResourceLocation> id){return slot(id,LOCKED,false,0,0,0);}
    private static int bounded(long ticks){return (int)Math.clamp(ticks,0,MAX_TICKS);}
    private static Slot slot(Optional<ResourceLocation> id,int state,boolean power,int remaining,int total,int charge){
        int r=bounded(remaining);return new Slot(id,state,power,r,Math.max(r,bounded(total)),charge);
    }
    public static void forget(UUID id){LAST.remove(id);}
    public static void clear(){LAST.clear();}
}
