package com.example.myvillage.worldplan.atlas;

import java.util.Base64;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;

/** Bounded immutable read-only atlas. No seed, city templates, chunks or ledger are transmitted. */
public record WorldAtlas(Status status, String fingerprint, int resolution, int radius, int coreRadius,
                         List<Region> regions, List<City> cities, String raster) {
    public static final int RESOLUTION = 256;
    public static final int MAX_REGIONS = 16;
    public static final int MAX_CITIES = 8;
    public static final int MAX_TEXT = 96;
    public static final int MAX_RASTER_TEXT = ((RESOLUTION * RESOLUTION + 2) / 3) * 4;
    public enum Status { LOADING, READY, UNAVAILABLE, FAILED }
    public record Region(String id, String name, double x, double z) {
        public Region {
            Objects.requireNonNull(id); Objects.requireNonNull(name);
            if (id.isBlank() || name.isBlank() || id.length() > MAX_TEXT || name.length() > MAX_TEXT
                    || !Double.isFinite(x) || !Double.isFinite(z) || Math.abs(x) > 100000 || Math.abs(z) > 100000)
                throw new IllegalArgumentException("Invalid atlas region");
        }
    }
    public record City(String id, String name, String regionId, int x, int y, int z) {
        public City {
            Objects.requireNonNull(id); Objects.requireNonNull(name); Objects.requireNonNull(regionId);
            if(!id.matches("capital_[a-z0-9_]{1,48}")||name.isBlank()||name.length()>MAX_TEXT||!regionId.matches("[a-z0-9_]{1,48}")
                    ||Math.abs((long)x)>100000||Math.abs((long)z)>100000||y< -64||y>=320)throw new IllegalArgumentException("Invalid atlas capital");
        }
    }
    /** Existing legacy callers retain an empty capital overlay. */
    public WorldAtlas(Status status,String fingerprint,int resolution,int radius,int coreRadius,List<Region> regions,String raster) {
        this(status,fingerprint,resolution,radius,coreRadius,regions,List.of(),raster);
    }
    public WorldAtlas {
        Objects.requireNonNull(status); Objects.requireNonNull(fingerprint); Objects.requireNonNull(raster);
        regions = List.copyOf(regions);
        cities = List.copyOf(cities);
        if (status == Status.READY) {
            if (!fingerprint.matches("[0-9a-f]{64}") || resolution != RESOLUTION || radius < 1000 || radius > 100000
                    || coreRadius <= 0 || coreRadius > radius || regions.isEmpty() || regions.size() > MAX_REGIONS
                    || new HashSet<>(regions.stream().map(Region::id).toList()).size() != regions.size()
                    || raster.length() != MAX_RASTER_TEXT) throw new IllegalArgumentException("Invalid atlas bounds");
            byte[] cells = Base64.getDecoder().decode(raster);
            if (cells.length != RESOLUTION * RESOLUTION) throw new IllegalArgumentException("Invalid atlas raster");
            for (byte cell : cells) if ((cell & 127) > regions.size()) throw new IllegalArgumentException("Unknown atlas region");
            if(cities.size()>MAX_CITIES||new HashSet<>(cities.stream().map(City::id).toList()).size()!=cities.size()
                    ||new HashSet<>(cities.stream().map(City::regionId).toList()).size()!=cities.size())throw new IllegalArgumentException("Invalid atlas city count/duplicates");
            for(var city:cities)if(Math.abs(city.x())>radius||Math.abs(city.z())>radius
                    ||regions.stream().noneMatch(r->r.id().equals(city.regionId())))throw new IllegalArgumentException("Atlas capital outside declared regions");
        } else if (!fingerprint.isEmpty() || resolution != 0 || radius != 0 || coreRadius != 0 || !regions.isEmpty() || !cities.isEmpty() || !raster.isEmpty()) {
            throw new IllegalArgumentException("Non-ready atlas has no geometry");
        }
    }
    public static WorldAtlas state(Status status) { return new WorldAtlas(status, "", 0, 0, 0, List.of(), ""); }
    public byte[] cells() { return Base64.getDecoder().decode(raster); }
    public String regionAt(double x, double z) {
        if (status != Status.READY || Math.abs(x) > radius || Math.abs(z) > radius) return "";
        int ix=(int)Math.round((x+radius)/(2.0*radius)*(resolution-1));
        int iz=(int)Math.round((z+radius)/(2.0*radius)*(resolution-1));
        int index=cells()[iz*resolution+ix]&127;
        return index==0 ? "" : regions.get(index-1).name();
    }
}
