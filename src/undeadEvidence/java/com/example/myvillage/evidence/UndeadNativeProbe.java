package com.example.myvillage.evidence;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.entity.undead.*;
import com.example.myvillage.wilderness.*;
import com.example.myvillage.stats.RealmKey;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Isolated developer fixtures exercising ordinary AI; never a mapped-player acceptance claim. */
@EventBusSubscriber(modid=MyVillageMod.MOD_ID)
public final class UndeadNativeProbe {
    private static final Path SAVED=Path.of("undead-probe-saved.json"), RESULT=Path.of("undead-probe-result.json");
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final List<Case> CASES=new ArrayList<>();
    private static MinecraftServer server;
    private static int tick;
    private static boolean restart,done;
    private static JsonArray saved;
    private static final List<UndeadEntity> BORN=new ArrayList<>();
    private static final JsonObject BIRTH_CHECKS=new JsonObject();
    @SubscribeEvent public static void start(ServerStartedEvent event) throws Exception {
        server=event.getServer();ServerLevel level=server.overworld();restart=Files.exists(SAVED);
        level.setDayTime(18000);server.setDifficulty(Difficulty.NORMAL,true);
        if(restart){saved=JsonParser.parseString(Files.readString(SAVED)).getAsJsonArray();return;}
        int i=0;
        var roster=new ArrayList<>(UndeadRules.get().variants());
        roster.add(UndeadRules.get().require("wandering_corpse"));
        roster.add(UndeadRules.get().require("bone_archer"));
        roster.add(UndeadRules.get().require("bone_archer"));
        for(var v:roster) {
            int index=i;int x=(i%6)*18,z=(i/6)*18;i++;
            boolean close=index==18||index==19,blocked=index==20;
            String label=v.id()+(close?"_close":blocked?"_wall":"");
            for(int dx=-2;dx<=12;dx++)for(int dz=-2;dz<=12;dz++) {
                BlockPos p=new BlockPos(x+dx,99,z+dz);level.setChunkForced(p.getX()>>4,p.getZ()>>4,true);level.setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());
            }
            var mob=UndeadEntities.TYPES.get(v.id()).get().create(level);
            if(mob==null)throw new IllegalStateException("create "+v.id());
            mob.moveTo(x+.5,100,z+.5,0,0);
            String region=v.region().equals("common")?"ximo":v.region();
            mob.assignWildernessBirth(new BeastBirth(new RealmKey("myvillage:qi_refining",0),region,x,100,z,false));
            mob.setHealth(mob.getMaxHealth()-1);
            var target=EntityType.IRON_GOLEM.create(level);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);target.setHealth(2000);target.setNoAi(true);target.setNoGravity(true);
            Vec3 pos=new Vec3(x+.5,100,z+(close?2.0:v.archer()?8.5:3.5));target.setPos(pos);target.setTarget(mob);
            level.addFreshEntity(target);level.addFreshEntity(mob);mob.setTarget(target);
            if(!BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("myvillage",v.id()+"_spawn_egg")))throw new IllegalStateException("missing egg "+v.id());
            CASES.add(new Case(label,mob,target,pos,blocked));
        }
        System.out.println("UNDEAD_PROBE started 21 ordinary AI fixtures, including near attacks and a projectile wall");
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) throws Exception {
        if(server==null||done)return;tick++;
        if(restart) {
            if(tick<40)return;
            JsonArray checks=new JsonArray();
            for(var item:saved) {
                var row=item.getAsJsonObject();var uuid=UUID.fromString(row.get("uuid").getAsString());
                var entity=server.overworld().getEntity(uuid);
                if(!(entity instanceof UndeadEntity mob))throw new IllegalStateException("restart missing "+row.get("id"));
                if(mob.wildernessBirth().isEmpty()||!mob.wildernessBirth().get().region().equals(row.get("region").getAsString()))throw new IllegalStateException("birth rerolled");
                if(Math.abs(mob.getHealth()-row.get("health").getAsFloat())>.001||mob.isMoveRunning())throw new IllegalStateException("restart healed or resumed attack");
                checks.add(row.get("id"));
            }
            JsonObject result=new JsonObject();result.addProperty("phase","restart");result.addProperty("status","passed");result.add("completed",checks);Files.writeString(RESULT,JSON.toJson(result));done=true;server.halt(false);return;
        }
        if(tick==20)birthChecks();
        for(var c:CASES) {
            c.target.setPos(c.targetPos);c.target.setDeltaMovement(Vec3.ZERO);
            if(c.blocked&&!c.wallRaised) {
                var arrows=server.overworld().getEntitiesOfClass(UndeadArrowEntity.class,c.mob.getBoundingBox().inflate(16),a->a.getOwner()==c.mob);
                if(!arrows.isEmpty()) {
                    int x=c.mob.getBlockX(),z=c.mob.getBlockZ()+4;
                    for(int dx=-2;dx<=2;dx++)for(int dy=0;dy<5;dy++)server.overworld().setBlockAndUpdate(new BlockPos(x+dx,100+dy,z),Blocks.STONE.defaultBlockState());
                    c.mob.setNoAi(true);c.wallRaised=true;
                }
            }
            int move=c.mob.syncedMoveValue();
            if(move>0) {
                var m=c.mob.definition().move(move-1);c.moves.add(m.animation());
                if(c.firstMove<0){c.firstMove=tick;c.windup=m.windupTicks();}
            }
            float health=c.target.getHealth();
            if(health<c.previousHealth) {
                if(c.firstDamage<0)c.firstDamage=tick;
                c.damageEvents++;c.loss+=c.previousHealth-health;
            }
            c.previousHealth=health;
        }
        if(tick<360)return;
        JsonArray rows=new JsonArray(),state=new JsonArray();List<String> failures=new ArrayList<>();
        for(var c:CASES) {
            JsonObject row=new JsonObject();row.addProperty("id",c.id);row.addProperty("damage_events",c.damageEvents);row.addProperty("health_loss",c.loss);row.addProperty("first_move_tick",c.firstMove);row.addProperty("first_damage_tick",c.firstDamage);row.add("moves",JSON.toJsonTree(c.moves));
            if(c.blocked) {if(!c.wallRaised||c.loss!=0)failures.add(c.id+" wall did not stop arrow");}
            else if(c.loss<=0||c.moves.isEmpty())failures.add(c.id+" no AI damage");
            if(c.firstDamage>=0&&c.firstDamage<c.firstMove+c.windup-1)failures.add(c.id+" damaged during windup");
            rows.add(row);c.mob.setNoAi(true);c.target.discard();
            JsonObject save=new JsonObject();save.addProperty("id",c.id);save.addProperty("uuid",c.mob.getUUID().toString());save.addProperty("region",c.mob.wildernessBirth().orElseThrow().region());save.addProperty("health",c.mob.getHealth());state.add(save);
        }
        for(var mob:BORN){JsonObject save=new JsonObject();save.addProperty("id",mob.variant().id()+"_natural");save.addProperty("uuid",mob.getUUID().toString());save.addProperty("region",mob.wildernessBirth().orElseThrow().region());save.addProperty("health",mob.getHealth());state.add(save);}
        JsonObject result=new JsonObject();result.add("birth_checks",BIRTH_CHECKS);result.addProperty("phase","ordinary_ai");result.addProperty("status",failures.isEmpty()?"passed":"failed");result.addProperty("selected",CASES.size());result.add("completed",rows);result.add("failed",JSON.toJsonTree(failures));Files.writeString(RESULT,JSON.toJson(result));
        if(!failures.isEmpty()){done=true;server.halt(false);return;}
        Files.writeString(SAVED,JSON.toJson(state));done=true;server.halt(false);
    }
    private static void birthChecks() {
        var level=server.overworld();BlockPos at=new BlockPos(120,100,80);
        level.setChunkForced(at.getX()>>4,at.getZ()>>4,true);
        for(int dx=-2;dx<=10;dx++)for(int dz=-2;dz<=2;dz++)level.setBlockAndUpdate(at.offset(dx,-1,dz),Blocks.STONE.defaultBlockState());
        String profile=com.example.myvillage.wilderness.runtime.WildernessRuntime.regionAt(level,at).orElseThrow(()->new IllegalStateException("probe region unavailable"));
        for(String id:List.of("wandering_corpse",profile+"_corpse")) {
            var mob=finalizeSpawn(id,at,MobSpawnType.NATURAL);
            if(mob.isSpawnCancelled()||mob.wildernessBirth().isEmpty()||!mob.wildernessBirth().get().natural())throw new IllegalStateException("natural finalize missing birth "+id);
            level.addFreshEntity(mob);BORN.add(mob);at=at.offset(3,0,0);
        }
        String wrong=UndeadRules.REGIONS.stream().filter(r->!r.equals("common")&&!r.equals(profile)).findFirst().orElseThrow()+"_corpse";
        var rejected=finalizeSpawn(wrong,at,MobSpawnType.NATURAL);
        if(!rejected.isSpawnCancelled())throw new IllegalStateException("wrong region was admitted");rejected.discard();
        var egg=finalizeSpawn(wrong,at,MobSpawnType.SPAWN_EGG);
        if(egg.wildernessBirth().isPresent()||egg.isSpawnCancelled())throw new IllegalStateException("egg birth unexpectedly assigned");egg.discard();
        BIRTH_CHECKS.addProperty("profile",profile);BIRTH_CHECKS.addProperty("natural_births",2);BIRTH_CHECKS.addProperty("wrong_region_cancelled",true);BIRTH_CHECKS.addProperty("egg_unassigned",true);
    }
    private static UndeadEntity finalizeSpawn(String id,BlockPos pos,MobSpawnType reason) {
        var level=server.overworld();var mob=UndeadEntities.TYPES.get(id).get().create(level);mob.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);mob.setNoAi(true);
        net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(mob,level,level.getCurrentDifficultyAt(pos),reason,null);
        return mob;
    }
    private static final class Case {
        final String id;final UndeadEntity mob;final IronGolem target;final Vec3 targetPos;final boolean blocked;boolean wallRaised;final Set<String> moves=new LinkedHashSet<>();
        int firstMove=-1,firstDamage=-1,windup,damageEvents;float previousHealth=2000,loss;
        Case(String id,UndeadEntity mob,IronGolem target,Vec3 targetPos,boolean blocked){this.id=id;this.mob=mob;this.target=target;this.targetPos=targetPos;this.blocked=blocked;this.previousHealth=target.getHealth();}
    }
}
