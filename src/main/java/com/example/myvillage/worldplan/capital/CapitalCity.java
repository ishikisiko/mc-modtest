package com.example.myvillage.worldplan.capital;

import com.example.myvillage.worldplan.WorldPlan;
import com.google.gson.JsonObject;

/** One real city origin; every consumer shares this placement, terrain and reservation. */
public record CapitalCity(CapitalGameBundle bundle, int x, int originY, int z, int structureBuffer) {
    public String cityId() { return bundle.cityId; }
    public String regionId() { return bundle.regionId; }
    public int margin() { return bundle.blendMargin + structureBuffer; }
    public boolean contains(int wx, int wz, int extra) {
        return wx >= x - extra && wx < x + bundle.width + extra && wz >= z - extra && wz < z + bundle.depth + extra;
    }
    public boolean intersects(int minX, int minZ, int maxX, int maxZ, int extra) {
        return minX < x + bundle.width + extra && maxX >= x - extra && minZ < z + bundle.depth + extra && maxZ >= z - extra;
    }
    public double distance(double wx, double wz) {
        double dx = Math.max(Math.max(x - wx, wx - (x + bundle.width - 1)), 0);
        double dz = Math.max(Math.max(z - wz, wz - (z + bundle.depth - 1)), 0);
        return StrictMath.hypot(dx, dz);
    }
    public int surface(int wx, int wz) { return originY + bundle.topSolid(wx - x, wz - z) + 1; }
    public WorldPlan.Position entrance() {
        var e = bundle.entrance(); return new WorldPlan.Position(x + e.x(), originY + e.y(), z + e.z());
    }
    public JsonObject manifest() {
        var json = new JsonObject(); json.addProperty("city_id", cityId()); json.addProperty("region_id", regionId());
        json.addProperty("display_name", bundle.displayName); json.addProperty("x", x); json.addProperty("origin_y", originY); json.addProperty("z", z);
        json.addProperty("width", bundle.width); json.addProperty("height", bundle.height); json.addProperty("depth", bundle.depth);
        json.addProperty("blend_margin", bundle.blendMargin); json.addProperty("structure_buffer", structureBuffer);
        json.addProperty("layout_family_id", bundle.layoutFamily); json.addProperty("layout_id", bundle.lifeLayout);
        json.addProperty("layout_sha256", bundle.bundleHash); json.addProperty("tiles", bundle.tiles.size());
        var at = entrance(); var entry = new JsonObject(); entry.addProperty("x", at.x()); entry.addProperty("y", at.y()); entry.addProperty("z", at.z());
        json.add("entry", entry); return json;
    }
}
