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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Isolated ordinary-AI fixtures. Readiness precedes entity creation; never player acceptance. */
@EventBusSubscriber(modid=MyVillageMod.MOD_ID)
public final class UndeadNativeProbe {
    private static final Path SAVED=Path.of("undead-probe-saved.json"), RESULT=Path.of("undead-probe-result.json");
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final List<Case> CASES=new ArrayList<>();
    private static final List<Spec> SPECS=new ArrayList<>();
    private static final List<UndeadEntity> BORN=new ArrayList<>();
    private static final JsonObject BIRTH_CHECKS=new JsonObject();
    private static final BlockPos BIRTH_POS=new BlockPos(120,100,80);
    private static MinecraftServer server;
    private static int tick,waitTicks;
    private static boolean restart,spawned,done;
    private static JsonArray saved;

    @SubscribeEvent public static void start(ServerStartedEvent event) throws Exception {
        server=event.getServer();var level=server.overworld();restart=Files.exists(SAVED);
        Files.deleteIfExists(RESULT);
        level.setDayTime(18000);server.setDifficulty(Difficulty.NORMAL,true);
        if(restart){
            saved=JsonParser.parseString(Files.readString(SAVED)).getAsJsonArray();
            // A headless restart has no nearby player. Load the recorded fixture chunks explicitly.
            for(var item:saved) {
                var row=item.getAsJsonObject();
                int x=(int)Math.floor(row.get("x").getAsDouble())>>4,z=(int)Math.floor(row.get("z").getAsDouble())>>4;
                level.setChunkForced(x,z,true);level.getChunk(x,z);
            }
            return;
        }
        var roster=new ArrayList<>(UndeadRules.get().variants());
        roster.add(UndeadRules.get().require("wandering_corpse"));
        roster.add(UndeadRules.get().require("bone_archer"));
        roster.add(UndeadRules.get().require("bone_archer"));
        for(int i=0;i<roster.size();i++) {
            var v=roster.get(i);int x=(i%6)*18,z=(i/6)*18;
            boolean close=i==18||i==19,blocked=i==20;
            SPECS.add(new Spec(v,x,z,v.id()+(close?"_close":blocked?"_wall":""),close,blocked));
            platform(x,z,12);
        }
        platform(BIRTH_POS.getX(),BIRTH_POS.getZ(),12);
    }
    private static void platform(int x,int z,int size) {
        var level=server.overworld();
        for(int dx=-2;dx<=size;dx++)for(int dz=-2;dz<=size;dz++) {
            BlockPos p=new BlockPos(x+dx,99,z+dz);
            level.setChunkForced(p.getX()>>4,p.getZ()>>4,true);
            level.setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());
        }
    }
    private static boolean ready(BlockPos pos) {
        var level=server.overworld();
        for(int x=(pos.getX()-1)>>4;x<=(pos.getX()+1)>>4;x++)for(int z=(pos.getZ()-1)>>4;z<=(pos.getZ()+1)>>4;z++) {
            if(!level.areEntitiesLoaded(ChunkPos.asLong(x,z))||!level.isPositionEntityTicking(new BlockPos(x*16+8,100,z*16+8)))return false;
        }
        return level.getBlockState(pos.below()).is(Blocks.STONE);
    }
    private static void spawnFixtures() {
        var level=server.overworld();
        for(var s:SPECS) {
            var mob=UndeadEntities.TYPES.get(s.variant.id()).get().create(level);
            if(mob==null)throw new IllegalStateException("create "+s.label);
            mob.moveTo(s.x+.5,100,s.z+.5,0,0);
            String region=s.variant.region().equals("common")?"ximo":s.variant.region();
            mob.assignWildernessBirth(new BeastBirth(new RealmKey("myvillage:qi_refining",0),region,s.x,100,s.z,false));
            mob.setHealth(mob.getMaxHealth()-1);
            var target=EntityType.IRON_GOLEM.create(level);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);target.setHealth(2000);target.setNoAi(true);target.setNoGravity(true);
            Vec3 pos=new Vec3(s.x+.5,100,s.z+(s.close?2.0:s.variant.archer()?8.5:3.5));target.setPos(pos);target.setTarget(mob);
            if(!level.addFreshEntity(target)||!level.addFreshEntity(mob))throw new IllegalStateException("fixture add rejected "+s.label);
            mob.setTarget(target);
            if(!BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("myvillage",s.variant.id()+"_spawn_egg")))throw new IllegalStateException("missing egg "+s.label);
            CASES.add(new Case(s.label,mob,target,pos,s.blocked));
        }
        System.out.println("UNDEAD_PROBE started 21 ordinary AI fixtures after "+waitTicks+" readiness ticks");
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) throws Exception {
        if(server==null||done)return;
        if(restart){restartTick();return;}
        if(!spawned) {
            waitTicks++;
            boolean all=ready(BIRTH_POS)&&ready(BIRTH_POS.offset(6,0,0));
            for(var s:SPECS)all&=ready(new BlockPos(s.x,100,s.z))&&ready(new BlockPos(s.x,100,s.z+(s.close?2:s.variant.archer()?8:3)));
            if(!all){if(waitTicks>=200)throw new IllegalStateException("fixture chunks/entities not ready after 200 ticks");return;}
            spawnFixtures();spawned=true;return;
        }
        tick++;
        if(tick==20)birthChecks();
        for(var c:CASES) {
            if(server.overworld().getEntity(c.mob.getUUID())!=c.mob||server.overworld().getEntity(c.target.getUUID())!=c.target)throw new IllegalStateException("fixture entity reference replaced "+c.id);
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
            if(health<c.previousHealth){if(c.firstDamage<0)c.firstDamage=tick;c.damageEvents++;c.loss+=c.previousHealth-health;}
            c.previousHealth=health;
        }
        if(tick>=360)finish();
    }
    private static void finish() throws Exception {
        JsonArray rows=new JsonArray(),state=new JsonArray();List<String> failures=new ArrayList<>();
        for(var c:CASES) {
            JsonObject row=new JsonObject();row.addProperty("id",c.id);row.addProperty("damage_events",c.damageEvents);row.addProperty("health_loss",c.loss);row.addProperty("first_move_tick",c.firstMove);row.addProperty("first_damage_tick",c.firstDamage);row.add("moves",JSON.toJsonTree(c.moves));
            if(c.blocked){if(!c.wallRaised||c.loss!=0)failures.add(c.id+" wall did not stop arrow");}
            else if(c.loss<=0||c.moves.isEmpty())failures.add(c.id+" no AI damage");
            if(c.firstDamage>=0&&c.firstDamage<c.firstMove+c.windup-1)failures.add(c.id+" damaged during windup");
            rows.add(row);c.mob.setNoAi(true);c.target.discard();state.add(snapshot(c.mob,c.id));
        }
        for(var mob:BORN)state.add(snapshot(mob,mob.variant().id()+"_natural"));
        JsonObject result=new JsonObject();result.add("birth_checks",BIRTH_CHECKS);result.addProperty("fixture_wait_ticks",waitTicks);result.addProperty("phase","ordinary_ai");result.addProperty("status",failures.isEmpty()?"passed":"failed");result.addProperty("selected",CASES.size());result.add("completed",rows);result.add("failed",JSON.toJsonTree(failures));Files.writeString(RESULT,JSON.toJson(result));
        if(failures.isEmpty())Files.writeString(SAVED,JSON.toJson(state));
        done=true;server.halt(false);
    }
    private static JsonObject snapshot(UndeadEntity mob,String id) {
        JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("uuid",mob.getUUID().toString());row.addProperty("region",mob.wildernessBirth().orElseThrow().region());row.addProperty("health",mob.getHealth());row.addProperty("x",mob.getX());row.addProperty("z",mob.getZ());return row;
    }
    private static void restartTick() throws Exception {
        tick++;if(tick<40)return;
        for(var item:saved)if(server.overworld().getEntity(UUID.fromString(item.getAsJsonObject().get("uuid").getAsString()))==null){if(tick>=200)throw new IllegalStateException("restart missing entity after load wait");return;}
        JsonArray checks=new JsonArray();
        for(var item:saved) {
            var row=item.getAsJsonObject();var entity=server.overworld().getEntity(UUID.fromString(row.get("uuid").getAsString()));
            if(!(entity instanceof UndeadEntity mob))throw new IllegalStateException("restart wrong type "+row.get("id"));
            if(mob.wildernessBirth().isEmpty()||!mob.wildernessBirth().get().region().equals(row.get("region").getAsString()))throw new IllegalStateException("birth rerolled");
            if(Math.abs(mob.getHealth()-row.get("health").getAsFloat())>.001||mob.isMoveRunning())throw new IllegalStateException("restart healed or resumed attack");
            checks.add(row.get("id"));
        }
        JsonObject result=new JsonObject();result.addProperty("phase","restart");result.addProperty("status","passed");result.addProperty("selected",saved.size());result.add("completed",checks);Files.writeString(RESULT,JSON.toJson(result));done=true;server.halt(false);
    }
    private static void birthChecks() {
        var level=server.overworld();BlockPos at=BIRTH_POS;
        String profile=com.example.myvillage.wilderness.runtime.WildernessRuntime.regionAt(level,at).orElseThrow();
        for(String id:List.of("wandering_corpse",profile+"_corpse")) {
            var mob=finalizeSpawn(id,at,MobSpawnType.NATURAL);
            if(mob.isSpawnCancelled()||mob.wildernessBirth().isEmpty()||!mob.wildernessBirth().get().natural())throw new IllegalStateException("natural finalize missing birth "+id);
            if(!level.addFreshEntity(mob))throw new IllegalStateException("natural fixture rejected");BORN.add(mob);at=at.offset(3,0,0);
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
        net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(mob,level,level.getCurrentDifficultyAt(pos),reason,null);return mob;
    }
    private record Spec(UndeadRules.Variant variant,int x,int z,String label,boolean close,boolean blocked) {}
    private static final class Case {
        final String id;final UndeadEntity mob;final IronGolem target;final Vec3 targetPos;final boolean blocked;boolean wallRaised;final Set<String> moves=new LinkedHashSet<>();
        int firstMove=-1,firstDamage=-1,windup,damageEvents;float previousHealth,loss;
        Case(String id,UndeadEntity mob,IronGolem target,Vec3 targetPos,boolean blocked){this.id=id;this.mob=mob;this.target=target;this.targetPos=targetPos;this.blocked=blocked;this.previousHealth=target.getHealth();}
    }
}
