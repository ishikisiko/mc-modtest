package com.example.myvillage.worldplan.capital;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Bounded orchestration for a future algorithm. Candidate generation and real terrain fitting are
 * mandatory caller-owned ports. There is no default terrain, hidden fallback, or production hook.
 */
public final class CapitalPlanner {
    private CapitalPlanner() {}

    public record RegionContext(CapitalRegion regionId, int assignedLevel, CapitalGeometry.Bounds searchBounds) {
        public RegionContext {
            Objects.requireNonNull(regionId); Objects.requireNonNull(searchBounds);
            if (assignedLevel < 1) throw new IllegalArgumentException("州域随机等级必须为正数");
        }
    }

    /** Every limit and reservation margin must be supplied from the future version's frozen config. */
    public record Budget(int maxCandidatesPerCity, int maxTerrainSamplesPerCandidate, int reservationPadding) {
        public Budget {
            if (maxCandidatesPerCity < 1 || maxTerrainSamplesPerCandidate < 1 || reservationPadding < 0)
                throw new IllegalArgumentException("主城规划必须有正的候选/地形预算与非负预留边距");
            try { Math.multiplyExact(Math.multiplyExact((long) maxCandidatesPerCity, maxTerrainSamplesPerCandidate),
                    CapitalRegion.values().length); }
            catch (ArithmeticException failure) { throw new IllegalArgumentException("主城规划总预算溢出", failure); }
        }
    }

    public record Request(long worldSeed, CapitalCatalog catalog, List<RegionContext> coreRegions,
                          List<CapitalGeometry.Bounds> existingReservations, Budget budget) {
        public Request {
            Objects.requireNonNull(catalog); Objects.requireNonNull(budget);
            coreRegions = List.copyOf(coreRegions); existingReservations = List.copyOf(existingReservations);
            var ids = new HashSet<CapitalRegion>();
            for (RegionContext region : coreRegions) if (!ids.add(region.regionId))
                throw new IllegalArgumentException("主城规划核心州域重复");
            if (ids.size() != CapitalRegion.values().length)
                throw new IllegalArgumentException("新主城规划必须接收八州；不能重算五至七州旧档");
        }
    }

    @FunctionalInterface
    public interface CandidateSource {
        /** Attempts are 0..maxCandidatesPerCity-1; empty consumes one attempt, never skips the city. */
        Optional<CapitalCandidate> propose(Request request, CapitalCatalog.Definition definition,
                                            RegionContext region, int attempt);
    }

    public sealed interface Result permits Success, Failure {}
    public record Success(CapitalCities cities, long candidatesUsed, long terrainSamplesUsed) implements Result {
        public Success {
            Objects.requireNonNull(cities);
            if (candidatesUsed < cities.cities().size() || terrainSamplesUsed < cities.cities().size())
                throw new IllegalArgumentException("八城结果不能缺少实际候选和地形预算耗用");
        }
    }
    public record CityFailure(String cityId, CapitalRegion regionId, int attempts, String lastReason) {
        public CityFailure {
            CapitalJson.id(cityId); Objects.requireNonNull(regionId);
            if (attempts < 1 || lastReason == null || lastReason.isBlank())
                throw new IllegalArgumentException("主城失败必须保留有限尝试次数和原因");
        }
    }
    /** Partial accepted candidates are intentionally not publishable through a failed result. */
    public record Failure(List<CityFailure> missingCities, long candidatesUsed, long terrainSamplesUsed) implements Result {
        public Failure {
            missingCities = List.copyOf(missingCities);
            if (missingCities.isEmpty() || candidatesUsed < 1 || terrainSamplesUsed < 0)
                throw new IllegalArgumentException("失败结果必须保留缺城身份与实际预算耗用");
        }
    }

    public static Result plan(Request request, CandidateSource source, CapitalTerrain terrain) {
        Objects.requireNonNull(source); Objects.requireNonNull(terrain);
        var regions = new EnumMap<CapitalRegion, RegionContext>(CapitalRegion.class);
        for (RegionContext region : request.coreRegions) regions.put(region.regionId, region);
        var accepted = new ArrayList<PlannedCapital>();
        var reservations = new ArrayList<>(request.existingReservations);
        var missing = new ArrayList<CityFailure>();
        long candidatesUsed = 0, samplesUsed = 0;
        for (var definition : request.catalog.definitions()) {
            RegionContext region = regions.get(definition.regionId());
            String lastReason = "尚无候选";
            PlannedCapital chosen = null;
            int attempts = 0;
            while (attempts < request.budget.maxCandidatesPerCity) {
                Optional<CapitalCandidate> proposal = source.propose(request, definition, region, attempts);
                attempts++; candidatesUsed++;
                if (proposal.isEmpty()) { lastReason = "候选提供器未给出城址"; continue; }
                CapitalCandidate candidate = proposal.get();
                String metadataProblem = metadataProblem(definition, candidate);
                if (metadataProblem != null) { lastReason = metadataProblem; continue; }
                CapitalGeometry.Bounds reserved = candidate.worldBounds().paddedHorizontally(request.budget.reservationPadding);
                if (!region.searchBounds.contains(reserved)) { lastReason = "候选预留超出显式搜索包络"; continue; }
                if (reservations.stream().anyMatch(reserved::intersectsHorizontally)) {
                    lastReason = "候选与已有空间预留相交"; continue;
                }
                CapitalTerrain.Assessment fit = Objects.requireNonNull(terrain.assess(definition, candidate, region,
                        reserved, request.budget.maxTerrainSamplesPerCandidate));
                if (fit.samplesUsed() > request.budget.maxTerrainSamplesPerCandidate)
                    throw new IllegalStateException("地形适配器超出候选预算: " + definition.cityId());
                samplesUsed += fit.samplesUsed();
                if (fit.outcome() != CapitalTerrain.Outcome.FIT) {
                    lastReason = fit.outcome() + ": " + fit.detail(); continue;
                }
                chosen = new PlannedCapital(definition.cityId(), definition.regionId(), region.assignedLevel,
                        definition.appearanceId(), candidate.layout(), candidate.placement(), candidate.manifest(), reserved, fit);
                accepted.add(chosen); reservations.add(reserved); break;
            }
            if (chosen == null) missing.add(new CityFailure(definition.cityId(), definition.regionId(), attempts, lastReason));
        }
        if (!missing.isEmpty()) return new Failure(missing, candidatesUsed, samplesUsed);
        return new Success(new CapitalCities(request.catalog, accepted), candidatesUsed, samplesUsed);
    }

    static String metadataProblem(CapitalCatalog.Definition definition, CapitalCandidate candidate) {
        CapitalManifest manifest = candidate.manifest();
        if (!definition.cityId().equals(manifest.cityId()) || definition.regionId() != manifest.regionId())
            return "候选州域或城市身份不匹配";
        var extension = manifest.toJson();
        // The catalogue owns a layout family; height profiles have separate concrete identities.
        if (extension.has("layout_family_id")) {
            if (!textEquals(extension, "layout_family_id", definition.layoutId())) return "布局族与目录不匹配";
            if (!extension.has("layout_id") || !extension.has("layout_sha256")) return "具体布局身份不完整";
        } else if (!definition.layoutId().equals(candidate.layout().layoutId())) return "候选布局身份不匹配";
        if (extension.has("layout_id") && !textEquals(extension, "layout_id", candidate.layout().layoutId()))
            return "具体布局 ID 与候选不匹配";
        if (extension.has("layout_sha256") && !textEquals(extension, "layout_sha256", candidate.layout().sha256()))
            return "具体布局摘要与候选不匹配";
        if (extension.has("appearance_id") && !textEquals(extension, "appearance_id", definition.appearanceId())) return "外观扩展身份不匹配";
        var bounds = manifest.bounds();
        if (bounds.min().x() == bounds.max().x() || bounds.min().z() == bounds.max().z()
                || bounds.min().y() == bounds.max().y()) return "候选城区包络没有三维体积";
        if (manifest.districts().isEmpty() || manifest.roads().isEmpty() || manifest.buildings().isEmpty()
                || manifest.entries().isEmpty()) return "预览缺少城区、道路、建筑或出入口，不能作为完整主城";
        var roles = new HashSet<String>();
        for (var district : manifest.districts()) if (!bounds.contains(district.bounds())) return "城区超出城市包络";
        for (var road : manifest.roads()) for (var point : road.points())
            if (!bounds.contains(point)) return "道路点超出城市包络";
        for (var building : manifest.buildings()) if (!bounds.contains(building.position())) return "建筑原点超出城市包络";
        for (var entry : manifest.entries()) if (!bounds.contains(entry.position())) return "入口超出城市包络";
        for (var service : manifest.services()) {
            if (!bounds.contains(service.position())) return "服务锚点超出城市包络";
            roles.add(service.role());
        }
        if (!roles.containsAll(definition.requiredServiceRoles())) return "服务职责尚不齐全";
        return null;
    }

    private static boolean textEquals(com.google.gson.JsonObject object, String key, String expected) {
        var value = object.get(key);
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && expected.equals(value.getAsString());
    }
}
