package com.example.myvillage.enchanting;

import static com.example.myvillage.enchanting.EnchantingRules.RULES;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

public final class EnchantingStructure {
    private static final JsonObject LAYOUT = EnchantingRules.read("structures", JsonObject.class);
    public static final List<Cell> LIBRARY = read("library");
    public static final List<Cell> SEAL = read("seal");

    public record Cell(int x, int y, int z, String part) {}
    public record Table(int strength, int candidates, boolean focused, boolean formed) {}

    public static BlockPos offset(BlockPos origin, Direction front, int x, int y, int z) {
        var right = front.getCounterClockWise();
        return origin.offset(right.getStepX() * x + front.getStepX() * z, y, right.getStepZ() * x + front.getStepZ() * z);
    }

    private static List<Cell> read(String name) {
        var cells = new ArrayList<Cell>();
        for (var entry : LAYOUT.getAsJsonArray(name)) {
            var row = entry.getAsJsonArray();
            cells.add(new Cell(row.get(0).getAsInt(), row.get(1).getAsInt(), row.get(2).getAsInt(), row.get(3).getAsString()));
        }
        return List.copyOf(cells);
    }

    public static BlockPos libraryCenter(BlockPos port, Direction front) {
        return offset(port, front, 0, -1, -5);
    }

    public static Block blockFor(Cell cell, boolean library) {
        if (!library && cell.part().equals("minecraft:deepslate_brick_slab")) return Blocks.DEEPSLATE_BRICK_SLAB;
        return EnchantingContent.PARTS.get(library ? "library_" + cell.part() : cell.part()).get();
    }

    static BlockState rawState(Cell cell) {
        var block = blockFor(cell, false);
        return block == Blocks.DEEPSLATE_BRICK_SLAB
                ? block.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM).setValue(SlabBlock.WATERLOGGED, false)
                : block.defaultBlockState();
    }

    /** Unloaded cells suspend operation without treating an unloaded chunk as a broken structure. */
    record Check(int missing, boolean unloaded) {
        boolean matched() { return missing == 0 && !unloaded; }
    }

    static Check checkSeal(Level level, BlockPos origin, Direction front, boolean formed) {
        int missing = 0;
        boolean unloaded = false;
        for (int i = 0; i < SEAL.size(); i++) {
            var cell = SEAL.get(i);
            var pos = offset(origin, front, cell.x(), cell.y(), cell.z());
            if (!level.isLoaded(pos)) {
                unloaded = true;
                continue;
            }
            var expected = formed ? SealMemberBlock.part(i, front) : rawState(cell);
            if (level.getBlockState(pos) != expected) missing++;
        }
        var socket = origin.below();
        if (!level.isLoaded(socket)) unloaded = true;
        else if (!(level.getBlockEntity(socket) instanceof EnchantingDevice device)
                || !device.getBlockState().is(EnchantingContent.SOCKET.get())
                || device.getBlockState().getValue(EnchantingBlock.FORMED) != formed) missing++;
        return new Check(missing, unloaded);
    }

    static boolean matches(Level level, BlockPos origin, Direction front, boolean library) {
        if (!library) return checkSeal(level, origin, front, true).matched();
        for (var cell : LIBRARY) {
            var pos = offset(origin, front, cell.x(), cell.y(), cell.z());
            if (!level.isLoaded(pos) || !level.getBlockState(pos).is(blockFor(cell, true))) return false;
        }
        return true;
    }

    static Table table(Level level, BlockPos origin, Direction front, boolean seal) {
        if (seal) {
            if (!(level.getBlockEntity(origin) instanceof EnchantingDevice device)
                    || !device.formed() || !checkSeal(level, origin, front, true).matched()) {
                return new Table(0, 0, false, false);
            }
            int base = level.getBlockEntity(origin.below()) instanceof EnchantingDevice socket
                    ? Math.max(RULES.empty_cap(), socket.stoneStrength()) : RULES.empty_cap();
            return new Table(SealRules.RULES.strength(base, device.discTypes()),
                    SealRules.RULES.draw_slots(), false, true);
        }
        int base = RULES.candidates();
        int strength = 0;
        int choices = 0;
        boolean focus = false;
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if (Math.max(Math.abs(x), Math.abs(z)) != 2) continue;
            for (int y = 0; y <= 1; y++) {
                var pos = offset(origin, front, x, y, z);
                var gap = offset(origin, front, x / 2, y, z / 2);
                if (!level.isLoaded(pos) || !level.isLoaded(gap) || !level.isEmptyBlock(gap)) continue;
                var state = level.getBlockState(pos);
                if (state.is(Blocks.BOOKSHELF) || state.is(EnchantingContent.PARTS.get("seal_rack").get())) strength++;
                else if (state.is(EnchantingContent.PARTS.get("crystal_shelf").get())) choices++;
                else if (state.is(EnchantingContent.PARTS.get("focus_shelf").get())) focus = true;
                else continue;
                break;
            }
        }
        int cap = RULES.empty_cap();
        if (level.getBlockEntity(origin.below()) instanceof EnchantingDevice device
                && device.getBlockState().is(EnchantingContent.SOCKET.get())) cap = Math.max(cap, device.stoneStrength());
        return new Table(Math.min(cap, Math.min(strength, RULES.shelf_cap())),
                Math.min(RULES.maximum_candidates(), base + choices), focus, false);
    }
}
