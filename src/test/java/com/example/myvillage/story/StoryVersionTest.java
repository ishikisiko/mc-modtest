package com.example.myvillage.story;

import com.google.gson.JsonParser;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoryVersionTest {
    private StoryDefinition upgraded() throws Exception {
        var json = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/myvillage/story/qianzhen_chapter_1.json"))).getAsJsonObject();
        json.addProperty("version", 2);
        return StoryDefinition.parse(new StringReader(json.toString()));
    }

    @Test void untouchedPlayerBindsCurrentDefinitionOnlyWhenAccepting() throws Exception {
        var definition = upgraded();
        assertTrue(StoryEngine.offered(definition, StoryProgress.EMPTY, definition.actions().get("accept")));
        var plan = StoryEngine.reduce(definition, StoryProgress.EMPTY, "accept", "talk", "guide",
                "qianzhen_test", "minecraft:overworld");
        assertTrue(plan.changed());
        assertEquals(2, plan.progress().definitionVersion());
        assertEquals("pharmacist", plan.progress().stage());
        assertEquals(1, StoryProgress.EMPTY.definitionVersion());
    }

    @Test void acceptedPlayerNeedsExplicitMigrationAndKeepsExistingState() throws Exception {
        var progress = StoryTestData.run("accept");
        var plan = StoryEngine.reduce(upgraded(), progress, "commission", "talk", "pharmacist",
                progress.site(), progress.dimension());
        assertFalse(plan.changed());
        assertSame(progress, plan.progress());
        assertEquals(1, progress.definitionVersion());
    }
}
