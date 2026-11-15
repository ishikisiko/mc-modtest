package com.example.myvillage.dropprobe;

import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.resources.BuiltinResources;
import com.example.myvillage.wilderness.habitat.HabitatRuntime;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** 仅在隔离验收模组中逐项检查真实注册表、数据包及新世界；不进入正式 JAR。 */
final class NamespaceProbe {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static JsonArray checks;
    private static JsonObject counts;
    private interface Checked {void run()throws Exception;}
    static void register(){NeoForge.EVENT_BUS.addListener(NamespaceProbe::commands);}
    private static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("namespace_probe").requires(s->s.hasPermission(4)).executes(c->run(c.getSource())));
        event.getDispatcher().register(Commands.literal("namespace_player_probe").requires(s->s.hasPermission(4)).then(Commands.argument("player",StringArgumentType.word()).executes(c->{
            var player=Objects.requireNonNull(c.getSource().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(c,"player")));var result=new JsonObject();
            result.addProperty("meditation_state",com.example.myvillage.cultivation.meditation.MeditationManager.status(player).state().name());
            c.getSource().sendSuccess(()->Component.literal("NAMESPACE_PLAYER "+result),false);return 1;
        })));
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("yuanxian",path);}
    private static void check(String name,Checked action){var row=new JsonObject();row.addProperty("id",name);try{action.run();row.addProperty("passed",true);}catch(Throwable error){row.addProperty("passed",false);row.addProperty("error",error.toString());}checks.add(row);}
    private static void registry(Registry<?> registry){
        String key=registry.key().location().toString();require(!registry.key().location().getNamespace().equals("myvillage"),"旧注册表仍存在："+key);
        int current=0;
        for(var identity:registry.keySet()){require(!identity.getNamespace().equals("myvillage"),"旧 ID 仍存在："+key+"/"+identity);if(identity.getNamespace().equals("yuanxian"))current++;}
        if(current>0)counts.addProperty(key,current);
    }
    private static int run(CommandSourceStack source){
        MinecraftServer server=source.getServer();var level=server.overworld();checks=new JsonArray();counts=new JsonObject();
        check("mod_identity",()->{require(ModList.get().isLoaded("yuanxian"),"新 mod ID 未加载");require(!ModList.get().isLoaded("myvillage"),"旧 mod ID 仍存在");});
        check("builtin_registries",()->{
            for(var registry:BuiltInRegistries.REGISTRY)registry(registry);
            for(String type:List.of("block","item","entity_type","menu","data_component_type","worldgen/chunk_generator","worldgen/biome_source"))require(counts.has("minecraft:"+type),"注册表没有新 ID："+type);
        });
        check("dynamic_registries",()->{
            server.registryAccess().registries().forEach(entry->registry(entry.value()));
            require(server.registryAccess().registryOrThrow(ModCultivationRegistries.REALMS).containsKey(id("qi_refining")),"练气境界缺失");
            require(server.registryAccess().registryOrThrow(ModCultivationRegistries.INNATE_FATES).size()>0,"天命缺失");
            for(String path:List.of("realm","spiritual_element","technique","school","heritage","innate_fate"))require(counts.has("yuanxian:"+path),"动态注册表缺失："+path);
        });
        check("item_component_codec",()->{
            require(BuiltInRegistries.ITEM.containsKey(id("qingfeng_sword")),"青锋剑缺失");
            var stack=new ItemStack(BuiltInRegistries.ITEM.get(id("qingfeng_sword")));var ops=server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            var encoded=ItemStack.CODEC.encodeStart(ops,stack).getOrThrow();var restored=ItemStack.CODEC.parse(ops,encoded).getOrThrow();
            require(encoded.toString().contains("yuanxian:qingfeng_sword")&&!encoded.toString().contains("myvillage:"),"物品编码仍含旧 ID");require(ItemStack.isSameItemSameComponents(stack,restored),"物品组件往返不一致");
        });
        check("recipe_loot_and_tag",()->{
            require(server.getRecipeManager().byKey(id("formula_huiling")).isPresent(),"丹方配方未加载");
            var table=server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,id("entities/demon_wolf")));
            var encoded=LootTable.DIRECT_CODEC.encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE),table).getOrThrow();require(encoded.toString().contains("yuanxian:"),"妖狼掉落表为空或未迁移");
            require(new ItemStack(BuiltInRegistries.ITEM.get(id("qingfeng_sword"))).is(ItemTags.SWORDS),"青锋剑标签未绑定");
        });
        check("entity_constructor",()->{var type=BuiltInRegistries.ENTITY_TYPE.get(id("demon_wolf"));var entity=type.create(level);require(entity!=null&&BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(id("demon_wolf")),"实体无法建立");entity.discard();});
        check("structure_manager",()->{
            for(String template:List.of("small_house_001","cultivation_town_001","legacy_sword_ruin")){var value=level.getStructureManager().get(id(template)).orElseThrow();require(value.getSize().getX()>0,"结构为空："+template);}
        });
        check("fresh_cultivation_world",()->{
            var generator=WorldPlanRuntime.generator(level).orElseThrow();var world=generator.plan();require(world.cities().size()==8,"八州主城不完整");require(world.spawn().regionId().equals("donghuang"),"新世界未从东荒开始");
            var layout=HabitatRuntime.layout(level);require(layout!=null&&layout.sites().size()==12,"东荒十二处据点未加载");
            for(var site:layout.sites())require(world.coreRegionAt(site.x(),site.z()).orElseThrow().id().equals("donghuang"),"据点落在其他地区");
            counts.addProperty("capitals",world.cities().size());counts.addProperty("east_habitats",layout.sites().size());
        });
        check("packed_runtime_resource",()->{
            for(String path:List.of("assets/yuanxian/beast/demon_wolf_model.json","data/yuanxian/drops/monster_drops.json"))try(var input=BuiltinResources.open(path)){require(input.read()>-1,"内置资源为空："+path);}
        });
        var report=new JsonObject();report.add("counts",counts);report.add("checks",checks);boolean passed=checks.asList().stream().allMatch(v->v.getAsJsonObject().get("passed").getAsBoolean());report.addProperty("passed",passed);
        try{Files.writeString(FMLPaths.GAMEDIR.get().resolve("namespace-probe.json"),JSON.toJson(report)+"\n");}catch(Exception error){throw new IllegalStateException(error);}
        source.sendSuccess(()->Component.literal("NAMESPACE_PROBE "+JSON.toJson(report)),false);return passed?1:0;
    }
}
