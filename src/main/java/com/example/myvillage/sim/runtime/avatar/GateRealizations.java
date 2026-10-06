package com.example.myvillage.sim.runtime.avatar;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Where each sect's compound (山门) was actually built, on the overworld
 * ({@code data/myvillage_world_sim_gates.dat}). The ledger only knows a gate's x/z and a
 * {@code gateRealized} flag; the build command records here what it built: the anchor with its y,
 * the build seed and spire variant, and the sim day. Avatars stand on the courtyards of a record
 * only while the ledger agrees with it (sect active, gate realized, same x/z).
 */
public final class GateRealizations extends SavedData {
    public static final String DATA_NAME = "myvillage_world_sim_gates";
    public static final int FORMAT = 1;
    static final String FORMAT_TAG = "format";
    static final String GATES_TAG = "gates";

    private final TreeMap<Integer, Gate> gates = new TreeMap<>();

    /** One built compound. {@code variant} is the forced spire variant ({@code "none"} or a variant name). */
    public record Gate(int sectId, BlockPos anchor, long seed, String variant, long day) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("sect", sectId);
            tag.putInt("x", anchor.getX());
            tag.putInt("y", anchor.getY());
            tag.putInt("z", anchor.getZ());
            tag.putLong("seed", seed);
            tag.putString("variant", variant);
            tag.putLong("day", day);
            return tag;
        }

        static Gate load(CompoundTag tag) {
            return new Gate(tag.getInt("sect"), new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
                    tag.getLong("seed"), tag.getString("variant"), tag.getLong("day"));
        }
    }

    public GateRealizations() {
    }

    public static GateRealizations get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(GateRealizations::new, GateRealizations::load), DATA_NAME);
    }

    static GateRealizations load(CompoundTag tag, HolderLookup.Provider registries) {
        GateRealizations out = new GateRealizations();
        ListTag list = tag.getList(GATES_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Gate gate = Gate.load(list.getCompound(i));
            out.gates.put(gate.sectId(), gate);
        }
        return out;
    }

    public Optional<Gate> gate(int sectId) {
        return Optional.ofNullable(gates.get(sectId));
    }

    /** All records in sect id order. */
    public Collection<Gate> all() {
        return Collections.unmodifiableCollection(gates.values());
    }

    public void put(Gate gate) {
        gates.put(gate.sectId(), gate);
        setDirty();
    }

    public void remove(int sectId) {
        if (gates.remove(sectId) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(FORMAT_TAG, FORMAT);
        ListTag list = new ListTag();
        for (Gate gate : gates.values()) {
            list.add(gate.save());
        }
        tag.put(GATES_TAG, list);
        return tag;
    }
}
