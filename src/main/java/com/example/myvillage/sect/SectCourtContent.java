package com.example.myvillage.sect;

import com.example.myvillage.manor.ManorCatalog;
import com.example.myvillage.resources.BuiltinResources;
import com.google.gson.JsonParser;
import com.mojang.serialization.MapCodec;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SectCourtContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("yuanxian");
    private static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(Registries.ENTITY_TYPE, "yuanxian");
    public static final DeferredBlock<LiftTerminalBlock> LIFT_TERMINAL = BLOCKS.register("sect_lift_terminal",
        () -> new LiftTerminalBlock(BlockBehaviour.Properties.of().strength(-1, 3600000).sound(SoundType.METAL)));
    public static final DeferredBlock<LiftGateBlock> LIFT_GATE = BLOCKS.register("sect_lift_gate",
        () -> new LiftGateBlock(BlockBehaviour.Properties.of().strength(-1, 3600000).noOcclusion().sound(SoundType.METAL)));
    public static final DeferredHolder<EntityType<?>, EntityType<SectLiftPlatform>> LIFT = ENTITIES.register("sect_lift",
        () -> EntityType.Builder.of(SectLiftPlatform::new, MobCategory.MISC)
            .sized(2, .25F).clientTrackingRange(10).updateInterval(1).noSummon().build("yuanxian:sect_lift"));

    public static void register(IEventBus bus) {
        try (var input = BuiltinResources.open("data/yuanxian/sect_blueprints/court_components.json.gz")) {
            if (input == null) throw new IllegalStateException("缺少宗门建筑雕构目录");
            try (var source = new InputStreamReader(new GZIPInputStream(input), StandardCharsets.UTF_8)) {
                var banks = JsonParser.parseReader(source).getAsJsonArray();
                for (var entry : banks) {
                    var bank = entry.getAsJsonObject();
                    var shapes = bank.getAsJsonArray("shapes").asList().stream().map(element -> {
                        var shape = element.getAsJsonObject();
                        return new Carving(ManorCatalog.Profile.fromJson(shape), shape.get("light").getAsInt());
                    }).toList();
                    if (shapes.size() != 32) throw new IllegalArgumentException("宗门建筑雕构状态不齐全");
                    BLOCKS.register(bank.get("id").getAsString(), () -> new CarvingBlock(shapes,
                        BlockBehaviour.Properties.of().strength(2, 6).noOcclusion()
                            .lightLevel(state -> shapes.get(state.getValue(CarvingBlock.SHAPE)).light())));
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("无法读取宗门建筑雕构", error);
        }
        BLOCKS.register(bus);
        ENTITIES.register(bus);
        NeoForge.EVENT_BUS.addListener(SectCourtContent::keepRiderAboard);
    }

    private static void keepRiderAboard(EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof SectLiftPlatform platform
            && platform.isTravelling() && !platform.isRemoved() && event.getEntityMounting().isAlive()
            && !event.getEntityMounting().isRemoved() && event.getEntityMounting().level() == platform.level()) {
            event.setCanceled(true);
        }
    }

    private record Carving(ManorCatalog.Profile profile, int light) { }

    private static final class CarvingBlock extends Block {
        private static final IntegerProperty SHAPE = IntegerProperty.create("shape", 0, 31);
        private final List<Carving> shapes;
        private final MapCodec<CarvingBlock> codec;

        CarvingBlock(List<Carving> shapes, Properties properties) {
            super(properties);
            this.shapes = shapes;
            this.codec = simpleCodec(p -> new CarvingBlock(shapes, p));
            registerDefaultState(stateDefinition.any().setValue(SHAPE, 0));
        }

        @Override protected MapCodec<? extends Block> codec() { return codec; }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(SHAPE);
        }

        @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return shapes.get(state.getValue(SHAPE)).profile().shape(0, Direction.NORTH);
        }

        @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return shapes.get(state.getValue(SHAPE)).profile().shape(1, Direction.NORTH);
        }
    }

    private SectCourtContent() { }
}
