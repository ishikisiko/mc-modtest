package com.example.myvillage.wilderness.habitat;

import com.example.myvillage.command.ModCommands;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.cultivation.time.CultivationCalendarSavedData;
import com.example.myvillage.cultivation.time.CultivationServerConfig;
import com.example.myvillage.entity.beast.BeastEntity;
import com.example.myvillage.wilderness.BeastBirth;
import com.example.myvillage.wilderness.WildernessRules;
import com.example.myvillage.stats.runtime.StatsRuntime;
import com.example.myvillage.worldplan.runtime.PlannedChunkGenerator;
import com.google.gson.Gson;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;

/** 东荒限定、有限名单、按自然历法月份刷新；实体与地形的所有权分别保存。 */
@EventBusSubscriber(modid=MyVillageMod.MOD_ID)
public final class HabitatRuntime {
    private static final String TAG="yuanxian_habitat";
    private static final Set<Mob> INSTALLED=Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<UUID,String> NEAR=new HashMap<>();
    public record Context(ServerLevel level,HabitatRules rules,HabitatLayout.Site site,HabitatRules.Kind kind,HabitatState.Round round,HabitatState.Member member) {}
    private HabitatRuntime(){}
    public static HabitatLayout layout(ServerLevel level){
        if(!level.dimension().equals(Level.OVERWORLD)||!level.getServer().getWorldData().worldGenOptions().generateStructures())return null;
        return level.getChunkSource().getGenerator() instanceof PlannedChunkGenerator generator?generator.habitats():null;
    }
    public static long month(ServerLevel level){var scale=CultivationServerConfig.scale();return CultivationCalendarSavedData.get(level).elapsedCalendarTicks()/scale.ticksPerDay()/scale.daysPerMonth();}
    public static Context member(Mob mob){
        if(!(mob.level() instanceof ServerLevel level)||!mob.getPersistentData().contains(TAG))return null;
        var layout=layout(level);if(layout==null)return null;var tag=mob.getPersistentData().getCompound(TAG);var saved=HabitatSavedData.get(level);
        if(!saved.state.current(tag.getString("site"),tag.getLong("generation"),mob.getUUID()))return null;
        var site=layout.sites().stream().filter(s->s.id().equals(tag.getString("site"))).findFirst().orElse(null);if(site==null)return null;
        var round=saved.state.site(site.id());var member=round.members.stream().filter(m->m.id.equals(mob.getUUID())).findFirst().orElseThrow();
        return new Context(level,layout.rules(),site,layout.rules().kind(site.kind()),round,member);
    }
    public static BlockPos ground(ServerLevel level,int x,int z,Mob mob){
        var first=new BlockPos(x,0,z);if(level.getChunkSource().getChunkNow(x>>4,z>>4)==null||!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(x>>4,z>>4)))return null;
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);var pos=new BlockPos(x,y,z);
        if(!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP)||!level.getFluidState(pos).isEmpty()||!level.getFluidState(pos.below()).isEmpty())return null;
        var box=mob.getBoundingBox().move(x+.5-mob.getX(),y-mob.getY(),z+.5-mob.getZ());
        for(int cx=((int)Math.floor(box.minX)-1)>>4;cx<=((int)Math.floor(box.maxX)+1)>>4;cx++)for(int cz=((int)Math.floor(box.minZ)-1)>>4;cz<=((int)Math.floor(box.maxZ)+1)>>4;cz++)if(level.getChunkSource().getChunkNow(cx,cz)==null||!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(cx,cz)))return null;
        return level.getWorldBorder().isWithinBounds(box)&&level.noCollision(mob,box)?pos:null;
    }
    static boolean protectedConstruction(HabitatState.Round round,HabitatRules rules,int x,int z){
        int margin=rules.integer("construction_clearance");return round.construction.stream().map(BlockPos::of).anyMatch(p->Math.abs(p.getX()-x)<=margin&&Math.abs(p.getZ()-z)<=margin);
    }
    private static boolean ready(ServerLevel level,HabitatLayout.Site site,int radius){
        for(int x=(site.x()-radius)>>4;x<=(site.x()+radius)>>4;x++)for(int z=(site.z()-radius)>>4;z<=(site.z()+radius)>>4;z++)
            if(level.getChunkSource().getChunkNow(x,z)==null||!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(x,z)))return false;
        return true;
    }
    private static double nearest(ServerLevel level,HabitatLayout.Site site){return level.players().stream().filter(p->!p.isSpectator()).mapToDouble(p->Math.hypot(p.getX()-site.x(),p.getZ()-site.z())).min().orElse(Double.POSITIVE_INFINITY);}
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){
        if(!(event.getLevel() instanceof ServerLevel level))return;var layout=layout(level);if(layout==null||level.getGameTime()%layout.rules().integer("tick_interval")!=0)return;
        if(!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING))return;
        var rules=layout.rules();var saved=HabitatSavedData.get(level);long month=month(level);
        if(month>saved.state.highestMonth){saved.state.highestMonth=month;saved.setDirty();}
        NEAR.keySet().removeIf(id->level.getServer().getPlayerList().getPlayer(id)==null);
        int spawnedThisPass=0;int active=0;for(var round:saved.state.rounds.values())for(var member:round.members)if(level.getEntity(member.id) instanceof Mob)active++;
        for(var site:layout.sites()){
            double distance=nearest(level,site);if(distance>rules.integer("activation_radius")||!ready(level,site,rules.integer("site_radius")+2))continue;
            var round=saved.state.site(site.id());var kind=rules.kind(site.kind());
            boolean combat=round.members.stream().map(m->level.getEntity(m.id)).filter(Mob.class::isInstance).map(Mob.class::cast).anyMatch(m->m.getTarget()!=null);
            if(saved.state.due(round,month,rules.integer("refresh_months"))&&distance>=rules.integer("refresh_safe_distance")&&!combat&&level.getDifficulty()!=net.minecraft.world.Difficulty.PEACEFUL){
                for(var old:round.members){var entity=level.getEntity(old.id);if(entity!=null){entity.discard();active--;}level.getScoreboard().removePlayerFromTeam(old.id.toString());}
                if(saved.state.refresh(round,month,rules.integer("refresh_months"),true,kind,level.getSeed()))saved.setDirty();
            }
            if(level.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL)continue;
            for(var member:round.members){
                var loaded=level.getEntity(member.id);
                if(loaded instanceof Mob mob){if(mob.getTarget()!=null&&mob.getTarget().isAlive()){member.activity="combat";alarm(mob,mob.getTarget());}continue;}
                if(member.dead||member.spawned||active>=rules.integer("max_active_members")||spawnedThisPass>=rules.integer("spawn_per_tick")||distance<rules.integer("spawn_min_distance"))continue;
                if(spawn(level,layout,site,round,member)){active++;spawnedThisPass++;saved.setDirty();}
            }
        }
        for(var player:level.players()){
            var close=layout.sites().stream().filter(s->Math.hypot(player.getX()-s.x(),player.getZ()-s.z())<rules.integer("alarm_radius")).findFirst().orElse(null);
            String id=close==null?"":close.id();String previous=NEAR.put(player.getUUID(),id);
            if(close!=null&&!id.equals(previous)){
                var round=saved.state.site(id);String detail=round.occupied()?"将避开附近建设恢复成员":round.generation<0?"附近发现活动痕迹":round.living()==0?"本月已清理，将于下月恢复":"附近有"+round.living()+"名成员活动";
                player.displayClientMessage(Component.literal(layout.rules().kind(close.kind()).name()+"："+detail),true);
            }
        }
    }
    private static boolean spawn(ServerLevel level,HabitatLayout layout,HabitatLayout.Site site,HabitatState.Round round,HabitatState.Member member){
        var key=ResourceLocation.fromNamespaceAndPath("yuanxian",member.entity);var raw=BuiltInRegistries.ENTITY_TYPE.get(key).create(level);if(!(raw instanceof Mob mob))throw new IllegalStateException("生态成员不是生物："+key);
        BlockPos point=null;int radius=layout.rules().integer(round.occupied()?"relocation_radius":"site_radius");
        for(int attempt=0;attempt<layout.rules().integer("spawn_attempts");attempt++){
            long hash=WildernessRules.mix(member.id.getLeastSignificantBits()+attempt);int x=site.x()+Math.floorMod(hash,2*radius+1)-radius,z=site.z()+Math.floorMod(WildernessRules.mix(hash),2*radius+1)-radius;
            if(protectedConstruction(round,layout.rules(),x,z))continue;
            if(!com.example.myvillage.wilderness.runtime.WildernessRuntime.regionAt(level,new BlockPos(x,site.y(),z)).filter(layout.rules().region::equals).isPresent())continue;
            point=ground(level,x,z,mob);if(point!=null&&level.getNearestPlayer(point.getX(),point.getY(),point.getZ(),layout.rules().integer("spawn_min_distance"),false)==null)break;point=null;
        }if(point==null){mob.discard();return false;}
        mob.moveTo(point.getX()+.5,point.getY(),point.getZ()+.5,0,0);mob.setUUID(member.id);mob.setPersistenceRequired();
        var identity=new CompoundTag();identity.putString("site",site.id());identity.putLong("generation",round.generation);identity.putInt("slot",member.slot);mob.getPersistentData().put(TAG,identity);
        var region=WildernessRules.bundled().region(layout.rules().region).orElseThrow();var range=layout.rules().kind(site.kind()).faction().equals("bandit")?region.rogue():region.beast();
        var realm=member.slot==0&&layout.rules().flag("leader_use_range_max")?range.max():range.sample(level.getSeed(),site.id(),member.id.getMostSignificantBits());
        if(mob instanceof BeastEntity beast)beast.assignWildernessBirth(new BeastBirth(realm,layout.rules().region,point.getX(),point.getY(),point.getZ(),false));
        else StatsRuntime.applyRealm(mob,realm.realmId(),realm.stage());
        mob.setHealth(mob.getMaxHealth());
        if(member.slot==0)mob.setCustomName(Component.literal(layout.rules().kind(site.kind()).name()+"首领"));
        var scoreboard=level.getScoreboard();String team="mvhab_"+Integer.toHexString(site.id().hashCode());PlayerTeam group=scoreboard.getPlayerTeam(team);if(group==null)group=scoreboard.addPlayerTeam(team);scoreboard.addPlayerToTeam(mob.getScoreboardName(),group);
        if(!level.addFreshEntity(mob)){mob.discard();return false;}member.spawned=true;return true;
    }
    private static void install(Mob mob){
        if(!INSTALLED.add(mob))return;mob.goalSelector.addGoal(0,new HabitatGoal(mob));
        mob.targetSelector.addGoal(0,new NearestAttackableTargetGoal<>(mob,Mob.class,member(mob).rules().integer("conflict_scan_ticks"),true,false,candidate->{
            if(!(candidate instanceof Mob other))return false;var a=member(mob);var b=member(other);
            return a!=null&&b!=null&&!a.kind().faction().equals(b.kind().faction())&&mob.distanceToSqr(other)<=a.rules().integer("conflict_radius")*a.rules().integer("conflict_radius");
        }));
    }
    private static void alarm(Mob mob,LivingEntity target){
        var c=member(mob);if(c==null||target==mob||mob.isAlliedTo(target))return;
        long now=c.level().getGameTime(),last=mob.getPersistentData().getLong("yuanxian_habitat_alarm");if(now-last<c.rules().integer("alarm_interval_ticks"))return;
        mob.getPersistentData().putLong("yuanxian_habitat_alarm",now);c.round().alarms++;HabitatSavedData.get(c.level()).setDirty();
        for(var friend:c.round().members){var entity=c.level().getEntity(friend.id);if(entity instanceof Mob ally&&ally!=mob&&ally.distanceToSqr(mob)<=c.rules().integer("alarm_radius")*c.rules().integer("alarm_radius")&&ally.getHealth()>=ally.getMaxHealth()*c.rules().number("retreat_health_fraction"))ally.setTarget(target);}
        if(c.site().kind().equals("wolf_den"))c.level().playSound(null,mob.blockPosition(),SoundEvents.WOLF_HOWL,SoundSource.HOSTILE,(float)c.rules().number("howl_volume"),(float)c.rules().number("howl_pitch"));
    }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event){
        if(!(event.getEntity() instanceof Mob mob)||event.getLevel().isClientSide()||!mob.getPersistentData().contains(TAG))return;
        if(member(mob)==null){event.setCanceled(true);mob.discard();return;}install(mob);
    }
    @SubscribeEvent public static void hurt(LivingIncomingDamageEvent event){if(event.getEntity() instanceof Mob mob&&event.getSource().getEntity() instanceof LivingEntity attacker)alarm(mob,attacker);}
    @SubscribeEvent public static void death(LivingDeathEvent event){if(event.getEntity() instanceof Mob mob){var c=member(mob);if(c!=null&&HabitatSavedData.get(c.level()).state.death(c.site().id(),c.round().generation,mob.getUUID()))HabitatSavedData.get(c.level()).setDirty();}}
    public static boolean validLootReceipt(LivingEntity entity){
        if(!(entity.level() instanceof ServerLevel level)||!entity.getPersistentData().contains(TAG))return false;
        var layout=layout(level);if(layout==null)return false;
        var identity=entity.getPersistentData().getCompound(TAG);var round=HabitatSavedData.get(level).state.rounds.get(identity.getString("site"));
        return round!=null&&round.generation==identity.getLong("generation")&&round.members.stream().anyMatch(m->m.id.equals(entity.getUUID())&&m.dead);
    }
    private static void construction(ServerLevel level,BlockPos pos,boolean placed){var layout=layout(level);if(layout==null)return;var saved=HabitatSavedData.get(level);int radius=layout.rules().integer("construction_radius");
        for(var site:layout.sites())if(Math.abs(pos.getX()-site.x())<=radius&&Math.abs(pos.getZ()-site.z())<=radius){var round=saved.state.site(site.id());if(placed&&round.construction.size()<4096)round.construction.add(pos.asLong());else if(!placed)round.construction.remove(pos.asLong());saved.setDirty();}
    }
    @SubscribeEvent public static void placed(BlockEvent.EntityPlaceEvent event){if(event.getEntity() instanceof ServerPlayer player)construction(player.serverLevel(),event.getPos(),true);}
    @SubscribeEvent public static void broken(BlockEvent.BreakEvent event){if(event.getPlayer() instanceof ServerPlayer player)construction(player.serverLevel(),event.getPos(),false);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){INSTALLED.clear();NEAR.clear();}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(ModCommands.adminRoot().then(Commands.literal("habitat").executes(c->{
            var level=c.getSource().getServer().overworld();var layout=layout(level);if(layout==null){c.getSource().sendFailure(Component.literal("当前世界没有东荒生态规划。"));return 0;}
            var data=new LinkedHashMap<String,Object>();data.put("month",month(level));data.put("region",layout.rules().region);data.put("sites",layout.sites());data.put("state",HabitatSavedData.get(level).state);data.put("refresh_months",layout.rules().integer("refresh_months"));data.put("ticks_per_month",(long)CultivationServerConfig.scale().ticksPerDay()*CultivationServerConfig.scale().daysPerMonth());data.put("loaded_sites",layout.sites().stream().filter(s->ready(level,s,layout.rules().integer("site_radius")+2)).map(HabitatLayout.Site::id).toList());
            data.put("shortfalls",layout.shortfalls());
            var living=new ArrayList<Map<String,Object>>();for(var round:HabitatSavedData.get(level).state.rounds.values())for(var member:round.members){var mob=level.getEntity(member.id);if(mob instanceof LivingEntity entity)living.add(Map.of("uuid",member.id,"realm",StatsRuntime.assignedRealm(entity).map(k->k.realmId()+"/"+k.stage()).orElse(""),"site",round.site,"health",entity.getHealth(),"max_health",entity.getMaxHealth(),"x",entity.getX(),"y",entity.getY(),"z",entity.getZ(),"activity",member.activity,"target",entity instanceof Mob m&&m.getTarget()!=null?m.getTarget().getUUID().toString():""));}data.put("living",living);
            c.getSource().sendSuccess(()->Component.literal("HABITAT "+new Gson().toJson(data)),false);return 1;
        })));
    }
}
