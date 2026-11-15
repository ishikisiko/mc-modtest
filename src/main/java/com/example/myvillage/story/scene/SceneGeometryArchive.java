package com.example.myvillage.story.scene;

import com.example.myvillage.worldplan.FrozenRulesEncoding;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonParser;

/** 保存当前世界的完整几何快照，重启时无需重放模板。 */
final class SceneGeometryArchive {
    private SceneGeometryArchive() {}
    static String freeze(SceneLayout layout) {
        var gson=new GsonBuilder().registerTypeAdapter(SceneLayout.Pos.class,(JsonSerializer<SceneLayout.Pos>)(p,t,c)->{
            var a=new JsonArray();a.add(p.x());a.add(p.y());a.add(p.z());return a;
        }).create();
        var root=gson.toJsonTree(layout).getAsJsonObject();root.addProperty("schema",1);
        if(layout.facilities().isEmpty())root.remove("facilities");
        return FrozenRulesEncoding.encode(gson.toJson(root));
    }
    static SceneLayout thaw(String encoded) {return SceneLayout.parse(JsonParser.parseString(FrozenRulesEncoding.decode(encoded)).getAsJsonObject());}
}
