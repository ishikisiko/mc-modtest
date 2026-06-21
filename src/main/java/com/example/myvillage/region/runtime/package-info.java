/**
 * Runtime companion to the offline region-topology generator
 * ({@code tools/buildgen/region_topology.py}).
 *
 * <p>This package ports the per-seed region-graph generator to Java so the
 * region layer is queryable in-game: the anchor (中州) is placed at the world
 * origin, world spawn is bound to the lowest-tier eligible region, and a
 * query API ({@code region_at} / {@code current_rung} / {@code next_rung_regions})
 * exposes the player's position on the tier ladder for downstream consumers
 * (compass / map / alignment / mobility — all deferred to future changes).
 *
 * <p>The offline Python module remains the single source of truth for the
 * algorithm; the Java mirror reproduces it bit-identically per seed, enforced
 * by golden fixtures under {@code src/test/resources/region_runtime_fixtures/}.
 * The runtime is passive: it reads the world seed, computes and caches the
 * graph, answers queries, and calls {@code setSpawnPos} exactly once per world.
 * It does not write terrain, override biomes, or hook chunk generation.
 *
 * <p>Landing in change {@code add-region-runtime-binding}.
 */
package com.example.myvillage.region.runtime;
