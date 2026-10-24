package com.example.myvillage.story.scene;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneGeometryArchiveTest {
    @Test void productionGeometryFreezesEveryPositionAndNeverNeedsTemplateReplay()throws Exception {
        var raw=Files.readString(Path.of("build/generated/qianzhen/resources/data/myvillage/story/qianzhen_scene.json"));
        var layout=SceneLayout.parse(JsonParser.parseString(raw).getAsJsonObject());var encoded=SceneGeometryArchive.freeze(layout);
        assertTrue(encoded.length()<65_000);assertEquals(layout,SceneGeometryArchive.thaw(encoded));
    }
    @Test void exactLegacyTablesRetainNineTargetsOriginalNpcPositionsAndNoNewFacilities() {
        for(String version:new String[]{"1-93b7ddbc5c59392e","1-f795e60913419e05","1-56da335c3dd1262f"}) {
            var old=SceneGeometryArchive.legacy(version);assertEquals(version,old.version());assertEquals(9,old.targets().size());
            assertTrue(old.facilities().isEmpty());assertEquals(old,SceneGeometryArchive.thaw(SceneGeometryArchive.freeze(old)));
        }
        assertThrows(IllegalStateException.class,()->SceneGeometryArchive.legacy("1-unknown"));
    }
}
