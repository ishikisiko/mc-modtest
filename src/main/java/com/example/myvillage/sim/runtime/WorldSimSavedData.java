package com.example.myvillage.sim.runtime;

import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The world ledger's save on the overworld ({@code data/myvillage_world_sim.dat}). The core's
 * {@code WorldSim.toBytes()} payload is stored verbatim as a byte array; the settlement scheduler
 * lives inside that payload. Outside it only a format version, the tier and a {@code paused}
 * mirror are kept, so a world whose payload cannot be restored still reports what it is.
 *
 * <p>While a live world is attached, every save serialises it afresh; otherwise the bytes that were
 * loaded are written back unchanged, so a payload this version cannot read (a newer format) is never
 * overwritten.
 */
public final class WorldSimSavedData extends SavedData {
    public static final String DATA_NAME = "myvillage_world_sim";
    /** Version of this NBT wrapper (not of the core payload, which carries its own). */
    public static final int FORMAT = 1;

    static final String FORMAT_TAG = "format";
    static final String TIER_TAG = "tier";
    static final String PAUSED_TAG = "paused";
    static final String PAYLOAD_TAG = "payload";

    private final boolean loadedFromDisk;
    private int format = FORMAT;
    private String tier = "";
    private boolean paused;
    private byte[] payload;
    /** The tag as loaded, written back verbatim until a live world takes over. */
    private CompoundTag loaded;
    private Supplier<Snapshot> live;

    /** What a save writes when a live world is attached. */
    public record Snapshot(String tier, boolean paused, byte[] payload) {
    }

    public WorldSimSavedData() {
        this.loadedFromDisk = false;
    }

    private WorldSimSavedData(CompoundTag tag) {
        this.loadedFromDisk = true;
        this.format = tag.contains(FORMAT_TAG, Tag.TAG_INT) ? tag.getInt(FORMAT_TAG) : 0;
        this.tier = tag.getString(TIER_TAG);
        this.paused = tag.getBoolean(PAUSED_TAG);
        this.payload = tag.contains(PAYLOAD_TAG, Tag.TAG_BYTE_ARRAY) ? tag.getByteArray(PAYLOAD_TAG) : null;
        this.loaded = tag.copy();
    }

    public static WorldSimSavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(WorldSimSavedData::new, WorldSimSavedData::load), DATA_NAME);
    }

    static WorldSimSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        return new WorldSimSavedData(tag);
    }

    /** True when this instance was read from disk (false: created fresh because no file was readable). */
    public boolean loadedFromDisk() {
        return loadedFromDisk;
    }

    /** The wrapper format the file was written with (0 when missing). */
    public int format() {
        return format;
    }

    public boolean newerFormat() {
        return format > FORMAT;
    }

    public String tier() {
        return tier;
    }

    public boolean pausedMirror() {
        return paused;
    }

    /** The stored core payload, or null when this world has no ledger yet. */
    public byte[] payload() {
        return payload == null ? null : payload.clone();
    }

    public boolean hasPayload() {
        return payload != null && payload.length > 0;
    }

    /** Binds the live world: from now on each save writes {@code snapshot.get()}. */
    public void attach(Supplier<Snapshot> snapshot) {
        this.live = snapshot;
        this.format = FORMAT;
        setDirty();
    }

    /**
     * Unbinds the live world and keeps the last checkpointed bytes (used when settlement failed
     * part-way: the half-settled state is never written).
     */
    public void detach() {
        this.live = null;
    }

    public boolean attached() {
        return live != null;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Supplier<Snapshot> source = live;
        if (source != null) {
            Snapshot s = source.get();
            tier = s.tier();
            paused = s.paused();
            payload = s.payload();
            loaded = null;
        } else if (loaded != null) {
            return tag.merge(loaded.copy());
        }
        tag.putInt(FORMAT_TAG, format);
        tag.putString(TIER_TAG, tier);
        tag.putBoolean(PAUSED_TAG, paused);
        if (payload != null) {
            tag.putByteArray(PAYLOAD_TAG, payload);
        }
        return tag;
    }
}
