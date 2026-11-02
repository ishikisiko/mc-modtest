package com.example.myvillage.sim.runtime.avatar;

import com.example.myvillage.sect.SectCourtyard;
import com.example.myvillage.sect.SectGenerator;
import com.example.myvillage.sim.SectView;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.sim.runtime.WorldSimServerConfig;
import com.example.myvillage.sim.runtime.WorldSimText;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code /myvillage world sect <id> build [here]}: builds a sect's compound (山门) where the ledger
 * puts its gate and records the realization. The ledger stays the authority: the build follows the
 * sect's record, never the other way round, and {@code here} moves the ledger's gate first.
 *
 * <p>默认入口高度与自动施工同样采样生成器原始地表；重建不受已有屋面或植被影响。
 * 显式 {@code here} 使用管理员请求的位置。当前蓝图身份由目录决定，施工同步执行。
 */
public final class GateBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(GateBuilder.class);

    private GateBuilder() {
    }

    /** Seed of a sect's compound in this world. */
    public static long seed(long worldSeed, int sectId) {
        return AvatarPlanner.mix(worldSeed ^ AvatarPlanner.mix(0x5EC7_0000L + sectId));
    }

    /** 当前目录中的宗门蓝图身份。 */
    public static String variant(long worldSeed, int sectId) {
        List<String> variants = SectCourtyard.variants();
        return variants.get((int) Math.floorMod(AvatarPlanner.mix(seed(worldSeed, sectId) ^ 0x7A11L), (long) variants.size()));
    }

    public static int build(CommandSourceStack source, int sectId, boolean here) {
        Optional<WorldSim> active = WorldSimRuntime.sim();
        if (active.isEmpty()) {
            String reason = WorldSimRuntime.inactiveReason();
            source.sendFailure(WorldSimText.line("inactive", reason == null ? "?" : reason));
            return 0;
        }
        WorldSim sim = active.get();
        Optional<SectView> found = sim.sect(sectId);
        if (found.isEmpty()) {
            source.sendFailure(WorldSimText.line("sect.not_found", String.valueOf(sectId)));
            return 0;
        }
        SectView sect = found.get();
        if (!sect.state().equals("active")) {
            source.sendFailure(WorldSimText.line("sect_build.destroyed", sect.name()));
            return 0;
        }
        ServerLevel level = source.getLevel();
        if (level.dimension() != Level.OVERWORLD) {
            source.sendFailure(WorldSimText.line("sect_build.overworld_only"));
            return 0;
        }
        var planned=com.example.myvillage.worldplan.runtime.WorldPlanRuntime.generator(level);
        if(planned.isPresent()){
            if(here){source.sendFailure(net.minecraft.network.chat.Component.literal("宗门使用创建世界时预留的地块，不能临时迁移"));return 0;}
            var plot=planned.get().plan().sectAtGate(sect.gateX(),sect.gateZ());
            if(plot.isEmpty()){source.sendFailure(net.minecraft.network.chat.Component.literal("宗门没有对应的世界生成地块"));return 0;}
            var site=plot.get();
            for(int z=Math.floorDiv(site.z()-site.margin(),16);z<=Math.floorDiv(site.z()+site.depth()+site.margin()-1,16);z++)
                for(int x=Math.floorDiv(site.x()-site.margin(),16);x<=Math.floorDiv(site.x()+site.width()+site.margin()-1,16);x++)level.getChunk(x,z);
            GateRealizer.bindLoaded(level,sim);
            source.sendSuccess(()->net.minecraft.network.chat.Component.literal("已加载宗门预留地块；已生成区块不会重新施工"),true);
            return 1;
        }
        BlockPos anchor;
        if (here) {
            anchor = BlockPos.containing(source.getPosition());
            try {
                sim.moveGate(sectId, anchor.getX(), anchor.getZ());
            } catch (IllegalArgumentException ex) {
                source.sendFailure(WorldSimText.line("sect_build.outside", sect.name(), anchor.getX(), anchor.getZ()));
                return 0;
            }
            sim.markGateRealized(sectId, false);
            markLedgerDirty(level);
            final BlockPos movedAnchor=anchor;
            String region = WorldSimRuntime.regionName(sim.sect(sectId).map(SectView::regionId).orElse(""));
            source.sendSuccess(() -> WorldSimText.line("sect_build.moved", sect.name(), movedAnchor.getX(), movedAnchor.getZ(),
                    region), true);
        } else {
            int x = sect.gateX();
            int z = sect.gateZ();
            anchor = new BlockPos(x, SectGenerator.generatorSurface(level,x,z), z);
        }
        anchor = SectGenerator.fitAnchor(level,anchor);
        final BlockPos buildAnchor=anchor;
        long seed = seed(level.getSeed(), sectId);
        String variant = variant(level.getSeed(), sectId);
        source.sendSuccess(() -> WorldSimText.line("sect_build.start", sect.name(), buildAnchor.getX(), buildAnchor.getY(),
                buildAnchor.getZ(), String.valueOf(seed), variant), true);
        long t0 = System.nanoTime();
        int built;
        try {
            built = SectGenerator.generateForcedAt(source, seed, variant, anchor);
        } catch (RuntimeException ex) {
            LOGGER.error("Building the compound of sect {} at {} failed", sectId, anchor, ex);
            built = 0;
        }
        long seconds = Math.round((System.nanoTime() - t0) / 1e9);
        if (built <= 0) {
            source.sendFailure(WorldSimText.line("sect_build.failed", sect.name()));
            return 0;
        }
        try { placeShelves(level, sectId, seed, anchor, variant); }
        catch (RuntimeException ex) {
            LOGGER.error("宗门经架绑定失败，未提交建造收据",ex);
            source.sendFailure(WorldSimText.line("sect_build.failed",sect.name()));return 0;
        }
        GateRealizations.get(level).put(new GateRealizations.Gate(sectId, anchor, seed, variant, sim.day(),com.example.myvillage.sect.SectBlueprint.get(variant).fingerprint));
        sim.markGateRealized(sectId, true);
        markLedgerDirty(level);
        WorldSimAvatars.gateChanged(sectId);
        LOGGER.info("World sim: built the compound of sect {} ({}) at {} {} {}, seed {}, variant {}, in {} s",
                sectId, sect.name(), anchor.getX(), anchor.getY(), anchor.getZ(), seed, variant, seconds);
        source.sendSuccess(() -> WorldSimText.line("sect_build.done", sect.name(), buildAnchor.getX(), buildAnchor.getY(),
                buildAnchor.getZ(), seconds, WorldSimServerConfig.avatarSpawnRadius()), true);
        return 1;
    }

    /** 经架全部成功绑定后才允许提交宗门建造收据。 */
    static void placeShelves(ServerLevel level, int sectId, long seed, BlockPos anchor, String variant) {
        var shelves=ScriptureShelves.place(level, sectId, seed, anchor, variant);
        if (shelves.size()!=com.example.myvillage.sect.SectBlueprint.get(variant).shelves.size())
            throw new IllegalStateException("宗门经架没有完整绑定，不能记录为建造完成");
    }

    /** The ledger payload changed outside a settled day; make sure the next world save writes it. */
    private static void markLedgerDirty(ServerLevel overworld) {
        WorldSimSavedData.get(overworld).setDirty();
    }
}
