package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.*;
import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.network.CombatHotbarPayload;
import com.example.myvillage.combat.network.CombatHotbarPayload.Slot;
import com.example.myvillage.combat.session.*;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.fate.WindStepRuntime;
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
            if(CombatService.getMode(p)!=CombatMode.CULTIVATION){forget(p.getUUID());continue;}
            var next=snapshot(p);
            if(!next.equals(LAST.put(p.getUUID(),next)) || server.getTickCount()%20==0)PacketDistributor.sendToPlayer(p,next);
        }
    }
    public static CombatHotbarPayload snapshot(ServerPlayer p) {
        var family=CombatStyles.bundled().weapon(p.getMainHandItem()).flatMap(w->w.family());
        long power=CultivationService.getProfile(p).currentSpiritualPower();
        boolean blocked=!p.isAlive() || p.isSpectator() || p.isSleeping() || p.isPassenger() || p.isUsingItem()
                || p.containerMenu!=p.inventoryMenu || MeditationManager.status(p).state().active();
        boolean busy=blocked || CombatSessionManager.hudBusy(p) || CombatDodgeService.isDodging(p);
        List<Slot> slots=new ArrayList<>(4);
        for(int i=0;i<2;i++) {
            int index=i;var art=family.flatMap(f->ArtDefinition.forSlot(f,index));
            if(art.isEmpty()){slots.add(slot(Optional.empty(),LOCKED,false,0,0,0));continue;}
            var a=art.get();int remaining=CombatArtActions.cooldownRemaining(p,a);
            slots.add(slot(Optional.of(a.id()),state(CombatArtActions.authorized(p,a),remaining,busy || (a.requiresGround() && !p.onGround())),
                    power>=ForgedEquipment.qiCost(p.getMainHandItem(),a.qiCost()),remaining,a.cooldownTicks(),0));
        }
        var wave=CombatSkillActions.choice(p);
        if(wave.isEmpty())slots.add(slot(Optional.empty(),LOCKED,false,0,0,0));
        else {
            var c=wave.get();var view=CombatSkillActions.presentation(p,c);
            int state=view.charge()>=0?CHARGING:view.remaining()>0?RECOVERING:busy?BLOCKED:READY;
            slots.add(slot(Optional.of(c.technique()),state,power>=ForgedEquipment.qiCost(p.getMainHandItem(),view.cost()),
                    view.remaining(),view.total(),Math.max(0,view.charge())));
        }
        var dodge=CombatDodgeService.currentChoice(p);
        if(dodge.isEmpty())slots.add(slot(Optional.empty(),LOCKED,true,0,0,0));
        else {
            var c=dodge.get();boolean wind=WindStepRuntime.enabled(p);
            boolean ground=wind?WindStepRuntime.grounded(p):CombatDodgeService.supported(p);
            int remaining=bounded(CombatDodgeService.cooldownRemaining(p));
            boolean unavailable=blocked || !CombatSessionManager.canDodgeCancel(p)
                    || (!ground && !(wind && WindStepRuntime.airChargeAvailable(p)));
            slots.add(slot(Optional.of(c.id()),state(true,remaining,unavailable),ground || power>=c.movement().qiCost(),
                    remaining,c.movement().cooldownTicks(),0));
        }
        return new CombatHotbarPayload(p.getId(),p.level().dimension().location(),
                BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()),slots);
    }
    static int state(boolean learned,int remaining,boolean blocked) {
        return !learned?LOCKED:remaining>0?COOLDOWN:blocked?BLOCKED:READY;
    }
    private static int bounded(long ticks){return (int)Math.clamp(ticks,0,MAX_TICKS);}
    private static Slot slot(Optional<ResourceLocation> id,int state,boolean power,int remaining,int total,int charge){
        int r=bounded(remaining);return new Slot(id,state,power,r,Math.max(r,bounded(total)),charge);
    }
    public static void forget(UUID id){LAST.remove(id);}
    public static void clear(){LAST.clear();}
}
