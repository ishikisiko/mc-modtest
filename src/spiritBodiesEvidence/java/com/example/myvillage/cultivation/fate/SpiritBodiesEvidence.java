package com.example.myvillage.cultivation.fate;

import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.foundation.HealingRuntime;
import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.combat.runtime.CombatDamageService;
import com.example.myvillage.combat.session.CombatSession;
import com.example.myvillage.cultivation.*;
import com.example.myvillage.mixin.PhoenixPlayerListAccess;
import com.example.myvillage.stats.runtime.*;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 原生服务器逻辑探针；无真实网络或映射输入，明确不替代客户端验收，且禁止打包。 */
@EventBusSubscriber(modid="myvillage")
public final class SpiritBodiesEvidence {
    private static final Path OUT=Path.of(System.getenv().getOrDefault("MYVILLAGE_SPIRIT_OUT","../reports/spirit-native"));
    private static final String PHASE=System.getenv().getOrDefault("MYVILLAGE_SPIRIT_PHASE","initial");
    private static final List<Map<String,Object>> CHECKS=new ArrayList<>();
    private static final List<String> FAILED=new ArrayList<>();
    private static final Map<UUID,Integer> DEATHS=new HashMap<>();
    private static final List<ServerPlayer> FIXTURES=new ArrayList<>();
    private static ServerPlayer gold,success,offline,failed,water,restarting,healing,pvp,killer,trialGold,trialPhoenix;
    private static double healStart,warmStart;
    private static Zombie enemy;
    private static int first=-1;
    private static boolean complete;
    private static void check(String name,boolean pass,Object detail){CHECKS.add(Map.of("name",name,"pass",pass,"detail",String.valueOf(detail)));if(!pass)FAILED.add(name+": "+detail);}
    private static boolean near(double a,double b){return Math.abs(a-b)<.004;}
    private static ResourceLocation id(String n){return ResourceLocation.parse(n.contains(":")?n:"myvillage:"+n);}
    private static final class NoOpConnection extends ServerGamePacketListenerImpl {
        final List<SpiritBodyStatusPayload> statuses=new ArrayList<>();
        NoOpConnection(MinecraftServer s,ServerPlayer p){super(s,new Connection(PacketFlow.SERVERBOUND),p,CommonListenerCookie.createInitial(p.getGameProfile(),false));}
        @Override public void send(Packet<?> p){if(p instanceof ClientboundCustomPayloadPacket c&&c.payload() instanceof SpiritBodyStatusPayload state)statuses.add(state);}
        @Override public void send(Packet<?> p,PacketSendListener listener){send(p);}
    }
    @SuppressWarnings("unchecked") private static void attach(MinecraftServer server,ServerPlayer p)throws Exception {
        var field=PlayerList.class.getDeclaredField("playersByUUID");field.setAccessible(true);((Map<UUID,ServerPlayer>)field.get(server.getPlayerList())).put(p.getUUID(),p);
        var players=PlayerList.class.getDeclaredField("players");players.setAccessible(true);((List<ServerPlayer>)players.get(server.getPlayerList())).add(p);p.serverLevel().addNewPlayer(p);
    }
    private static ServerPlayer actor(MinecraftServer server,String name,String fate,double x)throws Exception {
        var level=server.overworld();var p=new ServerPlayer(server,level,new GameProfile(UUID.nameUUIDFromBytes(("spirit-evidence:"+name).getBytes(StandardCharsets.UTF_8)),name),ClientInformation.createDefault());
        p.connection=new NoOpConnection(server,p);((com.example.myvillage.mixin.PhoenixServerPlayerAccess)p).myvillage$setEmberSpawnProtection(0);p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);p.getFoodData().setFoodLevel(10);p.getFoodData().setSaturation(0);p.moveTo(x,-59,20000.5,0,0);attach(server,p);FIXTURES.add(p);
        var profile=CultivationProfile.defaultProfile().withSpiritualRoot(new SpiritualRoot(Map.of(id("fire"),10000)))
                .withRealmAndStage(id("qi_refining"),id("qi_refining_1")).learnTechnique(id("basic_breathing"));
        var result=CultivationService.replaceProfile(p,profile);if(!result.success())throw new IllegalStateException(result.message());
        var ids=List.of("myvillage:"+fate,"myvillage:bright","myvillage:lightfoot");
        CultivationService.replaceFates(p,new FateState(1,1,ids,"",ids,true,0,0,0,0),false);FateRuntime.refresh(p);StatsRuntime.refreshPlayer(p);p.setHealth(p.getMaxHealth());
        if(fate.equals("phoenix_body"))CultivationService.replaceFates(p,FateService.state(p).withPhoenix(new PhoenixState(true,100)),false);
        return p;
    }
    private static void hurt(ServerPlayer p,float amount){p.invulnerableTime=0;p.hurt(p.damageSources().mobAttack(enemy),amount);}
    private static void begin(MinecraftServer server)throws Exception {
        var level=server.overworld();for(int cx=1249;cx<=1258;cx++)for(int cz=1249;cz<=1251;cz++)level.getChunk(cx,cz);
        for(int x=19998;x<=20130;x++)for(int z=19998;z<=20004;z++)level.setBlock(new net.minecraft.core.BlockPos(x,-60,z),Blocks.STONE.defaultBlockState(),3);
        gold=actor(server,"SpiritGold","metal_spirit_body",20000.5);success=actor(server,"SpiritPhoenix","phoenix_body",20010.5);
        offline=actor(server,"SpiritOffline","phoenix_body",20020.5);failed=actor(server,"SpiritFailure","phoenix_body",20030.5);water=actor(server,"SpiritWater","phoenix_body",20040.5);
        enemy=EntityType.ZOMBIE.create(level);enemy.setNoAi(true);enemy.moveTo(20001.5,-59,20000.5);enemy.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(5);level.addFreshEntity(enemy);
        check("gold_innate_health",near(gold.getMaxHealth(),39),gold.getMaxHealth());check("gold_innate_defense",near(gold.getAttributeValue(ModStatsAttributes.BODY_DEFENSE),1.3),gold.getAttributeValue(ModStatsAttributes.BODY_DEFENSE));
        check("phoenix_innate_health",near(success.getMaxHealth(),36),success.getMaxHealth());
        double before=gold.getHealth();hurt(gold,5);double firstLoss=before-gold.getHealth();
        check("first_hit_uses_old_defense",near(firstLoss,3.7),firstLoss);
        check("first_hit_trains_from_actual_loss",near(JingangRuntime.state(gold).total(),firstLoss/39/.01),JingangRuntime.state(gold));
        before=gold.getHealth();hurt(gold,5);check("later_hit_uses_adaptation",before-gold.getHealth()<firstLoss,before-gold.getHealth());
        double points=JingangRuntime.state(gold).total();gold.setAbsorptionAmount(20);hurt(gold,5);check("absorption_does_not_train",near(JingangRuntime.state(gold).total(),points),JingangRuntime.state(gold));gold.setAbsorptionAmount(0);
        gold.setHealth(gold.getMaxHealth());gold.invulnerableTime=0;CombatDamageService.hurtDerived(enemy,gold,gold.damageSources().mobAttack(enemy),1);
        check("derived_does_not_train",near(points,JingangRuntime.state(gold).total()),JingangRuntime.state(gold));
        JingangRuntime.forget(gold.getUUID());
        var session=new CombatSession(CombatStyles.bundled().styleFor(new ItemStack(com.example.myvillage.item.ModItems.QINGFENG_SWORD.get())).orElseThrow());
        try(var scope=JingangRuntime.attack(enemy,session)){for(int i=0;i<4;i++){gold.setHealth(gold.getMaxHealth());hurt(gold,10);}}
        check("multi_hit_action_shares_cap",near(JingangRuntime.state(gold).total(),10),JingangRuntime.state(gold));
        var gear=id("spirit_fixture_gear");gold.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier(gear,20,AttributeModifier.Operation.ADD_VALUE));
        FateRuntime.refresh(gold);FateRuntime.refresh(gold);check("spirit_health_does_not_magnify_gear",near(gold.getMaxHealth(),59),gold.getMaxHealth());
        gold.getAttribute(Attributes.MAX_HEALTH).removeModifier(gear);gold.setHealth(gold.getMaxHealth());
        JingangRuntime.forget(gold.getUUID());
        try(var scope=JingangRuntime.attack(enemy,7)){for(int i=0;i<3;i++){gold.setHealth(gold.getMaxHealth());hurt(gold,10);}}
        check("beast_action_shares_cap",near(JingangRuntime.state(gold).total(),10),JingangRuntime.state(gold));
        points=JingangRuntime.state(gold).total();gold.setHealth(gold.getMaxHealth());
        try(var scope=JingangRuntime.periodic()){hurt(gold,2);}
        check("continuous_field_does_not_train",near(JingangRuntime.state(gold).total(),points),JingangRuntime.state(gold));
        JingangRuntime.forget(gold.getUUID());
        for(int i=0;i<3;i++){
            var arrow=EntityType.ARROW.create(level);arrow.setOwner(enemy);arrow.moveTo(enemy.position());level.addFreshEntity(arrow);
            gold.setHealth(gold.getMaxHealth());gold.invulnerableTime=0;gold.hurt(gold.damageSources().arrow(arrow,enemy),10);arrow.discard();
        }
        check("same_volley_shares_adaptation_cap",near(JingangRuntime.state(gold).total(),10),JingangRuntime.state(gold));
        healing=actor(server,"SpiritHealing","phoenix_body",20050.5);healing.getFoodData().setFoodLevel(10);healing.getFoodData().setSaturation(0);
        double health=healing.getHealth();healing.invulnerableTime=0;healing.hurt(healing.damageSources().lava(),3);
        check("environment_lava_is_immune",near(healing.getHealth(),health),healing.getHealth());
        healing.invulnerableTime=0;healing.hurt(com.example.myvillage.combat.elemental.ElementalRules.source(enemy,com.example.myvillage.stats.DamageType.FIRE),3);
        check("attributed_fire_still_damages",healing.getHealth()<health,healing.getHealth());
        healing.setHealth(healing.getMaxHealth()-5);float gained=HealingRuntime.heal(healing,1,id("fixture/heal"));
        check("received_healing_bonus_applies_once",near(gained,1.2),gained);
        healing.setHealth(healing.getMaxHealth()-5);healStart=healing.getHealth();
        CultivationService.replaceFates(healing,FateService.state(healing).withPhoenix(new PhoenixState(true,62)),false);
        HealingRuntime.modifier(healing,id("fixture/healing_prohibited"),-1,100);
        check("healthy_player_cannot_begin_rebirth",!PhoenixRuntime.tryBegin(success,success.damageSources().mobAttack(enemy))&&FateService.state(success).phoenix().reserve()==100,success.getHealth());
        success.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.TOTEM_OF_UNDYING));hurt(success,1000);
        check("totem_precedes_phoenix",!PhoenixRuntime.active(success)&&success.getMainHandItem().isEmpty()&&FateService.state(success).phoenix().reserve()==100,SpiritBodyRuntime.status(success));
        success.removeAllEffects();success.setHealth(success.getMaxHealth());hurt(success,1000);
        check("lethal_enters_live_ember",PhoenixRuntime.active(success)&&success.isAlive()&&DEATHS.getOrDefault(success.getUUID(),0)==0,SpiritBodyRuntime.status(success));
        check("all_fire_is_spent",FateService.state(success).phoenix().reserve()==0,FateService.state(success).phoenix());
        check("full_reserve_ember_durability",near(success.getHealth(),21.6),success.getHealth());
        check("ember_rejects_healing",HealingRuntime.heal(success,100,id("fixture/heal"))==0,success.getHealth());
        check("ember_cannot_attack",!CombatActors.mayHarm(success,enemy),CombatActors.mayHarm(success,enemy));
        check("ember_cannot_switch_core",!CultivationService.switchCoreTechnique(success,id("basic_breathing")).success(),CultivationService.getProfile(success).activeCoreTechnique());
        var moved=PhoenixRuntime.constrainMovement(success,success.position().add(10,10,10));
        check("movement_has_tick_speed_budget",near(moved.subtract(success.position()).horizontalDistance(),.03)&&moved.y<=success.getY(),moved);
        var again=PhoenixRuntime.constrainMovement(success,success.position().add(10,0,0));check("multiple_packets_share_budget",near(again.subtract(success.position()).horizontalDistance(),0),again);
        hurt(offline,1000);server.getPlayerList().remove(offline);
        check("logout_retains_attackable_player",PhoenixRuntime.active(offline)&&level.getEntity(offline.getUUID())==offline&&!server.getPlayerList().getPlayers().contains(offline),offline.isRemoved());
        failed.getInventory().setItem(1,new ItemStack(Items.DIAMOND));hurt(failed,1000);
        for(int x=20039;x<=20041;x++)for(int z=19999;z<=20001;z++)level.setBlock(new net.minecraft.core.BlockPos(x,-59,z),Blocks.WATER.defaultBlockState(),3);
        hurt(water,1000);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent event){if(event.getEntity() instanceof ServerPlayer p)DEATHS.merge(p.getUUID(),1,Integer::sum);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(ServerTickEvent.Post event) {
        if(!"1".equals(System.getenv("MYVILLAGE_SPIRIT_EVIDENCE"))||complete)return;
        var server=event.getServer();
        try {
            if(first<0){first=server.getTickCount();if(PHASE.equals("initial"))begin(server);else restartBegin(server);}
            int frame=server.getTickCount()-first;
            for(var p:new ArrayList<>(FIXTURES))if(server.getPlayerList().getPlayers().contains(p)&&p.isAlive())p.doTick();
            if(PHASE.equals("initial"))initialTick(server,frame);else restartTick(server,frame);
        }catch(Throwable e){e.printStackTrace();FAILED.add(e.toString());complete=true;try{save(PHASE,false);}catch(Exception ignored){}server.halt(false);}
    }
    private static void initialTick(MinecraftServer server,int frame)throws Exception {
        if(frame==5){trialGold=trial(server,"SpiritTrialGold","metal_spirit_body",20090.5);trialPhoenix=trial(server,"SpiritTrialPhoenix","phoenix_body",20110.5);}
        if(frame==25) {
            float offlineBefore=offline.getHealth();hurt(offline,1);check("offline_ember_remains_damageable",offline.getHealth()<offlineBefore,offline.getHealth());
            int remaining=PhoenixRuntime.clock(success).remaining();float before=success.getHealth();hurt(success,2);
            check("ember_is_damageable_after_protection",success.getHealth()<before,success.getHealth());
            check("damage_does_not_restart_condensation",PhoenixRuntime.clock(success).remaining()==remaining,PhoenixRuntime.clock(success));
            var totem=new ItemStack(Items.TOTEM_OF_UNDYING);failed.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,totem);hurt(failed,1000);
            check("ember_break_is_one_true_death",DEATHS.getOrDefault(failed.getUUID(),0)==1,DEATHS);
            check("ember_does_not_consume_another_totem",totem.getCount()==1,totem.getCount());
        }
        if(frame==30) {
            check("prohibited_auto_healing_spends_no_fire",near(healing.getHealth(),healStart)&&FateService.state(healing).phoenix().reserve()>=62,SpiritBodyRuntime.status(healing));
            HealingRuntime.clear(healing,id("fixture/healing_prohibited"));
            var joining=new ServerPlayer(server,server.overworld(),offline.getGameProfile(),ClientInformation.createDefault());joining.connection=new NoOpConnection(server,joining);
            var loaded=server.getPlayerList().load(joining);check("rejoin_loads_current_ember_file",loaded.isPresent(),loaded.isPresent());
            attach(server,joining);FIXTURES.add(joining);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(joining));
            check("rejoin_preserves_condensation_progress",PhoenixRuntime.clock(joining).remaining()==70,PhoenixRuntime.clock(joining));
            float before=joining.getHealth();hurt(joining,1);check("rejoin_does_not_grant_spawn_protection",joining.getHealth()<before,joining.getHealth());
            check("rejoin_has_only_one_player_body",server.overworld().getEntity(joining.getUUID())==joining,server.overworld().getEntity(joining.getUUID()));
            offline=joining;server.getPlayerList().remove(joining);
        }
        if(frame==105) {
            check("condensation_reforms_without_death",!PhoenixRuntime.active(success)&&DEATHS.getOrDefault(success.getUUID(),0)==0&&near(success.getHealth(),success.getMaxHealth()*.6),SpiritBodyRuntime.status(success));
            check("offline_condensation_finishes_and_releases_body",!PhoenixRuntime.active(offline)&&server.overworld().getEntity(offline.getUUID())==null,offline.getHealth());
            check("offline_success_saves_health",near(offline.getHealth(),21.6)&&FateService.state(offline).phoenix().reserve()==0,FateService.state(offline).phoenix());
            check("immersion_extinguishes_once",!PhoenixRuntime.active(water)&&DEATHS.getOrDefault(water.getUUID(),0)==1,DEATHS);
            var captured=((NoOpConnection)gold.connection).statuses;
            check("private_status_not_sent_to_other_owner",!captured.isEmpty()&&captured.stream().allMatch(p->p.mode()==1),captured.size());
            var drops=server.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,failed.getBoundingBox().inflate(4));
            check("broken_ember_drops_inventory_once",drops.stream().filter(i->i.getItem().is(Items.DIAMOND)).mapToInt(i->i.getItem().getCount()).sum()==1,drops.size());
            check("ember_speed_modifier_is_removed",success.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(id("phoenix_ember_speed"))==null,success.getAttributeValue(Attributes.MOVEMENT_SPEED));
        }
        if(frame==60){check("auto_heal_uses_reserve_above_floor",healing.getHealth()>healStart&&FateService.state(healing).phoenix().reserve()>=60&&FateService.state(healing).phoenix().reserve()<62,SpiritBodyRuntime.status(healing));healing.setHealth(healing.getMaxHealth());}
        if(frame==75){server.overworld().setBlock(healing.blockPosition().offset(1,-1,0),Blocks.NETHERRACK.defaultBlockState(),3);server.overworld().setBlock(healing.blockPosition().offset(1,0,0),Blocks.FIRE.defaultBlockState(),3);warmStart=FateService.state(healing).phoenix().reserve();}
        if(frame==90)success.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(new AttributeModifier(id("fixture_health_change"),10,AttributeModifier.Operation.ADD_VALUE));
        if(frame==95)check("visible_fire_warms_without_stacking",near(FateService.state(healing).phoenix().reserve()-warmStart,.3),FateService.state(healing).phoenix().reserve()-warmStart);
        if(frame==150&&trialPhoenix!=null)trialPhoenix.setHealth(1);
        if(frame==215){
            check("formal_tribulation_trains_thunder",JingangRuntime.state(trialGold).points(AdaptationCategory.THUNDER)>0&&JingangRuntime.state(trialGold).total()<=10,JingangRuntime.state(trialGold));
            var attempt=com.example.myvillage.cultivation.tribulation.TribulationManager.load(trialPhoenix);
            check("failed_tribulation_does_not_spend_phoenix",attempt!=null&&!attempt.active()&&attempt.clock().penaltyApplied()&&!PhoenixRuntime.active(trialPhoenix)&&trialPhoenix.isAlive()&&FateService.state(trialPhoenix).phoenix().reserve()>=60,attempt);
            check("failed_tribulation_keeps_source_realm",CultivationService.getProfile(trialPhoenix).stageId().equals(id("qi_refining_9")),CultivationService.getProfile(trialPhoenix));
        }
        if(frame==225){
            restarting=actor(server,"SpiritRestart","phoenix_body",20060.5);CultivationService.replaceFates(restarting,FateService.state(restarting).withPhoenix(new PhoenixState(true,83)),false);hurt(restarting,1000);
            pvp=actor(server,"SpiritPvp","phoenix_body",20070.5);killer=actor(server,"SpiritKiller","metal_spirit_body",20080.5);killer.getInventory().setItem(2,new ItemStack(Items.EMERALD,7));
            pvp.invulnerableTime=0;pvp.hurt(pvp.damageSources().playerAttack(killer),1000);check("pvp_starts_live_ember",PhoenixRuntime.active(pvp)&&DEATHS.getOrDefault(pvp.getUUID(),0)==0,pvp.getHealth());
            server.getPlayerList().remove(killer);
        }
        if(frame==250){hurt(restarting,1);check("restart_fixture_is_wounded",PhoenixRuntime.clock(restarting).durability()<PhoenixRuntime.clock(restarting).maximumDurability(),PhoenixRuntime.clock(restarting));}
        if(frame==265) {
            var clock=PhoenixRuntime.clock(restarting);Files.createDirectories(OUT);
            var manifest=Map.of("owner",restarting.getUUID().toString(),"remaining",clock.remaining(),"health",clock.durability(),"maximum",clock.maximumDurability(),"pvp_owner",pvp.getUUID().toString(),"killer",killer.getUUID().toString());
            Files.writeString(OUT.resolve("restart.json"),new Gson().toJson(manifest));
            ((PhoenixPlayerListAccess)server.getPlayerList()).myvillage$savePhoenixPlayer(restarting);
            check("restart_saves_partial_condensation",clock.remaining()==60&&clock.durability()>0,clock);
            complete=true;save("initial",FAILED.isEmpty());server.halt(false);
        }
    }
    private static ServerPlayer trial(MinecraftServer server,String name,String fate,double x)throws Exception {
        var player=actor(server,name,fate,x);player.setOnGround(true);
        var profile=CultivationService.getProfile(player).withRealmAndStage(id("qi_refining"),id("qi_refining_9"));
        var realm=player.registryAccess().registryOrThrow(ModCultivationRegistries.REALMS).get(profile.realmId());
        var stageId=profile.stageId();var stage=realm.stages().stream().filter(s->s.id().equals(stageId)).findFirst().orElseThrow();
        CultivationService.replaceProfile(player,profile.withCultivationProgress(stage.cultivationCap().orElseThrow()).withStability(stage.advancement().orElseThrow().requiredStability()));
        FateRuntime.refresh(player);StatsRuntime.refreshPlayer(player);player.setHealth(player.getMaxHealth());
        if(fate.equals("phoenix_body"))CultivationService.replaceFates(player,FateService.state(player).withPhoenix(new PhoenixState(true,60)),false);
        check("formal_trial_starts_"+name,com.example.myvillage.cultivation.tribulation.TribulationManager.start(player,com.example.myvillage.cultivation.tribulation.TribulationKind.HEAVEN),com.example.myvillage.cultivation.tribulation.TribulationManager.statusJson(player));
        return player;
    }
    private static UUID restartOwner,killerId;private static int expectedRemaining;private static double expectedHealth;
    private static void restartBegin(MinecraftServer server)throws Exception {
        var manifest=JsonParser.parseString(Files.readString(OUT.resolve("restart.json"))).getAsJsonObject();restartOwner=UUID.fromString(manifest.get("owner").getAsString());expectedRemaining=manifest.get("remaining").getAsInt();expectedHealth=manifest.get("health").getAsDouble();
        restarting=(ServerPlayer)server.overworld().getEntity(restartOwner);
        check("restart_restores_live_offline_ember",restarting!=null&&PhoenixRuntime.active(restarting),restarting);
        if(restarting==null)throw new IllegalStateException("restart ember missing");
        check("restart_preserves_remaining_ticks",PhoenixRuntime.clock(restarting).remaining()==expectedRemaining-1,PhoenixRuntime.clock(restarting));
        check("restart_preserves_damage",near(restarting.getHealth(),expectedHealth),restarting.getHealth());
        check("restart_does_not_refill_fire",FateService.state(restarting).phoenix().reserve()==0,FateService.state(restarting).phoenix());
        enemy=EntityType.ZOMBIE.create(server.overworld());enemy.setNoAi(true);enemy.moveTo(restarting.position().add(2,0,0));enemy.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(5);server.overworld().addFreshEntity(enemy);
        float before=restarting.getHealth();hurt(restarting,1);check("restart_does_not_grant_spawn_invulnerability",restarting.getHealth()<before,restarting.getHealth());
        pvp=(ServerPlayer)server.overworld().getEntity(UUID.fromString(manifest.get("pvp_owner").getAsString()));killerId=UUID.fromString(manifest.get("killer").getAsString());
        check("restart_keeps_player_kill_credit",pvp!=null&&pvp.getKillCredit()!=null&&pvp.getKillCredit().getUUID().equals(killerId),pvp==null?null:pvp.getKillCredit());
        check("credit_proxy_is_not_a_world_player",server.getPlayerList().getPlayer(killerId)==null&&server.overworld().getEntity(killerId)==null,killerId);
        pvp.invulnerableTime=0;pvp.hurt(pvp.damageSources().generic(),1000);
        check("offline_pvp_ember_dies_once",DEATHS.getOrDefault(pvp.getUUID(),0)==1,DEATHS);
    }
    private static void restartTick(MinecraftServer server,int frame)throws Exception {
        if(frame==expectedRemaining+2) {
            check("resumed_condensation_finishes_once",!PhoenixRuntime.active(restarting)&&server.overworld().getEntity(restartOwner)==null&&DEATHS.getOrDefault(restartOwner,0)==0,restarting.getHealth());
            var restored=new ServerPlayer(server,server.overworld(),restarting.getGameProfile(),ClientInformation.createDefault());restored.connection=new NoOpConnection(server,restored);
            var loaded=server.getPlayerList().load(restored);
            check("normal_player_file_has_final_rebirth",loaded.isPresent()&&near(restored.getHealth(),21.6),restored.getHealth());
            check("normal_player_file_does_not_grant_initial_fire",FateService.state(restored).phoenix().initialized()&&FateService.state(restored).phoenix().reserve()==0,FateService.state(restored).phoenix());
            var killerFile=new ServerPlayer(server,server.overworld(),new GameProfile(killerId,"SpiritKiller"),ClientInformation.createDefault());killerFile.connection=new NoOpConnection(server,killerFile);server.getPlayerList().load(killerFile);
            check("offline_killer_inventory_is_untouched",killerFile.getInventory().getItem(2).is(Items.EMERALD)&&killerFile.getInventory().getItem(2).getCount()==7,killerFile.getInventory().getItem(2));
            check("offline_player_kill_is_awarded_once",killerFile.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAYER_KILLS))==1,killerFile.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAYER_KILLS)));
            complete=true;save("restart_result",FAILED.isEmpty());server.halt(false);
        }
    }
    private static void save(String phase,boolean pass)throws Exception {
        Files.createDirectories(OUT);var report=new LinkedHashMap<String,Object>();report.put("phase",phase);report.put("pass",pass);report.put("checks",CHECKS);report.put("failed",FAILED);report.put("input_evidence","native_server_fixture_no_mapped_input");
        Files.writeString(OUT.resolve(phase+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
