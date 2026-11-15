package com.example.myvillage.drops;

import com.example.myvillage.command.ModCommands;

import com.example.myvillage.forging.FireCaptureItem;
import com.example.myvillage.region.runtime.*;
import com.example.myvillage.stats.*;
import com.example.myvillage.stats.runtime.StatsRuntime;
import com.example.myvillage.wilderness.habitat.HabitatRuntime;
import com.google.gson.*;
import com.mojang.brigadier.arguments.*;
import com.mojang.serialization.JsonOps;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.server.*;
import org.slf4j.LoggerFactory;

/** Single server-owned drop entry point, evaluated on each accepted death. */
public final class DropRuntime {
    public static final String DEFAULTS="data/yuanxian/drops/monster_drops.json";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<MinecraftServer,DropConfigStore> STORES=new ConcurrentHashMap<>();
    private static final Map<MinecraftServer,String> ERRORS=new ConcurrentHashMap<>();
    private DropRuntime(){}
    public static void register(){
        NeoForge.EVENT_BUS.addListener(DropRuntime::started);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e)->{STORES.remove(e.getServer());ERRORS.remove(e.getServer());});
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,DropRuntime::drops);
        NeoForge.EVENT_BUS.addListener(DropRuntime::commands);
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent e)->{if(e.getPlayer()==null&&STORES.containsKey(e.getPlayerList().getServer()))exportQuietly(e.getPlayerList().getServer());});
    }
    public static int realmRank(RealmKey key){var rules=StatsRules.bundled();rules.definition(key);return rules.definition(key).order()*16+key.stage();}
    private static DropRules.Catalog catalog(MinecraftServer server){
        var regions=RegionCatalogLoader.loadFromResourceManager(server.getResourceManager(),"yuanxian").catalog().stream().map(p->p.id()).collect(java.util.stream.Collectors.toSet());
        return new DropRules.Catalog(id->BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(id)),
                id->!id.equals("minecraft:player")&&BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(id)),regions::contains,DropRuntime::realmRank);
    }
    private static void started(ServerStartedEvent e){
        try(var stream=com.example.myvillage.resources.BuiltinResources.open(DEFAULTS)){
            if(stream==null)throw new IllegalStateException("缺少掉落默认配置");
            String defaults=new String(stream.readNBytes(DropRules.MAX_BYTES+1),StandardCharsets.UTF_8);
            var store=new DropConfigStore(FMLPaths.CONFIGDIR.get().resolve("yuanxian/monster_drops.json"),defaults,catalog(e.getServer()));
            STORES.put(e.getServer(),store);
            try{store.reload();ERRORS.remove(e.getServer());}catch(Exception error){ERRORS.put(e.getServer(),error.getMessage());LoggerFactory.getLogger(DropRuntime.class).error("掉落配置未载入，使用本次包内默认值：{}",error.getMessage());}
            exportQuietly(e.getServer());
        }catch(IOException error){throw new IllegalStateException("无法读取掉落默认配置",error);}
    }
    public static DropConfigStore.Snapshot snapshot(MinecraftServer server){var store=STORES.get(server);if(store==null)throw new IllegalStateException("掉落配置尚未就绪");return store.active();}
    public static DropRules.Scene scene(LivingEntity entity){
        var tag=entity.getPersistentData();
        if(tag.hasUUID("yuanxian_memory_owner"))return DropRules.Scene.APTITUDE_TRIAL;
        if(tag.hasUUID("yuanxian:elder_trial_owner"))return DropRules.Scene.SECT_TRIAL;
        if(tag.hasUUID("yuanxian:quest_instance"))return DropRules.Scene.QUEST_REALM;
        if(tag.contains("yuanxian_habitat"))return DropRules.Scene.HABITAT;
        if(entity.level() instanceof ServerLevel level&&!com.example.myvillage.ruins.BattleReceipts.get(level).status(entity.getUUID()).equals("unknown"))return DropRules.Scene.BATTLE_RUIN;
        return DropRules.Scene.NORMAL;
    }
    private static String region(LivingEntity entity){return entity.level().dimension().equals(Level.OVERWORLD)?RegionRuntimeService.regionAt(entity.getBlockX(),entity.getBlockZ()).orElse(""):"";}
    public static DropRules.Context context(LivingEntity entity,boolean playerKill){
        return new DropRules.Context(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),scene(entity),region(entity),
                StatsRuntime.assignedRealm(entity).map(DropRuntime::realmRank).orElse(null),playerKill,entity.getPersistentData().getBoolean("yuanxian_fire_captured"));
    }
    private static void drops(LivingDropsEvent event){
        if(!(event.getEntity().level() instanceof ServerLevel level))return;
        var store=STORES.get(level.getServer());if(store==null)return;
        var entity=event.getEntity();
        if(entity instanceof net.minecraft.world.entity.player.Player)return;
        var snapshot=store.active();var context=context(entity,event.getSource().getEntity() instanceof ServerPlayer);
        if(context.scene()==DropRules.Scene.NORMAL&&!(entity instanceof net.minecraft.world.entity.monster.Enemy)
                &&entity.getType().getCategory()!=MobCategory.MONSTER&&!snapshot.rules().entities().contains(context.entity()))return;
        // Epoch/identity validation remains a server invariant, separate from editable reward amounts.
        if(entity.getPersistentData().contains("yuanxian_habitat")&&!HabitatRuntime.validLootReceipt(entity)){event.getDrops().clear();return;}
        var resolution=snapshot.rules().resolve(context);
        if(resolution.policy().mode()==DropRules.Mode.REPLACE||resolution.policy().mode()==DropRules.Mode.DISABLED)event.getDrops().clear();
        if(resolution.policy().mode()==DropRules.Mode.DISABLED)return;
        if(resolution.policy().drops().isEmpty()&&resolution.additions().isEmpty())return;
        int looting=0;if(event.getSource().getEntity() instanceof LivingEntity attacker){
            var holder=level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.LOOTING);
            looting=EnchantmentHelper.getEnchantmentLevel(holder,attacker);
        }
        for(var reward:DropRules.roll(resolution,looting,new Random(entity.getRandom().nextLong()))){
            var stack=reward.factory().equals("beast_fire")?FireCaptureItem.seed("beast",0,level.dimension().location().toString(),entity.blockPosition())
                    :new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(reward.item())));
            int remaining=reward.count();while(remaining>0){int count=Math.min(remaining,stack.getMaxStackSize());var part=stack.copyWithCount(count);remaining-=count;
                event.getDrops().add(new ItemEntity(level,entity.getX(),entity.getY(),entity.getZ(),part));}
        }
    }
    private static JsonObject describe(MinecraftServer server,DropConfigStore.Snapshot snapshot,DropRules.Context context,ResourceKey<LootTable> tableId){
        JsonObject out=new JsonObject();out.addProperty("revision",snapshot.revision());out.addProperty("sha256",snapshot.sha256());out.addProperty("source",snapshot.source().id());out.add("context",JSON.toJsonTree(context));
        var resolution=snapshot.rules().resolve(context);out.add("effective",JSON.toJsonTree(resolution));
        if(resolution.policy().mode()==DropRules.Mode.INHERIT||resolution.policy().mode()==DropRules.Mode.APPEND){
            out.addProperty("inherited_table_id",tableId.location().toString());
            var table=server.reloadableRegistries().getLootTable(tableId);
            out.add("inherited_table",LootTable.DIRECT_CODEC.encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE),table).result().orElseThrow(()->new IllegalStateException("无法导出原版战利品表")));
            out.addProperty("inherited_runtime_items","装备、搬运物、特殊头颅等原生死亡掉落仍按实体状态结算");
        }
        return out;
    }
    private static ResourceKey<LootTable> table(String entity){var id=ResourceLocation.parse(entity);return ResourceKey.create(Registries.LOOT_TABLE,ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"entities/"+id.getPath()));}
    private static int inspect(CommandSourceStack source,Entity target){
        if(!(target instanceof LivingEntity living)){source.sendFailure(Component.literal("目标必须是活体实体。"));return 0;}
        var out=describe(source.getServer(),snapshot(source.getServer()),context(living,true),living.getLootTable());
        out.add("assigned_realm",JSON.toJsonTree(StatsRuntime.assignedRealm(living).orElse(null)));source.sendSuccess(()->Component.literal("DROPS "+out),false);return 1;
    }
    private static int show(CommandSourceStack source,String entity,String scene,String region,String realm,int stage){
        if(!BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(entity))){source.sendFailure(Component.literal("实体不存在："+entity));return 0;}
        try{
            Integer rank=realm==null?null:realmRank(StatsRules.bundled().realmKey(realm,stage));
            var context=new DropRules.Context(entity,DropRules.Scene.parse(scene),region,rank,true,false);
            var out=describe(source.getServer(),snapshot(source.getServer()),context,table(entity));source.sendSuccess(()->Component.literal("DROPS "+out),false);return 1;
        }catch(IllegalArgumentException error){source.sendFailure(Component.literal(error.getMessage()));return 0;}
    }
    private static String currentRegion(CommandSourceStack source){return source.getLevel().dimension().equals(Level.OVERWORLD)?RegionRuntimeService.regionAt((int)Math.floor(source.getPosition().x),(int)Math.floor(source.getPosition().z)).orElse(""):"";}
    private static void commands(RegisterCommandsEvent e){
        var show=Commands.literal("show").then(Commands.argument("entity",ResourceLocationArgument.id())
            .executes(c->show(c.getSource(),ResourceLocationArgument.getId(c,"entity").toString(),"normal",currentRegion(c.getSource()),null,0))
            .then(Commands.argument("scene",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(DropRules.Scene.values()).map(DropRules.Scene::id),b))
                .executes(c->show(c.getSource(),ResourceLocationArgument.getId(c,"entity").toString(),StringArgumentType.getString(c,"scene"),currentRegion(c.getSource()),null,0))
                .then(Commands.argument("region",StringArgumentType.word()).executes(c->show(c.getSource(),ResourceLocationArgument.getId(c,"entity").toString(),StringArgumentType.getString(c,"scene"),StringArgumentType.getString(c,"region"),null,0))
                    .then(Commands.argument("realm",ResourceLocationArgument.id()).then(Commands.argument("stage",IntegerArgumentType.integer(0,8))
                        .executes(c->show(c.getSource(),ResourceLocationArgument.getId(c,"entity").toString(),StringArgumentType.getString(c,"scene"),StringArgumentType.getString(c,"region"),ResourceLocationArgument.getId(c,"realm").toString(),IntegerArgumentType.getInteger(c,"stage"))))))));
        e.getDispatcher().register(ModCommands.root().then(Commands.literal("drops").requires(s->s.hasPermission(2))
            .executes(c->status(c.getSource())).then(Commands.literal("status").executes(c->status(c.getSource())))
            .then(Commands.literal("reload").executes(c->reload(c.getSource())))
            .then(Commands.literal("inspect").then(Commands.argument("target",EntityArgument.entity()).executes(c->inspect(c.getSource(),EntityArgument.getEntity(c,"target")))))
            .then(show).then(Commands.literal("export").executes(c->{try{export(c.getSource().getServer());c.getSource().sendSuccess(()->Component.literal("已导出 config/yuanxian/monster_drops_effective.json 与 .csv。"),false);return 1;}catch(Exception error){c.getSource().sendFailure(Component.literal("导出失败："+error.getMessage()));return 0;}}))));
    }
    private static int status(CommandSourceStack source){
        var store=STORES.get(source.getServer());var s=store.active();var out=new JsonObject();
        out.addProperty("revision",s.revision());out.addProperty("sha256",s.sha256());out.addProperty("source",s.source().id());
        out.addProperty("config",s.source()==DropConfigStore.Source.BUNDLED?DEFAULTS:store.file().toAbsolutePath().toString());
        out.addProperty("external_config",store.file().toAbsolutePath().toString());out.addProperty("settings",store.settingsFile().toAbsolutePath().toString());
        out.addProperty("last_error",ERRORS.getOrDefault(source.getServer(),""));source.sendSuccess(()->Component.literal("DROPS_STATUS "+out),false);return 1;
    }
    private static int reload(CommandSourceStack source){
        var server=source.getServer();DropConfigStore.Snapshot s;
        try{s=STORES.get(server).reload();ERRORS.remove(server);}catch(Exception error){ERRORS.put(server,error.getMessage());source.sendFailure(Component.literal("掉落配置重载失败，当前规则继续生效："+error.getMessage()));return 0;}
        source.sendSuccess(()->Component.literal("掉落配置已生效，来源："+(s.source()==DropConfigStore.Source.BUNDLED?"模组内置":"外置配置")+"，版本 "+s.revision()+"，SHA-256 "+s.sha256()),true);exportQuietly(server);return 1;
    }
    private static void exportQuietly(MinecraftServer server){try{export(server);}catch(Exception error){LoggerFactory.getLogger(DropRuntime.class).warn("掉落规则已生效，导出未完成：{}",error.getMessage());}}
    public static void export(MinecraftServer server)throws IOException{
        var store=STORES.get(server);var s=store.active();JsonObject out=new JsonObject();out.addProperty("revision",s.revision());out.addProperty("sha256",s.sha256());out.addProperty("source",s.source().id());out.add("configuration",s.rules().json());
        out.addProperty("preview_scope","逐实体导出各场景规则；habitat使用本世界的实际群落地区，其余场景地区与境界留空；条件限定请用show或inspect查询；不执行随机抽取");JsonArray rows=new JsonArray();
        var ids=new TreeSet<>(s.rules().entities());for(var type:BuiltInRegistries.ENTITY_TYPE)if(type.getCategory()==MobCategory.MONSTER)ids.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        StringBuilder csv=new StringBuilder("实体ID,场景,模式,规则来源,配置掉落,原始战利品表ID,继承战利品表,原生状态掉落说明\r\n");
        for(String id:ids)for(var scene:DropRules.Scene.values()){
            var layout=scene==DropRules.Scene.HABITAT?HabitatRuntime.layout(server.overworld()):null;
            String region=layout==null?"":layout.rules().region;
            var context=new DropRules.Context(id,scene,region,null,true,false);var row=describe(server,s,context,table(id));rows.add(row);
            var resolution=s.rules().resolve(context);List<DropRules.Entry> entries=new ArrayList<>(resolution.policy().drops());for(var addition:resolution.additions())entries.addAll(addition.drops());
            csv.append(csv(id)).append(',').append(csv(scene.id())).append(',').append(csv(resolution.policy().mode().name().toLowerCase(Locale.ROOT))).append(',')
                .append(csv(resolution.origin())).append(',').append(csv(JSON.toJson(entries))).append(',').append(csv(row.has("inherited_table_id")?row.get("inherited_table_id").getAsString():""))
                .append(',').append(csv(row.has("inherited_table")?JSON.toJson(row.get("inherited_table")):""))
                .append(',').append(csv(row.has("inherited_runtime_items")?row.get("inherited_runtime_items").getAsString():"")).append("\r\n");
        }
        out.add("rows",rows);write(store.file().resolveSibling("monster_drops_effective.json"),JSON.toJson(out)+"\n");write(store.file().resolveSibling("monster_drops_effective.csv"),"\ufeff"+csv);
    }
    private static String csv(String s){return "\""+s.replace("\"","\"\"")+"\"";}
    private static void write(Path path,String data)throws IOException{Files.createDirectories(path.getParent());Path temp=Files.createTempFile(path.getParent(),"drops-",".tmp");try{Files.writeString(temp,data,StandardCharsets.UTF_8);try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}}
}
