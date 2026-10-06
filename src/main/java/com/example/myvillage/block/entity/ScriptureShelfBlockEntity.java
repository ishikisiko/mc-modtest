package com.example.myvillage.block.entity;

import com.example.myvillage.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The scripture shelf's (经架) only state: the ledger sect it belongs to, {@code -1} for an
 * ownerless shelf (one placed by hand from the creative tab). Written by the shelf placement
 * ({@code ScriptureShelves}) and read by {@code ScriptureHall}; saved as {@code Sect}.
 */
public final class ScriptureShelfBlockEntity extends BlockEntity {
    /** NBT key of the owning sect id. */
    public static final String TAG_SECT = "Sect";

    private int sectId = -1;

    public ScriptureShelfBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCRIPTURE_SHELF.get(), pos, state);
    }

    /** The owning sect's ledger id, or -1 when the shelf has no owner. */
    public int sectId() {
        return sectId;
    }

    /** Sets the owning sect (-1 for none) and marks the chunk for saving. */
    public void setSectId(int sectId) {
        this.sectId = sectId;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_SECT, sectId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        sectId = tag.contains(TAG_SECT) ? tag.getInt(TAG_SECT) : -1;
    }
}
