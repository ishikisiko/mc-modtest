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
 * the build seed, blueprint identity and fingerprint, and the sim day. Avatars stand on the courtyards of a record
 * only while the ledger agrees with it (sect active, gate realized, same x/z).
 */
public final class GateRealizations extends SavedData {
    public static final String DATA_NAME = "myvillage_world_sim_gates";
    public static final int FORMAT = 2;
    static final String FORMAT_TAG = "format";
    static final String GATES_TAG = "gates";

    private final TreeMap<Integer, Gate> gates = new TreeMap<>();

    /** One built compound. {@code variant} is the compiled blueprint resource id. */
    public record Gate(int sectId, BlockPos anchor, long seed, String variant, long day, String blueprintHash) {
        public Gate {
            if (sectId<0 || !variant.matches("myvillage:[a-z0-9_]+") || !blueprintHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("宗门蓝图收据无效");
            anchor=anchor.immutable();
        }
        public boolean matchesCurrentBlueprint() {
            var data=com.example.myvillage.sect.SectBlueprint.get(variant);
            return variant.equals(data.id.toString()) && blueprintHash.equals(data.fingerprint);
        }
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("sect", sectId);
            tag.putInt("x", anchor.getX());
            tag.putInt("y", anchor.getY());
            tag.putInt("z", anchor.getZ());
            tag.putLong("seed", seed);
            tag.putString("variant", variant);
            tag.putLong("day", day);
            tag.putString("blueprint_hash",blueprintHash);
            return tag;
        }

        static Gate load(CompoundTag tag) {
            return new Gate(tag.getInt("sect"), new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
                    tag.getLong("seed"), tag.getString("variant"), tag.getLong("day"),tag.getString("blueprint_hash"));
        }
    }

    public GateRealizations() {
    }

    public static GateRealizations get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(GateRealizations::new, GateRealizations::load), DATA_NAME);
    }

    static GateRealizations load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt(FORMAT_TAG)!=FORMAT) throw new IllegalArgumentException("宗门记录格式不支持，请使用当前开发版新世界");
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
