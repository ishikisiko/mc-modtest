package com.example.myvillage.story.scene;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 当前格式的真实NBT保存与读回；使用本次导出的场景作为夹具。 */
class SceneFrozenDeploymentTest {
    private CompoundTag receipt(SceneLayout layout) {
        CompoundTag tag=new CompoundTag();tag.putInt("schema",2);tag.putString("phase","COMPLETE");
        tag.putString("dimension","yuanxian:qianzhen/00000000-0000-0000-0000-000000000001");tag.putString("id",layout.id());tag.putString("version",layout.version());
        tag.putInt("x",123);tag.putInt("y",70);tag.putInt("z",-456);tag.putBoolean("regulated",false);
        tag.putString("geometry",SceneGeometryArchive.freeze(layout));return tag;
    }
    @Test void savedGeometryWinsOverChangedBundledGeometryAndSurvivesAnotherSave() {
        var frozen=SceneTestLayouts.create("fixture_saved",0);var current=SceneTestLayouts.create("fixture_changed",1);
        assertNotEquals(frozen.positions(),current.positions());
        var saved=SceneDeploymentData.load(receipt(frozen),null);
        assertEquals(frozen,saved.geometry(current));assertEquals("fixture_saved",saved.version());
        var loaded=SceneDeploymentData.load(saved.save(new CompoundTag(),null),null);
        assertEquals(frozen,loaded.geometry(current));assertEquals(saved.origin(),loaded.origin());assertEquals(saved.dimension(),loaded.dimension());
    }
    @Test void mismatchedFrozenIdentityDoesNotBecomeAnEmptyRebuildableWorld() {
        var tag=receipt(SceneTestLayouts.create("fixture_saved",0));tag.putString("version","fixture_other");
        assertThrows(IllegalStateException.class,()->SceneDeploymentData.load(tag,null));
    }
    @Test void obsoleteOrMissingGeometryCannotBecomeARebuildableDeployment() {
        var layout=SceneTestLayouts.create("fixture_saved",0);
        var obsolete=receipt(layout);obsolete.putInt("schema",1);
        assertThrows(IllegalStateException.class,()->SceneDeploymentData.load(obsolete,null));
        var missing=receipt(layout);missing.remove("geometry");
        assertThrows(IllegalStateException.class,()->SceneDeploymentData.load(missing,null));
        var empty=receipt(layout);empty.putString("geometry","");
        assertThrows(IllegalStateException.class,()->SceneDeploymentData.load(empty,null));
    }
}
