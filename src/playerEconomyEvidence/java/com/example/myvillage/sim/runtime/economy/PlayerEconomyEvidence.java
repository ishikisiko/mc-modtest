package com.example.myvillage.sim.runtime.economy;

import com.example.myvillage.block.*;
import com.example.myvillage.item.ModDataComponents;
import com.example.myvillage.sect.*;
import com.example.myvillage.sim.*;
import com.example.myvillage.sim.runtime.*;
import com.example.myvillage.sim.runtime.avatar.*;
import com.example.myvillage.sim.runtime.net.ScriptureBorrowPayload;
import com.example.myvillage.sim.runtime.player.*;
import com.example.myvillage.spiritstone.*;
import com.example.myvillage.worldplan.capital.CapitalCity;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 独立新世界的服务器交互与实际物品回执；没有映射客户端输入，不进入交付包。 */
@EventBusSubscriber(modid="myvillage")
public final class PlayerEconomyEvidence {
    private static final String PHASE = System.getenv("MYVILLAGE_ECONOMY_PHASE");
    private static final Path OUT = Path.of(System.getenv().getOrDefault("MYVILLAGE_ECONOMY_OUT", "../reports/economy-native"));
    private static final UUID OWNER = UUID.nameUUIDFromBytes("economy-proof-owner".getBytes(StandardCharsets.UTF_8));
    private static final UUID OTHER = UUID.nameUUIDFromBytes("economy-proof-other".getBytes(StandardCharsets.UTF_8));
    private static final List<Map<String,Object>> checks = new ArrayList<>();
    private static final List<String> failures = new ArrayList<>();
    private static ServerPlayer owner, other;
    private static int stage, nextTick, cityIndex, questCount, sect;
    private static int stopAt = -1;
    private static List<CapitalCity> cities;
    private static BlockPos board, otherBoard, counter, counterReader, shelf;
    private static String site, otherSite, paidManual;
    private static EconomyMenu firstMenu;
    private static EconomyIntentPayload firstIntent;
    private static com.example.myvillage.sim.economy.TownAssignment firstTask;
    private static com.example.myvillage.sim.economy.TaskReward questReward;
    private static int beforeContribution, beforeRep, beforePills;
    private static BigInteger beforeLow;
    private static WorldSim sim() { return WorldSimRuntime.sim().orElseThrow(); }
    private static String id() { return OWNER.toString(); }
    private static void check(String key, boolean pass, Object detail) {
        checks.add(Map.of("id",key,"pass",pass,"detail",String.valueOf(detail)));
        if (!pass) throw new IllegalStateException(key+": "+detail);
    }
    private static final class MemoryConnection extends ServerGamePacketListenerImpl {
        MemoryConnection(MinecraftServer server, ServerPlayer p) { super(server,new Connection(PacketFlow.SERVERBOUND),p,CommonListenerCookie.createInitial(p.getGameProfile(),false)); }
        @Override public boolean hasChannel(ResourceLocation channel) { return true; }
        @Override public void send(Packet<?> packet) { }
        @Override public void send(Packet<?> packet, PacketSendListener listener) { }
    }
    @SuppressWarnings("unchecked") private static ServerPlayer actor(MinecraftServer server, UUID uuid, String name, boolean load) throws Exception {
        var p = new ServerPlayer(server,server.overworld(),new GameProfile(uuid,name),ClientInformation.createDefault());
        p.connection = new MemoryConnection(server,p);
        if (load) check("player_file_loaded_"+name,server.getPlayerList().load(p).isPresent(),uuid);
        var ids = PlayerList.class.getDeclaredField("playersByUUID"); ids.setAccessible(true); ((Map<UUID,ServerPlayer>)ids.get(server.getPlayerList())).put(uuid,p);
        var players = PlayerList.class.getDeclaredField("players"); players.setAccessible(true); ((List<ServerPlayer>)players.get(server.getPlayerList())).add(p);
        p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL); p.getAbilities().invulnerable=true;
        p.serverLevel().addNewPlayer(p); return p;
    }
    private static void load(ServerLevel level, BlockPos at) {
        // 内存连接没有客户端区块追踪；测试夹具显式驻留目标，供正常观察 tick 登记设施。
        level.setChunkForced(at.getX() >> 4, at.getZ() >> 4, true); level.getChunk(at);
    }
    private static void visit(ServerPlayer p, BlockPos at) {
        load(p.serverLevel(),at); p.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0); p.setDeltaMovement(0,0,0);
    }
    private static BlockPos capitalPoint(CapitalCity city, boolean reader) {
        var row = city.bundle().life().getAsJsonArray("places").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(o->o.get("id").getAsString().equals("jianghu_board")).findFirst().orElseThrow();
        var p = row.getAsJsonArray(reader?"pos":"block_pos");
        return new BlockPos(city.x()+p.get(0).getAsInt(),city.originY()+p.get(1).getAsInt(),city.z()+p.get(2).getAsInt());
    }
    private static BigInteger low(ServerPlayer p) {
        var type = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("myvillage:low_grade_spirit_stone")));
        return SpiritStoneSources.open(p).copies().stream().map(s->SpiritStoneStorage.count(s,SpiritStoneStorage.ordinary(type))).reduce(BigInteger.ZERO,BigInteger::add);
    }
    private static int pills(ServerPlayer p) { return p.getInventory().items.stream().filter(s->s.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse("myvillage:pill_huiling")))).mapToInt(ItemStack::getCount).sum(); }
    private static int count(ServerPlayer p, net.minecraft.world.item.Item item) { return p.getInventory().items.stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum(); }
    private static EconomyMenu open(ServerPlayer p, BlockPos at) {
        EconomyRuntime.open(p,at); check("opens_real_counter_"+checks.size(),p.containerMenu instanceof EconomyMenu,p.containerMenu.getClass().getName()); return (EconomyMenu)p.containerMenu;
    }
    private static int row(EconomyMenu menu, String key) { for(int i=0;i<menu.rows.size();i++)if(menu.rows.get(i).id().equals(key))return i;throw new IllegalArgumentException(key); }
    private static void act(ServerPlayer p, EconomyMenu menu, String key) { menu.act(p,new EconomyIntentPayload(menu.containerId,menu.token,menu.sequence(),row(menu,key))); }
    private static void kill(ServerPlayer p) {
        var mob = (Mob)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("myvillage:demon_wolf")).create(p.serverLevel());
        mob.moveTo(p.getX()+2,p.getY(),p.getZ()+2,0,0); mob.setNoAi(true); p.serverLevel().addFreshEntity(mob); mob.setHealth(1); mob.invulnerableTime=0;
        check("actual_beast_death_"+checks.size(),mob.hurt(p.damageSources().playerAttack(p),1000000)&&!mob.isAlive(),mob.getHealth());
    }
    private static void begin(MinecraftServer server) throws Exception {
        WorldSimRuntime.setPaused(true);
        cities=WorldPlanRuntime.generator(server.overworld()).orElseThrow().plan().cities();
        check("new_world_has_eight_cities",cities.size()==8,cities.size());
        owner=actor(server,OWNER,"EconomyOwner",false); other=actor(server,OTHER,"EconomyOther",false);
        check("economic_fixture_starts_without_prior_rewards",sim().townTask(id()).isEmpty()&&sim().jianghuReputation(id())==0
                &&sim().playerMember(id()).map(p->p.contribution()==0&&!p.inSect()).orElse(true)&&!sim().hasBorrowed(id(),"jianqi_jue"),id());
        stage=1;
    }
    private static void inspectCities(MinecraftServer server) {
        if(cityIndex<cities.size()) {
            var city=cities.get(cityIndex++); var root=capitalPoint(city,false); var reader=capitalPoint(city,true);
            load(server.overworld(),root); visit(owner,reader);
            check("generated_board_"+city.cityId(),server.overworld().getBlockState(root).is(ModBlocks.JIANGHU_BOARD.get()),root);
            check("generated_reader_"+city.cityId(),server.overworld().getBlockState(reader).isAir()&&server.overworld().getBlockState(reader.below()).isFaceSturdy(server.overworld(),reader.below(),Direction.UP),reader);
            nextTick=server.getTickCount()+sim().lifeRules().runtimeIntervalTicks()+2; return;
        }
        if(cityIndex==cities.size()){cityIndex++;nextTick=server.getTickCount()+60;return;}
        check("eight_boards_bound_to_original_towns",cities.stream().allMatch(c->sim().lifeSite(c.cityId()).flatMap(s->s.place("jianghu_board")).isPresent()),sim().lifeSites().size());
        var plan=WorldPlanRuntime.generator(server.overworld()).orElseThrow().plan();
        for(var settlement:plan.settlements()) {
            var origin=new BlockPos(settlement.x(),settlement.offsetY(),settlement.z());
            var root=SettlementNoticeBoard.transform(origin,settlement.rotation(),false);var reader=SettlementNoticeBoard.transform(origin,settlement.rotation(),true);
            load(server.overworld(),root);visit(owner,reader);
            check("generated_settlement_board_"+settlement.id(),server.overworld().getBlockState(root).is(ModBlocks.JIANGHU_BOARD.get()),root);
            var menu=open(owner,root);check("settlement_board_uses_original_site_"+settlement.id(),menu.site.equals(settlement.id()),menu.site);owner.closeContainer();
        }
        var layouts=new HashSet<String>();
        for(var plot:plan.sectSites())if(layouts.add(plot.blueprintId())) {
            var blueprint=SectBlueprint.get(plot.blueprintId());var origin=new BlockPos(plot.x(),plot.baseY(),plot.z());
            var root=origin.offset(blueprint.linlangCounter);var reader=origin.offset(blueprint.linlangReader);load(server.overworld(),root);load(server.overworld(),reader);
            check("generated_linlang_"+plot.blueprintId(),server.overworld().getBlockState(root).is(ModBlocks.LINLANG_COUNTER.get())&&server.overworld().getBlockState(reader).isAir(),root);
        }
        check("all_six_linlang_layouts_are_realized",layouts.size()==6,layouts);
        var first=cities.getFirst();board=capitalPoint(first,false);site=first.cityId();visit(owner,capitalPoint(first,true));
        var second=cities.get(1);otherBoard=capitalPoint(second,false);otherSite=second.cityId();visit(other,capitalPoint(second,true));
        firstMenu=open(owner,board);firstIntent=new EconomyIntentPayload(firstMenu.containerId,firstMenu.token,firstMenu.sequence(),row(firstMenu,"supply_bread"));firstMenu.act(owner,firstIntent);
        firstTask=sim().townTask(id()).orElseThrow();check("real_board_accepts_task",firstTask.siteId().equals(site),firstTask);
        firstMenu.act(owner,firstIntent);check("accept_replay_does_not_replace_reward",sim().townTask(id()).orElseThrow().equals(firstTask),firstTask);
        for(int i=0;i<36;i++)owner.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));owner.getInventory().setItem(0,new ItemStack(Items.BREAD,64));
        stage=2;nextTick=server.getTickCount()+6;
    }
    private static void fullInventory(MinecraftServer server) {
        act(owner,firstMenu,"supply_bread");
        check("full_backpack_preserves_task_and_reputation",firstMenu.status().equals("inventory_full")&&sim().townTask(id()).filter(firstTask::equals).isPresent()&&sim().jianghuReputation(id())==0&&count(owner,Items.BREAD)==64,firstMenu.status());
        check("full_backpack_has_no_reward",low(owner).signum()==0,low(owner));
        for(int i=1;i<10;i++)owner.getInventory().setItem(i,ItemStack.EMPTY);
        stage=3;nextTick=server.getTickCount()+6;
    }
    private static void completeTown(MinecraftServer server) {
        var intent=new EconomyIntentPayload(firstMenu.containerId,firstMenu.token,firstMenu.sequence(),row(firstMenu,"supply_bread"));firstMenu.act(owner,intent);
        check("town_supply_consumes_exact_items",count(owner,Items.BREAD)==58,count(owner,Items.BREAD));
        check("town_reputation_and_real_stones_arrive",sim().jianghuReputation(id())==firstTask.reward().reputation()&&low(owner).equals(BigInteger.valueOf(firstTask.reward().stones().getOrDefault("low",0))),low(owner));
        firstMenu.act(owner,intent);check("completion_replay_pays_once",sim().townTask(id()).isEmpty()&&sim().jianghuReputation(id())==firstTask.reward().reputation(),sim().jianghuReputation(id()));
        check("same_town_daily_limit",!sim().townTaskAvailable(id(),site,EconomyRuntime.day(owner)),site);
        var secondMenu=open(other,otherBoard);act(other,secondMenu,"clear_beasts");var secondTask=sim().townTask(OTHER.toString()).orElseThrow();
        secondMenu.act(other,new EconomyIntentPayload(secondMenu.containerId,firstMenu.token,secondMenu.sequence(),0));
        check("other_player_cannot_reuse_owner_nonce",sim().townTask(OTHER.toString()).filter(secondTask::equals).isPresent()&&sim().jianghuReputation(OTHER.toString())==0,secondTask);
        kill(other);check("actual_death_advances_town_hunt",sim().townTask(OTHER.toString()).orElseThrow().progress()==1,sim().townTask(OTHER.toString()));
        var chosen=sim().sects(false).stream().filter(s->s.tierId().equals("main")).findFirst().orElse(sim().sects(false).getFirst());sect=chosen.id();
        sim().joinSect(id(),"EconomyOwner",sect,WorldSimPlayers.qualification(owner),true);sim().promotePlayer(id(),"EconomyOwner","elder");
        var plot=WorldPlanRuntime.generator(server.overworld()).orElseThrow().plan().sectAtGate(chosen.gateX(),chosen.gateZ()).orElseThrow();
        var blueprint=SectBlueprint.get(plot.blueprintId());var origin=new BlockPos(plot.x(),plot.baseY(),plot.z());
        counter=origin.offset(blueprint.linlangCounter);counterReader=origin.offset(blueprint.linlangReader);shelf=origin.offset(blueprint.shelves.getFirst().position());
        load(server.overworld(),new BlockPos(plot.gateX(),plot.gateY(),plot.gateZ()));load(server.overworld(),counter);
        for(var s:blueprint.shelves)load(server.overworld(),origin.offset(s.position()));
        GateRealizer.bindLoaded(server.overworld(),sim());visit(owner,counterReader);
        check("linlang_is_generated_in_claimed_sect",server.overworld().getBlockState(counter).is(ModBlocks.LINLANG_COUNTER.get()),blueprint.id+" "+counter);
        stage=4;nextTick=server.getTickCount()+6;
    }
    private static void takeSectTask(MinecraftServer server) {
        int attempts=0;while(sim().offerTask(id()).isEmpty()&&attempts++<=WorldSimRuntime.daysPerYear()+1)sim().step(WorldSimRuntime.daysPerYear());
        sim().acceptTask(id(),"EconomyOwner");var task=sim().task(id()).orElseThrow();questReward=sim().sectTaskReward(id());
        beforeContribution=sim().playerMember(id()).orElseThrow().contribution();
        if(task.kind().equals("tribute"))owner.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("myvillage:low_grade_spirit_stone")),task.count()));
        if(task.kind().equals("patrol")) {
            var s=sim().sect(sect).orElseThrow();var plot=WorldPlanRuntime.generator(server.overworld()).orElseThrow().plan().sectAtGate(s.gateX(),s.gateZ()).orElseThrow();visit(owner,new BlockPos(plot.gateX(),plot.gateY(),plot.gateZ()));
            for(int i=0;i<task.count();i++)kill(owner);
        }
        if(task.kind().equals("courier")) {
            var target=sim().sect(task.targetSectId()).orElseThrow();var plot=WorldPlanRuntime.generator(server.overworld()).orElseThrow().plan().sectAtGate(target.gateX(),target.gateZ()).orElseThrow();visit(owner,new BlockPos(plot.gateX(),plot.gateY(),plot.gateZ()));
        }
        beforeLow=low(owner);stage=5;nextTick=server.getTickCount()+30;
    }
    private static void turnInSect(MinecraftServer server) throws Exception {
        var task=sim().task(id()).orElseThrow();
        var method=SectTasks.class.getDeclaredMethod("turnIn",ServerPlayer.class,int.class);method.setAccessible(true);
        var result=(WorldSimPlayers.Result)method.invoke(null,owner,sect);check("actual_sect_task_turn_in_"+questCount,result.ok(),result);
        BigInteger expected=beforeLow.add(BigInteger.valueOf(questReward.stones().getOrDefault("low",0)-(task.kind().equals("tribute")?task.count():0)));
        check("sect_reward_real_stones_"+questCount,low(owner).equals(expected),low(owner)+" expected "+expected);
        check("sect_reward_contribution_"+questCount,sim().playerMember(id()).orElseThrow().contribution()==beforeContribution+questReward.contribution(),sim().playerMember(id()));
        method.invoke(null,owner,sect);check("sect_task_replay_pays_once_"+questCount,low(owner).equals(expected),low(owner));
        questCount++;
        int manualBudget=sim().borrowable(id()).stream().mapToInt(sim()::manualContributionCost).filter(c->c>0).min().orElseThrow();
        int required=Math.addExact(manualBudget,sim().playerEconomyRules().offer("huiling").cost());
        check("task_budget_is_bounded_"+questCount,questCount<=32,required);
        stage=questCount<2||sim().playerMember(id()).orElseThrow().contribution()<required?4:6;nextTick=server.getTickCount()+6;
    }
    private static void exchangeGoods(MinecraftServer server) {
        visit(owner,counterReader);var menu=open(owner,counter);beforeContribution=sim().playerMember(id()).orElseThrow().contribution();beforePills=pills(owner);
        var intent=new EconomyIntentPayload(menu.containerId,menu.token,menu.sequence(),row(menu,"huiling"));menu.act(owner,intent);
        check("linlang_spends_contribution_and_gives_real_item",sim().playerMember(id()).orElseThrow().contribution()==beforeContribution-4&&pills(owner)==beforePills+1,menu.status());
        menu.act(owner,intent);check("linlang_nonce_prevents_duplicate_purchase",sim().playerMember(id()).orElseThrow().contribution()==beforeContribution-4&&pills(owner)==beforePills+1,pills(owner));
        paidManual=sim().borrowable(id()).stream().filter(t->sim().manualContributionCost(t)>0).min(Comparator.comparingInt(t->sim().manualContributionCost(t))).orElseThrow();
        visit(owner,shelf.north());beforeContribution=sim().playerMember(id()).orElseThrow().contribution();ScriptureHall.handleBorrow(owner,new ScriptureBorrowPayload(shelf,paidManual));
        check("scripture_exchanges_paid_manual",sim().hasBorrowed(id(),paidManual)&&sim().playerMember(id()).orElseThrow().contribution()==beforeContribution-sim().manualContributionCost(paidManual),paidManual);
        stage=7;nextTick=server.getTickCount()+6;
    }
    private static void freeManualAndSave(MinecraftServer server) throws Exception {
        beforeContribution=sim().playerMember(id()).orElseThrow().contribution();ScriptureHall.handleBorrow(owner,new ScriptureBorrowPayload(shelf,"jianqi_jue"));
        check("scripture_keeps_free_manuals",sim().hasBorrowed(id(),"jianqi_jue")&&sim().playerMember(id()).orElseThrow().contribution()==beforeContribution,beforeContribution);
        int count=owner.getInventory().items.stream().filter(s->s.has(ModDataComponents.TECHNIQUE.get())).mapToInt(ItemStack::getCount).sum();check("actual_manual_items_present",count>=2,count);
        visit(owner,capitalPoint(cities.getFirst(),true));var menu=open(owner,board);beforeRep=sim().jianghuReputation(id());server.overworld().destroyBlock(board,false);
        menu.act(owner,new EconomyIntentPayload(menu.containerId,menu.token,menu.sequence(),0));
        check("broken_board_closes_old_menu",owner.containerMenu!=menu&&sim().jianghuReputation(id())==beforeRep,owner.containerMenu.getClass().getName());
        owner.closeContainer();other.closeContainer();Files.createDirectories(OUT);
        var expected=new JsonObject();expected.addProperty("sect",sect);expected.addProperty("reputation",sim().jianghuReputation(id()));expected.addProperty("contribution",sim().playerMember(id()).orElseThrow().contribution());
        expected.addProperty("low",low(owner).toString());expected.addProperty("pills",pills(owner));expected.addProperty("manual",paidManual);expected.add("broken_board",new Gson().toJsonTree(List.of(board.getX(),board.getY(),board.getZ())));
        expected.addProperty("other_task",sim().townTask(OTHER.toString()).orElseThrow().taskId());expected.addProperty("other_progress",1);
        Files.writeString(OUT.resolve("expected.json"),new GsonBuilder().setPrettyPrinting().create().toJson(expected));
        server.getPlayerList().saveAll();WorldSimSavedData.get(server.overworld()).setDirty();finish(server);
    }
    private static void restart(MinecraftServer server) throws Exception {
        WorldSimRuntime.setPaused(true);var expected=JsonParser.parseString(Files.readString(OUT.resolve("expected.json"))).getAsJsonObject();
        owner=actor(server,OWNER,"EconomyOwner",true);other=actor(server,OTHER,"EconomyOther",true);
        check("reputation_survives_real_restart",sim().jianghuReputation(id())==expected.get("reputation").getAsInt(),sim().jianghuReputation(id()));
        check("spent_contribution_survives_real_restart",sim().playerMember(id()).orElseThrow().contribution()==expected.get("contribution").getAsInt(),sim().playerMember(id()));
        check("earned_inventory_survives_real_restart",low(owner).toString().equals(expected.get("low").getAsString())&&pills(owner)==expected.get("pills").getAsInt(),low(owner));
        check("paid_and_free_manual_receipts_survive",sim().hasBorrowed(id(),expected.get("manual").getAsString())&&sim().hasBorrowed(id(),"jianqi_jue"),expected.get("manual"));
        var task=sim().townTask(OTHER.toString()).orElseThrow();check("unfinished_other_player_task_survives",task.taskId().equals(expected.get("other_task").getAsString())&&task.progress()==1,task);
        var xyz=expected.getAsJsonArray("broken_board");var root=new BlockPos(xyz.get(0).getAsInt(),xyz.get(1).getAsInt(),xyz.get(2).getAsInt());server.overworld().getChunk(root);
        check("broken_board_is_not_regenerated_on_restart",server.overworld().getBlockState(root).isAir(),root);finish(server);
    }
    private static void finish(MinecraftServer server) throws Exception {
        // 测试驻留只服务于跨城观察；停服前释放，并让正常 tick 完成在途区块与卸载。
        var level=server.overworld();
        for(long packed:level.getForcedChunks().toLongArray()) {
            var chunk=new net.minecraft.world.level.ChunkPos(packed);level.setChunkForced(chunk.x,chunk.z,false);
        }
        check("fixture_chunk_tickets_released",level.getForcedChunks().isEmpty(),level.getForcedChunks().size());
        Files.createDirectories(OUT);Files.writeString(OUT.resolve(PHASE+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("phase",PHASE,"pass",failures.isEmpty(),"checks",checks,"failures",failures,"mapped_input","not_verified")));
        stopAt=server.getTickCount()+200;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(PHASE==null)return;var server=event.getServer();
        if(stopAt>=0){if(server.getTickCount()>=stopAt)server.halt(false);return;}
        if(server.getTickCount()<80||server.getTickCount()<nextTick)return;
        try {
            if(PHASE.equals("restart")){restart(server);return;}
            switch(stage){case 0->begin(server);case 1->inspectCities(server);case 2->fullInventory(server);case 3->completeTown(server);case 4->takeSectTask(server);case 5->turnInSect(server);case 6->exchangeGoods(server);default->freeManualAndSave(server);}
        } catch(Throwable error) { error.printStackTrace();failures.add(error.toString());try{finish(server);}catch(Exception nested){server.halt(false);} }
    }
}
