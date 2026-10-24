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
    private static final org.slf4j.Logger LOGGER=com.mojang.logging.LogUtils.getLogger();
    private static StoryDefinition definition;
    private static StoryDefinition openingDefinition;
    private static final StorySessions SESSIONS=new StorySessions();
    private StoryRuntime() {}
    public static void register(IEventBus modBus) {
        StoryAttachments.register(modBus);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,StoryRuntime::placed);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,StoryRuntime::broken);
        NeoForge.EVENT_BUS.addListener(StoryRuntime::tick);
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
        SESSIONS.clear();PENDING_PLANTS.clear();definition=null;openingDefinition=null;
        definition=load(e,StoryDefinition.RESOURCE);
        openingDefinition=load(e,StoryDefinition.OPENING_RESOURCE);
    }
    private static StoryDefinition load(ServerStartedEvent e,String resource) {
        try(var stream=e.getServer().getResourceManager().open(ResourceLocation.fromNamespaceAndPath("myvillage",resource));
                var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var loaded=StoryDefinition.parse(reader);
            for(var r:loaded.rewards())com.example.myvillage.sim.runtime.commerce.CommerceInventory.item(r.item());
            if(loaded.work().enabled()&&!(com.example.myvillage.sim.runtime.commerce.CommerceInventory.item(loaded.work().item()) instanceof net.minecraft.world.item.BlockItem item
                    &&item.getBlock() instanceof com.example.myvillage.block.SpiritCropBlock))throw new IllegalArgumentException("work_requires_spirit_crop_item");
            return loaded;
        }catch(Exception invalid){throw new IllegalStateException("阡镇剧情定义加载失败；未启用部分剧情: "+resource,invalid);}
    }
    public static StoryProgress progress(ServerPlayer p){return p.getExistingData(StoryAttachments.PROGRESS).orElse(StoryProgress.EMPTY);}
    private static StoryProgress get(ServerPlayer p){return progress(p);}
    private static StoryDefinition current(ServerPlayer p){return StoryOpening.definition(get(p),definition,openingDefinition);}
    public static boolean openingAvailable(ServerPlayer p){return openingDefinition!=null&&validPlayer(p)&&!get(p).accepted()&&openingFacilities(p).ready();}
    /** Reconciles accepted personal facts and refreshes tracking; never accepts a recruitment. */
    public static void refresh(ServerPlayer p){sync(p);}
    /** An entrance must forward a real offered capability; proximity alone cannot accept work. */
    public static boolean acceptOpening(ServerPlayer p,NpcEntity npc,long nonce) {
        if(!openingAvailable(p)||npc==null||!QianzhenActors.role(npc).equals("pharmacist"))return false;
        choose(p,new StoryIntentPayload(npc.getId(),nonce,"accept"));
        return get(p).accepted()&&get(p).definitionVersion()==2;
    }
    private static boolean validPlayer(ServerPlayer p){return !p.isRemoved()&&p.isAlive()&&!p.isSpectator()&&p.getServer()!=null&&p.getServer().getPlayerList().getPlayer(p.getUUID())==p;}
    private static String dim(ServerPlayer p){return p.level().dimension().location().toString();}
    private static boolean validNpc(ServerPlayer p,NpcEntity npc) {
        return definition!=null&&validPlayer(p)&&npc!=null&&!npc.isRemoved()&&npc.isAlive()
                &&npc.level()==p.level()&&p.serverLevel().getEntity(npc.getUUID())==npc&&QianzhenActors.current(npc)
                &&p.distanceToSqr(npc)<=(double)current(p).range()*current(p).range()&&p.hasLineOfSight(npc)
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
        reconcileOpening(p);
        var d=current(p);var progress=get(p);String role=QianzhenActors.role(npc);
        if(d==null||!d.roles().containsKey(role))return;
        var person=WorldSimRuntime.sim().flatMap(s->s.person(npc.ledgerPersonId())).orElse(null);if(person==null)return;
        boolean returned=QianzhenActors.returned(p.serverLevel()),regulated=QianzhenScene.regulated(p.serverLevel());
        var options=new ArrayList<StoryDialoguePayload.Option>();
        var lines=new ArrayList<String>();
        if(!progress.accepted()&&(role.equals("guide")||role.equals("pharmacist"))) {
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
        if(!progress.accepted()&&d.work().enabled()&&!openingFacilities(p).ready()) {
            options.removeIf(o->o.id().equals("accept"));
            lines.clear();lines.add(role.equals("pharmacist")?"眼下还安排不了这份活，等药圃和院里的事妥当了再来问我吧。":"药庐眼下还安排不了这份活，过些时候再去问沈岚吧。");
            if(informed)message(p,"学徒设施暂不可用："+openingFacilities(p).reason()+"。现有行迹和作物保留，请管理员核对设施；不会自动重建旧镇。");
        }
        if(StoryOpening.active(progress)&&progress.stage().equals("planting")&&!openingFacilities(p).ready()) {
            lines.clear();lines.add("药圃眼下出了些状况，先歇歇。你已经种下的我记着，等能开工了再接着做。");
            message(p,"学徒设施暂不可用："+openingFacilities(p).reason()+"。补种与种苗领取记录保留，请管理员核对受损设施。");
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
        for(var a:d.actions().values())if((a.kind().equals("talk")||a.kind().equals("claim"))&&a.role().equals(role)&&StoryEngine.offered(d,p,a))
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
            if(!get(p).accepted()&&(id.role().equals("guide")||id.role().equals("pharmacist")))open(p,npc,true);return;
        }
        var d=current(p);
        var a=d.actions().get(intent.optionId());
        // The opening page can never authorize acceptance, even if a forged packet names it.
        if(a==null||!(a.kind().equals("talk")||a.kind().equals("claim"))||a.id().equals("accept")&&!session.informed())return;
        if(!StoryEngine.offered(d,get(p),a)||!a.role().equals(QianzhenActors.role(npc)))return;
        commit(p,a);
        if(validNpc(p,npc))open(p,npc,true);
    }
    private static boolean commit(ServerPlayer p,StoryDefinition.Action action) {
        if(action.kind().equals("fact"))return false;
        var d=current(p);var original=get(p);var placement=QianzhenScene.at(p.serverLevel()).orElse(null);if(placement==null)return false;
        var plan=StoryEngine.reduce(d,original,action.id(),action.kind(),action.kind().equals("investigate")?action.target():action.role(),placement.id(),dim(p));
        if(!plan.changed())return false;
        if(action.id().equals("accept")&&d.work().enabled()&&!openingFacilities(p).ready()) {
            message(p,"这处药庐尚不能安排学徒工作："+openingFacilities(p).reason()+"。旧章记录会保留；请管理员核对场景设施，或在独立新世界使用完整入口，不能覆盖旧镇或他人作物。");return false;
        }
        var profile=com.example.myvillage.cultivation.CultivationService.getProfile(p);
        boolean learned=profile.learnedTechniques().containsKey(com.example.myvillage.cultivation.data.ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID);
        if(!StoryOpening.allowed(plan.effect(),original,profile.spiritualRoot().isPresent(),
                (!plan.effect().equals("verify_root")||com.example.myvillage.cultivation.fate.FateService.state(p).confirmed()),learned,d.work().count())) {
            message(p,switch(plan.effect()) {
                case "verify_root" -> "请先在测灵碑前查看灵根并确认天命；已有结果不会重抽。";
                case "verify_inheritance" -> "请先在传承碑学会基础吐纳，原有功法和熟练度会保留。";
                case "verify_breath" -> "请先用 H 开始普通修炼，实际结算一回后收功再来。";
                case "verify_work" -> "还未完成约定的本人补种："+StoryOpening.planted(original)+" / "+d.work().count()+" 格。";
                default -> "这批种苗已经领取，不会重复发放。";
            });return false;
        }
        StoryRewards.Plan reward=null;
        if(plan.reward()) {
            reward=StoryRewards.plan(p.getInventory().items,d.rewards());
            if(!reward.ready()){message(p,"背包暂时装不下谢礼。先腾出位置，沈岚会替你留着。");sync(p);return false;}
        }
        if(plan.effect().equals("supply_work")) {
            reward=StoryRewards.plan(p.getInventory().items,List.of(new StoryDefinition.Reward(d.work().item(),d.work().count())));
            if(!reward.ready()){message(p,"先留出位置装种苗，种苗会替你留着。");return false;}
        }
        var replacement=plan.progress();
        if(plan.effect().equals("supply_work"))replacement=StoryOpening.receipt(replacement,StoryOpening.SUPPLIES);
        if(action.id().equals("accept")&&d.version()==2&&experienced(profile))replacement=StoryOpening.receipt(replacement,StoryOpening.EXPERIENCED);
        // Server thread, no callbacks between recheck and inventory+receipt writes.
        if(get(p)!=original)return false;
        if(plan.effect().equals("return_zhou_he")&&!QianzhenActors.returnZhouHe(p.serverLevel())) {
            message(p,"暂时无法安排周禾回药庐，请稍后再来。");return false;
        }
        if(plan.effect().equals("regulate")&&!QianzhenScene.regulate(p.serverLevel()))return false;
        if(reward!=null)for(int i=0;i<reward.items().size();i++)p.getInventory().items.set(i,reward.items().get(i));
        p.setData(StoryAttachments.PROGRESS,replacement);
        if(reward!=null){p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();}
        SESSIONS.forget(p.getUUID());
        if(!action.journal().isEmpty())message(p,action.journal());
        sync(p);
        if(Set.of("return_zhou_he","regulate").contains(plan.effect()))for(var other:p.getServer().getPlayerList().getPlayers())if(other!=p)sync(other);
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
        var d=current(p);
        for(var a:d.actions().values())if(a.kind().equals("investigate")&&a.target().equals(target.id())&&StoryEngine.offered(d,progress,a)) {
            commit(p,a);return;
        }
        message(p,progress.evidence().contains(target.id())?"这处线索已经记下。":"先把眼下的事问清，再来查看这里。");
    }
    private static Optional<ServerLevel> sceneLevel(ServerPlayer p) {
        if(p.getServer()==null)return Optional.empty();
        for(var level:p.getServer().getAllLevels())if(QianzhenScene.at(level).isPresent())return Optional.of(level);
        return Optional.empty();
    }
    public record OpeningFacilities(boolean ready,String reason,int soil,int empty,int mature,int growing) {}
    /** Loaded blocks, never a position-table promise. Does not generate chunks or alter an old save. */
    public static OpeningFacilities openingFacilities(ServerPlayer p) {
        if(openingDefinition==null)return new OpeningFacilities(false,"剧情尚未就绪",0,0,0,0);
        var scene=sceneLevel(p).orElse(null);
        if(scene==null)return new OpeningFacilities(false,"没有完整部署的阡镇场景",0,0,0,0);
        if(!QianzhenScene.layout(scene).facilities().stream().map(com.example.myvillage.story.scene.SceneLayout.Target::id)
                .collect(java.util.stream.Collectors.toSet()).containsAll(StoryDefinition.FACILITIES))
            return new OpeningFacilities(false,"旧场景没有完整登记学徒设施",0,0,0,0);
        for(var entry:Map.of("testing_stele","myvillage:spirit_testing_stele","inheritance_stele","myvillage:technique_inheritance_stele").entrySet()) {
            var pos=QianzhenScene.position(scene,entry.getKey()).orElse(null);
            if(pos==null)return new OpeningFacilities(false,"旧场景未登记测灵与传承设施",0,0,0,0);
            if(!scene.hasChunkAt(pos))return new OpeningFacilities(false,"入门院设施所在区块尚未加载，请先走近入门院再来",0,0,0,0);
            if(!BuiltInRegistries.BLOCK.getKey(scene.getBlockState(pos).getBlock()).toString().equals(entry.getValue()))
                return new OpeningFacilities(false,"测灵碑或传承碑实物缺失",0,0,0,0);
        }
        var work=openingDefinition.work();var center=QianzhenScene.position(scene,work.target()).map(BlockPos::above).orElse(null);
        if(center==null)return new OpeningFacilities(false,"旧场景没有学徒药圃",0,0,0,0);
        int soil=0,empty=0,mature=0,growing=0;
        // Facility points at actual soil; work cells are one block above, on this exact layer.
        for(int x=-work.radius();x<=work.radius();x++)for(int z=-work.radius();z<=work.radius();z++) {
            var pos=center.offset(x,0,z);
            if(center.distSqr(pos)>(double)work.radius()*work.radius()||!scene.hasChunkAt(pos))continue;
            if(!(scene.getBlockState(pos.below()).getBlock() instanceof com.example.myvillage.block.SpiritSoilBlock))continue;
            soil++;var block=scene.getBlockState(pos);
            if(block.isAir())empty++;
            else if(block.getBlock() instanceof com.example.myvillage.block.SpiritCropBlock crop) {
                if(crop.isMaxAge(block))mature++;else growing++;
            }
        }
        boolean ready=empty+mature+growing>=work.count();
        return new OpeningFacilities(ready,ready?"":("可持续补种的真实灵土不足 "+work.count()+" 格"),soil,empty,mature,growing);
    }
    public record Target(String dimension,BlockPos position,int personId,String label) {}
    /** Read-only marker target. An unaccepted opening is revealed only by explicit entrance tracking. */
    public static Optional<Target> target(ServerPlayer p) {
        if(definition==null||openingDefinition==null)return Optional.empty();
        var progress=get(p);var d=current(p);var scene=sceneLevel(p).orElse(null);
        if(scene==null||progress.completed()||progress.accepted()&&progress.definitionVersion()!=d.version())return Optional.empty();
        if(!progress.accepted()&&!com.example.myvillage.story.entrance.QianzhenEntrance.tracking(p))return Optional.empty();
        if(progress.accepted()&&(!progress.site().equals(QianzhenScene.at(scene).orElseThrow().id())
                ||!progress.dimension().equals(scene.dimension().location().toString())))return Optional.empty();
        String key=progress.accepted()?d.tracking(progress,QianzhenActors.returned(scene),QianzhenScene.regulated(scene)).target():"guide";
        if(key.isEmpty())return Optional.empty();
        boolean actor=d.roles().containsKey(key);
        var pos=actor?QianzhenActors.position(scene,key):QianzhenScene.position(scene,key);
        if(pos.isEmpty())return Optional.empty();
        int person=actor?QianzhenActors.personId(scene,key):-1;
        String label=actor?WorldSimRuntime.sim().flatMap(sim->sim.person(person)).map(v->v.name()).orElse(""):switch(key) {
            case "testing_stele" -> "测灵碑";
            case "inheritance_stele" -> "吐纳传承碑";
            case "apprentice_plot" -> "学徒药圃";
            default -> d.tracking(progress,QianzhenActors.returned(scene),QianzhenScene.regulated(scene)).objective();
        };
        // Narrative objectives may be longer than the marker's bounded short label.
        if(label.length()>64)label=label.substring(0,64);
        return Optional.of(new Target(scene.dimension().location().toString(),pos.get(),person,label));
    }
    public static void sync(ServerPlayer p) {
        if(definition==null||openingDefinition==null)return;
        reconcileOpening(p);
        var progress=get(p);var d=current(p);var scene=sceneLevel(p).orElse(null);
        boolean available=scene!=null&&(progress.accepted()||com.example.myvillage.story.entrance.QianzhenEntrance.tracking(p));
        var tracking=d.tracking(progress,scene!=null&&QianzhenActors.returned(scene),scene!=null&&QianzhenScene.regulated(scene));
        if(progress.accepted()&&progress.definitionVersion()!=d.version())tracking=new StoryDefinition.Tracking("行迹版本暂不兼容，请保留现有记录","","");
        if(!progress.accepted())tracking=new StoryDefinition.Tracking("药庐正在招灵草学徒","向程砚打听，或亲自去药庐找沈岚；是否接受由你决定。","guide");
        String objective=tracking.objective();
        if(StoryOpening.active(progress)&&progress.stage().equals("planting"))objective+="（"+StoryOpening.planted(progress)+" / "+d.work().count()+"）";
        var target=target(p).orElse(null);
        PacketDistributor.sendToPlayer(p,new StorySnapshotPayload(progress.revision(),available,progress.accepted(),progress.completed(),d.id(),d.title(),objective,tracking.hint(),
                target==null?"":target.dimension(),target==null?BlockPos.ZERO:target.position(),progress.journal(),target==null?-1:target.personId(),target==null?"":target.label()));
    }
    private static boolean experienced(com.example.myvillage.cultivation.CultivationProfile profile) {
        return StoryOpening.experienced(profile.spiritualRoot().isPresent(),
                !profile.realmId().equals(com.example.myvillage.cultivation.data.ModCultivationRegistries.MORTAL_REALM_ID),profile.cultivationProgress(),
                profile.learnedTechniques().getOrDefault(com.example.myvillage.cultivation.data.ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID,
                        new com.example.myvillage.cultivation.TechniqueProgress(0)).masteryPoints());
    }
    /** Accepted v2 only, on the server thread. No inventory/cultivation writes and no dialogue popup. */
    private static boolean reconcileOpening(ServerPlayer p) {
        if(openingDefinition==null||!validPlayer(p)||!StoryOpening.active(get(p)))return false;
        var original=get(p);var profile=com.example.myvillage.cultivation.CultivationService.getProfile(p);
        var facts=new StoryOpening.Facts(profile.spiritualRoot().isPresent(),com.example.myvillage.cultivation.fate.FateService.state(p).confirmed(),
                profile.learnedTechniques().containsKey(com.example.myvillage.cultivation.data.ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID));
        var replacement=StoryOpening.reconcile(openingDefinition,original,facts);
        if(replacement==original)return false;
        p.setData(StoryAttachments.PROGRESS,replacement);SESSIONS.forget(p.getUUID());return true;
    }
    /** Cultivation calls this only after its real successful normal/spirit settlement, never study. */
    public static void recordMeditationSettlement(ServerPlayer p) {
        if(definition==null||openingDefinition==null||!validPlayer(p))return;
        reconcileOpening(p);
        var original=get(p);
        // Personal practice can happen at home or in another dimension; the accepted site stays bound.
        var replacement=StoryOpening.recordBreath(original);
        if(replacement!=original) {
            p.setData(StoryAttachments.PROGRESS,replacement);SESSIONS.forget(p.getUUID());
            try {
                message(p,"这一回吐纳已有所得。收功后可找沈岚领种苗，闲下来再去也无妨。");sync(p);
            }catch(RuntimeException notificationFailure) {
                // The cultivation settlement and story receipt are committed; presentation cannot roll them back.
                LOGGER.warn("Opening practice receipt committed for {}, but notification failed",p.getUUID(),notificationFailure);
            }
        }
    }
    private record PlantAttempt(StoryOpening.PlantWitness<net.neoforged.bus.api.ICancellableEvent> witness,
            ServerPlayer player,ServerLevel level,BlockPos position,net.minecraft.world.level.block.state.BlockState placed,String site) {}
    private static final List<PlantAttempt> PENDING_PLANTS=new ArrayList<>();
    private static void laterPlantChange(net.minecraft.world.level.LevelAccessor level,BlockPos pos,net.neoforged.bus.api.ICancellableEvent event) {
        for(var attempt:PENDING_PLANTS)if(attempt.level()==level&&(attempt.position().equals(pos)||attempt.position().below().equals(pos)))
            attempt.witness().changedAfter(event);
    }
    private static void broken(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        laterPlantChange(event.getLevel(),event.getPos(),event);
    }
    private static void placed(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        // Observe all subsequent placements, even by players who are not apprentices or are in creative.
        // Only pending crop/soil cells are tracked; no global block ownership/history is introduced.
        if(event instanceof net.neoforged.neoforge.event.level.BlockEvent.EntityMultiPlaceEvent multi)
            for(var snapshot:multi.getReplacedBlockSnapshots())laterPlantChange(event.getLevel(),snapshot.getPos(),event);
        else laterPlantChange(event.getLevel(),event.getPos(),event);
        if(definition==null||openingDefinition==null||event.isCanceled()||!(event.getEntity() instanceof ServerPlayer p)
                ||!(event.getLevel() instanceof ServerLevel level)||!validPlayer(p)||p.isCreative())return;
        var progress=get(p);var d=current(p);var work=d.work();
        if(!StoryOpening.active(progress)||!progress.stage().equals("planting")||!work.enabled()||!boundHere(p)
                ||!progress.receipts().contains(StoryOpening.SUPPLIES)||StoryOpening.planted(progress)>=work.count())return;
        var item=com.example.myvillage.sim.runtime.commerce.CommerceInventory.item(work.item());
        if(!(item instanceof net.minecraft.world.item.BlockItem seed)||!event.getPlacedBlock().is(seed.getBlock())
                ||!event.getBlockSnapshot().getState().isAir()
                ||event.getPlacedBlock().getValue(com.example.myvillage.block.SpiritCropBlock.AGE)!=0)return;
        var center=QianzhenScene.position(level,work.target()).map(BlockPos::above).orElse(null);var pos=event.getPos();
        if(center==null||center.getY()!=pos.getY()||center.distSqr(pos)>(double)work.radius()*work.radius()||!p.canInteractWithBlock(pos,0)
                ||!(level.getBlockState(pos.below()).getBlock() instanceof com.example.myvillage.block.SpiritSoilBlock))return;
        if(PENDING_PLANTS.stream().filter(a->a.player()==p).count()>=work.count())return;
        // The event fires before vanilla finishes/cancellation unwinds. Verify the real final block at tick end.
        PENDING_PLANTS.add(new PlantAttempt(new StoryOpening.PlantWitness<>(event),p,level,pos.immutable(),event.getPlacedBlock(),progress.site()));
    }
    private static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        var pending=new ArrayList<>(PENDING_PLANTS);PENDING_PLANTS.clear();
        for(var attempt:pending) {
            var p=attempt.player();var pos=attempt.position();var level=attempt.level();
            if(!attempt.witness().intact(net.neoforged.bus.api.ICancellableEvent::isCanceled)||!validPlayer(p)||p.serverLevel()!=level||!boundHere(p)||!level.hasChunkAt(pos)
                    ||!level.getBlockState(pos).equals(attempt.placed())||!get(p).site().equals(attempt.site())
                    ||!(level.getBlockState(pos.below()).getBlock() instanceof com.example.myvillage.block.SpiritSoilBlock))continue;
            var original=get(p);var replacement=StoryOpening.recordPlant(original,pos.asLong(),current(p).work().count());
            if(replacement==original)continue;
            p.setData(StoryAttachments.PROGRESS,replacement);SESSIONS.forget(p.getUUID());
            message(p,"已亲手补种 "+StoryOpening.planted(replacement)+" / "+current(p).work().count()+" 格灵叶。");sync(p);
        }
        if(definition!=null&&openingDefinition!=null)
            for(var p:event.getServer().getPlayerList().getPlayers())if(validPlayer(p)) {
                boolean changed=reconcileOpening(p);
                if(changed||event.getServer().getTickCount()%20==0)sync(p);
            }
    }
    private static void message(ServerPlayer p,String s){p.displayClientMessage(Component.literal(s),false);}
    private static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){SESSIONS.forget(p.getUUID());sync(p);}}
    private static void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p){SESSIONS.forget(p.getUUID());sync(p);}}
    private static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p){SESSIONS.forget(p.getUUID());sync(p);}}
    private static void logout(PlayerEvent.PlayerLoggedOutEvent e){SESSIONS.forget(e.getEntity().getUUID());}
    private static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)SESSIONS.forget(p.getUUID());}
    private static void stopping(ServerStoppingEvent e){SESSIONS.clear();PENDING_PLANTS.clear();definition=null;openingDefinition=null;}
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
        out.addProperty("active_definition_version",definition==null||openingDefinition==null?-1:current(p).version());
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
        out.add("npc",actors);
        var work=new JsonObject();work.addProperty("planted",StoryOpening.planted(progress));
        work.addProperty("required",openingDefinition==null?0:openingDefinition.work().count());
        work.addProperty("supplies",progress.receipts().contains(StoryOpening.SUPPLIES));
        work.addProperty("breath_settled",progress.receipts().contains(StoryOpening.BREATH));
        work.addProperty("experienced",progress.receipts().contains(StoryOpening.EXPERIENCED));out.add("opening",work);
        var facilities=openingFacilities(p);var readyJson=new JsonObject();readyJson.addProperty("ready",facilities.ready());readyJson.addProperty("reason",facilities.reason());
        readyJson.addProperty("soil",facilities.soil());readyJson.addProperty("empty",facilities.empty());readyJson.addProperty("mature",facilities.mature());readyJson.addProperty("growing",facilities.growing());out.add("opening_facilities",readyJson);
        var target=target(p).orElse(null);var targetJson=new JsonObject();
        targetJson.addProperty("dimension",target==null?"":target.dimension());targetJson.addProperty("personId",target==null?-1:target.personId());
        targetJson.addProperty("label",target==null?"":target.label());targetJson.add("position",target==null?JsonNull.INSTANCE:posJson(target.position()));out.add("target",targetJson);
        return out.toString();
    }
    private static JsonElement posJson(BlockPos p){var o=new JsonObject();o.addProperty("x",p.getX());o.addProperty("y",p.getY());o.addProperty("z",p.getZ());return o;}
}
