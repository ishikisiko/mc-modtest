package com.example.myvillage.stats;

import com.example.myvillage.cultivation.*;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.stats.runtime.*;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 真服务器数值/存档 fixture；不是玩家映射输入、视觉或手感验收。 */
@EventBusSubscriber(modid="myvillage")
public final class AdditiveStatsEvidence {
    private static final boolean ENABLED="1".equals(System.getenv("MYVILLAGE_ADDITIVE_EVIDENCE"));
    private static final boolean RESTART="restart".equals(System.getenv("MYVILLAGE_ADDITIVE_PHASE"));
    private static final Path OUT=Path.of(System.getenv().getOrDefault("MYVILLAGE_ADDITIVE_OUT","../reports/additive-native"));
    private static final List<Map<String,Object>> CHECKS=new ArrayList<>();
    private static final List<String> FAILED=new ArrayList<>(),DONE=new ArrayList<>();
    private static final List<String> SELECTED=RESTART?List.of("restart_profile_and_wounded_health"):
            List.of("approved_tables","attribute_composition","mock_guards_and_settlement","repair_only_difference","vanilla_melee","custom_move_and_critical","armor_resistance_absorption","projectile_and_environment","elemental_and_derived","npc_and_beast","save_profile");
    private static int ticks;private static boolean finished;
    private AdditiveStatsEvidence(){}
    private static boolean close(double a,double b){return Math.abs(a-b)<.0003;}
    private static void check(String name,boolean pass,Object detail){CHECKS.add(Map.of("name",name,"pass",pass,"detail",String.valueOf(detail)));if(!pass)FAILED.add(name+": "+detail);}
    private static class HurtPlayer extends FakePlayer {
        HurtPlayer(ServerLevel level,String name){super(level,new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)),name));}
        @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source){return getAbilities().invulnerable;}
    }
    private static ServerPlayer player(ServerLevel level,String name)throws Exception{
        var p=new HurtPlayer(level,name);p.setGameMode(GameType.SURVIVAL);p.setPos(.5,82,.5);p.setOnGround(true);
        var grace=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");grace.setAccessible(true);grace.setInt(p,0);
        return p;
    }
    private static CultivationProfile profile(String realm,String stage,int root,int golden){
        return CultivationProfile.DEFAULT.withSpiritualRoot(new SpiritualRoot(Map.of(ResourceLocation.parse("myvillage:fire"),10000)))
                .learnTechnique(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)
                .withRealmAndStage(ResourceLocation.parse("myvillage:"+realm),ResourceLocation.parse("myvillage:"+stage))
                .withFoundationQuality(root).withGoldenCoreQuality(golden);
    }
    private static void install(ServerPlayer p,CultivationProfile profile){
        var result=CultivationService.replaceProfile(p,profile);if(!result.success())throw new IllegalStateException(result.message());
    }
    private static void ready(ServerPlayer p){p.invulnerableTime=0;p.setHealth(p.getMaxHealth());p.setAbsorptionAmount(0);}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!ENABLED||finished||++ticks<45)return;
        var server=event.getServer();
        try{if(RESTART)restart(server);else run(server);}catch(Throwable t){FAILED.add("exception: "+t);t.printStackTrace();}
        finally{finished=true;try{Files.createDirectories(OUT);Files.writeString(OUT.resolve(RESTART?"restart.json":"initial.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "selected",SELECTED,"completed",DONE,"failed",FAILED,"unrun",SELECTED.stream().filter(s->!DONE.contains(s)).toList(),
                "checks",CHECKS,"pass",FAILED.isEmpty()&&DONE.size()==SELECTED.size(),"evidence_kind","native_server_fixture_not_mapped_input")));}catch(Exception e){e.printStackTrace();}server.halt(false);}
    }
    private static void run(MinecraftServer server)throws Exception{
        var level=server.overworld();level.getChunkAt(new net.minecraft.core.BlockPos(0,82,0));
        var p=player(level,"StatsProfiles");
        int[] hp={30,32,34,36,38,40,42,44,46};double[] attack={2,2,3,3,4,4,5,5,6},def={1,1,1.5,1.5,2,2,2.5,2.5,3};
        for(int i=0;i<9;i++){
            install(p,profile("qi_refining","qi_refining_"+(i+1),0,0));
            check("qi_"+(i+1),close(p.getMaxHealth(),hp[i])&&close(p.getAttributeValue(Attributes.ATTACK_DAMAGE),attack[i])
                    &&close(p.getAttributeValue(ModStatsAttributes.BODY_DEFENSE),def[i])&&CultivationService.spiritualPowerCap(p)==30+10*i,StatsRuntime.info(p));
        }
        double[][] foundations={{62,10,4,160},{66,11,4.5,180},{70,12,5,200},{78,14,6,240}};
        for(int q=1;q<=4;q++){
            install(p,profile("foundation_establishment","foundation_early",q,0));var e=foundations[q-1];
            check("foundation_"+q,close(p.getMaxHealth(),e[0])&&close(p.getAttributeValue(Attributes.ATTACK_DAMAGE),e[1])
                    &&close(p.getAttributeValue(ModStatsAttributes.BODY_DEFENSE),e[2])&&CultivationService.spiritualPowerCap(p)==(long)e[3],StatsRuntime.info(p));
        }
        for(int q=1;q<=3;q++){
            install(p,profile("golden_core","golden_late",4,q));
            check("golden_late_"+q,close(p.getMaxHealth(),130+10*q)&&close(p.getAttributeValue(Attributes.ATTACK_DAMAGE),28+4*q)
                    &&close(p.getAttributeValue(ModStatsAttributes.BODY_DEFENSE),11+q)&&CultivationService.spiritualPowerCap(p)==440+40*q,StatsRuntime.info(p));
        }
        DONE.add("approved_tables");
        var equip=ResourceLocation.parse("test:equipment");p.getAttribute(Attributes.ATTACK_DAMAGE).addTransientModifier(new AttributeModifier(equip,6,AttributeModifier.Operation.ADD_VALUE));
        StatsRuntime.refreshPlayer(p);StatsRuntime.refreshPlayer(p);
        check("equipment_adds_six_once",close(p.getAttributeValue(Attributes.ATTACK_DAMAGE),46)&&p.getAttribute(Attributes.ATTACK_DAMAGE).getModifiers().size()==2,StatsRuntime.info(p));
        p.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(equip);DONE.add("attribute_composition");
        var m=player(level,"StatsMock");var registry=level.registryAccess().registryOrThrow(ModCultivationRegistries.REALMS);
        var qs=registry.get(ResourceLocation.parse("myvillage:qi_refining")).stages().getLast();
        var start=profile("qi_refining","qi_refining_9",0,0).withCultivationProgress(qs.cultivationCap().orElseThrow()).withStability(qs.advancement().orElseThrow().requiredStability());
        install(m,start.withStability(0));check("mock_refuses_short_stability",!MockBreakthroughs.request(m,true,4)&&CultivationService.getProfile(m).foundationQuality()==0,CultivationService.getProfile(m));
        install(m,start);float currentHealth=m.getHealth();
        check("mock_heaven_success",MockBreakthroughs.request(m,true,4)&&close(m.getMaxHealth(),78)&&close(m.getHealth(),currentHealth),StatsRuntime.info(m));
        check("mock_duplicate_refused",!MockBreakthroughs.request(m,true,4),CultivationService.getProfile(m));
        var fs=registry.get(ResourceLocation.parse("myvillage:foundation_establishment")).stages().getLast();
        install(m,profile("foundation_establishment","foundation_late",1,0).withCultivationProgress(fs.cultivationCap().orElseThrow()).withStability(fs.advancement().orElseThrow().requiredStability()));
        check("low_foundation_can_choose_high_golden",MockBreakthroughs.request(m,false,3)&&CultivationService.getProfile(m).goldenCoreQuality()==3
                &&CultivationService.getProfile(m).foundationQuality()==1&&close(m.getMaxHealth(),124),StatsRuntime.info(m));
        check("mock_does_not_run_real_trial",!com.example.myvillage.cultivation.tribulation.TribulationManager.isActive(m),"active=false");
        DONE.add("mock_guards_and_settlement");
        var repair=new net.minecraft.world.item.ItemStack(com.example.myvillage.item.ModItems.PILL_BUJI.get(),2);
        com.example.myvillage.alchemy.PillQuality.apply(repair,3);
        float repairHealth=m.getHealth();long repairPower=CultivationService.getProfile(m).currentSpiritualPower();
        repair.getItem().finishUsingItem(repair,level,m);
        check("repair_adds_only_ground_quality_difference",close(m.getMaxHealth(),132)&&close(m.getHealth(),repairHealth)
                &&CultivationService.getProfile(m).foundationQuality()==3&&CultivationService.getProfile(m).goldenCoreQuality()==3
                &&CultivationService.getProfile(m).currentSpiritualPower()==repairPower&&repair.getCount()==1,StatsRuntime.info(m));
        repair.getItem().finishUsingItem(repair,level,m);
        check("repeated_repair_does_not_reward_or_consume",close(m.getMaxHealth(),132)&&repair.getCount()==1,StatsRuntime.info(m));
        var heaven=player(level,"StatsHeavenRepair");install(heaven,profile("foundation_establishment","foundation_early",4,0));
        var heavenPill=repair.copy();heavenPill.getItem().finishUsingItem(heavenPill,level,heaven);
        check("repair_preserves_heaven_origin",close(heaven.getMaxHealth(),78)&&CultivationService.getProfile(heaven).foundationQuality()==4
                &&heavenPill.getCount()==1,StatsRuntime.info(heaven));DONE.add("repair_only_difference");
        var target=player(level,"StatsDefender");install(target,profile("foundation_establishment","foundation_early",1,0));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        target.getAttribute(ModStatsAttributes.BODY_DEFENSE).setBaseValue(1);target.getAttribute(Attributes.ARMOR).setBaseValue(0);
        var z=EntityType.ZOMBIE.create(level);z.setNoAi(true);z.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20);
        ready(target);float before=target.getHealth();target.hurt(level.damageSources().mobAttack(z),30);
        check("vanilla_critical_factor_after_defense",close(before-target.getHealth(),22.5),StatsRuntime.lastTrace(target));DONE.add("vanilla_melee");
        ready(target);before=target.getHealth();float raw=StatsRuntime.outgoing(z,20,3)*1.5f;
        StatsRuntime.hurt(z,target,level.damageSources().mobAttack(z),raw);
        check("custom_move_and_critical_after_defense",close(before-target.getHealth(),67.5),StatsRuntime.lastTrace(target));DONE.add("custom_move_and_critical");
        target.getAttribute(Attributes.ARMOR).setBaseValue(20);target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
        target.getAttribute(ModStatsAttributes.PHYSICAL_RESISTANCE).setBaseValue(.25);target.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20);
        ready(target);target.setAbsorptionAmount(2);before=target.getHealth();target.hurt(level.damageSources().mobAttack(z),20);
        var trace=StatsRuntime.lastTrace(target).orElseThrow();
        check("defense_then_vanilla_then_resistance_then_absorption_once",close(before-target.getHealth(),1.9375)
                &&trace.bodyDefense()!=null&&close(trace.bodyDefense().afterDefense(),15)&&trace.armor()!=null
                &&close(trace.armor().defenderScale(),1)&&close(trace.armor().damageAtArmor(),15)
                &&close(trace.armor().armorOutput(),5.25)&&close(trace.afterResistance(),3.9375),trace);
        DONE.add("armor_resistance_absorption");
        target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.getAttribute(ModStatsAttributes.PHYSICAL_RESISTANCE).setBaseValue(0);
        var arrow=new Arrow(EntityType.ARROW,level);arrow.setOwner(z);ready(target);before=target.getHealth();
        target.hurt(level.damageSources().arrow(arrow,z),12);check("projectile_uses_own_damage_base",close(before-target.getHealth(),7),StatsRuntime.lastTrace(target));
        ready(target);before=target.getHealth();target.hurt(level.damageSources().fall(),10);check("fall_ignores_body_defense",close(before-target.getHealth(),10),StatsRuntime.lastTrace(target));
        z.getAttribute(ModStatsAttributes.DEFENSE_PENETRATION).setBaseValue(1);ready(target);before=target.getHealth();target.hurt(level.damageSources().mobAttack(z),20);
        check("body_penetration_is_independent",close(before-target.getHealth(),20),StatsRuntime.lastTrace(target));DONE.add("projectile_and_environment");
        z.getAttribute(ModStatsAttributes.DEFENSE_PENETRATION).setBaseValue(0);
        ready(target);before=target.getHealth();
        var elemental=com.example.myvillage.combat.elemental.ElementalRules.source(p,DamageType.FIRE);
        boolean elementalHit=com.example.myvillage.combat.runtime.CombatDamageService.hurtElementalTick(p,target,elemental,StatsRuntime.outgoing(p,20,1));
        check("independent_elemental_tick_defended_once",elementalHit&&close(before-target.getHealth(),15)
                &&StatsRuntime.lastTrace(target).orElseThrow().bodyDefense()!=null,StatsRuntime.lastTrace(target));
        ready(target);before=target.getHealth();
        boolean derivedHit=com.example.myvillage.combat.runtime.CombatDamageService.hurtDerived(z,target,level.damageSources().mobAttack(z),6);
        check("derived_actual_loss_does_not_repeat_body_defense",derivedHit&&close(before-target.getHealth(),6)
                &&StatsRuntime.lastTrace(target).orElseThrow().bodyDefense()==null,StatsRuntime.lastTrace(target));DONE.add("elemental_and_derived");
        var npc=com.example.myvillage.entity.ModEntities.CULTIVATOR.get().create(level);
        var beast=com.example.myvillage.entity.ModEntities.THUNDER_TIGER.get().create(level);
        for(var actor:List.of(npc,beast)){
            double baseHealth=actor.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
            StatsRuntime.applyRealm(actor,"myvillage:foundation_establishment",0);
            StatsRuntime.applyRealm(actor,"myvillage:foundation_establishment",0);
            check("native_body_"+actor.getType(),close(actor.getMaxHealth(),baseHealth+42)
                    &&close(actor.getAttributeValue(ModStatsAttributes.BODY_DEFENSE),4),StatsRuntime.info(actor));
            ready(target);before=target.getHealth();double nativeAttack=actor.getAttributeValue(Attributes.ATTACK_DAMAGE);
            target.hurt(level.damageSources().mobAttack(actor),(float)nativeAttack);
            check("native_attack_"+actor.getType(),close(before-target.getHealth(),BodyDefense.calculate(nativeAttack,5,0,CultivationStatsRules.bundled()).afterDefense()),StatsRuntime.lastTrace(target));
        }
        DONE.add("npc_and_beast");
        p.setHealth(123);install(p,CultivationService.getProfile(p).withCurrentSpiritualPower(77));
        CompoundTag tag=new CompoundTag();p.saveWithoutId(tag);Files.createDirectories(OUT);NbtIo.writeCompressed(tag,OUT.resolve("profile.nbt"));
        check("wounded_save_not_healed",close(p.getHealth(),123)&&CultivationService.getProfile(p).goldenCoreQuality()==3,StatsRuntime.info(p));DONE.add("save_profile");
    }
    private static void restart(MinecraftServer server)throws Exception{
        var p=player(server.overworld(),"StatsProfiles");p.load(NbtIo.readCompressed(OUT.resolve("profile.nbt"),NbtAccounter.unlimitedHeap()));
        check("nbt_restore_before_refresh",close(p.getMaxHealth(),160)&&close(p.getHealth(),123),StatsRuntime.info(p));
        StatsRuntime.refreshPlayer(p);StatsRuntime.refreshPlayer(p);
        check("restart_qualities_and_power",CultivationService.getProfile(p).foundationQuality()==4&&CultivationService.getProfile(p).goldenCoreQuality()==3
                &&CultivationService.getProfile(p).currentSpiritualPower()==77&&CultivationService.spiritualPowerCap(p)==560,StatsRuntime.info(p));
        check("restart_idempotent_and_no_healing",close(p.getMaxHealth(),160)&&close(p.getHealth(),123)&&close(p.getAttributeValue(Attributes.ATTACK_DAMAGE),40)
                &&close(p.getAttributeValue(ModStatsAttributes.BODY_DEFENSE),14),StatsRuntime.info(p));DONE.add("restart_profile_and_wounded_health");
    }
}
