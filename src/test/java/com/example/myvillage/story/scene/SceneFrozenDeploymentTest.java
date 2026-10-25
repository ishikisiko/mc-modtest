package com.example.myvillage.story.scene;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** NBT lifecycle tests, scheduled remotely; pure archive roundtrips are covered separately. */
class SceneFrozenDeploymentTest {
    private CompoundTag receipt(SceneLayout layout) {
        CompoundTag tag=new CompoundTag();tag.putInt("schema",2);tag.putString("phase","COMPLETE");
        tag.putString("dimension","minecraft:overworld");tag.putString("id",layout.id());tag.putString("version",layout.version());
        tag.putInt("x",123);tag.putInt("y",70);tag.putInt("z",-456);tag.putBoolean("regulated",false);
        tag.putString("geometry",SceneGeometryArchive.freeze(layout));return tag;
    }
    @Test void frozenGeometryAlwaysWinsOverDifferentBundledCurrentVersion() {
        var old=SceneGeometryArchive.legacy("1-f795e60913419e05");var current=SceneGeometryArchive.legacy("1-93b7ddbc5c59392e");
        var saved=SceneDeploymentData.load(receipt(old),null);
        assertEquals(old,saved.geometry(current));assertEquals("1-f795e60913419e05",saved.version());
        var loaded=SceneDeploymentData.load(saved.save(new CompoundTag(),null),null);
        assertEquals(old,loaded.geometry(current));assertEquals(saved.origin(),loaded.origin());
    }
    @Test void mismatchedFrozenIdentityDoesNotBecomeAnEmptyRebuildableWorld() {
        var tag=receipt(SceneGeometryArchive.legacy("1-f795e60913419e05"));tag.putString("version","1-93b7ddbc5c59392e");
        assertThrows(IllegalStateException.class,()->SceneDeploymentData.load(tag,null));
    }
    @Test void private090SchemaOneResolvesItsExactOldGeometryWithoutAcquiringNewFacilities() {
        var old=SceneGeometryArchive.legacy("1-93b7ddbc5c59392e");
        var tag=receipt(old);tag.putInt("schema",1);tag.remove("geometry");
        String dimension="myvillage:qianzhen/00000000-0000-0000-0000-000000000001";
        tag.putString("dimension",dimension);tag.putBoolean("regulated",true);
        var saved=SceneDeploymentData.load(tag,null);
        var current=SceneGeometryArchive.legacy("1-f795e60913419e05");
        assertEquals(old,saved.geometry(current));assertTrue(saved.geometry(current).facilities().isEmpty());
        var restored=SceneDeploymentData.load(saved.save(new CompoundTag(),null),null);
        assertEquals(dimension,restored.dimension());assertTrue(restored.regulated());
        assertEquals(old,restored.geometry(current));assertEquals(saved.origin(),restored.origin());
    }
}
