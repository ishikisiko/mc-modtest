package com.example.myvillage.worldplan.runtime;

import com.example.myvillage.command.ModCommands;
import com.example.myvillage.entity.ModEntities;

import com.example.myvillage.worldplan.*;
import com.example.myvillage.worldplan.mine.StarterMineGeneration;
import com.example.myvillage.worldplan.mine.StarterMineLayout;
import com.example.myvillage.worldplan.wonder.FieldArenaEncounter;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.LightLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.*;

/** Explicit bounded developer acceptance commands; ordinary queries never request chunks. */
public final class WorldPlanCommands {
    private WorldPlanCommands() {}
    public static void register(RegisterCommandsEvent e){
        e.getDispatcher().register(ModCommands.adminRoot()
            .then(Commands.literal("worldplan")
                .then(Commands.literal("info").executes(c->info(c.getSource())))
                .then(Commands.literal("mine").executes(c->mine(c.getSource())))
                .then(Commands.literal("folded_ridge").executes(c->foldedRidge(c.getSource()))
                    .then(Commands.literal("sample").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(c->foldedRidgeSample(c.getSource(),BlockPosArgument.getLoadedBlockPos(c,"pos"))))))
                .then(Commands.literal("lotus_mountain").executes(c->lotusMountain(c.getSource())))
                .then(Commands.literal("provincial_biomes").executes(c->provincialBiomes(c.getSource()))
                    .then(Commands.literal("sample").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(c->provincialSample(c.getSource(), BlockPosArgument.getLoadedBlockPos(c,"pos"))))))
                .then(Commands.literal("field_encounters").executes(c->fieldEncounters(c.getSource()))
                    .then(Commands.literal("sample").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(c->fieldEncounterSample(c.getSource(),BlockPosArgument.getLoadedBlockPos(c,"pos"))))))
                .then(Commands.literal("city").executes(c->cities(c.getSource(),""))
                    .then(Commands.argument("region",StringArgumentType.word())
                        .suggests((c,b)->{for(var region:com.example.myvillage.worldplan.capital.CapitalRegion.values())b.suggest(region.id());return b.buildFuture();})
                        .executes(c->cities(c.getSource(),StringArgumentType.getString(c,"region")))))
                .then(Commands.literal("qianji").executes(c->qianji(c.getSource(),""))
                    .then(Commands.argument("region",StringArgumentType.word())
                        .executes(c->qianji(c.getSource(),StringArgumentType.getString(c,"region")))))
                .then(Commands.literal("settlements").executes(c->info(c.getSource())))
                .then(Commands.literal("qianzhen").executes(c->info(c.getSource())))
                .then(Commands.literal("at").then(Commands.argument("x",IntegerArgumentType.integer(-29999984,29999984))
                    .then(Commands.argument("z",IntegerArgumentType.integer(-29999984,29999984))
                        .executes(c->at(c.getSource(),IntegerArgumentType.getInteger(c,"x"),IntegerArgumentType.getInteger(c,"z"))))))
                .then(Commands.literal("export").executes(c->export(c.getSource())))
                .then(Commands.literal("audit_city").executes(c->audit(c.getSource())))
                .then(Commands.literal("pregen_city").then(Commands.argument("order",StringArgumentType.word())
                    .suggests((c,b)->{b.suggest("forward");b.suggest("reverse");return b.buildFuture();})
                    .executes(c->pregen(c.getSource(),StringArgumentType.getString(c,"order"),35))
                    .then(Commands.argument("limit",IntegerArgumentType.integer(1,35)).executes(c->pregen(c.getSource(),StringArgumentType.getString(c,"order"),IntegerArgumentType.getInteger(c,"limit"))))))
                .then(Commands.literal("probe").then(Commands.argument("chunk_x",IntegerArgumentType.integer(-1874999,1874999))
                    .then(Commands.argument("chunk_z",IntegerArgumentType.integer(-1874999,1874999))
                        .executes(c->probe(c.getSource(),IntegerArgumentType.getInteger(c,"chunk_x"),IntegerArgumentType.getInteger(c,"chunk_z"))))))));
    }
    private static PlannedChunkGenerator generator(CommandSourceStack s){
        return WorldPlanRuntime.generator(s.getLevel()).orElseThrow(()->new IllegalArgumentException("当前世界未使用修仙大陆预设；已有世界不会自动转换。"));
    }
    private static int reply(CommandSourceStack s,JsonObject o){s.sendSuccess(()->Component.literal("WORLDPLAN "+WorldPlanRules.JSON.toJson(o)),false);return 1;}
    private static int fail(CommandSourceStack s,Exception e){s.sendFailure(Component.literal("大陆蓝图："+e.getMessage()));return 0;}
    private static int cities(CommandSourceStack s,String region){try {
        var p=generator(s).plan();
        if(p.rules().capitals==null) {
            var c=p.city();
            if(!region.isEmpty()&&!region.equals(c.host())&&!region.equals(p.rules().profile(c.host()).name()))throw new IllegalArgumentException("旧版世界仅保留云山城。");
            int x=c.x()+c.width()/2,z=c.z()+c.depth()-4,y=c.baseY()+p.rules().city.surface(c.width()/2,c.depth()-4);
            s.sendSuccess(()->Component.literal("云山城｜"+p.rules().profile(c.host()).name()+"｜入口 "+x+" "+y+" "+z+"（旧版规划）"),false);return 1;
        }
        var selected=p.cities().stream().filter(c->region.isEmpty()||c.regionId().equals(region)||c.cityId().equals(region)||p.rules().profile(c.regionId()).name().equals(region)).toList();
        if(selected.isEmpty())throw new IllegalArgumentException("未找到该州主城；可输入 zhongzhou、donghai、ximo、nanjiang、beiyuan、donghuang、lingyue、moyu。");
        for(var c:selected) {
            var at=c.entrance();
            var condition=com.example.myvillage.sim.runtime.life.NpcLifeSites.availability(s.getLevel(),
                com.example.myvillage.sim.life.LifePlace.free("entry","entry",at.x(),at.y(),at.z()));
            String state=s.getServer().getWorldData().worldGenOptions().generateStructures()
                ?switch(condition){case AVAILABLE->"可站立";case BLOCKED->"入口受阻";case UNLOADED->"尚未加载";}:"结构生成已关闭";
            String line=c.bundle().displayName+"｜"+p.rules().profile(c.regionId()).name()+"｜入口 "+at.x()+" "+at.y()+" "+at.z()+"（"+state+"）";
            s.sendSuccess(()->Component.literal(line),false);
        }
        return selected.size();
    }catch(Exception e){return fail(s,e);}}
    private static int qianji(CommandSourceStack s,String region){try{
        var world=generator(s).plan();var list=new com.google.gson.JsonArray();
        for(var city:world.cities())if(region.isEmpty()||city.regionId().equals(region)){
            var office=city.bundle().qianji;var point=office.reader();var entry=office.entry();
            var row=new JsonObject();row.addProperty("region",city.regionId());row.addProperty("name",office.name());row.addProperty("scale",office.scale());
            row.add("entry",WorldPlanRules.JSON.toJsonTree(new int[]{city.x()+entry.x(),city.originY()+entry.y(),city.z()+entry.z()}));
            row.add("task_reader",WorldPlanRules.JSON.toJsonTree(new int[]{city.x()+point.x(),city.originY()+point.y(),city.z()+point.z()}));list.add(row);
        }
        if(list.isEmpty())throw new IllegalArgumentException("未找到该州的千机阁");
        var out=new JsonObject();out.add("qianji",list);return reply(s,out);
    }catch(Exception e){return fail(s,e);}}
    private static int info(CommandSourceStack s){try{
        var p=generator(s).plan();JsonObject o=new JsonObject();o.addProperty("active",true);o.addProperty("seed",Long.toString(p.seed()));
        o.addProperty("algorithm",p.rules().algorithm);
        o.addProperty("plan_sha256",p.fingerprint());o.addProperty("rules_sha256",p.rules().fingerprint());
        if(p.rules().capitals==null)o.add("city",WorldPlanRules.JSON.toJsonTree(p.city()));else {
            o.add("cities",p.manifest().get("cities"));var life=new com.google.gson.JsonArray();
            com.example.myvillage.sim.runtime.WorldSimRuntime.sim().ifPresent(sim->{for(var city:p.cities())life.add(com.example.myvillage.sim.runtime.life.NpcLifeSites.capitalStatus(s.getServer(),sim,city));});
            o.add("life",life);
        }
        o.add("settlements",WorldPlanRules.JSON.toJsonTree(p.settlements()));
        o.add("qianzhen",WorldPlanRules.JSON.toJsonTree(p.qianzhen()));o.add("qianzhen_buildings",WorldPlanRules.JSON.toJsonTree(p.qianzhenBuildings()));
        o.add("spawn",WorldPlanRules.JSON.toJsonTree(p.spawn()));o.addProperty("structures_enabled",s.getServer().getWorldData().worldGenOptions().generateStructures());
        o.addProperty("atlas_directory",WorldPlanRuntime.directory(s.getLevel()).toString());return reply(s,o);
    }catch(Exception e){return fail(s,e);}}
    private static int at(CommandSourceStack s,int x,int z){try{return reply(s,WorldPlanRules.JSON.toJsonTree(generator(s).sample(x,z)).getAsJsonObject());}catch(Exception e){return fail(s,e);}}
    private static int fieldEncounters(CommandSourceStack source) {
        try {
            var generator = generator(source);
            var result = new JsonObject();
            result.addProperty("seed", Long.toString(generator.plan().seed()));
            var rules = generator.rules().fieldBiomes;
            if (rules == null) return reply(source, result);
            for (var domain : rules.domains) {
                var slot = new JsonObject();
                var site = generator.fieldSites().get(domain.kind());
                slot.addProperty("biome", domain.biome());
                slot.addProperty("region", domain.region());
                slot.addProperty("name", domain.kind() == FieldBiomeRules.Kind.TIDE ? "三汊潮峡" : "沉碑台地");
                slot.addProperty("status", site == null ? "unavailable" :
                        FieldArenaEncounter.status(source.getLevel(), domain.kind()));
                if (site != null) {
                    slot.add("home", blocks(site.home()));
                    slot.add("exits", WorldPlanRules.JSON.toJsonTree(site.exits().stream().map(WorldPlanCommands::blocks).toList()));
                    var box = site.bounds();
                    slot.add("bounds", WorldPlanRules.JSON.toJsonTree(new int[]{box.minX(), box.minZ(), box.maxX(), box.maxZ()}));
                    slot.addProperty("dry_area", site.dryArea());
                    slot.addProperty("phase", FieldArenaEncounter.phase(source.getLevel(), domain.kind()));
                    slot.addProperty("temporary_blocks", FieldArenaEncounter.temporaryCount(source.getLevel(), domain.kind()));
                    if (site.bay() != null) {
                        slot.add("sluice", blocks(site.marker()));
                        slot.add("shoal", blocks(site.exposedShoal()));
                        slot.addProperty("channels", site.bay().branches());
                    } else {
                        slot.add("bridge_a", blocks(site.bridgeA()));
                        slot.add("bridge_b", blocks(site.bridgeB()));
                    }
                }
                result.add(domain.kind().name().toLowerCase(Locale.ROOT), slot);
            }
            return reply(source, result);
        } catch (Exception e) { return fail(source, e); }
    }

    private static com.google.gson.JsonElement blocks(BlockPos pos) {
        return WorldPlanRules.JSON.toJsonTree(new int[]{pos.getX(), pos.getY(), pos.getZ()});
    }

    private static int provincialBiomes(CommandSourceStack source) {
        try {
            var generator = generator(source);
            var out = new JsonObject();
            out.addProperty("seed", Long.toString(generator.plan().seed()));
            var rules = generator.rules().provincialBiomes;
            if (rules == null) return reply(source, out);
            out.addProperty("vanilla_weight", rules.vanillaWeight);
            var sites = new JsonObject();
            for (var profile : rules.profiles) {
                var row = new JsonObject();
                row.addProperty("region", profile.region());
                row.addProperty("status", "unavailable");
                sites.add(profile.biome(), row);
            }
            for (var site : generator.fieldLayout().provincialSites()) {
                var row = sites.getAsJsonObject(site.profile().biome());
                row.addProperty("status", "planned");
                var column = generator.fieldLayout().surface(site.x(), site.z());
                row.add("center", blocks(new BlockPos(site.x(), Math.max(column.height(), column.liquidY()), site.z())));
                row.add("window", WorldPlanRules.JSON.toJsonTree(new int[]{site.profile().width(), site.profile().depth()}));
            }
            for (var domain : generator.rules().fieldBiomes.domains) {
                var row = new JsonObject();
                row.addProperty("region", domain.region());
                var site = generator.fieldSites().get(domain.kind());
                row.addProperty("status", site == null ? "unavailable" : "planned");
                if (site != null) row.add("center", blocks(site.home()));
                sites.add(domain.biome(), row);
            }
            out.add("groups", sites);
            return reply(source, out);
        } catch (Exception error) { return fail(source, error); }
    }

    private static int provincialSample(CommandSourceStack source, BlockPos pos) {
        try {
            var level = source.getLevel();
            var generator = generator(source);
            var biome = level.getBiome(pos).unwrapKey().orElseThrow().location();
            var out = new JsonObject();
            out.addProperty("biome", biome.toString());
            out.addProperty("region", generator.sample(pos.getX(), pos.getZ()).regionId());
            out.addProperty("surface", level.getHeight(Heightmap.Types.OCEAN_FLOOR, pos.getX(), pos.getZ()));
            out.addProperty("top", level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()));
            out.addProperty("planned_floor", generator.fieldSurfaceY(pos.getX(), pos.getZ()));
            var spawns = new JsonObject();
            for (var rule : com.example.myvillage.entity.beast.ProvincialFauna.rules()) {
                if (rule.biome().equals(biome)) spawns.addProperty(rule.id(),
                        com.example.myvillage.entity.beast.ProvincialAnimal.eligible(rule, level, pos));
            }
            out.add("spawn_allowed", spawns);
            return reply(source, out);
        } catch (Exception error) { return fail(source, error); }
    }

    private static int fieldEncounterSample(CommandSourceStack source, BlockPos pos) {
        try {
            var world = source.getLevel();
            var generator = generator(source);
            var result = new JsonObject();
            var biome = world.getBiome(pos);
            result.addProperty("biome", biome.unwrapKey().orElseThrow().location().toString());
            result.addProperty("floor", net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(world.getBlockState(pos.below()).getBlock()).toString());
            result.addProperty("surface", world.getHeight(Heightmap.Types.OCEAN_FLOOR, pos.getX(), pos.getZ()));
            result.addProperty("light", world.getMaxLocalRawBrightness(pos));
            result.addProperty("planned_floor", generator.fieldSurfaceY(pos.getX(),pos.getZ()));
            var tide = generator.fieldSites().get(FieldBiomeRules.Kind.TIDE);
            if (tide != null && tide.contains(pos.getX(), pos.getZ()))
                result.addProperty("high_tide", generator.fieldLayout().terrain().tideTop(tide.bay(), pos.getX(), pos.getZ()));
            var spawns = new JsonObject();
            for (var type : List.of(ModEntities.TIDE_MUD_CRAB.get(), ModEntities.HOOK_JAW_SCORPION.get())) {
                var mob = type.create(world);
                mob.moveTo(pos.getX()+.5, pos.getY(), pos.getZ()+.5, 0, 0);
                boolean allowed = SpawnPlacements.isSpawnPositionOk(type, world, pos)
                        && SpawnPlacements.checkSpawnRules(type, world, MobSpawnType.NATURAL, pos, RandomSource.create(0))
                        && mob.checkSpawnRules(world, MobSpawnType.NATURAL) && mob.checkSpawnObstruction(world);
                spawns.addProperty(net.minecraft.world.entity.EntityType.getKey(type).getPath(), allowed);
            }
            result.add("spawn_allowed", spawns);
            return reply(source, result);
        } catch (Exception e) { return fail(source, e); }
    }

    private static int foldedRidge(CommandSourceStack source) {
        try {
            var generator = generator(source);
            var ridge = generator.foldedRidge();
            var result = new JsonObject();
            result.addProperty("region", generator.rules().foldedRidge.region());
            result.addProperty("name", "折山岭");
            result.addProperty("status", ridge.isPresent() ? "planned" : "unavailable");
            ridge.ifPresent(site -> {
                BlockPos origin = site.origin(), entry = site.entry();
                result.add("origin", WorldPlanRules.JSON.toJsonTree(new int[]{origin.getX(), origin.getY(), origin.getZ()}));
                result.add("entry", WorldPlanRules.JSON.toJsonTree(new int[]{entry.getX(), entry.getY(), entry.getZ()}));
            });
            return reply(source, result);
        } catch (Exception e) { return fail(source, e); }
    }

    private static int foldedRidgeSample(CommandSourceStack source, BlockPos pos) {
        try {
            ServerLevel world = source.getLevel();
            var result = new JsonObject();
            var site = generator(source).foldedRidge().orElseThrow();
            result.addProperty("interlayer", site.interlayer(pos.getX(), pos.getY(), pos.getZ()));
            result.addProperty("biome", world.getBiome(pos).unwrapKey().orElseThrow().location().toString());
            result.addProperty("sky_light", world.getBrightness(LightLayer.SKY, pos));
            result.addProperty("block_light", world.getBrightness(LightLayer.BLOCK, pos));
            var spawns = new JsonObject();
            for (var type : List.of(ModEntities.LANTERN_CARRIER.get(), ModEntities.CLIFF_RAY.get())) {
                var mob = type.create(world);
                mob.moveTo(pos.getX()+.5, pos.getY(), pos.getZ()+.5, 0, 0);
                boolean allowed = SpawnPlacements.isSpawnPositionOk(type, world, pos)
                        && SpawnPlacements.checkSpawnRules(type, world, MobSpawnType.NATURAL, pos, RandomSource.create(0))
                        && mob.checkSpawnRules(world, MobSpawnType.NATURAL) && mob.checkSpawnObstruction(world);
                spawns.addProperty(net.minecraft.world.entity.EntityType.getKey(type).getPath(), allowed);
            }
            result.add("spawn_allowed", spawns);
            return reply(source, result);
        } catch (Exception e) { return fail(source, e); }
    }

    private static int lotusMountain(CommandSourceStack source) {
        try {
            var generator = generator(source);
            var result = new JsonObject();
            result.addProperty("region", generator.rules().lotusMountain.region());
            result.addProperty("name", "石莲山");
            var arena = generator.lotusMountain().map(com.example.myvillage.worldplan.wonder.LotusMountain::arena);
            result.addProperty("status", arena.isPresent() ? "planned" : "unavailable");
            arena.ifPresent(pos -> {
                result.add("arena", WorldPlanRules.JSON.toJsonTree(new int[]{pos.getX(), pos.getY(), pos.getZ()}));
                result.addProperty("boss_state", com.example.myvillage.worldplan.wonder.LotusArenaEncounter.status(source.getLevel(), pos));
            });
            return reply(source, result);
        } catch (Exception e) { return fail(source, e); }
    }
    private static int mine(CommandSourceStack s){try{
        var generation=generator(s).starterMine();var definition=generation.definition();
        var result=generation.layout().map(layout->starterMineDetails(generation,layout)).orElseGet(JsonObject::new);
        generation.shortfall().ifPresent(shortfall->{
            result.addProperty("starter_status","unavailable");
            result.add("starter_shortfall",WorldPlanRules.JSON.toJsonTree(shortfall));
        });
        var regional=new com.google.gson.JsonArray();
        for(var site:generation.regional()){
            var surface=site.surface();var entry=surface.manifest();entry.addProperty("mode","existing_natural_cave");entry.addProperty("natural_stances",site.naturalStances().size());
            entry.add("entry",WorldPlanRules.JSON.toJsonTree(surface.position(definition.entry)));
            var endpoint=new com.google.gson.JsonObject();endpoint.addProperty("x",surface.worldX(definition.entry.x(),site.endZ()));endpoint.addProperty("y",surface.y+site.floorY());endpoint.addProperty("z",surface.worldZ(definition.entry.x(),site.endZ()));entry.add("cave_entry",endpoint);
            var access=new com.google.gson.JsonArray();for(var cell:site.access(definition))if(cell.x()==definition.entry.x()){var row=new com.google.gson.JsonArray();row.add(surface.worldX(cell.x(),cell.z())+.5);row.add(surface.y+cell.feet2()/2.0);row.add(surface.worldZ(cell.x(),cell.z())+.5);access.add(row);}entry.add("world_access_route",access);regional.add(entry);
        }
        result.add("regional_entries",regional);result.addProperty("regional_target_per_region",definition.regional.count);
        result.addProperty("structures_enabled",s.getServer().getWorldData().worldGenOptions().generateStructures());
        return reply(s,result);
    }catch(Exception e){return fail(s,e);}}
    private static JsonObject starterMineDetails(StarterMineGeneration generation,StarterMineLayout layout){
        var definition=generation.definition();
        var result=layout.manifest();result.addProperty("starter_status","planned");
        result.addProperty("name",definition.name);result.addProperty("layout_hash",layout.hash);
        result.add("entry",WorldPlanRules.JSON.toJsonTree(layout.position(definition.entry)));
        result.add("road_port",WorldPlanRules.JSON.toJsonTree(layout.position(definition.port)));
        var bounds=new com.google.gson.JsonArray();for(int value:new int[]{layout.minX(definition),layout.y+Math.min(definition.minBlockY,definition.caves.minY),layout.minZ(definition),layout.maxX(definition),layout.y+definition.maxBlockY,layout.maxZ(definition)})bounds.add(value);result.add("bounds",bounds);
        var points=new com.google.gson.JsonArray();for(var landmark:definition.landmarks){var point=WorldPlanRules.JSON.toJsonTree(layout.position(landmark.position())).getAsJsonObject();point.addProperty("id",landmark.id());point.addProperty("name",landmark.name());points.add(point);}result.add("landmarks",points);
        var trees=new com.google.gson.JsonArray();for(var tree:definition.trees)trees.add(WorldPlanRules.JSON.toJsonTree(layout.position(tree)));result.add("trees",trees);
        var cave=generation.caves().manifest();cave.addProperty("plan_hash",generation.caves().hash);cave.addProperty("algorithm","minecraft_cave_carver_v1");
        var route=new com.google.gson.JsonArray();for(int z=definition.entry.z();z<=generation.caves().endZ;z++){var p=generation.caves().at(definition.entry.x(),z);var row=new com.google.gson.JsonArray();row.add(layout.worldX(p.x(),p.z())+.5);row.add(layout.y+p.feet2()/2.0);row.add(layout.worldZ(p.x(),p.z())+.5);route.add(row);}cave.add("world_access_route",route);result.add("cave",cave);
        return result;
    }
    private static int export(CommandSourceStack s){try{WorldPlanRuntime.export(s.getLevel(),256);return info(s);}catch(Exception e){return fail(s,e);}}
    private static int pregen(CommandSourceStack s,String order,int limit){try{
        if(!order.equals("forward")&&!order.equals("reverse"))throw new IllegalArgumentException("Order must be forward or reverse");
        var p=generator(s).plan();
        if(p.rules().capitals!=null) {
            var positions=new ArrayList<net.minecraft.core.BlockPos>();
            for(var city:p.cities())for(var tile:city.bundle().tiles)positions.add(new net.minecraft.core.BlockPos(city.x()+tile.x(),city.originY(),city.z()+tile.z()));
            if(order.equals("reverse"))Collections.reverse(positions);
            for(var at:positions.subList(0,Math.min(limit,positions.size())))s.getLevel().getChunkSource().getChunk(at.getX()>>4,at.getZ()>>4,ChunkStatus.FULL,true);
            JsonObject o=new JsonObject();o.addProperty("requested",Math.min(limit,positions.size()));o.addProperty("order",order);o.addProperty("permanent_tickets_added",0);return reply(s,o);
        }
        var tiles=new ArrayList<>(p.rules().city.tiles());if(order.equals("reverse"))Collections.reverse(tiles);
        for(var tile:tiles.subList(0,Math.min(limit,tiles.size())))s.getLevel().getChunkSource().getChunk((p.city().x()+tile.x())>>4,(p.city().z()+tile.z())>>4,ChunkStatus.FULL,true);
        JsonObject o=new JsonObject();o.addProperty("requested",Math.min(limit,tiles.size()));o.addProperty("order",order);o.addProperty("permanent_tickets_added",0);return reply(s,o);
    }catch(Exception e){return fail(s,e);}}
    private static int audit(CommandSourceStack s){try{return reply(s,PlannedCity.audit(generator(s).plan(),s.getLevel()));}catch(Exception e){return fail(s,e);}}
    private static int probe(CommandSourceStack s,int cx,int cz){try{
        var g=generator(s);ServerLevel level=s.getLevel();ChunkAccess chunk=level.getChunkSource().getChunk(cx,cz,ChunkStatus.NOISE,true);
        if(chunk==null)throw new IllegalStateException("NOISE chunk request failed");
        boolean exact=chunk.getPersistedStatus()==ChunkStatus.NOISE;int mismatch=0,columns=0;StringBuilder signature=new StringBuilder();
        for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            int wx=cx*16+x,wz=cz*16+z,target=g.plan().surfaceY(wx,wz);int actual=chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x,z)+1;
            if(exact&&actual!=target)mismatch++;
            int predicted=g.getBaseHeight(wx,wz,Heightmap.Types.OCEAN_FLOOR_WG,level,g.terrainState());
            if(predicted!=target)throw new IllegalStateException("Base-height contract mismatch");
            signature.append(wx).append(',').append(wz).append(':').append(actual).append(';');columns++;
        }
        JsonObject o=new JsonObject();o.addProperty("chunk_x",cx);o.addProperty("chunk_z",cz);o.addProperty("status",chunk.getPersistedStatus().toString());
        o.addProperty("exact_noise_stage",exact);o.addProperty("columns",columns);o.addProperty("height_mismatches",mismatch);
        o.addProperty("height_sha256",WorldPlanRules.sha256(signature.toString()));o.addProperty("plan_sha256",g.plan().fingerprint());return reply(s,o);
    }catch(Exception e){return fail(s,e);}}
}
