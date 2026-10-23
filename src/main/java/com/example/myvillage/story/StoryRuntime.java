package com.example.myvillage.story;

import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.portrait.PortraitAssign;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.avatar.WorldSimAvatars;
import com.example.myvillage.story.net.*;
import com.example.myvillage.story.npc.QianzhenActors;
import com.example.myvillage.story.scene.QianzhenScene;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sole player-progress writer. All methods run on the server thread; the wire carries intentions. */
public final class StoryRuntime {
    private static StoryDefinition definition;
    private static final StorySessions SESSIONS=new StorySessions();
    private StoryRuntime() {}
    public static void register(IEventBus modBus) {
        StoryAttachments.register(modBus);
        NeoForge.EVENT_BUS.addListener(StoryRuntime::started);
        NeoForge.EVENT_BUS.addListener(StoryRuntime::commands);
        NeoForge.EVENT_BUS.addListener(StoryRuntime::investigate);
        NeoForge.EVENT_BUS.addListener(StoryRuntime::login);
        NeoForge.EVENT_BUS.addListener(StoryRuntime::respawn);
        NeoForge.EVENT_BUS.addListener(StoryRuntime::dimension);
        NeoForge.EVENT_BUS.addListener(StoryRuntime::logout);
        NeoForge.EVENT_BUS.addListener(StoryRuntime::death);
        NeoForge.EVENT_BUS.addListener(StoryRuntime::stopping);
    }
    private static void started(ServerStartedEvent e) {
        SESSIONS.clear();definition=null;
        try(var stream=e.getServer().getResourceManager().open(ResourceLocation.fromNamespaceAndPath("myvillage",StoryDefinition.RESOURCE));
                var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var loaded=StoryDefinition.parse(reader);
            for(var r:loaded.rewards())com.example.myvillage.sim.runtime.commerce.CommerceInventory.item(r.item());
            definition=loaded;
        }catch(Exception invalid){throw new IllegalStateException("阡镇剧情定义加载失败；未启用部分剧情",invalid);}
    }
    private static StoryProgress get(ServerPlayer p){return p.getExistingData(StoryAttachments.PROGRESS).orElse(StoryProgress.EMPTY);}
    private static boolean validPlayer(ServerPlayer p){return !p.isRemoved()&&p.isAlive()&&!p.isSpectator()&&p.getServer()!=null&&p.getServer().getPlayerList().getPlayer(p.getUUID())==p;}
    private static String dim(ServerPlayer p){return p.level().dimension().location().toString();}
    private static boolean validNpc(ServerPlayer p,NpcEntity npc) {
        return definition!=null&&validPlayer(p)&&npc!=null&&!npc.isRemoved()&&npc.isAlive()
                &&npc.level()==p.level()&&p.serverLevel().getEntity(npc.getUUID())==npc&&QianzhenActors.current(npc)
                &&p.distanceToSqr(npc)<=(double)definition.range()*definition.range()&&p.hasLineOfSight(npc)
                &&QianzhenScene.at(p.serverLevel()).isPresent();
    }
    private static StorySessions.Identity identity(ServerPlayer p,NpcEntity npc) {
        return new StorySessions.Identity(npc.getUUID(),npc.getId(),npc.ledgerPersonId(),WorldSimAvatars.lifeEpoch(npc),
                QianzhenActors.role(npc),dim(p),get(p).revision(),QianzhenScene.at(p.serverLevel()).map(QianzhenScene.Placement::id).orElse(""));
    }
    /** Called only by the real NPC mobInteract path. Bound actors always consume the interaction. */
    public static boolean interact(ServerPlayer p,NpcEntity npc) {
        String role=QianzhenActors.role(npc);if(role.isEmpty())return false;
        if(!validNpc(p,npc)||!boundHere(p)){message(p,"眼下不便交谈，请走近本人再试。");return true;}
        open(p,npc,false);sync(p);return true;
    }
    private static boolean boundHere(ServerPlayer p) {
        var progress=get(p);var site=QianzhenScene.at(p.serverLevel());
        return site.isPresent()&&(!progress.accepted()||progress.site().equals(site.get().id())&&progress.dimension().equals(dim(p)));
    }
    private static void open(ServerPlayer p,NpcEntity npc,boolean informed) {
        var d=definition;var progress=get(p);String role=QianzhenActors.role(npc);
        if(d==null||!d.roles().containsKey(role))return;
        var person=WorldSimRuntime.sim().flatMap(s->s.person(npc.ledgerPersonId())).orElse(null);if(person==null)return;
        boolean returned=QianzhenActors.returned(p.serverLevel()),regulated=QianzhenScene.regulated(p.serverLevel());
        var options=new ArrayList<StoryDialoguePayload.Option>();
        var lines=new ArrayList<String>();
        if(!progress.accepted()&&role.equals("guide")) {
            if(informed) {
                lines.addAll(d.openingLines(progress,returned,regulated));
                addActions(d,progress,role,returned,regulated,options);
            }else {
                lines.addAll(d.dialogue(progress,role,returned,regulated));
                options.add(new StoryDialoguePayload.Option("learn",d.opening().learnText()));
            }
        }else {
            lines.addAll(d.dialogue(progress,role,returned,regulated));
            addActions(d,progress,role,returned,regulated,options);
        }
        options.add(new StoryDialoguePayload.Option("close","先告辞"));
        if(options.size()>4||lines.size()>6)throw new IllegalStateException("Story dialogue bounds");
        var ids=new LinkedHashSet<String>();options.forEach(o->ids.add(o.id()));
        var session=SESSIONS.open(p.getUUID(),identity(p,npc),p.serverLevel().getGameTime(),d.sessionTicks(),ids,informed);
        npc.getNavigation().stop();npc.getLookControl().setLookAt(p,30,30);
        var sim=WorldSimRuntime.sim().orElseThrow();
        PacketDistributor.sendToPlayer(p,new StoryDialoguePayload(npc.getId(),session.nonce(),role,person.name(),
                PortraitAssign.of(person,sim.day(),WorldSimRuntime.daysPerYear()),d.title(),List.copyOf(lines),List.copyOf(options)));
    }
    private static void addActions(StoryDefinition d,StoryProgress p,String role,boolean returned,boolean regulated,List<StoryDialoguePayload.Option> out) {
        for(var a:d.actions().values())if(!a.kind().equals("investigate")&&a.role().equals(role)&&StoryEngine.offered(d,p,a))
            out.add(new StoryDialoguePayload.Option(a.id(),d.actionText(a,p,returned,regulated)));
    }
    public static void choose(ServerPlayer p,StoryIntentPayload intent) {
        if(definition==null)return;
        var old=SESSIONS.current(p.getUUID());if(old==null)return;
        var entity=p.serverLevel().getEntity(intent.entityId());var npc=entity instanceof NpcEntity n?n:null;
        var id=npc==null?old.identity():identity(p,npc);
        var session=SESSIONS.consume(p.getUUID(),intent.nonce(),intent.optionId(),id,p.serverLevel().getGameTime(),
                intent.entityId()==old.identity().entityId()&&validNpc(p,npc)&&boundHere(p));
        if(session==null) {
            // Reading a page past its lease expiry must not leave every action disabled.
            // Only the exact old capability and the same nearby body may request a fresh page;
            // no stale capability advances progress or replaces a newer session.
            if(old.nonce()==intent.nonce()&&!intent.optionId().equals("close")
                    &&old.options().contains(intent.optionId())&&validNpc(p,npc)&&boundHere(p)
                    &&npc.getUUID().equals(old.identity().entity())&&npc.ledgerPersonId()==old.identity().person())
                open(p,npc,old.informed());
            return;
        }
        if(intent.optionId().equals("close"))return;
        if(intent.optionId().equals("learn")) {
            if(!get(p).accepted()&&id.role().equals("guide"))open(p,npc,true);return;
        }
        var a=definition.actions().get(intent.optionId());
        // The opening page can never authorize acceptance, even if a forged packet names it.
        if(a==null||a.kind().equals("investigate")||a.id().equals("accept")&&!session.informed())return;
        if(!StoryEngine.offered(definition,get(p),a)||!a.role().equals(QianzhenActors.role(npc)))return;
        commit(p,a);
        if(validNpc(p,npc))open(p,npc,true);
    }
    private static boolean commit(ServerPlayer p,StoryDefinition.Action action) {
        var original=get(p);var placement=QianzhenScene.at(p.serverLevel()).orElse(null);if(placement==null)return false;
        var plan=StoryEngine.reduce(definition,original,action.id(),action.kind(),action.kind().equals("investigate")?action.target():action.role(),placement.id(),dim(p));
        if(!plan.changed())return false;
        StoryRewards.Plan reward=null;
        if(plan.reward()) {
            reward=StoryRewards.plan(p.getInventory().items,definition.rewards());
            if(!reward.ready()){message(p,"背包暂时装不下谢礼。先腾出位置，沈岚会替你留着。");sync(p);return false;}
        }
        // Server thread, no callbacks between recheck and inventory+receipt writes.
        if(get(p)!=original)return false;
        if(plan.effect().equals("return_zhou_he")&&!QianzhenActors.returnZhouHe(p.serverLevel())) {
            message(p,"暂时无法安排周禾回药庐，请稍后再来。");return false;
        }
        if(plan.effect().equals("regulate")&&!QianzhenScene.regulate(p.serverLevel()))return false;
        if(reward!=null)for(int i=0;i<reward.items().size();i++)p.getInventory().items.set(i,reward.items().get(i));
        p.setData(StoryAttachments.PROGRESS,plan.progress());
        if(reward!=null){p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();}
        SESSIONS.forget(p.getUUID());
        if(!action.journal().isEmpty())message(p,action.journal());
        sync(p);
        if(!plan.effect().isEmpty())for(var other:p.getServer().getPlayerList().getPlayers())if(other!=p)sync(other);
        return true;
    }
    private static void investigate(PlayerInteractEvent.RightClickBlock event) {
        if(definition==null||event.getHand()!=InteractionHand.MAIN_HAND||!(event.getEntity() instanceof ServerPlayer p)
                ||!validPlayer(p)||!boundHere(p)||!p.serverLevel().hasChunkAt(event.getPos()))return;
        var target=QianzhenScene.target(p.serverLevel(),event.getPos()).orElse(null);if(target==null)return;
        // Reserve authored evidence blocks so vanilla container use cannot alter the fixture.
        event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
        var progress=get(p);
        if(target.id().equals("sample_examination")&&!(p.serverLevel().getBlockState(event.getPos()).getBlock() instanceof net.minecraft.world.level.block.AbstractCauldronBlock))return;
        if(!p.canInteractWithBlock(event.getPos(),0)||!BuiltInRegistries.BLOCK.getKey(p.serverLevel().getBlockState(event.getPos()).getBlock()).toString().equals(target.block()))return;
        Vec3 eye=p.getEyePosition(),hit=event.getHitVec().getLocation();
        if(!Double.isFinite(hit.x)||!Double.isFinite(hit.y)||!Double.isFinite(hit.z)
                ||!event.getHitVec().getBlockPos().equals(event.getPos())||hit.distanceToSqr(Vec3.atCenterOf(event.getPos()))>3)return;
        // Trace the claimed point from the server eye, rejecting occlusion or a fabricated behind-wall hit.
        var trace=p.serverLevel().clip(new ClipContext(eye,hit.add(hit.subtract(eye).normalize().scale(.01)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,p));
        if(trace.getType()!=HitResult.Type.BLOCK||!trace.getBlockPos().equals(event.getPos()))return;
        for(var a:definition.actions().values())if(a.kind().equals("investigate")&&a.target().equals(target.id())&&StoryEngine.offered(definition,progress,a)) {
            commit(p,a);return;
        }
        message(p,progress.evidence().contains(target.id())?"这处线索已经记下。":"先把眼下的事问清，再来查看这里。");
    }
    private static Optional<ServerLevel> sceneLevel(ServerPlayer p) {
        if(p.getServer()==null)return Optional.empty();
        for(var level:p.getServer().getAllLevels())if(QianzhenScene.at(level).isPresent())return Optional.of(level);
        return Optional.empty();
    }
    public static void sync(ServerPlayer p) {
        if(definition==null)return;
        var progress=get(p);
        Optional<BlockPos> position=Optional.empty();
        var scene=sceneLevel(p).orElse(null);
        boolean available=scene!=null;
        var tracking=definition.tracking(progress,available&&QianzhenActors.returned(scene),available&&QianzhenScene.regulated(scene));
        String key=tracking.target();
        boolean bound=available&&(!progress.accepted()||progress.site().equals(QianzhenScene.at(scene).orElseThrow().id())&&progress.dimension().equals(scene.dimension().location().toString()));
        if(bound&&!key.isEmpty())position=definition.roles().containsKey(key)?QianzhenActors.position(scene,key):QianzhenScene.position(scene,key);
        PacketDistributor.sendToPlayer(p,new StorySnapshotPayload(progress.revision(),available,progress.accepted(),progress.completed(),definition.id(),definition.title(),tracking.objective(),tracking.hint(),
                position.isPresent()?scene.dimension().location().toString():"",position.orElse(BlockPos.ZERO),progress.journal()));
    }
    private static void message(ServerPlayer p,String s){p.displayClientMessage(Component.literal(s),false);}
    private static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){SESSIONS.forget(p.getUUID());sync(p);}}
    private static void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p){SESSIONS.forget(p.getUUID());sync(p);}}
    private static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p){SESSIONS.forget(p.getUUID());sync(p);}}
    private static void logout(PlayerEvent.PlayerLoggedOutEvent e){SESSIONS.forget(e.getEntity().getUUID());}
    private static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)SESSIONS.forget(p.getUUID());}
    private static void stopping(ServerStoppingEvent e){SESSIONS.clear();definition=null;}
    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("myvillage").then(Commands.literal("story").requires(s->s.hasPermission(2))
                .then(Commands.literal("status").then(Commands.argument("player",EntityArgument.player()).executes(c->{
                    var player=EntityArgument.getPlayer(c,"player");
                    c.getSource().sendSuccess(()->Component.literal("QIANZHEN_STORY_STATUS "+readback(player)),false);return 1;
                })))));
    }
    /** Read-only, stable field names for evidence collectors. Does not create actors or move players. */
    public static String readback(ServerPlayer p) {
        var progress=get(p);var out=new JsonObject();out.addProperty("player",p.getUUID().toString());out.addProperty("chapter",progress.chapter());
        out.addProperty("stage",progress.stage());out.addProperty("revision",progress.revision());out.addProperty("accepted",progress.accepted());out.addProperty("completed",progress.completed());
        out.addProperty("schema",progress.schema());out.addProperty("definition_version",progress.definitionVersion());
        out.addProperty("site",progress.site());out.addProperty("dimension",progress.dimension());
        var gson=new Gson();out.add("evidence",gson.toJsonTree(progress.evidence()));out.add("receipts",gson.toJsonTree(progress.receipts()));
        out.addProperty("reward",progress.receipts().contains(StoryEngine.RECEIPT)?"claimed":progress.stage().equals("report")?"pending":"locked");
        var scene=sceneLevel(p).orElse(null);
        var shared=new JsonObject();shared.addProperty("returned",scene!=null&&QianzhenActors.returned(scene));shared.addProperty("regulated",scene!=null&&QianzhenScene.regulated(scene));out.add("shared",shared);
        var actors=new JsonObject();
        for(String role:List.of("guide","pharmacist","zhou_he","warden")) {
            int person=scene==null?-1:QianzhenActors.personId(scene,role);var actor=new JsonObject();actor.addProperty("personId",person);
            var position=scene==null?Optional.<BlockPos>empty():QianzhenActors.position(scene,role);actor.add("position",position.map(StoryRuntime::posJson).orElse(JsonNull.INSTANCE));
            long epoch=WorldSimRuntime.sim().flatMap(s->s.life(person)).map(l->l.epoch()).orElse(-1L);actor.addProperty("epoch",epoch);actors.add(role,actor);
        }
        out.add("npc",actors);return out.toString();
    }
    private static JsonElement posJson(BlockPos p){var o=new JsonObject();o.addProperty("x",p.getX());o.addProperty("y",p.getY());o.addProperty("z",p.getZ());return o;}
}
