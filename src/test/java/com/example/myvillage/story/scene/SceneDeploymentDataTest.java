package com.example.myvillage.story.scene;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneDeploymentDataTest {
    private CompoundTag record(String phase) {
        CompoundTag tag = new CompoundTag(); tag.putInt("schema", 1); tag.putString("phase", phase);
        tag.putString("dimension", "minecraft:overworld"); tag.putString("id", "qianzhen_chapter_1");
        tag.putString("version", "1-test"); tag.putInt("x", 100); tag.putInt("y", -61); tag.putInt("z", 200);
        tag.putBoolean("regulated", false); return tag;
    }
    @Test void completeRestartRetainsOriginAndOneSharedFact() {
        var data = SceneDeploymentData.load(record("COMPLETE"), null);
        assertTrue(data.markRegulated()); assertFalse(data.markRegulated());
        var loaded = SceneDeploymentData.load(data.save(new CompoundTag(), null), null);
        assertEquals(SceneDeploymentData.Phase.COMPLETE, loaded.phase());
        assertEquals(data.origin(), loaded.origin()); assertEquals(-61, loaded.origin().getY());
        assertEquals("minecraft:overworld", loaded.dimension()); assertEquals("1-test", loaded.version()); assertTrue(loaded.regulated());
    }
    @Test void privateDimensionsKeepRegulationIndependentAfterSave() {
        var a=record("COMPLETE");a.putString("dimension","myvillage:qianzhen/00000000-0000-0000-0000-000000000001");
        var b=record("COMPLETE");b.putString("dimension","myvillage:qianzhen/00000000-0000-0000-0000-000000000002");
        var first=SceneDeploymentData.load(a,null);var second=SceneDeploymentData.load(b,null);
        first.markRegulated();
        assertTrue(SceneDeploymentData.load(first.save(new CompoundTag(),null),null).regulated());
        assertFalse(SceneDeploymentData.load(second.save(new CompoundTag(),null),null).regulated());
        assertNotEquals(first.dimension(),second.dimension());
    }
    @Test void interruptedOrFailedPlacementNeverTurnsBackIntoEmpty() {
        for (String phase : new String[]{"PLACING", "FAILED"}) {
            var loaded = SceneDeploymentData.load(record(phase), null);
            assertNotEquals(SceneDeploymentData.Phase.EMPTY, loaded.phase());
            assertThrows(IllegalStateException.class, loaded::markRegulated);
        }
    }
    @Test void brokenIdentityAndSchemaCannotResetTheScene() {
        CompoundTag bad = record("COMPLETE"); bad.remove("dimension");
        assertThrows(IllegalStateException.class, () -> SceneDeploymentData.load(bad, null));
        CompoundTag future = record("COMPLETE"); future.putInt("schema", 2);
        assertThrows(IllegalStateException.class, () -> SceneDeploymentData.load(future, null));
        CompoundTag placing = record("PLACING"); placing.putBoolean("regulated", true);
        assertThrows(IllegalStateException.class, () -> SceneDeploymentData.load(placing, null));
        CompoundTag missingPosition = record("COMPLETE"); missingPosition.remove("x");
        assertThrows(IllegalStateException.class, () -> SceneDeploymentData.load(missingPosition, null));
    }
}
