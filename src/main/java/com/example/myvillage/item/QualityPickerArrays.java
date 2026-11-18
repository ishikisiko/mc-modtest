package com.example.myvillage.item;

import com.example.myvillage.alchemy.array.AlchemyArrayContent;
import com.example.myvillage.alchemy.array.AlchemyArrayPattern;
import com.example.myvillage.alchemy.array.AlchemyArrayRules;
import com.example.myvillage.enchanting.EnchantingContent;
import com.example.myvillage.enchanting.EnchantingStructure;
import com.example.myvillage.estate.EstateRuntime;
import com.example.myvillage.forging.array.ForgingArrayContent;
import com.example.myvillage.forging.array.ForgingArrayPattern;
import com.example.myvillage.forging.array.ForgingArrayRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

public final class QualityPickerArrays {
    public static final byte CONFIRM = 5;
    public static final byte CANCEL = 6;
    private static final String MODE = "QuickArray";
    private static final String PREVIEW = "QuickArrayPreview";
    private static final Map<ServerPlayer, Pending> PENDING = new WeakHashMap<>();
    private static long nextTicket;
    private static final int FLAGS = Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS;
    private static final int SEAL_FRONT = 4;

    public enum Layout {
        NONE(0, "关闭快捷搭建"),
        ALCHEMY(1, "青铜丹炉阵"),
        FORGING(2, "玄枢炼器鼎"),
        SEAL(3, "篆纹阵"),
        LIBRARY(4, "仙纹图书馆");

        private final byte id;
        private final String title;

        Layout(int id, String title) {
            this.id = (byte) id;
            this.title = title;
        }

        public byte id() { return id; }
        public String title() { return title; }

        public static Optional<Layout> fromId(int id) {
            return java.util.Arrays.stream(values()).filter(layout -> layout.id == id).findFirst();
        }
    }

    public record Cell(BlockPos pos, Function<Direction, BlockState> state, Direction direction) {
        public BlockState blockState() { return state.apply(direction); }
    }

    public record Build(Layout layout, BlockPos controller, Direction front, List<Cell> cells) {
        public BoundingBox bounds() {
            var box = BoundingBox.encapsulatingPositions(cells.stream().map(Cell::pos).toList()).orElseThrow();
            // Leave room above the actual components for the folded and extended seal arms.
            return layout == Layout.SEAL
                    ? new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), controller.getY() + 5, box.maxZ())
                    : box;
        }
    }

    public record Preview(Layout layout, BlockPos ground, Direction front, ResourceLocation dimension, long ticket, int occupied) {}
    private record Pending(Preview preview, ItemStack stack, Map<BlockPos, BlockState> initial) {}
    private record Placement(BlockSnapshot snapshot, BlockState state) {}

    private QualityPickerArrays() {}

    public static Layout selected(ItemStack stack) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return Layout.fromId(data.getByte(MODE)).orElse(Layout.NONE);
    }

    public static boolean builderHand(net.minecraft.world.entity.player.Player player) {
        return player.isCreative() && player.isAlive() && !player.isRemoved()
                && player.getMainHandItem().is(ModItems.QUALITY_PICKER.get());
    }

    public static Optional<Preview> selectedPreview(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(PREVIEW);
        long ticket = tag.getLong("Ticket");
        var dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        Layout layout = selected(stack);
        if (ticket <= 0 || dimension == null || layout == Layout.NONE) return Optional.empty();
        return Optional.of(new Preview(layout, BlockPos.of(tag.getLong("Ground")),
                Direction.from2DDataValue(tag.getInt("Front")), dimension, ticket, tag.getInt("Occupied")));
    }

    public static void request(ServerPlayer player, byte id, long ticket) {
        if (!builderHand(player)) return;
        if (id == CONFIRM || id == CANCEL) {
            var pending = PENDING.get(player);
            if (pending == null || pending.stack() != player.getMainHandItem() || pending.preview().ticket() != ticket) {
                fail(player, "预览已失效，请重新选定位置。");
                if (pending == null || pending.stack() != player.getMainHandItem()) clearPreview(player);
            } else if (id == CANCEL) {
                clearPreview(player);
            } else {
                confirm(player, pending);
            }
            return;
        }
        Layout.fromId(id).ifPresent(layout -> {
            PENDING.remove(player);
            CustomData.update(DataComponents.CUSTOM_DATA, player.getMainHandItem(), tag -> {
                tag.remove(PREVIEW);
                if (layout == Layout.NONE) tag.remove(MODE);
                else tag.putByte(MODE, layout.id());
            });
            sync(player);
            player.displayClientMessage(Component.literal(layout == Layout.NONE ? layout.title()
                    : "已选择" + layout.title() + "，潜行右键地面预览。"), true);
        });
    }

    static InteractionResult preview(ServerPlayer player, UseOnContext context, Layout layout) {
        if (!builderHand(player)) return InteractionResult.FAIL;
        if (context.getClickedFace() != Direction.UP) return fail(player, "请潜行右键地面的上表面。");
        ServerLevel level = player.serverLevel();
        var ground = context.getClickedPos();
        var front = context.getHorizontalDirection().getOpposite();
        var build = blueprint(layout, ground, front);
        if (!setPreview(player, layout, ground, front, level, build)) return InteractionResult.FAIL;
        player.displayClientMessage(Component.literal("已选定区域。普通右键打开确认框，潜行右键可重选。"), true);
        return InteractionResult.CONSUME;
    }

    private static boolean setPreview(ServerPlayer player, Layout layout, BlockPos ground, Direction front,
            ServerLevel level, Build build) {
        var initial = new LinkedHashMap<BlockPos, BlockState>();
        int occupied = 0;
        for (var pos : region(build)) {
            if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos)) {
                fail(player, "有格子超出可搭建范围：" + pos.toShortString());
                return false;
            }
            var state = level.getBlockState(pos);
            initial.put(pos, state);
            if (!state.isAir()) occupied++;
        }
        var view = new Preview(layout, ground, front, level.dimension().location(), ++nextTicket, occupied);
        PENDING.put(player, new Pending(view, player.getMainHandItem(), initial));
        var preview = new CompoundTag();
        preview.putLong("Ground", ground.asLong());
        preview.putInt("Front", front.get2DDataValue());
        preview.putString("Dimension", view.dimension().toString());
        preview.putLong("Ticket", view.ticket());
        preview.putInt("Occupied", view.occupied());
        CustomData.update(DataComponents.CUSTOM_DATA, player.getMainHandItem(), tag -> tag.put(PREVIEW, preview));
        sync(player);
        return true;
    }

    private static List<BlockPos> region(Build build) {
        var box = build.bounds();
        return BlockPos.betweenClosedStream(box)
                .map(BlockPos::immutable).toList();
    }

    private static void confirm(ServerPlayer player, Pending pending) {
        var view = pending.preview();
        ServerLevel level = player.serverLevel();
        if (!view.dimension().equals(level.dimension().location()) || selected(pending.stack()) != view.layout()) {
            clearPreview(player);
            fail(player, "预览已失效，请重新选定位置。");
            return;
        }
        if (!player.canInteractWithBlock(view.ground(), 0)) {
            fail(player, "请回到选定位置附近再确认。");
            return;
        }
        var build = blueprint(view.layout(), view.ground(), view.front());
        for (var entry : pending.initial().entrySet()) {
            if (!level.hasChunkAt(entry.getKey())) {
                fail(player, "阵法区域尚未加载。");
                return;
            }
            if (level.getBlockState(entry.getKey()) != entry.getValue()) {
                if (setPreview(player, view.layout(), view.ground(), view.front(), level, build))
                    fail(player, "区域有变化，预览已更新，请重新确认。");
                return;
            }
        }
        var desired = new LinkedHashMap<BlockPos, BlockState>();
        build.cells().forEach(cell -> desired.put(cell.pos(), cell.blockState()));
        var placements = new ArrayList<Placement>();
        Set<BlockPos> checkedColumns = new HashSet<>();
        for (var entry : pending.initial().entrySet()) {
            var pos = entry.getKey();
            BlockState old = entry.getValue();
            BlockState state = desired.getOrDefault(pos, Blocks.AIR.defaultBlockState());
            if (old.isAir() && state.isAir() || old == state && !old.hasBlockEntity()) continue;
            if (!level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos)
                    || !player.mayUseItemAt(pos, Direction.UP, pending.stack())) {
                fail(player, "无法清理此处：" + pos.toShortString());
                return;
            }
            if (checkedColumns.add(pos.atY(0)) && EstateRuntime.blocked(player, level, pos.getX() + .5, pos.getZ() + .5))
                return;
            if (!state.isAir() && !level.isUnobstructed(state, pos, CollisionContext.of(player))) {
                fail(player, "有生物占着阵位：" + pos.toShortString());
                return;
            }
            placements.add(new Placement(BlockSnapshot.create(level.dimension(), level, pos, FLAGS), state));
        }
        if (placeAll(player, level, placements)) {
            clearPreview(player);
            player.displayClientMessage(Component.literal("已清理区域并搭建" + view.layout().title() + "。"), true);
        } else {
            fail(player, "区域未改动，放置被取消。");
        }
    }

    private static boolean placeAll(ServerPlayer player, ServerLevel level, List<Placement> placements) {
        if (placements.isEmpty() || level.captureBlockSnapshots) return false;
        if (level.isDebug()) return false;
        var changed = new ArrayList<BlockSnapshot>();
        boolean done = false;
        level.captureBlockSnapshots = true;
        try {
            for (var placement : placements) {
                var original = placement.snapshot();
                var pos = original.getPos();
                var state = placement.state();
                boolean hasEntity = original.getState().hasBlockEntity();
                changed.add(original);
                // Store original data for cancellation before detaching containers, avoiding inventory drops.
                if (hasEntity) {
                    level.removeBlockEntity(pos);
                    if (original.getState().is(state.getBlock())
                            && !level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS)) return false;
                }
                if (!level.setBlock(pos, state, FLAGS)) return false;
                level.capturedBlockSnapshots.clear();
            }
            level.captureBlockSnapshots = false;
            if (changed.size() > 1 && EventHooks.onMultiBlockPlace(player, changed, Direction.UP)
                    || changed.size() == 1 && EventHooks.onBlockPlace(player, changed.getFirst(), Direction.UP)) return false;

            done = true;
            for (var original : changed) {
                var pos = original.getPos();
                var state = level.getBlockState(pos);
                state.onPlace(level, pos, original.getState(), false);
                level.markAndNotifyBlock(pos, level.getChunkAt(pos), original.getState(), state, FLAGS, 512);
            }
            return true;
        } finally {
            level.captureBlockSnapshots = false;
            level.capturedBlockSnapshots.clear();
            if (!done) {
                level.restoringBlockSnapshots = true;
                try {
                    for (var snapshot : changed.reversed()) {
                        if (!snapshot.restore(FLAGS) && snapshot.getState() == level.getBlockState(snapshot.getPos()))
                            snapshot.restoreBlockEntity(level, snapshot.getPos());
                    }
                } finally {
                    level.restoringBlockSnapshots = false;
                }
            }
        }
    }

    private static void clearPreview(ServerPlayer player) {
        PENDING.remove(player);
        CustomData.update(DataComponents.CUSTOM_DATA, player.getMainHandItem(), tag -> tag.remove(PREVIEW));
        sync(player);
    }

    private static void sync(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    private static InteractionResult fail(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
        return InteractionResult.FAIL;
    }

    public static Build blueprint(Layout layout, BlockPos ground, Direction front) {
        BlockPos threshold = ground.above();
        var cells = new ArrayList<Cell>();
        BlockPos controller;
        switch (layout) {
            case ALCHEMY -> {
                controller = threshold.above();
                for (var cell : AlchemyArrayRules.get().cells()) {
                    var pos = AlchemyArrayPattern.worldPos(controller, front, cell);
                    cells.add(new Cell(pos, direction -> cell.role() == AlchemyArrayRules.Role.AIR ? Blocks.AIR.defaultBlockState()
                            : facing(AlchemyArrayContent.PARTS.get(cell.role()).get(), direction),
                            AlchemyArrayPattern.outward(front, cell.role())));
                }
            }
            case FORGING -> {
                controller = threshold.above();
                for (var cell : ForgingArrayRules.get().cells()) {
                    var pos = ForgingArrayPattern.worldPos(controller, front, cell);
                    cells.add(new Cell(pos, direction -> cell.role() == ForgingArrayRules.Role.AIR ? Blocks.AIR.defaultBlockState()
                            : facing(ForgingArrayContent.PARTS.get(cell.role()).get(), direction), front));
                }
            }
            case SEAL -> {
                controller = threshold.relative(front.getOpposite(), SEAL_FRONT).above();
                addEnchanting(cells, EnchantingStructure.SEAL, controller, front, false);
                cells.add(new Cell(controller, direction -> facing(EnchantingContent.SEAL.get(), direction), front));
                cells.add(new Cell(controller.below(), direction -> facing(EnchantingContent.SOCKET.get(), direction), front));
            }
            case LIBRARY -> {
                controller = threshold.above();
                var center = EnchantingStructure.libraryCenter(controller, front);
                addEnchanting(cells, EnchantingStructure.LIBRARY, center, front, true);
            }
            default -> throw new IllegalArgumentException(layout.name());
        }
        return new Build(layout, controller, front, List.copyOf(cells));
    }

    private static void addEnchanting(List<Cell> cells, List<EnchantingStructure.Cell> layout,
            BlockPos center, Direction front, boolean library) {
        for (var cell : layout) {
            var pos = EnchantingStructure.offset(center, front, cell.x(), cell.y(), cell.z());
            cells.add(new Cell(pos, direction -> facing(EnchantingStructure.blockFor(cell, library), direction), front));
        }
    }

    private static BlockState facing(Block block, Direction front) {
        var state = block.defaultBlockState();
        return state.trySetValue(BlockStateProperties.HORIZONTAL_FACING, front);
    }
}
