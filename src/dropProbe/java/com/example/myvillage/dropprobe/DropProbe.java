package com.example.myvillage.dropprobe;

import com.example.myvillage.drops.*;
import com.example.myvillage.stats.runtime.StatsRuntime;
import com.example.myvillage.wilderness.habitat.*;
import com.google.gson.*;
import com.mojang.brigadier.arguments.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** Explicit server fixtures: accepted death callbacks and real spawned item entities, no mapped player input. */
@Mod("yuanxian_drop_probe")
public final class DropProbe {
    private static UUID target;
    private static ServerLevel lastLevel;
    private static AABB area;
    private static JsonObject last=new JsonObject();
    private static final Gson JSON=new Gson();
    public DropProbe(){BrandProbe.register();NamespaceProbe.register();MaterialProbe.register();DoorProbe.register();HousingProbe.register();NpcHousingProbe.register();NeoForge.EVENT_BUS.addListener(DropProbe::commands);NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,DropProbe::drops);}
    private static void reply(CommandSourceStack source){source.sendSuccess(()->Component.literal("DROP_PROBE "+last),false);}
    private static void drops(LivingDropsEvent e){if(target!=null&&target.equals(e.getEntity().getUUID())){
        Map<String,Integer> items=new TreeMap<>();for(var drop:e.getDrops())items.merge(BuiltInRegistries.ITEM.getKey(drop.getItem().getItem()).toString(),drop.getItem().getCount(),Integer::sum);
        last.add("event_items",JSON.toJsonTree(items));last.addProperty("drop_event_canceled",e.isCanceled());
        last.add("death_context",JSON.toJsonTree(DropRuntime.context(e.getEntity(),e.getSource().getEntity() instanceof ServerPlayer)));
    }}
    private static void clean(){if(lastLevel!=null&&area!=null)for(var item:lastLevel.getEntitiesOfClass(ItemEntity.class,area))item.discard();}
    private static int spawn(CommandSourceStack source,String id,String scene,int stage){
        clean();ServerLevel level=source.getServer().overworld();var type=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id));
        if(!(type.create(level) instanceof Mob mob))throw new IllegalArgumentException("探针仅创建Mob");
        int x=level.getSharedSpawnPos().getX()+12,z=level.getSharedSpawnPos().getZ()+12;long generation=-1;String siteId=null;
        if(scene.equals("habitat")){
            var layout=HabitatRuntime.layout(level);if(layout==null)throw new IllegalArgumentException("需要修仙大陆才能建立据点身份夹具");
            var site=layout.sites().stream().filter(s->s.kind().equals("wolf_den")).findFirst().orElseThrow();
            var data=HabitatSavedData.get(level);var round=data.state.site(site.id());
            if(round.generation<0)data.state.refresh(round,HabitatRuntime.month(level),layout.rules().integer("refresh_months"),true,layout.rules().kind(site.kind()),level.getSeed());
            var member=round.members.stream().filter(m->!m.dead&&!m.spawned).findFirst().orElseThrow();
            if(!member.entity.equals(id.substring(id.indexOf(':')+1)))throw new IllegalArgumentException("据点夹具实体须匹配名单");
            mob.setUUID(member.id);member.spawned=true;generation=round.generation;siteId=site.id();x=site.x();z=site.z();data.setDirty();
        }
        level.setChunkForced(x>>4,z>>4,true);level.getChunk(x>>4,z>>4);int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)+4;
        mob.moveTo(x+.5,y,z+.5,0,0);mob.setNoAi(true);mob.setPersistenceRequired();mob.setSilent(true);
        if(stage>=0)StatsRuntime.applyRealm(mob,"yuanxian:qi_refining",stage);
        if(siteId!=null){var tag=new net.minecraft.nbt.CompoundTag();tag.putString("site",siteId);tag.putLong("generation",generation);mob.getPersistentData().put("yuanxian_habitat",tag);}
        if(!level.addFreshEntity(mob))throw new IllegalArgumentException("探针实体加入失败");
        var fake=FakePlayerFactory.getMinecraft(level);
        switch(scene){case "quest_realm"->mob.getPersistentData().putUUID("yuanxian:quest_instance",UUID.randomUUID());case "sect_trial"->mob.getPersistentData().putUUID("yuanxian:elder_trial_owner",fake.getUUID());case "aptitude_trial"->mob.getPersistentData().putUUID("yuanxian_memory_owner",fake.getUUID());case "stale_habitat"->{var tag=new net.minecraft.nbt.CompoundTag();tag.putString("site","invalid");tag.putLong("generation",999);mob.getPersistentData().put("yuanxian_habitat",tag);}default->{}}
        target=mob.getUUID();lastLevel=level;area=new AABB(x-8,y-16,z-8,x+9,y+16,z+9);last=new JsonObject();last.addProperty("uuid",target.toString());last.addProperty("entity",id);last.addProperty("fixture_scene",scene);last.add("spawn_context",JSON.toJsonTree(DropRuntime.context(mob,true)));reply(source);return 1;
    }
    private static int finish(CommandSourceStack source,int looting,boolean captured){
        if(target==null||!(lastLevel.getEntity(target) instanceof LivingEntity mob))throw new IllegalArgumentException("没有已加载的探针目标");
        var fake=FakePlayerFactory.getMinecraft(lastLevel);var sword=new ItemStack(Items.DIAMOND_SWORD);
        if(looting>0)sword.enchant(lastLevel.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.LOOTING),looting);
        fake.setItemSlot(EquipmentSlot.MAINHAND,sword);mob.getPersistentData().putBoolean("yuanxian_fire_captured",captured);
        mob.setLastHurtByPlayer(fake);mob.setHealth(0);mob.die(lastLevel.damageSources().playerAttack(fake));
        last.addProperty("fixture_death_callback",true);last.addProperty("looting",looting);reply(source);return 1;
    }
    private static int ready(CommandSourceStack source){
        var mob=lastLevel==null||target==null?null:lastLevel.getEntity(target);
        last.addProperty("target_loaded",mob instanceof LivingEntity);
        last.addProperty("chunk_entity_ticking",mob!=null&&lastLevel.isPositionEntityTicking(mob.blockPosition()));
        reply(source);return mob==null?0:1;
    }
    private static int realm(CommandSourceStack source,String realm,int stage) {
        if(target==null||!(lastLevel.getEntity(target) instanceof LivingEntity mob))throw new IllegalArgumentException("没有探针目标");
        StatsRuntime.applyRealm(mob,realm,stage);last.add("spawn_context",JSON.toJsonTree(DropRuntime.context(mob,true)));reply(source);return 1;
    }
    private static int otherDeath(CommandSourceStack source,boolean npc) {
        if(target==null||!(lastLevel.getEntity(target) instanceof LivingEntity mob))throw new IllegalArgumentException("没有探针目标");
        mob.setLastHurtByPlayer(null);mob.setHealth(0);
        if(npc) {
            var attacker=(LivingEntity)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("yuanxian:cultivator")).create(lastLevel);
            mob.die(lastLevel.damageSources().mobAttack(attacker));attacker.discard();
        }else mob.die(lastLevel.damageSources().genericKill());
        last.addProperty("fixture_death_callback",true);last.addProperty("death_source",npc?"npc":"environment");reply(source);return 1;
    }
    private static int read(CommandSourceStack source){
        Map<String,Integer> items=new TreeMap<>();JsonArray details=new JsonArray();
        if(lastLevel!=null&&area!=null)for(var item:lastLevel.getEntitiesOfClass(ItemEntity.class,area)){
            var stack=item.getItem();items.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getCount(),Integer::sum);
            var detail=new JsonObject();detail.addProperty("item",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());detail.addProperty("count",stack.getCount());
            detail.add("components",ItemStack.CODEC.encodeStart(lastLevel.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),stack).result().orElseThrow());details.add(detail);
        }
        last.add("world_items",JSON.toJsonTree(items));last.add("world_item_details",details);reply(source);return 1;
    }
    private static int deny(CommandSourceStack source){
        var server=source.getServer();var before=DropRuntime.snapshot(server);var low=FakePlayerFactory.getMinecraft(server.overworld()).createCommandSourceStack().withPermission(0);
        boolean denied=false;try{var result=server.getCommands().getDispatcher().execute("yuanxian drops reload",low);denied=result==0;}catch(Exception expected){denied=true;}
        last=new JsonObject();last.addProperty("permission_zero_denied",denied);last.addProperty("revision_unchanged",before.revision()==DropRuntime.snapshot(server).revision());reply(source);return denied?1:0;
    }
    private static int variants(CommandSourceStack source) {
        JsonObject options=new JsonObject();
        for(var item:BuiltInRegistries.ITEM){
            String id=BuiltInRegistries.ITEM.getKey(item).toString();
            if(!id.startsWith("minecraft:")&&!id.startsWith("yuanxian:"))continue;
            JsonObject row=new JsonObject();row.addProperty("quality_kind",DropItemVariants.qualityKind(item));
            JsonArray qualities=new JsonArray();for(int q=0;q<=4;q++)if(DropItemVariants.allowsQuality(id,q))qualities.add(q);
            JsonArray rarities=new JsonArray();if(DropItemVariants.allowsRarity(id))for(String r:List.of("common","uncommon","rare","epic"))rarities.add(r);
            row.add("qualities",qualities);row.add("rarities",rarities);options.add(id,row);
        }
        try{java.nio.file.Files.writeString(java.nio.file.Path.of("drop-variant-catalogue.json"),JSON.toJson(options));}
        catch(java.io.IOException error){throw new IllegalStateException(error);}
        last=new JsonObject();last.addProperty("variant_items",options.size());reply(source);return options.size();
    }
    private static void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("drop_probe").requires(s->s.hasPermission(4))
            .then(Commands.literal("spawn").then(Commands.argument("entity",ResourceLocationArgument.id())
                .then(Commands.argument("scene",StringArgumentType.word()).then(Commands.argument("stage",IntegerArgumentType.integer(-1,8))
                    .executes(c->spawn(c.getSource(),ResourceLocationArgument.getId(c,"entity").toString(),StringArgumentType.getString(c,"scene"),IntegerArgumentType.getInteger(c,"stage")))))))
            .then(Commands.literal("finish").then(Commands.argument("looting",IntegerArgumentType.integer(0,255)).then(Commands.argument("captured",BoolArgumentType.bool())
                .executes(c->finish(c.getSource(),IntegerArgumentType.getInteger(c,"looting"),BoolArgumentType.getBool(c,"captured"))))))
            .then(Commands.literal("read").executes(c->read(c.getSource())))
            .then(Commands.literal("realm").then(Commands.argument("realm",ResourceLocationArgument.id()).then(Commands.argument("stage",IntegerArgumentType.integer(0,8))
                    .executes(c->realm(c.getSource(),ResourceLocationArgument.getId(c,"realm").toString(),IntegerArgumentType.getInteger(c,"stage"))))))
            .then(Commands.literal("environment").executes(c->otherDeath(c.getSource(),false)))
            .then(Commands.literal("npc_finish").executes(c->otherDeath(c.getSource(),true)))
            .then(Commands.literal("ready").executes(c->ready(c.getSource())))
            .then(Commands.literal("variants").executes(c->variants(c.getSource())))
            .then(Commands.literal("deny").executes(c->deny(c.getSource()))));
    }
}
