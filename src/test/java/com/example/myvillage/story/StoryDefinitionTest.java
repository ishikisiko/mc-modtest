package com.example.myvillage.story;

import java.io.StringReader;
import java.nio.file.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoryDefinitionTest {
    private String source()throws Exception{return Files.readString(Path.of("src/main/resources/data/myvillage/story/qianzhen_chapter_1.json"));}
    @Test void bundledDefinitionHasFourActorsAndBothRoutes(){var d=StoryTestData.definition();assertEquals(4,d.roles().size());assertTrue(d.actions().containsKey("examine_sample"));assertTrue(d.actions().containsKey("inspect_chest"));}
    @Test void strictJsonRejectsDuplicateKeysUnknownFieldsAndUnsupportedSchema()throws Exception {
        String s=source();
        assertThrows(RuntimeException.class,()->StoryDefinition.parse(new StringReader(s.replaceFirst("\\\"schema\\\": 1","\"schema\": 1, \"schema\": 1"))));
        assertThrows(RuntimeException.class,()->StoryDefinition.parse(new StringReader(s.replaceFirst("\\{","{\"force_complete\":true,"))));
        assertThrows(RuntimeException.class,()->StoryDefinition.parse(new StringReader(s.replaceFirst("\\\"schema\\\": 1","\"schema\": 2"))));
    }
    @Test void danglingReferencesCyclesAndDuplicateActionsFail()throws Exception {
        var root=JsonParser.parseString(source()).getAsJsonObject();
        root.getAsJsonArray("actions").get(0).getAsJsonObject().addProperty("next","nonexistent");assertThrows(RuntimeException.class,()->parse(root));
        var cycle=JsonParser.parseString(source()).getAsJsonObject();var stage=cycle.getAsJsonArray("stages").get(2).getAsJsonObject();stage.addProperty("auto_next","samples");assertThrows(RuntimeException.class,()->parse(cycle));
        var dup=JsonParser.parseString(source()).getAsJsonObject();dup.getAsJsonArray("actions").add(dup.getAsJsonArray("actions").get(0).deepCopy());assertThrows(RuntimeException.class,()->parse(dup));
    }
    @Test void unknownRolesTargetsAndRewardsFail()throws Exception {
        for(String field:new String[]{"role","target"}) {var root=JsonParser.parseString(source()).getAsJsonObject();root.getAsJsonArray("actions").get(0).getAsJsonObject().addProperty(field,"imaginary");assertThrows(RuntimeException.class,()->parse(root));}
        var root=JsonParser.parseString(source()).getAsJsonObject();root.getAsJsonArray("rewards").get(0).getAsJsonObject().addProperty("count",-1);assertThrows(RuntimeException.class,()->parse(root));
    }
    @Test void effectsCannotPretendTalkIsPhysicalArrayInvestigation()throws Exception {
        var root=JsonParser.parseString(source()).getAsJsonObject();
        root.getAsJsonArray("actions").get(0).getAsJsonObject().addProperty("effect","regulate");
        assertThrows(RuntimeException.class,()->parse(root));
    }
    private StoryDefinition parse(JsonObject root){return StoryDefinition.parse(new StringReader(root.toString()));}
}
