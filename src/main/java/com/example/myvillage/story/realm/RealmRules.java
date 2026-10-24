package com.example.myvillage.story.realm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public record RealmRules(BlockPos origin, int borderPadding, int roadsPerTick, int returnRadius) {
    public static RealmRules load(MinecraftServer server) {
        try (var in = server.getResourceManager().open(ResourceLocation.parse("myvillage:story/qianzhen_realm.json"));
             var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (java.io.IOException error) { throw new IllegalStateException("缺少阡镇秘境规则", error); }
    }
    public static RealmRules parse(JsonObject o) {
        if (o.get("schema").getAsInt()!=1) throw new IllegalArgumentException("Unsupported realm rules");
        var pos=o.getAsJsonArray("origin");
        if(pos.size()!=3)throw new IllegalArgumentException("Invalid realm origin");
        var rules=new RealmRules(new BlockPos(pos.get(0).getAsInt(),pos.get(1).getAsInt(),pos.get(2).getAsInt()),
                o.get("border_padding").getAsInt(),o.get("roads_per_tick").getAsInt(),o.get("return_radius").getAsInt());
        if(rules.origin.getY() < -63 || rules.origin.getY()>191 || rules.borderPadding<8 || rules.borderPadding>128
                ||rules.roadsPerTick<1||rules.roadsPerTick>4096||rules.returnRadius<1||rules.returnRadius>16)
            throw new IllegalArgumentException("Invalid realm bounds");
        return rules;
    }
}
