package com.example.myvillage.forging;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.block.SpiritSoilBlock;
import com.example.myvillage.item.ModDataComponents;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid=MyVillageMod.MOD_ID)
public final class WorkshopChainHarvest {
    private static final Map<UUID,ArrayDeque<Harvest>> ACTIVE = new HashMap<>();
    private static final Set<UUID> BREAKING = new HashSet<>();
    private static final Map<ServerLevel,Set<BlockPos>> CHECK_REMOVED_LOGS = new HashMap<>();

    private record Target(BlockPos pos,BlockState state) {}
    private record Replant(BlockItem sapling,List<BlockPos> roots) {}
    private static final class Harvest {
        final ServerLevel level;
        final ItemStack tool;
        final BlockPos origin;
        final BlockState initial;
        final int rank;
        final TagKey<Block> ore;
        final int interval;
        ArrayDeque<Target> logs;
        Replant replant;
        boolean complete = true;
        long nextTick;

        Harvest(ServerLevel level,ItemStack tool,BlockPos origin,BlockState initial,int rank,
                TagKey<Block> ore){
            this.level=level;
            this.tool=tool;
            this.origin=origin.immutable();
            this.initial=initial;
            this.rank=rank;
            this.ore=ore;
            interval=ore==null ? WorkshopChainConfig.axeInterval(rank) : 0;
            nextTick=level.getGameTime()+interval;
        }
    }

    private WorkshopChainHarvest() {}

    static void start(ItemStack tool,Level level,BlockState state,BlockPos pos,LivingEntity miner){
        if (!(level instanceof ServerLevel server) || !(miner instanceof ServerPlayer player)
                || !canHarvest(player,server,tool) || BREAKING.contains(player.getUUID())
                || !tool.isCorrectToolForDrops(state)) return;
        int rank=rank(tool);
        if (rank<0) return;

        TagKey<Block> ore=null;
        if (tool.getItem() instanceof WorkshopTools.Pickaxe){
            ore=oreFamily(state);
            if (ore==null || (WorkshopChainConfig.pickaxe(rank).count()==1
                    && !WorkshopToolSpecialties.has(tool,ForgeMark.SEEK_VEIN))) return;
        } else if (!wood(state) || WorkshopHarvestLedger.get(server).playerPlacedLog(pos)) return;
        var jobs=ACTIVE.computeIfAbsent(player.getUUID(),id -> new ArrayDeque<>());
        if (ore==null && jobs.stream().anyMatch(job -> includes(job,tool,pos))) return;
        jobs.addLast(new Harvest(server,tool,pos,state,rank,ore));
    }

    private static int rank(ItemStack tool){
        return WorkshopEquipment.craft(tool).flatMap(craft -> ForgingRules.get().recipe(craft.recipe()))
                .map(recipe -> WorkshopChainConfig.rank(recipe.qualityTier())).orElse(-1);
    }

    private static boolean includes(Harvest job,ItemStack tool,BlockPos pos){
        return job.tool==tool && (job.origin.equals(pos) || job.logs!=null
                && job.logs.stream().anyMatch(target -> target.pos().equals(pos)));
    }

    static boolean fellAndReplant(ServerPlayer player,BlockPos pos,BlockState state){
        var level=player.serverLevel();
        var tool=player.getMainHandItem();
        int rank=rank(tool);
        if (rank<0 || !wood(state) || !canHarvest(player,level,tool)
                || WorkshopHarvestLedger.get(level).playerPlacedLog(pos)) return false;
        var jobs=ACTIVE.get(player.getUUID());
        if (jobs!=null && jobs.stream().anyMatch(job -> includes(job,tool,pos))) return false;

        var job=new Harvest(level,tool,pos,state,rank,null);
        var plan=tree(job);
        var sapling=sapling(job,plan);
        if (sapling==null || !destroy(player,job,pos,state)) return false;
        job.replant=new Replant(sapling,plan.roots());
        job.logs=targets(level,plan);
        if (job.logs.isEmpty()) replant(player,job);
        else ACTIVE.computeIfAbsent(player.getUUID(),id -> new ArrayDeque<>()).addLast(job);
        return true;
    }

    private static TagKey<Block> oreFamily(BlockState state){
        for (String family : WorkshopChainConfig.oreFamilies()){
            var tag=TagKey.create(Registries.BLOCK,ResourceLocation.parse(family));
            if (state.is(tag)) return tag;
        }
        return null;
    }

    private static boolean wood(BlockState state){
        return state.is(BlockTags.LOGS);
    }

    private static boolean canHarvest(ServerPlayer player,ServerLevel level,ItemStack tool){
        return player.serverLevel()==level && player.isAlive() && !player.isCreative() && !player.isSpectator()
                && !tool.isEmpty() && player.getMainHandItem()==tool;
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event){
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id=player.getUUID();
        var jobs=ACTIVE.get(id);
        if (jobs==null) return;
        try {
            jobs.removeIf(job -> !prepare(player,job));
            while (!jobs.isEmpty()){
                Harvest job=jobs.getFirst();
                if (job.level.getGameTime()<job.nextTick) return;
                if (job.ore!=null){
                    if (WorkshopToolSpecialties.has(job.tool,ForgeMark.SEEK_VEIN))
                        job.tool.set(ModDataComponents.LAST_MINED_ORE_FAMILY.get(),job.ore.location());
                    WorkshopHarvestSearch.mine(job.origin,WorkshopChainConfig.pickaxe(job.rank),pos -> {
                        if (!job.level.hasChunkAt(pos)) return false;
                        BlockState state=job.level.getBlockState(pos);
                        return state.is(job.ore) && destroy(player,job,pos,state);
                    });
                    jobs.removeFirst();
                    continue;
                }
                if (!cutNextLog(player,job)){
                    jobs.removeFirst();
                    continue;
                }
                long nextTick=job.level.getGameTime()+job.interval;
                if (job.logs.isEmpty()) {
                    jobs.removeFirst();
                    replant(player,job);
                }
                if (!jobs.isEmpty()) jobs.getFirst().nextTick=Math.max(jobs.getFirst().nextTick,nextTick);
                return;
            }
        } finally {
            if (jobs.isEmpty()) ACTIVE.remove(id);
        }
    }

    private static boolean prepare(ServerPlayer player,Harvest job){
        if (!canHarvest(player,job.level,job.tool)) return false;
        if (job.logs!=null) return !job.logs.isEmpty();
        // The mining hook runs before removal. Begin only after the first block has actually changed.
        if (!job.level.hasChunkAt(job.origin) || job.level.getBlockState(job.origin)==job.initial) return false;
        if (job.ore!=null) return true;
        job.logs=targets(job.level,tree(job));
        return !job.logs.isEmpty();
    }

    private static boolean cutNextLog(ServerPlayer player,Harvest job){
        var ledger=WorkshopHarvestLedger.get(job.level);
        while (!job.logs.isEmpty() && canHarvest(player,job.level,job.tool)){
            Target target=job.logs.removeFirst();
            if (!ledger.playerPlacedLog(target.pos()) && destroy(player,job,target.pos(),target.state())) return true;
            job.complete=false;
        }
        return false;
    }

    private static boolean destroy(ServerPlayer player,Harvest job,BlockPos pos,BlockState state){
        var level=job.level;
        if (!canHarvest(player,level,job.tool) || !WorkshopToolWork.accessible(player,pos)
                || level.getBlockState(pos)!=state || !job.tool.isCorrectToolForDrops(state)) return false;
        UUID id=player.getUUID();
        BREAKING.add(id);
        try {
            return player.gameMode.destroyBlock(pos) && level.getBlockState(pos)!=state;
        } finally {
            BREAKING.remove(id);
        }
    }

    private static WorkshopHarvestSearch.TreePlan tree(Harvest job){
        var level=job.level;
        var ledger=WorkshopHarvestLedger.get(level);
        BlockState trunk=job.initial;
        boolean mangrove=trunk.is(Blocks.MANGROVE_LOG);
        boolean fungus=trunk.is(Blocks.CRIMSON_STEM) || trunk.is(Blocks.WARPED_STEM);
        boolean thick=trunk.is(Blocks.DARK_OAK_LOG) || trunk.is(Blocks.SPRUCE_LOG) || trunk.is(Blocks.JUNGLE_LOG);
        return WorkshopHarvestSearch.tree(job.origin,pos -> {
            if (!level.hasChunkAt(pos) || ledger.playerPlacedLog(pos)) return false;
            return level.getBlockState(pos).is(trunk.getBlock());
        },pos -> {
            var below=level.getBlockState(pos.below());
            return below.is(BlockTags.DIRT) || below.is(BlockTags.SAND) || below.is(BlockTags.NYLIUM)
                    || below.is(Blocks.MOSS_BLOCK) || below.is(Blocks.FARMLAND)
                    || below.getBlock() instanceof SpiritSoilBlock || mangrove && (below.is(Blocks.MANGROVE_ROOTS)
                            || below.is(Blocks.MUDDY_MANGROVE_ROOTS));
        },pos -> hasCrown(level,pos,fungus),thick);
    }

    private static ArrayDeque<Target> targets(ServerLevel level,WorkshopHarvestSearch.TreePlan plan){
        var result=new ArrayDeque<Target>();
        for (BlockPos pos : plan.logs()) result.addLast(new Target(pos,level.getBlockState(pos)));
        return result;
    }

    private static BlockItem sapling(Harvest job,WorkshopHarvestSearch.TreePlan plan){
        if (plan.roots().isEmpty()) return null;
        var log=BuiltInRegistries.BLOCK.getKey(job.initial.getBlock());
        var choices=WorkshopSpecialtyRules.get().saplings().stream().filter(s -> s.log().equals(log)).toList();
        if (choices.isEmpty()) return null;
        var choice=choices.getFirst();
        if (choices.size()>1){
            var wood=new ArrayDeque<>(plan.logs());
            wood.addFirst(job.origin);
            var visited=new HashSet<BlockPos>();
            var counts=new HashMap<ResourceLocation,Integer>();
            for (BlockPos trunk : wood) for (BlockPos pos : BlockPos.betweenClosed(trunk.offset(-1,-1,-1),trunk.offset(1,1,1))){
                if (!job.level.hasChunkAt(pos) || !visited.add(pos.immutable())) continue;
                var state=job.level.getBlockState(pos);
                if (state.is(BlockTags.LEAVES) && state.hasProperty(BlockStateProperties.PERSISTENT)
                        && !state.getValue(BlockStateProperties.PERSISTENT))
                    counts.merge(BuiltInRegistries.BLOCK.getKey(state.getBlock()),1,Integer::sum);
            }
            choice=choices.stream().max(java.util.Comparator.comparingInt(s -> s.crown().stream()
                    .mapToInt(leaf -> counts.getOrDefault(leaf,0)).sum())).orElseThrow();
        }
        return BuiltInRegistries.ITEM.get(choice.plant()) instanceof BlockItem seed ? seed : null;
    }

    private static void replant(ServerPlayer player,Harvest job){
        var plan=job.replant;
        if (plan==null || !job.complete || player.serverLevel()!=job.level || !player.isAlive()) return;
        var seed=new ItemStack(plan.sapling());
        var seedState=plan.sapling().getBlock().defaultBlockState();
        for (BlockPos pos : plan.roots()){
            if (!WorkshopToolWork.accessible(player,pos) || !job.level.getBlockState(pos).isAir()
                    || !seedState.canSurvive(job.level,pos)) return;
        }
        for (BlockPos pos : plan.roots()) WorkshopToolWork.placeOne(player,pos,seed);
    }

    private static boolean hasCrown(ServerLevel level,BlockPos pos,boolean fungus){
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-2,-1,-2),pos.offset(2,2,2))){
            if (!level.hasChunkAt(near)) continue;
            var state=level.getBlockState(near);
            if (state.is(BlockTags.LEAVES) && state.hasProperty(BlockStateProperties.PERSISTENT)
                    && !state.getValue(BlockStateProperties.PERSISTENT)) return true;
            if (fungus && (state.is(Blocks.NETHER_WART_BLOCK) || state.is(Blocks.WARPED_WART_BLOCK))) return true;
        }
        return false;
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void placed(BlockEvent.EntityPlaceEvent event){
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi){
            for (var snapshot : multi.getReplacedBlockSnapshots()) recordPlacement(level,snapshot.getPos());
        } else recordPlacement(level,event.getPos());
    }

    private static void recordPlacement(ServerLevel level,BlockPos pos){
        if (wood(level.getBlockState(pos))) WorkshopHarvestLedger.get(level).markPlacedLog(pos);
    }

    static void logMoved(ServerLevel level,BlockPos source,BlockPos destination){
        if (!wood(level.getBlockState(source))) return;
        WorkshopHarvestLedger.get(level).markPlacedLog(destination);
        checkRemoved(level,source);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void broken(BlockEvent.BreakEvent event){
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level && wood(event.getState()))
            checkRemoved(level,event.getPos());
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void exploded(ExplosionEvent.Detonate event){
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        for (BlockPos pos : event.getAffectedBlocks()) checkRemoved(level,pos);
    }

    private static void checkRemoved(ServerLevel level,BlockPos pos){
        if (WorkshopHarvestLedger.get(level).playerPlacedLog(pos))
            CHECK_REMOVED_LOGS.computeIfAbsent(level,key -> new HashSet<>()).add(pos.immutable());
    }

    @SubscribeEvent
    public static void cleanPlacements(ServerTickEvent.Post event){
        CHECK_REMOVED_LOGS.forEach((level,positions) -> {
            var ledger=WorkshopHarvestLedger.get(level);
            for (BlockPos pos : positions){
                if (level.hasChunkAt(pos) && !wood(level.getBlockState(pos)) && !level.getBlockState(pos).is(Blocks.MOVING_PISTON))
                    ledger.forgetPlacedLog(pos);
            }
        });
        CHECK_REMOVED_LOGS.clear();
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){ACTIVE.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){ACTIVE.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){ACTIVE.clear();BREAKING.clear();CHECK_REMOVED_LOGS.clear();}
}
