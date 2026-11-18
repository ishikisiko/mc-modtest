package com.example.myvillage.enchanting;

import static com.example.myvillage.enchanting.EnchantingRules.RULES;

import com.example.myvillage.estate.EstateRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class EnchantingDevice extends BlockEntity {
    private ItemStack stone = ItemStack.EMPTY;
    private CompoundTag ledger = new CompoundTag();
    private volatile boolean formed;
    private int pose;
    private long phaseEnd;
    private final NonNullList<ItemStack> discs = NonNullList.withSize(SealProjection.SLOTS.size(), ItemStack.EMPTY);
    private List<String> visibleDiscs = Collections.nCopies(SealProjection.SLOTS.size(), "empty");
    private long formedAt = Long.MIN_VALUE;

    EnchantingDevice(BlockPos pos, BlockState state) {
        super(EnchantingContent.DEVICE.get(), pos, state);
    }

    public Direction front() { return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING); }
    public boolean seal() { return getBlockState().is(EnchantingContent.SEAL.get()); }
    public boolean library() { return getBlockState().is(EnchantingContent.PORT.get()); }
    public boolean formed() { return formed; }
    public int pose() { return pose; }
    public long formedAt() { return formedAt; }
    public List<String> discTypes() {
        return level != null && level.isClientSide() ? visibleDiscs : discs.stream().map(SealDisc::type).toList();
    }
    int stoneStrength() { return EnchantingContent.stoneStrength(stone); }

    void exchangeStone(ServerPlayer player) {
        var hand = player.getMainHandItem();
        if (!hand.isEmpty() && EnchantingContent.stoneStrength(hand) == 0) return;
        var old = stone;
        stone = hand.isEmpty() ? ItemStack.EMPTY : hand.copyWithCount(1);
        if (!hand.isEmpty()) hand.shrink(1);
        if (!old.isEmpty()) player.getInventory().placeItemBackInInventory(old);
        setChanged();
    }

    ItemStack removeStone() {
        var old = stone;
        stone = ItemStack.EMPTY;
        return old;
    }

    CompoundTag ledgerCopy() { return ledger.copy(); }
    void replaceLedger(CompoundTag next) { ledger = next; setChanged(); }
    static int points(CompoundTag ledger, String key) { return ledger.getCompound(key).getInt("Points"); }
    static int recorded(CompoundTag ledger, String key) { return ledger.getCompound(key).getInt("Level"); }

    static boolean adjust(CompoundTag ledger, String key, int delta, int seenLevel) {
        int points = points(ledger, key) + delta;
        if (points < 0 || points > RULES.point_capacity()) return false;
        var row = new CompoundTag();
        row.putInt("Points", points);
        row.putInt("Level", Math.max(recorded(ledger, key), seenLevel));
        ledger.put(key, row);
        return true;
    }

    boolean verifyStructure() {
        if (level == null) return false;
        if (library()) return EnchantingStructure.matches(level,
                EnchantingStructure.libraryCenter(worldPosition, front()), front(), true);
        return seal() && formed && EnchantingStructure.checkSeal(level, worldPosition, front(), true).matched();
    }

    void form(ServerPlayer player) {
        if (!(level instanceof ServerLevel server) || formed || !seal()) return;
        var front = front();
        var check = EnchantingStructure.checkSeal(server, worldPosition, front, false);
        if (!check.matched()) {
            player.displayClientMessage(Component.literal(check.unloaded() ? "还有方块所在区块未加载。"
                    : "还有 " + check.missing() + " 处方块需要补齐或调整。"), true);
            return;
        }
        if (!mayAssemble(player, server, worldPosition.below())) return;
        for (var cell : EnchantingStructure.SEAL) {
            var at = EnchantingStructure.offset(worldPosition, front, cell.x(), cell.y(), cell.z());
            if (!mayAssemble(player, server, at)) return;
        }
        formed = true;
        for (int i = 0; i < EnchantingStructure.SEAL.size(); i++) {
            var cell = EnchantingStructure.SEAL.get(i);
            var at = EnchantingStructure.offset(worldPosition, front, cell.x(), cell.y(), cell.z());
            if (!server.setBlock(at, SealMemberBlock.part(i, front), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) {
                unform(null);
                return;
            }
        }
        var socket = server.getBlockState(worldPosition.below());
        if (!server.setBlock(worldPosition.below(), socket.setValue(EnchantingBlock.FORMED, true), Block.UPDATE_CLIENTS)
                || !server.setBlock(worldPosition, getBlockState().setValue(EnchantingBlock.FORMED, true), Block.UPDATE_CLIENTS)
                || !verifyStructure()) {
            unform(null);
            return;
        }
        sync();
        player.displayClientMessage(Component.literal("篆纹阵已成型。右键阵台可开始附魔。"), true);
    }

    private static boolean mayAssemble(ServerPlayer player, ServerLevel level, BlockPos at) {
        return level.mayInteract(player, at) && !EstateRuntime.blocked(player, level, at.getX() + .5, at.getZ() + .5);
    }

    void unform(BlockPos removed) {
        if (!(level instanceof ServerLevel server) || !seal() || !formed) return;
        var front = front();
        formed = false;
        pose = 0;
        if (!worldPosition.equals(removed) && server.getBlockState(worldPosition).is(EnchantingContent.SEAL.get())) {
            server.setBlock(worldPosition, getBlockState().setValue(EnchantingBlock.FORMED, false), Block.UPDATE_CLIENTS);
        }
        var socketPos = worldPosition.below();
        if (!socketPos.equals(removed) && server.isLoaded(socketPos)) {
            var socket = server.getBlockState(socketPos);
            if (socket.is(EnchantingContent.SOCKET.get()) && socket.getValue(EnchantingBlock.FORMED)) {
                server.setBlock(socketPos, socket.setValue(EnchantingBlock.FORMED, false), Block.UPDATE_CLIENTS);
            }
        }
        for (int i = 0; i < EnchantingStructure.SEAL.size(); i++) {
            var cell = EnchantingStructure.SEAL.get(i);
            var at = EnchantingStructure.offset(worldPosition, front, cell.x(), cell.y(), cell.z());
            if (!at.equals(removed) && server.isLoaded(at) && server.getBlockState(at) == SealMemberBlock.part(i, front)) {
                server.setBlock(at, EnchantingStructure.rawState(cell), Block.UPDATE_ALL);
            }
        }
        sync();
    }

    void useDisc(ServerPlayer player, int slot) {
        var hand = player.getMainHandItem();
        var old = discs.get(slot);
        if (hand.isEmpty() && !player.isShiftKeyDown()) {
            var disc = SealDisc.TYPES.get(SealDisc.type(old));
            player.displayClientMessage(Component.literal(SealProjection.slotName(slot) + "　"
                    + disc.name() + (old.isEmpty() ? "" : "　" + disc.description())), true);
            return;
        }
        if (!hand.isEmpty() && !SealDisc.fits(slot, hand)) {
            player.displayClientMessage(Component.literal("这张牒片不适用于" + SealProjection.slotName(slot) + "。"), true);
            return;
        }
        if (hand.isEmpty() && old.isEmpty()) return;
        discs.set(slot, hand.isEmpty() ? ItemStack.EMPTY : hand.copyWithCount(1));
        if (!hand.isEmpty() && !player.hasInfiniteMaterials()) hand.shrink(1);
        if (!old.isEmpty()) player.getInventory().placeItemBackInInventory(old);
        var next = SealDisc.TYPES.get(SealDisc.type(discs.get(slot)));
        player.displayClientMessage(Component.literal(SealProjection.slotName(slot) + "　" + next.name()), true);
        sync();
    }

    void dropDiscs(ServerLevel server) {
        for (int i = 0; i < discs.size(); i++) {
            var stack = discs.set(i, ItemStack.EMPTY);
            if (!stack.isEmpty()) Block.popResource(server, worldPosition, stack);
        }
    }

    void tick(ServerLevel server) {
        long time = server.getGameTime();
        if (Math.floorMod(time + worldPosition.asLong(), 20) == 0) {
            if (seal()) {
                if (formed && EnchantingStructure.checkSeal(server, worldPosition, front(), true).missing() != 0) unform(null);
            } else if (library()) {
                boolean ready = verifyStructure();
                if (ready != formed) {
                    formed = ready;
                    if (!formed) pose = 0;
                    sync();
                }
            }
        }
        if (pose != 0 && time >= phaseEnd) {
            pose = 0;
            sync();
        }
    }

    void animate(int target) {
        if (!(level instanceof ServerLevel server)) return;
        if (!verifyStructure()) return;
        formed = true;
        pose = target;
        phaseEnd = server.getGameTime() + (target == 1 ? 100 : 34);
        sync();
        server.sendParticles(target == 1 ? ParticleTypes.ENCHANT : ParticleTypes.END_ROD,
                worldPosition.getX() + .5, worldPosition.getY() + 1.45, worldPosition.getZ() + .5, 14, .36, .38, .36, .04);
    }

    private void sync() {
        if (level != null) setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!stone.isEmpty()) tag.put("Stone", stone.save(registries));
        if (library()) tag.put("Ledger", ledger.copy());
        if (seal()) {
            tag.putBoolean("Formed", getBlockState().getValue(EnchantingBlock.FORMED));
            var items = new ListTag();
            for (int i = 0; i < discs.size(); i++) if (!discs.get(i).isEmpty()) {
                var entry = new CompoundTag();
                entry.putInt("Slot", i);
                entry.put("Item", discs.get(i).save(registries));
                items.add(entry);
            }
            tag.put("Discs", items);
        }
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stone = ItemStack.parseOptional(registries, tag.getCompound("Stone"));
        ledger = tag.getCompound("Ledger").copy();
        if (seal()) {
            formed = tag.getBoolean("Formed") && getBlockState().getValue(EnchantingBlock.FORMED);
            Collections.fill(discs, ItemStack.EMPTY);
            for (var value : tag.getList("Discs", Tag.TAG_COMPOUND)) {
                var entry = (CompoundTag) value;
                int slot = entry.getInt("Slot");
                var stack = ItemStack.parseOptional(registries, entry.getCompound("Item"));
                if (SealDisc.fits(slot, stack)) discs.set(slot, stack.copyWithCount(1));
            }
        }
    }

    @Override protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (library() && !ledger.isEmpty()) {
            var tag = new CompoundTag();
            tag.put("LibraryLedger", ledger.copy());
            components.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    @Override protected void applyImplicitComponents(DataComponentInput components) {
        super.applyImplicitComponents(components);
        if (library()) ledger = components.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getCompound("LibraryLedger");
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putBoolean("Formed", formed);
        tag.putInt("Pose", pose);
        if (seal()) {
            var types = new ListTag();
            discTypes().forEach(type -> types.add(net.minecraft.nbt.StringTag.valueOf(type)));
            tag.put("DiscTypes", types);
        }
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        boolean before = formed;
        formed = tag.getBoolean("Formed");
        pose = tag.getInt("Pose");
        if (seal()) {
            var types = tag.getList("DiscTypes", Tag.TAG_STRING).stream().map(Tag::getAsString)
                    .map(type -> SealDisc.TYPES.containsKey(type) ? type : "empty").toList();
            if (types.size() == discs.size()) visibleDiscs = types;
            if (!before && formed && level != null) formedAt = level.getGameTime();
        } else if (before != formed) refreshLibraryModels();
    }

    @Override public void onDataPacket(net.minecraft.network.Connection connection,
            ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }

    @Override public void setRemoved() {
        formed = false;
        refreshLibraryModels();
        super.setRemoved();
    }

    @Override public void onChunkUnloaded() {
        if (library()) formed = false;
        refreshLibraryModels();
        super.onChunkUnloaded();
    }

    @Override public void onLoad() {
        super.onLoad();
        refreshLibraryModels();
    }

    private void refreshLibraryModels() {
        if (level == null || !level.isClientSide() || !library()) return;
        var center = EnchantingStructure.libraryCenter(worldPosition, front());
        for (var cell : EnchantingStructure.LIBRARY) {
            if (!cell.part().equals("outer") && !cell.part().equals("equator") && !cell.part().equals("oblique")) continue;
            var pos = EnchantingStructure.offset(center, front(), cell.x(), cell.y(), cell.z());
            if (!level.isLoaded(pos)) continue;
            var state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
