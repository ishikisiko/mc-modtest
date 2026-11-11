package com.example.myvillage.estate;

import com.example.myvillage.entity.ModEntities;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.npc.NpcDialogueOptions.Page;
import com.example.myvillage.spiritstone.SpiritStoneSources;
import com.example.myvillage.spiritstone.SpiritStoneStorage;
import com.example.myvillage.story.net.StoryDialoguePayload.Option;
import com.example.myvillage.worldplan.capital.CapitalCity;
import com.example.myvillage.worldplan.runtime.PlannedChunkGenerator;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** One server authority for the gate steward, payment, title and complete private XZ column. */
public final class EstateRuntime {
    public record Site(CapitalCity city, EstateDefinition definition, UUID stewardId) {
        public String key() { return city.cityId() + "/" + definition.id(); }
        public BlockPos world(BlockPos local) { return local.offset(city.x(), city.originY(), city.z()); }
        public EstateBounds bounds() {
            var b = definition.bounds();
            return new EstateBounds(b.minX()+city.x(), b.minZ()+city.z(), b.maxX()+city.x(), b.maxZ()+city.z());
        }
    }
    private static final Map<ServerLevel, List<Site>> SITES = new WeakHashMap<>();
    private static final Map<UUID, Long> NOTICES = new HashMap<>();
    private EstateRuntime() { }
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EstateRuntime::tick);
        NeoForge.EVENT_BUS.addListener(EstateRuntime::playerTick);
        NeoForge.EVENT_BUS.addListener(EstateRuntime::login);
        NeoForge.EVENT_BUS.addListener(EstateRuntime::logout);
        NeoForge.EVENT_BUS.addListener(EstateRuntime::respawn);
        NeoForge.EVENT_BUS.addListener(EstateRuntime::rightClick);
        NeoForge.EVENT_BUS.addListener(EstateRuntime::leftClick);
        NeoForge.EVENT_BUS.addListener(EstateRuntime::commands);
        NeoForge.EVENT_BUS.addListener(EstateRuntime::stopped);
    }
    public static List<Site> sites(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD || !(level.getChunkSource().getGenerator() instanceof PlannedChunkGenerator generator))
            return List.of();
        return SITES.computeIfAbsent(level, ignored -> {
            var result = new ArrayList<Site>();
            for (var city : generator.plan().cities()) for (var definition : city.bundle().estates) {
                String key = level.getSeed()+":"+city.cityId()+":"+city.x()+":"+city.z()+":"+definition.id();
                result.add(new Site(city, definition, UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8))));
            }
            return List.copyOf(result);
        });
    }
    public static Optional<Site> steward(NpcEntity npc) {
        if (!(npc.level() instanceof ServerLevel level) || npc.isLedgerAvatar()) return Optional.empty();
        return sites(level).stream().filter(s -> s.stewardId().equals(npc.getUUID())
                && npc.distanceToSqr(Vec3.atBottomCenterOf(s.world(s.definition.steward()))) < 1).findFirst();
    }
    public static String dialogueIdentity(NpcEntity npc) {
        return steward(npc).map(s -> s.key()+":"+Optional.ofNullable(EstateSavedData.get(npc.getServer()).titles.get(s.key()))
                .map(t -> t.owner().toString()).orElse("unsold")).orElse("");
    }
    public static Optional<Page> interact(ServerPlayer player, NpcEntity npc, String option) {
        var found = steward(npc); if (found.isEmpty()) return Optional.empty();
        Site site = found.get(); var d = site.definition(); var data = EstateSavedData.get(player.getServer());
        if (option.equals("estate_buy")) {
            var result = data.titles.purchase(site.key(), player.getUUID(), player.getGameProfile().getName(), () -> {
                // Copies include bags/accessories; failure leaves every original source unchanged.
                var access = SpiritStoneSources.open(player); var copies = access.copies();
                var currency = BuiltInRegistries.ITEM.get(d.currency());
                return SpiritStoneStorage.remove(copies, stack -> stack.is(currency), d.price()) && access.commit(copies);
            });
            if (result == EstateTitles.Result.PURCHASED) {
                data.setDirty(); player.containerMenu.broadcastChanges();
                return Optional.of(page("房契已经记在道友名下。整座庄园现已向所有人开放。"));
            }
            if (result == EstateTitles.Result.PAYMENT_FAILED)
                return Optional.of(new Page(List.of("购房需要 "+d.price()+" 枚"+d.currencyName()+"。请备齐后再来，灵石袋中的灵石也可支付。"),
                        List.of(new Option("estate_home", "查看房产"), new Option("close", "告辞"))));
        }
        var title = data.titles.get(site.key());
        if (title == null) return Optional.of(new Page(List.of("我是这座庄园的管家。"+d.name()+"尚未出售。",
                "房价为 "+d.price()+" 枚"+d.currencyName()+"，一次付清。购房后，房契归你，庄园向所有人开放。",
                "办好房契前，请在院外等候。"), List.of(new Option("estate_confirm", "购买庄园"), new Option("close", "告辞"))));
        return Optional.of(page(title.owner().equals(player.getUUID()) ? "房契记在道友名下，庄园已向所有人开放。" : "这座庄园已售出，现已向所有人开放，请进。"));
    }
    public static Optional<Page> confirm(NpcEntity npc) {
        return steward(npc).map(s -> new Page(List.of("确认支付 "+s.definition.price()+" 枚"+s.definition.currencyName()+"，购买"+s.definition.name()+"？",
                "背包和灵石袋可共同支付。房契将记在道友名下，庄园向所有人开放。"),
                List.of(new Option("estate_buy", "确认购买"), new Option("estate_home", "返回"), new Option("close", "告辞"))));
    }
    private static Page page(String line) { return new Page(List.of(line), List.of(new Option("estate_home", "查看房产"), new Option("close", "告辞"))); }
    private static boolean allowed(ServerPlayer player, Site site) {
        return EstateSavedData.get(player.getServer()).titles.openToPlayers(site.key());
    }
    public static boolean blocked(ServerPlayer player, ServerLevel destination, double x, double z) {
        double radius = player.getBbWidth()/2.0;
        for (var site : sites(destination)) if (site.bounds().overlaps(x,z,radius) && !allowed(player,site)) { notice(player); return true; }
        return false;
    }
    /** Called on the network's server thread, after vanilla validates packet coordinates. */
    public static boolean movement(ServerPlayer player, double x, double z) {
        for (var site : sites(player.serverLevel())) if (!allowed(player,site)
                && site.bounds().crosses(player.getX(),player.getZ(),x,z,player.getBbWidth()/2.0)) {
            notice(player); player.stopRiding(); player.setDeltaMovement(Vec3.ZERO);
            if (site.bounds().overlaps(player.getX(),player.getZ(),player.getBbWidth()/2.0)) eject(player,site);
            else player.connection.teleport(player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot());
            return true;
        }
        return false;
    }
    public static void enforce(ServerPlayer player) {
        if (player.isRemoved()) return;
        for (var site : sites(player.serverLevel())) if (!allowed(player,site)
                && site.bounds().overlaps(player.getX(),player.getZ(),player.getBbWidth()/2.0)) {
            notice(player); eject(player,site); return;
        }
    }
    private static void eject(ServerPlayer player, Site site) {
        BlockPos at = site.world(site.definition.exit());
        player.stopRiding(); player.setDeltaMovement(Vec3.ZERO); player.fallDistance=0;
        player.teleportTo(player.serverLevel(),at.getX()+.5,at.getY(),at.getZ()+.5,player.getYRot(),player.getXRot());
    }
    private static void notice(ServerPlayer player) {
        long now = player.serverLevel().getGameTime(); Long last = NOTICES.get(player.getUUID());
        if (last == null || now < last || now-last >= 40) {
            player.displayClientMessage(Component.literal("庄园尚未出售，购房请与大门外的管家交谈。"), true); NOTICES.put(player.getUUID(),now);
        }
    }
    private static void playerTick(PlayerTickEvent.Post e) { if(e.getEntity() instanceof ServerPlayer p) enforce(p); }
    private static void login(PlayerEvent.PlayerLoggedInEvent e) { if(e.getEntity() instanceof ServerPlayer p) enforce(p); }
    private static void respawn(PlayerEvent.PlayerRespawnEvent e) { if(e.getEntity() instanceof ServerPlayer p) enforce(p); }
    private static void logout(PlayerEvent.PlayerLoggedOutEvent e) { NOTICES.remove(e.getEntity().getUUID()); }
    private static void rightClick(PlayerInteractEvent.RightClickBlock e) {
        if(e.getEntity() instanceof ServerPlayer p && blocked(p,p.serverLevel(),e.getPos().getX()+.5,e.getPos().getZ()+.5)) e.setCanceled(true);
    }
    private static void leftClick(PlayerInteractEvent.LeftClickBlock e) {
        if(e.getEntity() instanceof ServerPlayer p && blocked(p,p.serverLevel(),e.getPos().getX()+.5,e.getPos().getZ()+.5)) e.setCanceled(true);
    }
    private static void tick(ServerTickEvent.Post e) {
        if(e.getServer().getTickCount()%20!=0)return;
        ServerLevel level=e.getServer().overworld();
        for(var site:sites(level)) {
            BlockPos at=site.world(site.definition.steward());
            if(!level.isPositionEntityTicking(at))continue;
            Entity existing=level.getEntity(site.stewardId());
            NpcEntity npc;
            if(existing==null) {
                npc=ModEntities.CULTIVATOR.get().create(level); if(npc==null)throw new IllegalStateException("无法创建庄园管家");
                npc.setUUID(site.stewardId()); npc.setPersistenceRequired(); npc.setNoAi(true); npc.setInvulnerable(true);
                npc.setCustomName(Component.literal(site.definition.stewardName())); npc.setCustomNameVisible(true);
                npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,site.definition.yaw(),0);
                if(!level.addFreshEntity(npc))throw new IllegalStateException("庄园管家加入失败");
            } else if(existing instanceof NpcEntity n) npc=n; else throw new IllegalStateException("庄园管家身份被其他实体占用");
            npc.setNoAi(true); npc.setInvulnerable(true); npc.setDeltaMovement(Vec3.ZERO);
            if(npc.distanceToSqr(Vec3.atBottomCenterOf(at))>.01)npc.teleportTo(at.getX()+.5,at.getY(),at.getZ()+.5);
        }
    }
    private static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("myvillage").then(Commands.literal("estate").executes(c -> {
            for(var site:sites(c.getSource().getServer().overworld())) {
                var at=site.world(site.definition.steward()); var title=EstateSavedData.get(c.getSource().getServer()).titles.get(site.key());
                c.getSource().sendSuccess(() -> Component.literal(site.definition.name()+"：管家位于 "+at.toShortString()+"；"
                        +(title==null?"待售，"+site.definition.price()+" 枚"+site.definition.currencyName():"已购，当前向所有人开放")),false);
            }
            return 1;
        })));
    }
    private static void stopped(ServerStoppedEvent e) { SITES.clear(); NOTICES.clear(); }
}
