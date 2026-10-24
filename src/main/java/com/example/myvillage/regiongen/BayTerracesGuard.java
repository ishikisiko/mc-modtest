package com.example.myvillage.regiongen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Reservations exist only during decoration. Player writes never pass through this guard. */
public final class BayTerracesGuard {
    public record Reservation(int minX, int minY, int minZ, int maxX, int maxZ) {
        public boolean contains(BlockPos at) {
            return at.getX() >= minX && at.getX() <= maxX && at.getZ() >= minZ && at.getZ() <= maxZ && at.getY() >= minY;
        }
    }
    private static final ThreadLocal<Scope> DECORATING = new ThreadLocal<>();
    private static final ThreadLocal<OwnedSlice> OWNED = new ThreadLocal<>();
    private BayTerracesGuard() {}

    public static Scope decorating(WorldGenLevel level, ChunkAccess center) {
        List<Reservation> reservations = new ArrayList<>();
        if (level instanceof WorldGenRegion region) {
            var seen = new HashSet<Long>();var cp = center.getPos();
            // FEATURES already requires CARVERS in this 3x3 area and STRUCTURE_STARTS within eight chunks.
            // A core's radius is at most six chunks, so neighbouring writes never need an uncached start.
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                ChunkAccess chunk = region.getChunk(cp.x + dx, cp.z + dz, ChunkStatus.STRUCTURE_REFERENCES, false);
                if (chunk == null) continue;
                for (var entry : chunk.getAllReferences().entrySet()) {
                    if (!(entry.getKey() instanceof BayTerracesStructure)) continue;
                    for (long reference : entry.getValue()) {
                        if (!seen.add(reference)) continue;
                        ChunkPos pos = new ChunkPos(reference);
                        if (!region.hasChunk(pos.x, pos.z)) continue;
                        var startChunk = region.getChunk(pos.x, pos.z, ChunkStatus.STRUCTURE_STARTS, false);
                        var start = startChunk == null ? null : startChunk.getStartForStructure(entry.getKey());
                        if (start != null && start.isValid()) for (var piece : start.getPieces()) {
                            if (piece instanceof BayTerracesPiece bay) reservations.add(bay.reservation());
                        }
                    }
                }
            }
        }
        return reserve(level, reservations);
    }

    static Scope reserve(WorldGenLevel level, List<Reservation> reservations) {
        Scope scope = new Scope(level, List.copyOf(reservations), DECORATING.get());
        DECORATING.set(scope);return scope;
    }

    public static boolean allows(WorldGenRegion level, BlockPos at) {
        OwnedSlice own = OWNED.get();
        if (own != null && own.world == level) return own.clip.isInside(at);
        Scope scope = DECORATING.get();
        return scope == null || scope.world != level || !scope.contains(at);
    }

    public static boolean allowsOre(BlockPos at) {
        Scope scope = DECORATING.get();
        return scope == null || !scope.contains(at);
    }

    public static OwnedSlice ownSlice(WorldGenLevel world, BoundingBox clip) {
        OwnedSlice scope = new OwnedSlice(world, clip, OWNED.get());OWNED.set(scope);return scope;
    }

    public static final class Scope implements AutoCloseable {
        private final WorldGenLevel world;
        private final List<Reservation> reservations;
        private final Scope previous;
        private Scope(WorldGenLevel world, List<Reservation> reservations, Scope previous) {
            this.world=world;this.reservations=reservations;this.previous=previous;
        }
        private boolean contains(BlockPos at) {
            for (int i=0;i<reservations.size();i++) if (reservations.get(i).contains(at)) return true;
            return false;
        }
        @Override public void close() { if (previous == null) DECORATING.remove();else DECORATING.set(previous); }
    }
    public static final class OwnedSlice implements AutoCloseable {
        private final WorldGenLevel world;
        private final BoundingBox clip;
        private final OwnedSlice previous;
        private OwnedSlice(WorldGenLevel world, BoundingBox clip, OwnedSlice previous) {
            this.world=world;this.clip=clip;this.previous=previous;
        }
        @Override public void close() { if (previous == null) OWNED.remove();else OWNED.set(previous); }
    }
}
