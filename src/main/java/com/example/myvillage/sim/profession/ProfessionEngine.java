package com.example.myvillage.sim.profession;

import com.example.myvillage.sim.engine.SimContext;
import com.example.myvillage.sim.life.*;
import java.util.*;

/** Bounded ledger professions. Callers retain the life activity revision/arrival authority. */
public final class ProfessionEngine {
    public static final Set<String> ROLES = Set.of("herb_child", "spirit_grower", "alchemist");
    public static final Set<String> ACTIVITIES = Set.of("plant", "tend", "harvest", "brew");
    private ProfessionEngine() { }
    private static ProfessionRules rules(SimContext ctx) { return ctx.data.professionRules(); }
    /** One site's shared herb warehouse, including any unclassified legacy stock. */
    public static int stockCapacity(SimContext ctx,String site) {
        int fallback=ctx.data.lifeRules().maxInventory();
        return ctx.state.commerce.markets.containsKey(site)
                ?Math.min(fallback,ctx.data.commerceRules().herbCapacity(fallback)):fallback;
    }
    public static ProfessionState.Profile profile(SimContext ctx, int id) {
        return ctx.state.life.professions.computeIfAbsent(id,k->new ProfessionState.Profile());
    }
    public static Optional<ProfessionView> view(SimContext ctx,int id) {
        if(!ctx.state.persons.containsKey(id))return Optional.empty();
        LifeResident r=ctx.state.life.residents.get(id);
        var p=ctx.state.life.professions.get(id);
        if(p==null&&r==null)return Optional.empty();
        if(p==null)p=new ProfessionState.Profile();
        var typed=r==null?p.savedHerbInventory:r.herbInventory;
        return Optional.of(new ProfessionView(id,r==null?"":r.role,p.gatheringExperience,rules(ctx).level(p.gatheringExperience),
                p.plantingExperience,rules(ctx).level(p.plantingExperience),p.alchemyExperience,rules(ctx).level(p.alchemyExperience),
                typed,(r==null?p.savedHerbs:r.herbs)-total(typed)));
    }
    public static Optional<ProfessionSupplyView> supplyView(SimContext ctx,String site) {
        var s=ctx.state.life.supplies.get(site);if(s==null)return Optional.empty();
        List<ProfessionSupplyView.GardenView> gardens=new ArrayList<>();
        String prefix=site+"|";
        ctx.state.life.gardens.forEach((key,g)->{if(key.startsWith(prefix))gardens.add(new ProfessionSupplyView.GardenView(
                key.substring(prefix.length()),g.herb,g.plantedAt,g.matureAt,g.tended,
                g.physical,g.cropX,g.cropY,g.cropZ,g.observedAge,g.harvestSerial));});
        return Optional.of(new ProfessionSupplyView(site,s.herbStock,s.stock-total(s.herbStock),gardens));
    }
    public static boolean registerGathering(SimContext ctx,String site,String place,String herb,int quota) {
        requirePlace(ctx,site,place,"gather");
        if(!rules(ctx).herbs().contains(herb)||quota<1||quota>rules(ctx).gatherQuotaLimit())throw new IllegalArgumentException("invalid_gathering_rule");
        String key=LifeState.placeKey(site,place);var old=ctx.state.life.gathering.get(key);
        if(old!=null) {
            if(old.herb.equals(herb)&&old.dailyQuota==quota)return false;
            throw new IllegalArgumentException("gathering_already_bound");
        }
        var g=new ProfessionState.Gathering();g.herb=herb;g.dailyQuota=quota;
        ctx.state.life.gathering.put(key,g);return true;
    }
    public static boolean configureGarden(SimContext ctx,String site,String place,String herb) {
        requirePlace(ctx,site,place,"garden");
        if(!rules(ctx).herbs().contains(herb))throw new IllegalArgumentException("unknown_herb");
        String key=LifeState.placeKey(site,place);var old=ctx.state.life.gardens.get(key);
        if(old!=null) {
            if(old.herb.equals(herb))return false;
            throw new IllegalArgumentException("garden_already_bound");
        }
        if(ctx.state.life.gardens.keySet().stream().filter(k->k.startsWith(site+"|")).count()>=rules(ctx).maxGardensPerSite())
            throw new IllegalArgumentException("garden_limit");
        var g=new ProfessionState.Garden();g.herb=herb;ctx.state.life.gardens.put(key,g);return true;
    }
    /** Bind one real crop to an existing garden stand. An active abstract batch cannot become a crop. */
    public static boolean bindPhysicalGarden(SimContext ctx,String site,String place,String herb,int x,int y,int z) {
        requirePlace(ctx,site,place,"garden");
        LifePlace.requirePosition(x,y,z);
        if(!rules(ctx).herbs().contains(herb))throw new IllegalArgumentException("unknown_herb");
        LifePlace stand=ctx.state.life.sites.get(site).place(place).orElseThrow();
        if(stand.x()==x&&stand.y()==y&&stand.z()==z)throw new IllegalArgumentException("crop_equals_stand");
        double dx=(double)x-stand.x(),dy=(double)y-stand.y(),dz=(double)z-stand.z();
        double reach=rules(ctx).physicalReach();
        if(dx*dx+dy*dy+dz*dz>reach*reach)throw new IllegalArgumentException("crop_out_of_reach");
        String key=LifeState.placeKey(site,place);
        var old=ctx.state.life.gardens.get(key);
        if(old!=null) {
            if(old.physical) {
                if(old.herb.equals(herb)&&old.cropX==x&&old.cropY==y&&old.cropZ==z)return false;
                throw new IllegalArgumentException("garden_already_bound");
            }
            if(old.plantedAt!=-1||old.matureAt!=-1||old.tended)throw new IllegalArgumentException("abstract_batch_active");
            if(!old.herb.equals(herb))throw new IllegalArgumentException("garden_already_bound");
        } else if(ctx.state.life.gardens.keySet().stream().filter(k->k.startsWith(site+"|")).count()>=rules(ctx).maxGardensPerSite())
            throw new IllegalArgumentException("garden_limit");
        String dimension=ctx.state.life.sites.get(site).dimension();
        for(var entry:ctx.state.life.gardens.entrySet()) {
            if(entry.getKey().equals(key)||!entry.getValue().physical)continue;
            String otherSite=entry.getKey().substring(0,entry.getKey().indexOf('|'));
            var other=ctx.state.life.sites.get(otherSite);
            var g=entry.getValue();
            if(other!=null&&other.dimension().equals(dimension)&&g.cropX==x&&g.cropY==y&&g.cropZ==z)
                throw new IllegalArgumentException("crop_already_bound");
        }
        var g=old==null?new ProfessionState.Garden():old;
        g.herb=herb;g.physical=true;g.cropX=x;g.cropY=y;g.cropZ=z;g.observedAge=-2;
        ctx.state.life.gardens.put(key,g);return true;
    }
    private static void requirePlace(SimContext ctx,String site,String place,String kind) {
        var s=ctx.state.life.sites.get(site);
        if(s==null||s.place(place).filter(p->p.kind().equals(kind)).isEmpty())throw new IllegalArgumentException("invalid_profession_place");
    }
    public static boolean canGather(SimContext ctx,LifeResident r,LifePlace place,long at) {
        boolean compatible=r.role.equals("rogue")||r.role.equals("visitor");
        if(!compatible&&!r.role.equals("herb_child"))return false;
        var g=ctx.state.life.gathering.get(LifeState.placeKey(r.siteId,place.id()));
        if(g==null)return compatible;
        var h=rules(ctx).herbRules().get(g.herb);
        if(h==null||rules(ctx).level(profile(ctx,r.personId).gatheringExperience)<h.gatheringLevel())return false;
        long day=at/ctx.state.life.ticksPerDay;
        return day>g.quotaDay||g.gatheredToday<g.dailyQuota;
    }
    /** Return accepted units. An old untyped gather slot remains untyped. */
    public static int gather(SimContext ctx,LifeResident r,int requested,long at) {
        var place=ctx.state.life.sites.get(r.siteId).place(r.targetId).orElseThrow();
        if(!place.kind().equals("gather")||!canGather(ctx,r,place,at))return 0;
        var g=ctx.state.life.gathering.get(LifeState.placeKey(r.siteId,r.targetId));
        int units=requested;
        if(g!=null) {
            long day=at/ctx.state.life.ticksPerDay;
            if(day>g.quotaDay){g.quotaDay=day;g.gatheredToday=0;}
            units=Math.min(units,g.dailyQuota-g.gatheredToday);
            if(units>0){g.gatheredToday+=units;r.herbInventory.merge(g.herb,units,Integer::sum);}
        }
        if(units>0)gain(ctx,profile(ctx,r.personId),"gather",rules(ctx).gatheringExperience());
        return units;
    }
    public static boolean canPlant(SimContext ctx,LifeResident r,String herb) {
        if(!r.role.equals("herb_child")&&!r.role.equals("spirit_grower"))return false;
        var h=rules(ctx).herbRules().get(herb);
        var p=ctx.state.life.professions.get(r.personId);
        return h!=null&&p!=null&&rules(ctx).level(p.plantingExperience)>=h.plantingLevel();
    }
    public static String gardenActivity(SimContext ctx,LifeResident r,LifePlace place,long at) {
        if(!place.kind().equals("garden"))return "";
        var g=ctx.state.life.gardens.get(LifeState.placeKey(r.siteId,place.id()));
        if(g==null||!canPlant(ctx,r,g.herb))return "";
        var s=ctx.state.life.supplies.get(r.siteId);var rules=rules(ctx);
        if(g.physical) {
            if(!r.observed||!r.embodied||g.observedAge==-2||s==null)return "";
            if(g.observedAge==-1)return s.herbStock.getOrDefault(g.herb,0)>=rules.seedUnits()?"plant":"";
            if(g.observedAge==2)return s.stock<stockCapacity(ctx,r.siteId)&&g.harvestSerial<Long.MAX_VALUE?"harvest":"";
            return r.role.equals("spirit_grower")&&!g.tended?"tend":"";
        }
        if(g.plantedAt<0)return s.herbStock.getOrDefault(g.herb,0)>=rules.seedUnits()?"plant":"";
        int yield=rules.harvestUnits()+(g.tended?rules.tendedBonus():0);
        if(at>=g.matureAt)return s.stock<=stockCapacity(ctx,r.siteId)-yield?"harvest":"";
        return r.role.equals("spirit_grower")&&!g.tended?"tend":"";
    }
    public static ProfessionRules.Recipe availableRecipe(SimContext ctx,LifeResident r) {
        if(!r.role.equals("alchemist"))return null;
        return rules(ctx).recipes().stream().filter(recipe->canBrew(ctx,r,recipe))
                .min(Comparator.comparingInt((ProfessionRules.Recipe recipe)->ctx.state.commerce.markets.get(r.siteId).items.getOrDefault(recipe.output(),0))
                        .thenComparing(ProfessionRules.Recipe::id)).orElse(null);
    }
    public static boolean canBrew(SimContext ctx,LifeResident r,ProfessionRules.Recipe recipe) {
        if(!r.role.equals("alchemist")||recipe==null||rules(ctx).level(profile(ctx,r.personId).alchemyExperience)<recipe.minimumLevel())return false;
        var m=ctx.state.commerce.markets.get(r.siteId);var s=ctx.state.life.supplies.get(r.siteId);
        if(m==null||s==null||m.revision==Long.MAX_VALUE)return false;
        var offer=ctx.data.commerceRules().offers().stream().filter(o->o.kind().equals("buy")&&o.item().equals(recipe.output())).findFirst().orElse(null);
        if(offer==null||m.items.getOrDefault(recipe.output(),0)>offer.capacity()-recipe.outputCount())return false;
        return recipe.ingredients().entrySet().stream().allMatch(e->s.herbStock.getOrDefault(e.getKey(),0)>=e.getValue());
    }
    public static int duration(SimContext ctx,LifeResident r) {
        return switch(r.activity) {
            case "plant"->rules(ctx).plantTicks();case "tend"->rules(ctx).tendTicks();case "harvest"->rules(ctx).harvestTicks();
            case "brew"->{var recipe=rules(ctx).recipe(r.recipeId);if(recipe==null)throw new IllegalStateException("missing_recipe");yield recipe.durationTicks();}
            default->ctx.data.lifeRules().duration(r.activity);
        };
    }
    /** Executed once by LifeEngine.complete after place/lease/revision validation. */
    public static boolean complete(SimContext ctx,LifeResident r,long at) {
        var s=ctx.state.life.supplies.get(r.siteId);var rules=rules(ctx);var p=profile(ctx,r.personId);
        if(r.activity.equals("brew")) {
            var workplace=ctx.state.life.sites.get(r.siteId).place(r.targetId).orElse(null);
            if(workplace==null||!workplace.kind().equals("work")||!r.targetId.equals(r.workId))return false;
            var recipe=rules.recipe(r.recipeId);
            if(!canBrew(ctx,r,recipe))return false;
            recipe.ingredients().forEach((herb,units)->{remove(s.herbStock,herb,units);s.stock-=units;});
            var m=ctx.state.commerce.markets.get(r.siteId);
            m.items.merge(recipe.output(),recipe.outputCount(),Integer::sum);m.revision++;
            gain(ctx,p,"alchemy",rules.alchemyExperience());return true;
        }
        var site=ctx.state.life.sites.get(r.siteId);var place=site.place(r.targetId).orElseThrow();
        var g=ctx.state.life.gardens.get(LifeState.placeKey(r.siteId,r.targetId));
        if(g==null||g.physical||!place.kind().equals("garden")||!canPlant(ctx,r,g.herb))return false;
        String allowed=gardenActivity(ctx,r,place,at);
        if(!r.activity.equals(allowed))return false;
        switch(r.activity) {
            case "plant"->{
                if(at>Long.MAX_VALUE-rules.growthTicks())return false;
                remove(s.herbStock,g.herb,rules.seedUnits());s.stock-=rules.seedUnits();
                g.plantedAt=at;g.matureAt=at+rules.growthTicks();g.tended=false;
                gain(ctx,p,"plant",rules.plantingExperience());
            }
            case "tend"->{g.tended=true;gain(ctx,p,"plant",rules.plantingExperience());}
            case "harvest"->{
                int units=rules.harvestUnits()+(g.tended?rules.tendedBonus():0);
                s.stock+=units;s.herbStock.merge(g.herb,units,Integer::sum);
                g.plantedAt=-1;g.matureAt=-1;g.tended=false;
                gain(ctx,p,"plant",rules.harvestExperience());
            }
            default->{return false;}
        }
        return true;
    }
    /** Called only by WorldSim after the real crop mutation succeeds. */
    public static void creditPhysicalWork(SimContext ctx,LifeResident r,int harvestUnits) {
        var s=ctx.state.life.supplies.get(r.siteId);
        var g=ctx.state.life.gardens.get(LifeState.placeKey(r.siteId,r.targetId));
        var p=ctx.state.life.professions.get(r.personId);
        switch(r.activity) {
            case "plant"->{
                remove(s.herbStock,g.herb,rules(ctx).seedUnits());s.stock-=rules(ctx).seedUnits();
                g.tended=false;gain(ctx,p,"plant",rules(ctx).plantingExperience());
            }
            case "tend"->{g.tended=true;gain(ctx,p,"plant",rules(ctx).plantingExperience());}
            case "harvest"->{
                s.stock+=harvestUnits;s.herbStock.merge(g.herb,harvestUnits,Integer::sum);
                g.harvestSerial++;g.tended=false;gain(ctx,p,"plant",rules(ctx).harvestExperience());
            }
            default->throw new IllegalStateException("not_physical_garden_activity");
        }
        g.observedAge=-2;
    }
    /** Transfer known kinds first; a legacy remainder stays unclassified. */
    public static void deliver(LifeResident r,LifeState.Supply s,int units) {
        int left=units;
        for(String herb:new ArrayList<>(r.herbInventory.keySet())) {
            int moved=Math.min(left,r.herbInventory.get(herb));if(moved==0)continue;
            remove(r.herbInventory,herb,moved);s.herbStock.merge(herb,moved,Integer::sum);left-=moved;
            if(left==0)break;
        }
        r.herbs-=units;s.stock+=units;
    }
    private static void gain(SimContext ctx,ProfessionState.Profile p,String skill,int amount) {
        switch(skill) {
            case "gather"->p.gatheringExperience=(int)Math.min(rules(ctx).maxExperience(),(long)p.gatheringExperience+amount);
            case "plant"->p.plantingExperience=(int)Math.min(rules(ctx).maxExperience(),(long)p.plantingExperience+amount);
            case "alchemy"->p.alchemyExperience=(int)Math.min(rules(ctx).maxExperience(),(long)p.alchemyExperience+amount);
            default->throw new IllegalArgumentException("unknown_skill");
        }
    }
    private static void remove(Map<String,Integer> map,String herb,int units) {
        int next=map.getOrDefault(herb,0)-units;
        if(next<0)throw new IllegalStateException("herb_underflow");
        if(next==0)map.remove(herb);else map.put(herb,next);
    }
    public static int total(Map<String,Integer> map) { return map.values().stream().mapToInt(Integer::intValue).sum(); }
}
