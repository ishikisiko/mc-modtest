package com.example.myvillage.sim.life;

import java.util.Set;

/** A stable activity slot. Position is a person's feet; an optional fixture has its own position. */
public record LifePlace(String id, String kind, int x, int y, int z, int capacity,
                        String block, int blockX, int blockY, int blockZ) {
    public static final Set<String> KINDS = Set.of("rest", "work", "gather", "market", "social", "entry", "cultivate", "garden");

    public LifePlace {
        requireId(id);
        if (!KINDS.contains(kind)) throw new IllegalArgumentException("Unknown life place kind: " + kind);
        if (capacity < 1 || capacity > 64) throw new IllegalArgumentException("Place capacity must be 1..64");
        requirePosition(x, y, z);
        requirePosition(blockX, blockY, blockZ);
        if (block == null || (!block.isEmpty() && !block.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))) {
            throw new IllegalArgumentException("Invalid fixture block id");
        }
    }

    public static LifePlace free(String id, String kind, int x, int y, int z) {
        return new LifePlace(id, kind, x, y, z, 1, "", x, y, z);
    }

    public static void requireId(String id) {
        if (id == null || !id.matches("[a-zA-Z0-9_.:/-]{1,96}")) throw new IllegalArgumentException("Invalid life id: " + id);
    }

    public static void requirePosition(int x, int y, int z) {
        if (Math.abs((long) x) > 30_000_000 || Math.abs((long) z) > 30_000_000 || y < -2048 || y > 2048) {
            throw new IllegalArgumentException("Life position is outside supported world bounds");
        }
    }
}
