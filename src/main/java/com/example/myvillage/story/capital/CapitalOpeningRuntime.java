package com.example.myvillage.story.capital;

import com.example.myvillage.block.SpiritCropBlock;
import com.example.myvillage.block.SpiritSoilBlock;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.TechniqueProgress;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.root.SpiritualRootAwakeningService;
import com.example.myvillage.cultivation.technique.TechniqueInheritanceService;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.npc.NpcDialogue;
import com.example.myvillage.npc.NpcDialogueOptions.Page;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.life.LifeSite;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.sim.runtime.commerce.CommerceInventory;
import com.example.myvillage.story.*;
import com.example.myvillage.story.net.StoryDialoguePayload.Option;
import com.example.myvillage.worldplan.capital.CapitalCity;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Voluntary public-capital opening. WorldSim owns residents; attachments own personal receipts. */
public final class CapitalOpeningRuntime {
    private static CapitalOpeningRules rules;
    private static final List<PlantAttempt> PENDING = new ArrayList<>();
    private CapitalOpeningRuntime() {}
    public static void register() {
        NeoForge.EVENT_BUS.addListener(CapitalOpeningRuntime::started);
        NeoForge.EVENT_BUS.addListener(CapitalOpeningRuntime::placed);
        NeoForge.EVENT_BUS.addListener(CapitalOpeningRuntime::broken);
        NeoForge.EVENT_BUS.addListener(CapitalOpeningRuntime::tick);
        NeoForge.EVENT_BUS.addListener(CapitalOpeningRuntime::stopping);
    }
    private static void started(ServerStartedEvent event) {
        rules = null; PENDING.clear();
        try (var stream = event.getServer().getResourceManager().open(ResourceLocation.fromNamespaceAndPath("myvillage", CapitalOpeningRules.RESOURCE));
                var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var loaded = CapitalOpeningRules.read(reader);
            if (!(CommerceInventory.item(loaded.seedItem()) instanceof BlockItem seed) || !(seed.getBlock() instanceof SpiritCropBlock)
                    || !(CommerceInventory.item(loaded.soilItem()) instanceof BlockItem soil) || !(soil.getBlock() instanceof SpiritSoilBlock))
                throw new IllegalArgumentException("capital_opening_requires_spirit_crop_and_soil");
            rules = loaded;
        } catch (Exception failure) { throw new IllegalStateException("首城入门配置加载失败", failure); }
    }
    private static void stopping(ServerStoppingEvent event) { rules = null; PENDING.clear(); }
    public static boolean reserve(MinecraftServer server, WorldSim sim, CapitalCity city) {
        if (rules == null) return false;
        boolean changed = sim.ensureCapitalResidents(city.cityId(), rules.residents(city.regionId()));
        if (changed) WorldSimSavedData.get(server.overworld()).setDirty();
        return changed;
    }
    public static CapitalOpeningProgress progress(ServerPlayer p) {
        return p.getExistingData(StoryAttachments.CAPITAL_OPENING).orElse(CapitalOpeningProgress.EMPTY);
    }
    private static CapitalCity firstCity(MinecraftServer server) {
        return WorldPlanRuntime.generator(server.overworld()).filter(g -> g.plan().rules().capitals != null)
                .flatMap(g -> g.plan().capitalOf(g.plan().spawn().regionId())).orElse(null);
    }
    private static boolean validPlayer(ServerPlayer p) {
        return p != null && !p.isRemoved() && p.isAlive() && !p.isSpectator() && p.getServer() != null
                && p.getServer().getPlayerList().getPlayer(p.getUUID()) == p;
    }
    private record Host(WorldSim sim, LifeSite site, CapitalCity city, String role) {}
    private static Host host(ServerPlayer p, NpcEntity npc) {
        if (rules == null || !validPlayer(p) || !npc.isLedgerAvatar() || p.level() != p.getServer().overworld()) return null;
        var city = firstCity(p.getServer()); var sim = WorldSimRuntime.sim().orElse(null);
        if (city == null || sim == null) return null;
        var life = sim.life(npc.ledgerPersonId()).orElse(null);
        if (life == null || !life.siteId().equals(city.cityId())
                || sim.capitalResident(city.cityId(), life.role()).orElse(-1) != npc.ledgerPersonId()) return null;
        var site = sim.lifeSite(city.cityId()).orElse(null);
        return site == null ? null : new Host(sim, site, city, life.role());
    }
    public static boolean canDiscuss(ServerPlayer p, NpcEntity npc) { return host(p, npc) != null; }
    private static boolean learned(ServerPlayer p) {
        return CultivationService.getProfile(p).learnedTechniques().containsKey(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID);
    }
    private static boolean experienced(ServerPlayer p) {
        var profile = CultivationService.getProfile(p);
        return StoryOpening.experienced(profile.spiritualRoot().isPresent(),
                !profile.realmId().equals(ModCultivationRegistries.MORTAL_REALM_ID), profile.cultivationProgress(),
                profile.learnedTechniques().getOrDefault(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID, TechniqueProgress.ZERO).masteryPoints());
    }
    private static void reconcile(ServerPlayer p, String site) {
        if (!learned(p) || CultivationService.getProfile(p).spiritualRoot().isEmpty()) return;
        var before = progress(p); var after = before.learned(site, experienced(p));
        if (before != after) p.setData(StoryAttachments.CAPITAL_OPENING, after);
    }
    private static String place(Host h, String id) {
        var p = h.site().place(id).orElseThrow(); return "（" + p.x() + "，" + p.y() + "，" + p.z() + "）";
    }
    private static String person(Host h, String role) {
        return h.sim().capitalResident(h.site().id(), role).flatMap(h.sim()::person).map(p -> p.name()).orElse("当值者");
    }
    private static Page page(List<String> lines, Option... choices) {
        var options = new ArrayList<>(List.of(choices)); options.add(new Option("home", "聊些别的"));
        return new Page(lines, options);
    }
    /** Called only after the shared dialogue has consumed its server-offered one-use capability. */
    public static Optional<Page> interact(ServerPlayer p, NpcEntity npc, String choice) {
        var h = host(p, npc);
        if (h == null || !NpcDialogue.valid(p, npc)) return Optional.empty();
        reconcile(p, h.site().id());
        var state = progress(p);
        if (!state.site().isEmpty() && !state.site().equals(h.site().id())) return Optional.empty();
        if (choice.equals("capital_accept") && h.role().equals("herbalist") && state.stage().equals("new") && !p.isCreative()) {
            var plan = StoryRewards.plan(p.getInventory().items, List.of(
                    new StoryDefinition.Reward(rules.soilItem(), rules.workCount()), new StoryDefinition.Reward(rules.seedItem(), rules.workCount())));
            if (!plan.ready()) return Optional.of(page(List.of("先在背包里腾出位置，我会把灵土和种苗一起交给你。"), new Option("capital_intro", "再谈谈试种")));
            var accepted = state.accept(h.site().id());
            for (int i = 0; i < plan.items().size(); i++) p.getInventory().items.set(i, plan.items().get(i));
            p.setData(StoryAttachments.CAPITAL_OPENING, accepted);
            p.getInventory().setChanged(); p.inventoryMenu.broadcastChanges();
            return Optional.of(workPage(p));
        }
        if (choice.equals("capital_teach") && h.role().equals("herbalist") && state.stage().equals("teaching")
                && state.planted().size() >= rules.workCount()) {
            if (CultivationService.getProfile(p).spiritualRoot().isEmpty()) {
                var awakened = SpiritualRootAwakeningService.awaken(p);
                if (!awakened.success()) return Optional.of(page(List.of("这次未能完成测灵。你的试种记录会保留，稍后可以再来。"), new Option("capital_intro", "查看约定")));
            }
            if (!learned(p) && !TechniqueInheritanceService.inheritBasicBreathing(p).success())
                return Optional.of(page(List.of("灵根已经确认，但这次未能完成吐纳教学。试种记录仍在，下次可以继续。"), new Option("capital_intro", "查看约定")));
            reconcile(p, h.site().id()); CultivationService.syncToClient(p);
            return Optional.of(practicePage());
        }
        if (choice.equals("capital_lodging")) return Optional.of(page(List.of(
                "客栈由" + person(h,"innkeeper") + "当值，柜台在" + place(h,"inn_work") + "。",
                "可以花灵石歇息。若要设置重生点，请亲自使用空床；夜里店家会回住处休息。"), new Option("capital_intro", "继续询问")));
        if (choice.equals("capital_shops")) return Optional.of(page(List.of(
                "药铺的" + person(h,"herbalist") + "在" + place(h,"herbalist_work") + "出售种苗、收购药材和丹药。现货和报价可当面查看。",
                "铁匠" + person(h,"smith") + "在" + place(h,"smith_work") + "当值。先看现货，再决定采料锻造还是花钱购买。"), new Option("capital_intro", "继续询问")));
        if (choice.equals("capital_work")) return Optional.of(page(List.of(
                "药铺的" + person(h,"herbalist") + "正在找人试种灵叶，药铺在" + place(h,"herbalist_work") + "。",
                "先把劳动与报酬谈清楚，再决定接不接。你也可以先种药卖钱，或者自行出城采集。"), new Option("capital_intro", "继续询问")));
        // Unknown/stale capital intents only redisplay the current authoritative page; they never award anything.
        return Optional.of(switch (h.role()) {
            case "guide" -> page(List.of("我是" + person(h,"guide") + "，白天在入城口接待。你可以先安顿下来，再决定往哪里去。",
                    "客栈、药铺和铁匠铺都有人当值。夜里他们会回住处，白天再到店里找会方便一些。"),
                    new Option("capital_lodging", "哪里可以歇脚"), new Option("capital_work", "哪里能找活和学修行"), new Option("capital_shops", "药铺和铁匠在哪里"));
            case "innkeeper" -> page(List.of("我是" + person(h,"innkeeper") + "。需要歇脚可以看看店里的服务。",
                    "想找修行门路，可以问入城口的" + person(h,"guide") + "，或者去药铺找" + person(h,"herbalist") + place(h,"herbalist_work") + "。"),
                    new Option("trade", "查看交易与歇息服务"), new Option("capital_work", "问问药铺的工作"));
            case "smith" -> page(List.of("我是" + person(h,"smith") + "，在此做些修补和锻造生意。手头灵石有限，就先看需要什么，不必急着买齐。",
                    "药铺收药材和丹药。采集或试种有了所得，再来准备工具也不迟。"),
                    new Option("trade", "查看店内现货"), new Option("capital_shops", "问问药铺的位置"));
            default -> apothecaryPage(p);
        });
    }
    private static Page apothecaryPage(ServerPlayer p) {
        return switch (progress(p).stage()) {
            case "working" -> workPage(p);
            case "teaching" -> page(List.of("约好的 " + rules.workCount() + " 格试种都已记下。你若愿意，我现在就替你测灵，再教你基础吐纳。",
                    "已经觉醒的灵根和已有功法都会保留。"), new Option("capital_teach", "现在请教测灵与吐纳"), new Option("trade", "先看看药铺交易"));
            case "practice" -> practicePage();
            case "complete" -> page(List.of("你已经会自行吐纳。往后种出的药材和炼成的丹药，都可以按店里当前的报价出售。",
                    "种苗按周补货。继续种药、炼丹还是出城，由你自己决定。"), new Option("trade", "查看药铺交易"));
            default -> p.isCreative() ? page(List.of("试种协作需要在生存模式下亲手种植。店里的交易仍可照常查看。"), new Option("trade", "查看药铺交易"))
                    : page(List.of("我想试试这一带的灵叶长势。你若愿意，可以领 " + rules.workCount() + " 份灵土和种苗，在城外空地种成一小片药圃。",
                            "请在城界外 " + rules.workRadius() + " 格以内、本州范围内亲手种下 " + rules.workCount() + " 格灵叶。灵土可铺在地表，种苗种在灵土上。",
                            "完成后回来，我替你测灵并教基础吐纳。材料只领一次，不收费；种下的药圃由你继续照看，是否接下由你决定。"),
                            new Option("capital_accept", "接受试种并领取材料"), new Option("trade", "先看看药铺交易"));
        };
    }
    private static Page workPage(ServerPlayer p) {
        return page(List.of("已亲手种下 " + progress(p).planted().size() + " / " + rules.workCount() + " 格灵叶。",
                "请在城界外 " + rules.workRadius() + " 格以内、本州范围内找空地，把种苗种在灵土上。每处只计一次，采来的成品不抵试种。",
                "材料只发一次；若有遗失，可以购买种苗，或收获后留种继续。完成后回来找我。"), new Option("trade", "查看药铺交易"));
    }
    private static Page practicePage() {
        return page(List.of("基础吐纳已经学会。找安全的地方开始普通打坐，默认按 V；想收功时默认按 X。按键改过的话，以控制设置为准。",
                "正常完成一次修炼结算后，这段入门便已完成。以后若想选择天命，可使用测灵碑。"), new Option("trade", "查看药铺交易"));
    }

    private record PlantAttempt(StoryOpening.PlantWitness<ICancellableEvent> witness, ServerPlayer player,
            ServerLevel level, BlockPos pos, BlockState crop, String site) {}
    private static void changed(net.minecraft.world.level.LevelAccessor level, BlockPos pos, ICancellableEvent event) {
        for (var a : PENDING) if (a.level() == level && (a.pos().equals(pos) || a.pos().below().equals(pos))) a.witness().changedAfter(event);
    }
    private static void broken(BlockEvent.BreakEvent e) { changed(e.getLevel(), e.getPos(), e); }
    private static boolean workPosition(MinecraftServer server, CapitalCity city, BlockPos pos) {
        return !city.contains(pos.getX(), pos.getZ(), 0) && city.distance(pos.getX(), pos.getZ()) <= rules.workRadius()
                && WorldPlanRuntime.generator(server.overworld()).map(g -> g.plan().sample(pos.getX(), pos.getZ()).regionId().equals(city.regionId())).orElse(false);
    }
    private static void placed(BlockEvent.EntityPlaceEvent e) {
        if (e instanceof BlockEvent.EntityMultiPlaceEvent multi)
            for (var snapshot : multi.getReplacedBlockSnapshots()) changed(e.getLevel(), snapshot.getPos(), e);
        else changed(e.getLevel(), e.getPos(), e);
        if (rules == null || e.isCanceled() || !(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level)
                || !validPlayer(p) || p.isCreative() || level != p.getServer().overworld()) return;
        var state = progress(p); var city = firstCity(p.getServer());
        if (!state.stage().equals("working") || city == null || !state.site().equals(city.cityId())) return;
        var seed = (BlockItem)CommerceInventory.item(rules.seedItem());
        if (!e.getPlacedBlock().is(seed.getBlock()) || e.getPlacedBlock().getValue(SpiritCropBlock.AGE) != 0
                || !e.getBlockSnapshot().getState().isAir() || !p.canInteractWithBlock(e.getPos(), 0)
                || !workPosition(p.getServer(), city, e.getPos()) || !(level.getBlockState(e.getPos().below()).getBlock() instanceof SpiritSoilBlock)
                || PENDING.stream().filter(a -> a.player() == p).count() >= rules.workCount()) return;
        PENDING.add(new PlantAttempt(new StoryOpening.PlantWitness<>(e), p, level, e.getPos().immutable(), e.getPlacedBlock(), state.site()));
    }
    private static void tick(ServerTickEvent.Post e) {
        var pending = new ArrayList<>(PENDING); PENDING.clear();
        for (var a : pending) {
            var p = a.player(); var city = firstCity(e.getServer());
            if (rules == null || !a.witness().intact(ICancellableEvent::isCanceled) || !validPlayer(p) || p.isCreative()
                    || p.serverLevel() != a.level() || !progress(p).site().equals(a.site()) || city == null
                    || !workPosition(e.getServer(), city, a.pos()) || !a.level().hasChunkAt(a.pos())
                    || !a.level().getBlockState(a.pos()).equals(a.crop())
                    || !(a.level().getBlockState(a.pos().below()).getBlock() instanceof SpiritSoilBlock)) continue;
            var before = progress(p); var after = before.plant(a.pos().asLong(), rules.workCount());
            if (before == after) continue;
            p.setData(StoryAttachments.CAPITAL_OPENING, after);
            p.displayClientMessage(Component.literal("首城试种：" + after.planted().size() + " / " + rules.workCount()
                    + (after.stage().equals("teaching") ? "。可以回药铺请教吐纳了。" : "。")), false);
        }
    }
    /** Only called after an ordinary/spirit-stone meditation actually commits its settlement. */
    public static void recordMeditationSettlement(ServerPlayer p) {
        if (rules == null || !validPlayer(p) || !learned(p)) return;
        var before = progress(p); var after = before.practiced();
        if (before == after) return;
        p.setData(StoryAttachments.CAPITAL_OPENING, after);
        p.displayClientMessage(Component.literal("这次吐纳已有所得。首城入门已经完成，往后可以自行修炼。"), false);
    }
}
