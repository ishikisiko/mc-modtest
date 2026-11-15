package com.example.myvillage.story.scene;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class SceneTestLayouts {
    static SceneLayout create(String version,int guideShift) {
        try {
            var root=JsonParser.parseString(Files.readString(Path.of("build/generated/qianzhen/resources/data/yuanxian/story/qianzhen_scene.json"))).getAsJsonObject();
            root.addProperty("version",version);
            var guide=root.getAsJsonObject("positions").getAsJsonObject("guide");
            for(String field:new String[]{"pos","approach"}) {
                var point=guide.getAsJsonArray(field);point.set(0,new com.google.gson.JsonPrimitive(point.get(0).getAsInt()+guideShift));
            }
            return SceneLayout.parse(root);
        } catch(IOException error) { throw new UncheckedIOException(error); }
    }
    private SceneTestLayouts() {}
}
