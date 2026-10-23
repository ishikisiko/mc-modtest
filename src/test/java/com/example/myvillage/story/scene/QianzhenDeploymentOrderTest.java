package com.example.myvillage.story.scene;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source boundary guard only; native deployment/world persistence still needs the remote runtime gate. */
class QianzhenDeploymentOrderTest {
    @Test void actorRefusalPrecedesChunkGenerationReservationAndTheFailureHandler() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/example/myvillage/story/scene/QianzhenScene.java"));
        String deploy = source.substring(source.indexOf("static boolean deploy("),
                source.indexOf("private static void preflight("));
        int actors = deploy.indexOf("QianzhenActors.validateDeployment(level, assets.layout, origin);");
        int terrain = deploy.indexOf("preflight(level, origin, assets.layout);");
        int begin = deploy.indexOf("data.begin(");
        int handler = deploy.indexOf("try {");
        int template = deploy.indexOf(".placeInWorld(");
        int roads = deploy.indexOf("level.setBlock(");
        int complete = deploy.indexOf("data.complete()");
        assertTrue(actors >= 0 && actors < terrain && terrain < begin && begin < handler,
                "Actor refusal must escape while SavedData is EMPTY, before chunk-generating terrain reads");
        assertTrue(handler < template && template < roads && roads < complete);
        String initialize = deploy.substring(deploy.indexOf("QianzhenActors.ensure(level);"));
        assertFalse(initialize.contains("catch ("), "Do not silently accept an inconsistent completed cast");
    }

    @Test void proposedAndExistingScenesShareTheCastAndProposedPositionsNeedNoCompleteRecord() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/example/myvillage/story/npc/QianzhenActors.java"));
        String ensure = source.substring(source.indexOf("public static void ensure(ServerLevel"),
                source.indexOf("public static void validateDeployment("));
        String validate = source.substring(source.indexOf("public static void validateDeployment("),
                source.indexOf("private static StoryCastDefinition cast("));
        assertTrue(ensure.contains("cast(level, sim, placement,"));
        assertTrue(validate.contains("sim.validateStoryCast(cast(level, sim, placement,"));
        assertTrue(validate.contains("layout.positions().get(key)"));
        assertTrue(validate.contains("station.pos()"), "Use actor feet, never the player approach position");
        assertFalse(validate.contains("QianzhenScene.at("));
        assertFalse(validate.contains("QianzhenScene.position("));
        assertFalse(validate.contains("ensureStoryCast("));
        assertFalse(validate.contains("dirty("));
    }
}
