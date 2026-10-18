package com.example.myvillage.sim.runtime.commerce;

import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.entity.npc.NpcCombatRuntime;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.commerce.CommerceRules;
import com.example.myvillage.sim.life.LifeResidentView;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.sim.runtime.avatar.WorldSimAvatars;
import com.example.myvillage.sim.runtime.life.NpcLifeSites;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** Real-body authorization, independent of the client opening a screen or knowing a person ID. */
public final class NpcCommerce {
    private NpcCommerce() { }
    public static boolean open(ServerPlayer player,NpcEntity npc) {
        if(player.isShiftKeyDown())return false; // Preserve the existing conversation/sect interface.
        WorldSim sim=WorldSimRuntime.sim().orElse(null);
        LifeResidentView life=sim==null?null:sim.life(npc.ledgerPersonId()).orElse(null);
        if(life==null||!CommerceRules.ROLES.contains(life.role())||sim.commerce(life.siteId()).isEmpty())return false;
        if(!valid(player,npc,sim)) {
            player.displayClientMessage(Component.translatable("message.myvillage.commerce.off_duty"),true);return true;
        }
        var rules=sim.commerceRules();var offers=rules.offers(life.role());
        if(offers.isEmpty())return false;
        // Fail explicitly before opening a menu or mutating an inventory on invalid pack items.
        try {
            CommerceInventory.item(rules.currency());
            offers.forEach(o->{if(!o.kind().equals("rest"))CommerceInventory.item(o.item());});
        } catch (IllegalArgumentException invalidPack) {
            org.slf4j.LoggerFactory.getLogger(NpcCommerce.class).warn("NPC commerce item definition unavailable", invalidPack);
            player.displayClientMessage(Component.translatable("screen.myvillage.commerce.status.unavailable"),true);
            return true;
        }
        sim.registerPlayerActor(player.getUUID().toString(),player.getGameProfile().getName());
        sim.rememberConversation(life.personId(),player.getUUID().toString());
        pause(player,npc,sim);dirty(player);
        long token=UUID.randomUUID().getLeastSignificantBits();
        var title=Component.translatable("screen.myvillage.commerce.title",
                sim.person(life.personId()).map(p->p.name()).orElse(""));
        player.openMenu(new SimpleMenuProvider((id,inventory,viewer)->new CommerceMenu(id,inventory,npc,token,rules.currency(),offers),title),
                buf->CommerceMenu.writeOpening(buf,token,rules.currency(),offers));
        return true;
    }
    public static boolean valid(ServerPlayer player,NpcEntity npc,WorldSim sim) {
        if(sim==null||!sim.commerceDaytime()||npc==null||player.isRemoved()||!player.isAlive()||player.isSpectator()
                ||npc.isRemoved()||!npc.isAlive()||!npc.isLedgerAvatar()||npc.combatEngaged()
                ||player.level()!=npc.level()||player.serverLevel().getEntity(npc.getUUID())!=npc
                ||!NpcCombatRuntime.managed(npc)||!NpcCombatRuntime.canTick(npc))return false;
        var life=sim.life(npc.ledgerPersonId()).orElse(null);var rules=sim.commerceRules();
        if(life==null||!life.embodied()||life.epoch()!=WorldSimAvatars.lifeEpoch(npc)
                ||!CommerceRules.ROLES.contains(life.role())||!life.activity().equals("work")
                ||!life.phase().equals("working")||!life.targetId().equals(life.workId())
                ||player.distanceToSqr(npc)>(double)rules.interactionRange()*rules.interactionRange()
                ||!player.hasLineOfSight(npc))return false;
        var site=sim.lifeSite(life.siteId()).orElse(null);
        if(site==null||!site.dimension().equals(player.level().dimension().location().toString()))return false;
        var work=site.place(life.workId()).orElse(null);
        if(work==null||NpcLifeSites.availability(player.serverLevel(),work)!=NpcLifeSites.Availability.AVAILABLE)return false;
        return npc.distanceToSqr(work.x()+.5,work.y(),work.z()+.5)<=(double)rules.stationRange()*rules.stationRange();
    }
    public static String restRefusal(ServerPlayer player,NpcEntity npc,WorldSim sim) {
        if(player.hurtTime>0||player.isOnFire()||player.getLastHurtByMob()!=null
                &&player.tickCount-player.getLastHurtByMobTimestamp()<sim.commerceRules().restSafeTicks())return "unavailable";
        var life=sim.life(npc.ledgerPersonId()).orElseThrow();var site=sim.lifeSite(life.siteId()).orElseThrow();
        boolean bed=site.places().stream().filter(p->p.kind().equals("rest")&&p.block().endsWith("_bed"))
                .anyMatch(p->NpcLifeSites.availability(player.serverLevel(),p)==NpcLifeSites.Availability.AVAILABLE
                        &&sim.lifeResidents().stream().noneMatch(r->r.siteId().equals(site.id())&&r.homeId().equals(p.id())));
        if(!bed)return "no_room";
        var profile=CultivationService.getProfile(player);var rules=sim.commerceRules();
        boolean health=rules.restHealthFraction()>0&&player.getHealth()<player.getMaxHealth();
        boolean qi=rules.restQiFraction()>0&&profile.awakened()&&profile.currentSpiritualPower()<CultivationService.spiritualPowerCap(player);
        return health||qi?"ok":"no_recovery";
    }
    public static void restore(ServerPlayer player,WorldSim sim) {
        var rules=sim.commerceRules();
        player.heal((float)(player.getMaxHealth()*rules.restHealthFraction()));
        if(CultivationService.getProfile(player).awakened())
            CultivationService.recoverSpiritualPower(player,(long)(CultivationService.spiritualPowerCap(player)*rules.restQiFraction()));
    }
    public static void pause(ServerPlayer player,NpcEntity npc,WorldSim sim) {
        sim.pauseLife(npc.ledgerPersonId(),sim.lifeRules().conversationPauseTicks());
        npc.getNavigation().stop();npc.getLookControl().setLookAt(player,30,30);
    }
    public static void dirty(ServerPlayer player){WorldSimSavedData.get(player.getServer().overworld()).setDirty();}
}
