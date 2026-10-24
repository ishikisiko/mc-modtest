package com.example.myvillage.story.scene;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/** Explicit operator-only repair; no login/start/tick handler and no scene/actor/task save writes. */
@EventBusSubscriber(modid="myvillage")
public final class QianzhenRoofRepair {
    private static final Logger LOG=LogUtils.getLogger();
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    private QianzhenRoofRepair() {}
    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("myvillage").then(Commands.literal("qianzhen")
                .then(Commands.literal("repair_roof").requires(source->source.hasPermission(2))
                        .then(Commands.literal("preview").executes(ctx->run(ctx.getSource(),false)))
                        .then(Commands.literal("apply").executes(ctx->run(ctx.getSource(),true))))));
    }
    private static int run(CommandSourceStack source,boolean apply) {
        ServerLevel level=source.getLevel();
        try {
            if(!level.getServer().isSameThread()) throw new IllegalStateException("屋架检查必须在服务器线程执行");
            SceneDeploymentData data=SceneDeploymentData.get(level);
            if(data.phase()!=SceneDeploymentData.Phase.COMPLETE) throw new IllegalStateException("场景记录未完整完成");
            if(!data.dimension().equals(level.dimension().location().toString())) throw new IllegalStateException("当前真实维度与场景记录不符："+data.dimension());
            // Version-exact legacy fallback only; frozen geometry, if present, remains authoritative.
            QianzhenRoofRepairPlan plan=load();
            plan.requireSupportedVersion(data.version());
            SceneLayout layout=data.geometry(SceneGeometryArchive.legacy(data.version()));
            SceneLayout.Site site=layout.sites().stream().filter(s->s.id().equals("canal_gardens")).findFirst()
                    .orElseThrow(()->new IllegalStateException("冻结布局缺渠圃"));
            var receipt=new QianzhenRoofRepairPlan.Receipt(true,data.id(),data.version(),data.dimension(),level.dimension().location().toString(),
                    pos(data.origin()),layout.id(),layout.version(),new QianzhenRoofRepairPlan.Site(site.id(),site.template(),pos(site.origin()),pos(site.size()),site.sha256()));
            var world=new NativeWorld(level);
            if(apply) {
                int count=plan.apply(receipt,world);
                source.sendSuccess(()->Component.literal("阡镇取样亭屋架修复完成：新增 "+count+" 格支架，完整复核通过。场景版本 "+data.version()+"；再次执行应为0格。"),true);
                LOG.info("Qianzhen roof repair by {}: dimension={}, sceneOrigin={}, version={}, added={}",source.getTextName(),data.dimension(),data.origin(),data.version(),count);
            } else {
                var changes=plan.preview(receipt,world);
                source.sendSuccess(()->Component.literal("只读预览通过：待改 "+changes.size()+" 格；维度 "+data.dimension()+"，版本 "+data.version()+"。保护格与全部目标已完整核对。"),false);
                for(var change:changes) source.sendSuccess(()->Component.literal(change.pos().coordinates()+"："+change.before()+" → "+change.after()+"；"+change.reason()),false);
                source.sendSuccess(()->Component.literal(changes.isEmpty()?"已修复，未写入任何方块。":"预览未写入；执行 /myvillage qianzhen repair_roof apply 将重新完整检查后补齐支架。"),false);
            }
            return 1;
        } catch(RuntimeException failure) {
            if(apply) LOG.error("Qianzhen roof repair rejected/rolled back; operator={}, dimension={}",source.getTextName(),level.dimension().location(),failure);
            source.sendFailure(Component.literal("阡镇屋架"+(apply?"修复":"只读预览")+"拒绝："+failure.getMessage())); return 0;
        }
    }
    private static QianzhenRoofRepairPlan load() {
        // Packaged authored manifest, deliberately independent of data-pack replacement and reports.
        try(var in=QianzhenRoofRepair.class.getResourceAsStream("/data/myvillage/story/qianzhen_roof_repair.json")) {
            if(in==null) throw new IllegalStateException("缺少已打包屋架补丁资源");
            byte[] bytes=in.readNBytes(512*1024+1);
            if(bytes.length>512*1024) throw new IllegalStateException("屋架补丁资源超限");
            return QianzhenRoofRepairPlan.parse(JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject());
        } catch(IOException bad) { throw new IllegalStateException("无法读取屋架补丁",bad); }
    }
    private static final class NativeWorld implements QianzhenRoofRepairPlan.World {
        private final ServerLevel level;
        private final Map<String,BlockState> states=new HashMap<>();
        NativeWorld(ServerLevel level) { this.level=level; }
        @Override public boolean loaded(QianzhenRoofRepairPlan.Pos p) {
            BlockPos at=block(p);
            return level.isInWorldBounds(at) && level.getWorldBorder().isWithinBounds(at) && level.hasChunkAt(at);
        }
        @Override public boolean matches(QianzhenRoofRepairPlan.Pos p,String text) { return level.getBlockState(block(p))==state(text); }
        @Override public boolean hasBlockEntity(QianzhenRoofRepairPlan.Pos p) { return level.getBlockState(block(p)).hasBlockEntity() || level.getBlockEntity(block(p))!=null; }
        @Override public boolean write(QianzhenRoofRepairPlan.Pos p,String text) {
            // Log/air only, preflight rejects all block entities; no drops or neighbour/shape propagation.
            return level.setBlock(block(p),state(text),FLAGS);
        }
        private BlockState state(String text) {
            return states.computeIfAbsent(text,key->{
                try { return BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK),key,false).blockState(); }
                catch(com.mojang.brigadier.exceptions.CommandSyntaxException bad) { throw new IllegalStateException("非法补丁块态："+key,bad); }
            });
        }
    }
    private static BlockPos block(QianzhenRoofRepairPlan.Pos p) { return new BlockPos(p.x(),p.y(),p.z()); }
    private static QianzhenRoofRepairPlan.Pos pos(BlockPos p) { return new QianzhenRoofRepairPlan.Pos(p.getX(),p.getY(),p.getZ()); }
    private static QianzhenRoofRepairPlan.Pos pos(SceneLayout.Pos p) { return new QianzhenRoofRepairPlan.Pos(p.x(),p.y(),p.z()); }
}
