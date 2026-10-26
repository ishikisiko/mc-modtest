package com.example.myvillage.sim.commerce;

import com.example.myvillage.sim.engine.SimContext;
import com.example.myvillage.sim.life.*;
import com.example.myvillage.sim.profession.ProfessionEngine;
import java.util.*;

/** Ledger half of a server-thread transaction. No ItemStacks, networking or second NPC identity. */
public final class CommerceEngine {
    public record Quote(String result,String siteId,int personId,long epoch,String offerId,
                        long revision,int available,int price,String playerId,int quantity) {
        public boolean ready() { return result.equals("ok"); }
    }
    private CommerceEngine() { }
    public static boolean initialize(SimContext ctx,String site) {
        if(!ctx.state.life.sites.containsKey(site)) throw new IllegalArgumentException("unknown_site");
        if(ctx.state.commerce.markets.containsKey(site)) return false;
        if(ctx.state.commerce.markets.size()>=CommerceState.MAX_MARKETS) throw new IllegalArgumentException("market_limit");
        var market=new CommerceState.Market();
        for(var o:ctx.data.commerceRules().offers()) if(o.pool().equals("items")) market.items.putIfAbsent(o.item(),o.initialStock());
        ctx.state.commerce.markets.put(site,market);return true;
    }
    public static Optional<CommerceView> view(SimContext ctx,String site) {
        var market=ctx.state.commerce.markets.get(site);var supplies=ctx.state.life.supplies.get(site);
        return market==null||supplies==null?Optional.empty():Optional.of(new CommerceView(site,market.revision,
                market.transactions,supplies.treasury,market.items,market.staffedRoles));
    }
    /** Bounded, deterministic recruitment from existing LOCAL eligible people; never synthesize people. */
    public static List<Integer> staff(SimContext ctx,String siteId) {
        var market=ctx.state.commerce.markets.get(siteId);var site=ctx.state.life.sites.get(siteId);
        if(market==null||site==null||market.revision>Long.MAX_VALUE-CommerceRules.ROLES.size())return List.of();
        List<Integer> added=new ArrayList<>();
        for(var job:ctx.data.commerceRules().staff()) {
            if(market.staffedRoles.contains(job.role()))continue;
            var existing=ctx.state.life.residents.values().stream().filter(r->r.siteId.equals(siteId)&&r.role.equals(job.role())).findFirst();
            if(existing.isPresent()){market.staffedRoles.add(job.role());market.revision++;continue;}
            LifePlace home=site.place(job.home()).orElse(null),work=site.place(job.work()).orElse(null);
            if(home==null||work==null||!home.kind().equals("rest")||!work.kind().equals("work")
                    ||!available(ctx,siteId,home.id())||!available(ctx,siteId,work.id()))continue;
            long dx=(long)home.x()-work.x(),dy=(long)home.y()-work.y(),dz=(long)home.z()-work.z();
            if(dx*dx+dy*dy+dz*dz>(long)ctx.data.lifeRules().maxPathDistance()*ctx.data.lifeRules().maxPathDistance())continue;
            if(ctx.state.life.residents.values().stream().filter(r->r.siteId.equals(siteId)).count()>=ctx.data.lifeRules().maxResidentsPerSite()
                    ||ctx.state.life.residents.values().stream().filter(r->r.siteId.equals(siteId)&&r.homeId.equals(home.id())).count()>=home.capacity())continue;
            var person=ctx.state.persons.values().stream().filter(p->p.regionId.equals(site.regionId())&&p.sectId<0
                    &&!p.status.equals("travelling")&&!p.status.equals("secluded")&&!ctx.state.life.residents.containsKey(p.id)
                    &&!ctx.state.combat.leases.containsKey(p.id)
                    &&(ctx.state.combat.profiles.get(p.id)==null || ctx.state.combat.profiles.get(p.id).body()==null
                    ||!ctx.state.combat.profiles.get(p.id).body().unresolved())).findFirst();
            if(person.isEmpty())continue;
            if(LifeEngine.assign(ctx,person.get().id,siteId,job.role(),job.home(),job.work())) {
                market.staffedRoles.add(job.role());market.revision++;added.add(person.get().id);
            }
        }
        return List.copyOf(added);
    }
    public static Quote quote(SimContext ctx,int person,long epoch,String offerId,String player) {
        return quote(ctx,person,epoch,offerId,player,1);
    }
    public static Quote quote(SimContext ctx,int person,long epoch,String offerId,String player,int quantity) {
        var o=ctx.data.commerceRules().offer(offerId);var r=ctx.state.life.residents.get(person);
        String site=r==null?"":r.siteId;var m=ctx.state.commerce.markets.get(site);
        String reason="ok";int count=0;
        boolean playerKnown=com.example.myvillage.sim.social.SocialEngine.actor(ctx.state.social,ctx.state,
                com.example.myvillage.sim.social.ActorRef.player(player)).isPresent();
        if(quantity<1||quantity>ctx.data.commerceRules().stockLimit()||o!=null&&o.kind().equals("rest")&&quantity!=1)reason="invalid_quantity";
        else if(!playerKnown)reason="unavailable";
        else if(ctx.state.scheduler.paused())reason="paused";
        else if(o==null||r==null||m==null||!ctx.state.persons.containsKey(person))reason="unavailable";
        else if(!r.embodied||r.epoch!=epoch||ctx.state.combat.engaged.contains(person))reason="unavailable";
        else if(hostile(ctx,person,player))reason="hostile";
        else if(!LifeEngine.merchantDaytime(ctx)||!r.role.equals(o.role())||!r.activity.equals("work")||!r.phase.equals("working")
                ||!r.targetId.equals(r.workId)||!available(ctx,site,r.workId))reason="off_duty";
        else if(o.kind().equals("rest")) {
            long now=Math.max(0,ctx.state.life.clockTicks);
            if(ctx.state.commerce.restUntil.getOrDefault(player,0L)>now) reason="rest_cooldown";
            else if(!ctx.state.commerce.restUntil.containsKey(player)&&ctx.state.commerce.restUntil.entrySet().stream()
                    .filter(e->e.getValue()>now).count()>=CommerceState.MAX_COOLDOWNS)reason="unavailable";
            else count=1;
        } else {
            var s=ctx.state.life.supplies.get(site);
            if(o.kind().equals("buy"))count=m.items.getOrDefault(o.item(),0)/o.units();
            else count=Math.min(s.treasury/o.price(),o.pool().equals("herbs")
                    ?Math.min(ProfessionEngine.stockCapacity(ctx,site)-s.stock,
                            o.capacity()-s.herbStock.getOrDefault(o.item(),0))/o.units()
                    :(o.capacity()-m.items.getOrDefault(o.item(),0))/o.units());
            if(count<quantity)reason=o.kind().equals("sell")?"cannot_buy":"out_of_stock";
        }
        if(reason.equals("ok")&&!o.kind().equals("sell")) {
            int capacity=(ctx.data.lifeRules().maxInventory()-ctx.state.life.supplies.get(site).treasury)/o.price();
            count=Math.min(count,capacity);
            if(count<quantity)reason="treasury_full";
        }
        return new Quote(reason,site,person,epoch,offerId,m==null?-1:m.revision,Math.max(0,count),
                o==null||quantity<1||quantity>ctx.data.commerceRules().stockLimit()?0:o.price()*quantity,player,quantity);
    }
    /** Caller first prepares a complete inventory replacement, then commits this receipt once. */
    public static boolean commit(SimContext ctx,Quote expected,String player) {
        Quote fresh=quote(ctx,expected.personId(),expected.epoch(),expected.offerId(),player,expected.quantity());
        if(!expected.playerId().equals(player)||!expected.ready()||!fresh.ready()||!fresh.siteId().equals(expected.siteId())||fresh.revision()!=expected.revision())return false;
        var o=ctx.data.commerceRules().offer(expected.offerId());var m=ctx.state.commerce.markets.get(expected.siteId());
        var s=ctx.state.life.supplies.get(expected.siteId());
        if(m.revision==Long.MAX_VALUE||m.transactions==Long.MAX_VALUE||s.services==Long.MAX_VALUE)return false;
        int price=o.price()*fresh.quantity(),units=o.units()*fresh.quantity();
        if(o.kind().equals("sell")) {
            s.treasury-=price;
            if(o.pool().equals("herbs")) {
                s.stock+=units;
                s.herbStock.merge(o.item(),units,Integer::sum);
            }
            else m.items.put(o.item(),m.items.getOrDefault(o.item(),0)+units);
        } else {
            s.treasury+=price;
            if(o.kind().equals("rest")) {
                long now=Math.max(0,ctx.state.life.clockTicks);
                ctx.state.commerce.restUntil.entrySet().removeIf(e->e.getValue()<=now);
                ctx.state.commerce.restUntil.put(player,now>Long.MAX_VALUE-ctx.data.commerceRules().restCooldownTicks()
                        ?Long.MAX_VALUE:now+ctx.data.commerceRules().restCooldownTicks());
                s.services++;
            } else m.items.put(o.item(),m.items.get(o.item())-units);
        }
        m.revision++;m.transactions++;return true;
    }
    /** Compatibility hook: a shop clerk never prepares medicine or refills stock. */
    public static void restock(SimContext ctx,LifeResident r) { }
    private static boolean hostile(SimContext ctx,int person,String player) {
        var p=ctx.state.persons.get(person);
        var personal=ctx.state.combat.grievances.get(person);
        var sect=p==null?null:ctx.state.combat.sectGrievances.get(p.sectId);
        String actor="player:"+player;
        return personal!=null&&personal.containsKey(actor)||sect!=null&&sect.containsKey(actor);
    }
    private static boolean available(SimContext ctx,String site,String place) {
        return "available".equals(ctx.state.life.availability.get(LifeState.placeKey(site,place)));
    }
}
