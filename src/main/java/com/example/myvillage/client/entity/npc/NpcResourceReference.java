package com.example.myvillage.client.entity.npc;

import com.example.myvillage.entity.beast.BeastDataException;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Function;
import java.util.regex.Pattern;

/** One-hop NPC packaging indirection; inline resource-pack replacements continue to win at the old path. */
public final class NpcResourceReference {
    private static final Pattern SHARED = Pattern.compile("npc/shared/[0-9a-f]{64}_(model|animations)\\.json");
    private NpcResourceReference() { }

    public static JsonObject resolve(ResourceLocation requested, Function<ResourceLocation, JsonObject> read) {
        JsonObject original = read.apply(requested);
        if (!original.has("resource_ref")) return original;
        if (original.size() != 1 || !original.get("resource_ref").isJsonPrimitive()
                || !original.getAsJsonPrimitive("resource_ref").isString())
            throw invalid(requested, "must contain only a string resource_ref");
        ResourceLocation target;
        try { target = ResourceLocation.parse(original.get("resource_ref").getAsString()); }
        catch (RuntimeException e) { throw invalid(requested, "invalid reference id"); }
        String kind = requested.getPath().endsWith("_model.json") ? "_model.json"
                : requested.getPath().endsWith("_animations.json") ? "_animations.json" : "";
        if (kind.isEmpty() || !requested.getNamespace().equals(target.getNamespace())
                || !SHARED.matcher(target.getPath()).matches() || !target.getPath().endsWith(kind))
            throw invalid(requested, "must target a same-namespace shared payload of the same kind");
        JsonObject resolved = read.apply(target);
        if (resolved.has("resource_ref")) throw invalid(requested, "chained references are not allowed");
        return resolved;
    }
    private static BeastDataException invalid(ResourceLocation file, String reason) {
        return new BeastDataException(file.toString(), "resource_ref", reason);
    }
}
