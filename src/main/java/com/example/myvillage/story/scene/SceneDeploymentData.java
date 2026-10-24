package com.example.myvillage.story.scene;

import java.io.IOException;
import java.nio.file.Files;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;

/** One scene per save, with the dimension recorded explicitly. No restart template replay. */
final class SceneDeploymentData extends SavedData {
    static final String NAME = "myvillage_qianzhen_scene";
    enum Phase { EMPTY, PLACING, COMPLETE, FAILED }
    private Phase phase = Phase.EMPTY;
    private String dimension = "", id = "", version = "";
    private BlockPos origin = BlockPos.ZERO;
    private boolean regulated;
    private String frozenGeometry = "";
    private SceneLayout cachedGeometry;

    static SceneDeploymentData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(() -> fresh(level), SceneDeploymentData::load), NAME);
    }
    private static SceneDeploymentData fresh(ServerLevel level) {
        // DimensionDataStorage logs deserialization failures and then calls the supplier.
        // An existing unreadable record must never silently become an empty deployable world.
        if (Files.exists(level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve(NAME+".dat")))
            throw new IllegalStateException("Unreadable Qianzhen deployment record; restore the world backup");
        return new SceneDeploymentData();
    }
    static SceneDeploymentData load(CompoundTag tag, HolderLookup.Provider registries) {
        SceneDeploymentData data = new SceneDeploymentData();
        // Malformed data locks deployment, rather than pretending that no occupied scene exists.
        if (!tag.contains("schema", Tag.TAG_INT) || (tag.getInt("schema") != 1 && tag.getInt("schema") != 2))
            throw new IllegalStateException("Unsupported Qianzhen deployment save");
        for (String key : new String[]{"phase", "dimension", "id", "version"})
            if (!tag.contains(key, Tag.TAG_STRING)) throw new IllegalStateException("Missing Qianzhen saved text: "+key);
        for (String key : new String[]{"x", "y", "z"})
            if (!tag.contains(key, Tag.TAG_INT)) throw new IllegalStateException("Missing Qianzhen saved coordinate: "+key);
        if (!tag.contains("regulated", Tag.TAG_BYTE)) throw new IllegalStateException("Missing Qianzhen shared fact");
        try { data.phase = Phase.valueOf(tag.getString("phase")); }
        catch (IllegalArgumentException invalid) { throw new IllegalStateException("Invalid Qianzhen deployment phase", invalid); }
        data.dimension = tag.getString("dimension"); data.id = tag.getString("id"); data.version = tag.getString("version");
        if (tag.getInt("schema") == 2) {
            if (!tag.contains("geometry", Tag.TAG_STRING)) throw new IllegalStateException("Missing frozen Qianzhen geometry");
            data.frozenGeometry = tag.getString("geometry");
            if (!data.frozenGeometry.isEmpty()) {
                data.cachedGeometry = SceneGeometryArchive.thaw(data.frozenGeometry);
                if (!data.cachedGeometry.version().equals(data.version)) throw new IllegalStateException("Frozen Qianzhen geometry identity differs");
            }
        }
        data.origin = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")); data.regulated = tag.getBoolean("regulated");
        if (data.phase != Phase.EMPTY && (!data.dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || !data.id.equals("qianzhen_chapter_1") || !data.version.matches("[A-Za-z0-9_.-]{1,80}")))
            throw new IllegalStateException("Incomplete Qianzhen deployment identity");
        if (data.phase == Phase.EMPTY && (!data.dimension.isEmpty() || !data.id.isEmpty() || !data.version.isEmpty()
                || !data.origin.equals(BlockPos.ZERO))) throw new IllegalStateException("Occupied Qianzhen scene marked empty");
        if (data.regulated && data.phase != Phase.COMPLETE) throw new IllegalStateException("Regulated incomplete Qianzhen scene");
        return data;
    }
    SceneLayout geometry(SceneLayout current) {
        if (phase == Phase.EMPTY) return current;
        if (cachedGeometry != null) return cachedGeometry;
        return version.equals(current.version()) ? current : SceneGeometryArchive.legacy(version);
    }

    Phase phase() { return phase; }
    String dimension() { return dimension; }
    String id() { return id; }
    String version() { return version; }
    BlockPos origin() { return origin; }
    boolean regulated() { return regulated; }

    void begin(ServerLevel level, SceneLayout layout, BlockPos at) {
        if (phase != Phase.EMPTY) throw new IllegalStateException("Scene already reserved");
        String encoded = SceneGeometryArchive.freeze(layout); // bounded before changing the receipt
        dimension = level.dimension().location().toString(); id = layout.id(); version = layout.version();
        frozenGeometry = encoded; cachedGeometry = layout;
        origin = at.immutable(); phase = Phase.PLACING; setDirty();
    }
    void complete() {
        if (phase != Phase.PLACING) throw new IllegalStateException("Scene not being placed");
        phase = Phase.COMPLETE; setDirty();
    }
    void failed() { phase = Phase.FAILED; setDirty(); }
    boolean markRegulated() {
        if (phase != Phase.COMPLETE) throw new IllegalStateException("Scene not complete");
        if (regulated) return false;
        regulated = true; setDirty(); return true;
    }
    void undoRegulated() { regulated = false; setDirty(); }

    /** A durable reservation precedes the first block write. Normal async SavedData writes are drained first. */
    void checkpoint(ServerLevel level) throws IOException {
        IOUtilities.waitUntilIOWorkerComplete();
        var folder = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data");
        Files.createDirectories(folder);
        CompoundTag root = new CompoundTag(); root.put("data", save(new CompoundTag(), level.registryAccess()));
        NbtUtils.addCurrentDataVersion(root);
        IOUtilities.writeNbtCompressed(root, folder.resolve(NAME+".dat"));
        setDirty(false);
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("schema", 2); tag.putString("geometry", frozenGeometry); tag.putString("phase", phase.name()); tag.putString("dimension", dimension);
        tag.putString("id", id); tag.putString("version", version); tag.putInt("x", origin.getX());
        tag.putInt("y", origin.getY()); tag.putInt("z", origin.getZ()); tag.putBoolean("regulated", regulated); return tag;
    }
}
