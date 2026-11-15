package com.example.myvillage.story.scene;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneGeometryArchiveTest {
    @Test void productionGeometryFreezesEveryPositionAndNeverNeedsTemplateReplay()throws Exception {
        var raw=Files.readString(Path.of("build/generated/qianzhen/resources/data/yuanxian/story/qianzhen_scene.json"));
        var layout=SceneLayout.parse(JsonParser.parseString(raw).getAsJsonObject());var encoded=SceneGeometryArchive.freeze(layout);
        assertTrue(encoded.length()<65_000);assertEquals(layout,SceneGeometryArchive.thaw(encoded));
    }
    @Test void corruptFrozenGeometryIsRejectedWithoutTemplateFallback() {
        assertThrows(IllegalArgumentException.class,()->SceneGeometryArchive.thaw("gzip:broken"));
        assertThrows(IllegalArgumentException.class,()->SceneGeometryArchive.thaw("unencoded"));
    }
}
