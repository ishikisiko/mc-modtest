package com.example.myvillage.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class PlaqueBlock extends Block {
    public static final MapCodec<PlaqueBlock> CODEC = simpleCodec(PlaqueBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Frame> FRAME = EnumProperty.create("frame", Frame.class);
    public static final EnumProperty<Row> ROW = EnumProperty.create("row", Row.class);
    public static final EnumProperty<Col> COL = EnumProperty.create("col", Col.class);
    private static final VoxelShape SUPPORT_SHAPE = Shapes.block();

    public PlaqueBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FRAME, Frame.TOWN_SHOP_WOOD_3W)
                .setValue(ROW, Row.SINGLE)
                .setValue(COL, Col.CENTER));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FRAME, ROW, COL);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SUPPORT_SHAPE;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    public enum Frame implements StringRepresentable {
        TOWN_SHOP_WOOD_3W("town_shop_wood_3w"),
        TOWN_INN_LACQUERED_4W("town_inn_lacquered_4w"),
        TOWN_NOTICE_BOARD_3W("town_notice_board_3w"),
        TAVERN_SIGNBOARD_4W("tavern_signboard_4w"),
        SECT_SIMPLE_PINE_4W("sect_simple_pine_4w"),
        SECT_SCRIPTURE_ORNATE_4W("sect_scripture_ornate_4w"),
        LORD_MANOR_HERALDRY_5W("lord_manor_heraldry_5w"),
        SECT_TREASURE_GILDED_5W_2H("sect_treasure_gilded_5w_2h");

        private final String name;

        Frame(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public enum Row implements StringRepresentable {
        TOP("top"),
        UPPER_MIDDLE("upper_middle"),
        MIDDLE("middle"),
        LOWER_MIDDLE("lower_middle"),
        SINGLE("single"),
        BOTTOM("bottom");

        private final String name;

        Row(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public enum Col implements StringRepresentable {
        LEFT("left"),
        INNER_LEFT("inner_left"),
        CENTER("center"),
        INNER_RIGHT("inner_right"),
        SINGLE("single"),
        RIGHT("right");

        private final String name;

        Col(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
