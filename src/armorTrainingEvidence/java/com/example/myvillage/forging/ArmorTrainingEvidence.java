package com.example.myvillage.forging;

import com.example.myvillage.stats.runtime.RealmArmorRuntime;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real server hurt pipeline using fake players; this is not mapped player input. */
@EventBusSubscriber(modid="myvillage")
public final class ArmorTrainingEvidence {
    private static final boolean ENABLED="1".equals(System.getenv("MYVILLAGE_ARMOR_TRAINING_EVIDENCE"));
    private static final String IN="myvillage_workshop_armor_received_paid_damage", OUT="myvillage_workshop_paid_damage";
    private static final List<String> SELECTED=List.of("direct_actual_and_all_slots","replay_post","different_attackers_no_remainder","shared_cap_and_nbt_reload","projectile_attribution","invalid_sources","melee_weapon_and_armor","tool_work","receipt_limit_and_cleanup");
    private static final List<String> DONE=new ArrayList<>(), FAILED=new ArrayList<>();
    private static final List<Map<String,Object>> CHECKS=new ArrayList<>();
    private static int ticks, replays; private static boolean finished;
    private static ServerPlayer replayPlayer;
    private ArmorTrainingEvidence() {}
    private static void check(String name,boolean pass,Object detail){
        CHECKS.add(Map.of("name",name,"pass",pass,"detail",String.valueOf(detail)));
        if(!pass)FAILED.add(name+": "+detail);
    }
    private static boolean close(double a,double b){return Math.abs(a-b)<.0002;}
    private static ItemStack item(String id){return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("myvillage:"+id)));}
    private static final class HurtEligiblePlayer extends FakePlayer {
        HurtEligiblePlayer(ServerLevel level,GameProfile profile){super(level,profile);}
        // FakePlayer normally rejects every hit; preserve creative immunity in this fixture.
        @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source){return getAbilities().invulnerable;}
    }
    private static ServerPlayer player(ServerLevel level,String name) throws ReflectiveOperationException {
        var p=new HurtEligiblePlayer(level,new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)),name));
        // FakePlayer.tick() is intentionally empty, so vanilla's 60-tick spawn grace never expires.
        var grace=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");grace.setAccessible(true);grace.setInt(p,0);
        p.setGameMode(GameType.SURVIVAL);p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);p.setHealth(200);p.setPos(.5,82,.5);
        p.setItemSlot(EquipmentSlot.HEAD,item("xuantie_helmet"));p.setItemSlot(EquipmentSlot.CHEST,item("xuantie_chestplate"));
        p.setItemSlot(EquipmentSlot.LEGS,item("xuantie_leggings"));p.setItemSlot(EquipmentSlot.FEET,item("xuantie_boots"));
        return p;
    }
    private static Zombie zombie(ServerLevel level,double hp){
        var z=EntityType.ZOMBIE.create(level);if(z==null)throw new IllegalStateException("zombie create");
        z.setPos(2.5,82,.5);z.setNoAi(true);z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);z.setHealth((float)hp);
        z.getAttribute(Attributes.ARMOR).setBaseValue(0);level.addFreshEntity(z);return z;
    }
    private static int xp(ServerPlayer p,EquipmentSlot slot){return WorkshopEquipment.record(p.getItemBySlot(slot)).experience();}
    private static void ready(ServerPlayer p){p.invulnerableTime=0;p.setHealth(p.getMaxHealth());p.setAbsorptionAmount(0);}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void replay(LivingDamageEvent.Post event){
        if(!ENABLED||event.getEntity()!=replayPlayer)return;
        int before=xp(replayPlayer,EquipmentSlot.CHEST);
        var entity=event.getSource().getEntity();double paid=entity==null?0:entity.getPersistentData().getDouble(IN);
        check("container_mixin_visible",RealmArmorRuntime.currentContainer(replayPlayer)!=null,RealmArmorRuntime.containerDepth(replayPlayer));
        WorkshopProgress.damaged(event);replays++;
        check("duplicate_post_no_award",before==xp(replayPlayer,EquipmentSlot.CHEST)
            &&(entity==null||close(paid,entity.getPersistentData().getDouble(IN))),paid);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!ENABLED||finished||++ticks<40)return;
        var server=event.getServer();
        try {run(server,event);}catch(Throwable t){FAILED.add("exception: "+t);t.printStackTrace();}finally{finish(server);}
    }
    private static void run(MinecraftServer server,ServerTickEvent.Post tick) throws Exception {
        var level=server.overworld();level.getChunkAt(new net.minecraft.core.BlockPos(0,82,0));
        var p=player(level,"TrainingFixtureA");var q=player(level,"TrainingFixtureB");
        var z=zombie(level,20);double before=z.getPersistentData().getDouble(IN);ready(p);float health=p.getHealth();replayPlayer=p;
        boolean hit=p.hurt(level.damageSources().mobAttack(z),20);replayPlayer=null;
        double loss=health-p.getHealth(),paid=z.getPersistentData().getDouble(IN);
        check("direct_health_loss_and_paid",hit&&loss>0&&close(paid-before,loss),"loss="+loss+" paid="+paid);
        for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))
            check("worn_"+slot.getName(),paid>=4&&xp(p,slot)==(int)(paid/4),"xp="+xp(p,slot)+" paid="+paid);
        DONE.add("direct_actual_and_all_slots");check("replay_seen",replays>0,replays);DONE.add("replay_post");
        var a=zombie(level,20);var b=zombie(level,20);int start=xp(p,EquipmentSlot.CHEST);
        ready(p);p.hurt(level.damageSources().mobAttack(a),2);ready(p);p.hurt(level.damageSources().mobAttack(b),2);
        double pa=a.getPersistentData().getDouble(IN),pb=b.getPersistentData().getDouble(IN);
        check("remainders_stay_per_source",pa>0&&pb>0&&pa<4&&pb<4&&xp(p,EquipmentSlot.CHEST)==start,"a="+pa+" b="+pb);DONE.add("different_attackers_no_remainder");
        ready(q);q.hurt(level.damageSources().mobAttack(z),12);ready(p);p.hurt(level.damageSources().mobAttack(z),30);
        check("budget_shared_and_capped",close(z.getPersistentData().getDouble(IN),20),z.getPersistentData().getDouble(IN));
        int capXp=xp(q,EquipmentSlot.CHEST);ready(q);q.hurt(level.damageSources().mobAttack(z),12);
        check("cap_survives_heal_and_player_change",xp(q,EquipmentSlot.CHEST)==capXp&&close(z.getPersistentData().getDouble(IN),20),xp(q,EquipmentSlot.CHEST));
        CompoundTag saved=new CompoundTag();z.saveWithoutId(saved);var fixture=Path.of("../reports/armor-training-native/attacker.nbt");Files.createDirectories(fixture.getParent());
        NbtIo.writeCompressed(saved,fixture);var restored=EntityType.ZOMBIE.create(level);restored.load(NbtIo.readCompressed(fixture,NbtAccounter.unlimitedHeap()));
        check("source_nbt_reload_budget",close(restored.getPersistentData().getDouble(IN),20),restored.getPersistentData());DONE.add("shared_cap_and_nbt_reload");
        var archer=zombie(level,50);var arrow=new Arrow(EntityType.ARROW,level);arrow.setOwner(archer);
        ready(p);p.hurt(level.damageSources().arrow(arrow,archer),10);double arrowPaid=archer.getPersistentData().getDouble(IN);
        check("enemy_arrow_credited",arrowPaid>0,arrowPaid);
        var wrong=new Arrow(EntityType.ARROW,level);wrong.setOwner(a);ready(p);p.hurt(level.damageSources().arrow(wrong,archer),10);
        check("wrong_arrow_owner_rejected",close(arrowPaid,archer.getPersistentData().getDouble(IN)),archer.getPersistentData());
        var ownerless=new Arrow(EntityType.ARROW,level);ready(p);p.hurt(level.damageSources().arrow(ownerless,archer),10);
        check("ownerless_arrow_rejected",close(arrowPaid,archer.getPersistentData().getDouble(IN)),archer.getPersistentData());DONE.add("projectile_attribution");
        int invalidXp=xp(p,EquipmentSlot.CHEST);ready(p);p.hurt(level.damageSources().fall(),10);ready(p);p.hurt(level.damageSources().playerAttack(q),10);
        check("environment_and_pvp_rejected",xp(p,EquipmentSlot.CHEST)==invalidXp,xp(p,EquipmentSlot.CHEST));
        ready(p);p.setGameMode(GameType.CREATIVE);p.hurt(level.damageSources().mobAttack(archer),10);p.setGameMode(GameType.SURVIVAL);
        check("creative_rejected",close(arrowPaid,archer.getPersistentData().getDouble(IN)),archer.getPersistentData());DONE.add("invalid_sources");
        p.setItemSlot(EquipmentSlot.MAINHAND,item("qingfeng_sword"));var target=zombie(level,100);int armorBefore=xp(p,EquipmentSlot.CHEST);
        target.invulnerableTime=0;target.hurt(level.damageSources().playerAttack(p),8);target.invulnerableTime=0;target.hurt(level.damageSources().playerAttack(p),8);
        double melee=target.getPersistentData().getDouble(OUT);
        check("melee_weapon_threshold",melee>0&&xp(p,EquipmentSlot.MAINHAND)==(int)(melee/4),"paid="+melee+" weapon="+xp(p,EquipmentSlot.MAINHAND));
        check("melee_armor_threshold",xp(p,EquipmentSlot.CHEST)-armorBefore==(int)(melee/16),"paid="+melee+" armor="+(xp(p,EquipmentSlot.CHEST)-armorBefore));
        p.setItemSlot(EquipmentSlot.MAINHAND,item("lingxiao_spear"));var spearTarget=zombie(level,100);
        spearTarget.hurt(level.damageSources().playerAttack(p),8);double spearPaid=spearTarget.getPersistentData().getDouble(OUT);
        check("spear_melee_threshold",spearPaid>0&&xp(p,EquipmentSlot.MAINHAND)==(int)(spearPaid/4),"paid="+spearPaid+" xp="+xp(p,EquipmentSlot.MAINHAND));DONE.add("melee_weapon_and_armor");
        var hoe=item("forged_hoe");var pos=new net.minecraft.core.BlockPos(1024+(int)(level.getGameTime()%500000),80,1024);WorkshopProgress.tilled(hoe,level,pos,p);
        int hoeXp=WorkshopEquipment.record(hoe).experience();WorkshopProgress.tilled(hoe,level,pos,p);
        check("hoe_work_once",hoeXp>0&&WorkshopEquipment.record(hoe).experience()==hoeXp,hoeXp);
        var names=List.of("forged_pickaxe","forged_axe","forged_shovel");
        var states=List.of(net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState(),net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState(),net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
        for(int i=0;i<names.size();i++){
            var tool=item(names.get(i));var place=pos.offset(i+1,0,0);WorkshopProgress.mined(tool,level,states.get(i),place,p);
            int earned=WorkshopEquipment.record(tool).experience();WorkshopProgress.mined(tool,level,states.get(i),place,p);
            check(names.get(i)+"_work_once",earned>0&&WorkshopEquipment.record(tool).experience()==earned,earned);
        }
        DONE.add("tool_work");
        WorkshopProgress.tick(tick);
        var field=WorkshopProgress.class.getDeclaredField("DAMAGE");field.setAccessible(true);
        @SuppressWarnings("unchecked") var receipts=(Map<DamageContainer,?>)field.get(null);
        int limit=WorkshopRules.get().damage_receipt_limit();
        for(int i=0;i<limit;i++)fakeReceipt(p,z);
        check("receipt_limit_filled",receipts.size()==limit,receipts.size());
        Object control=new Object();var witness=new java.lang.ref.WeakReference<>(control);control=null;
        for(int i=0;i<20&&witness.get()!=null;i++){System.gc();Thread.sleep(10);}
        check("gc_observed",witness.get()==null,witness.get());
        check("receipt_limit_survives_gc",receipts.size()==limit,receipts.size());
        var overflow=new DamageContainer(level.damageSources().mobAttack(z),1);
        WorkshopProgress.beforeDamage(new LivingDamageEvent.Pre(p,overflow));
        check("receipt_overflow_rejected",receipts.size()==limit&&!receipts.containsKey(overflow),receipts.size());
        WorkshopProgress.tick(tick);check("receipt_tick_cleanup",receipts.isEmpty(),receipts.size());DONE.add("receipt_limit_and_cleanup");
    }
    private static void fakeReceipt(ServerPlayer p,Zombie z){
        WorkshopProgress.beforeDamage(new LivingDamageEvent.Pre(p,new DamageContainer(z.damageSources().mobAttack(z),1)));
    }
    private static void finish(MinecraftServer server){
        finished=true;var unrun=new ArrayList<>(SELECTED);unrun.removeAll(DONE);
        var result=new LinkedHashMap<String,Object>();result.put("selected",SELECTED);result.put("completed",DONE);result.put("failed",FAILED);result.put("unrun",unrun);result.put("checks",CHECKS);
        result.put("kind","native server hurt pipeline with fake players; no mapped player input");
        try{var path=Path.of("../reports/armor-training-native/status.json");Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception e){e.printStackTrace();}
        server.halt(false);
    }
}
