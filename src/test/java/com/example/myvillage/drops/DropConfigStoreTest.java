package com.example.myvillage.drops;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DropConfigStoreTest {
    @TempDir Path dir;
    private Path file(){return dir.resolve("yuanxian/monster_drops.json");}
    private DropConfigStore store()throws Exception{return new DropConfigStore(file(),DropRulesTest.defaults(),DropRulesTest.catalog());}
    private void external(DropConfigStore store,boolean enabled)throws Exception{
        Files.createDirectories(store.settingsFile().getParent());
        Files.writeString(store.settingsFile(),"{\"use_external_config\":"+enabled+"}");
    }
    private static DropRules.Policy wolf(DropConfigStore store){
        return store.active().rules().resolve(DropRulesTest.context("yuanxian:demon_wolf",DropRules.Scene.NORMAL)).policy();
    }
    private static JsonObject custom()throws Exception{
        var json=DropRulesTest.json();json.getAsJsonObject("entities").add("yuanxian:demon_wolf",DropRulesTest.diamond("replace"));return json;
    }

    @Test void firstRunUsesBundledRulesAndCreatesDisabledSettings()throws Exception{
        var store=store();var current=store.reload();
        assertEquals(1,current.revision());assertEquals(DropConfigStore.Source.BUNDLED,current.source());
        assertEquals(DropConfigStore.hash(DropRulesTest.defaults()),current.sha256());
        assertTrue(Files.readString(store.settingsFile()).contains("\"use_external_config\": false"));
        assertFalse(Files.exists(file()));assertEquals(DropRules.Mode.INHERIT,wolf(store).mode());
    }
    @Test void preexistingExternalFileIsIgnoredAndPreservedByDefault()throws Exception{
        var store=store();Files.createDirectories(file().getParent());Files.writeString(file(),custom().toString());
        String original=Files.readString(file());store.reload();
        assertEquals(DropConfigStore.Source.BUNDLED,store.active().source());assertEquals(DropRules.Mode.INHERIT,wolf(store).mode());assertEquals(original,Files.readString(file()));
        Files.writeString(file(),"{unused invalid config");store.reload();
        assertEquals(DropRules.Mode.INHERIT,wolf(store).mode());assertEquals("{unused invalid config",Files.readString(file()));
    }
    @Test void nextModUsesNewBundledRulesWithoutChangingExistingExternalFile()throws Exception{
        var old=store();old.reload();Files.writeString(file(),DropRulesTest.defaults());String original=Files.readString(file());
        String updated=custom().toString();var next=new DropConfigStore(file(),updated,DropRulesTest.catalog());next.reload();
        assertEquals(DropConfigStore.Source.BUNDLED,next.active().source());assertEquals(DropConfigStore.hash(updated),next.active().sha256());
        assertEquals(DropRules.Mode.REPLACE,wolf(next).mode());assertEquals(2,wolf(next).drops().getFirst().min());assertEquals(original,Files.readString(file()));
    }
    @Test void enabledExternalOverrideCreatesEditableDefaultsAndPreservesCustomRulesOnRestart()throws Exception{
        var store=store();external(store,true);store.reload();
        assertEquals(DropConfigStore.Source.EXTERNAL,store.active().source());assertTrue(Files.readString(file()).contains("\n  \"schema\""));
        Files.writeString(file(),custom().toString());String original=Files.readString(file());
        var next=store();next.reload();assertEquals(DropConfigStore.Source.EXTERNAL,next.active().source());
        assertEquals(DropRules.Mode.REPLACE,wolf(next).mode());assertEquals(original,Files.readString(file()));
    }
    @Test void reloadTogglesSourcesForExistingContextsWithoutOverwritingCustomFile()throws Exception{
        var store=store();store.reload();external(store,true);Files.writeString(file(),custom().toString());var custom=store.reload();
        assertEquals(DropConfigStore.Source.EXTERNAL,custom.source());assertEquals(DropRules.Mode.REPLACE,wolf(store).mode());
        String original=Files.readString(file());external(store,false);var bundled=store.reload();
        assertEquals(custom.revision()+1,bundled.revision());assertEquals(DropConfigStore.Source.BUNDLED,bundled.source());assertEquals(DropRules.Mode.INHERIT,wolf(store).mode());
        assertEquals(DropConfigStore.hash(DropRulesTest.defaults()),bundled.sha256());assertEquals(original,Files.readString(file()));
        external(store,true);store.reload();assertEquals(DropRules.Mode.REPLACE,wolf(store).mode());
    }
    @Test void reloadChangesExistingContextAndRejectedInputKeepsSameSnapshot()throws Exception{
        var store=store();external(store,true);store.reload();var context=DropRulesTest.context("yuanxian:demon_wolf",DropRules.Scene.NORMAL);
        assertEquals(DropRules.Mode.INHERIT,store.active().rules().resolve(context).policy().mode());
        var json=DropRulesTest.json();json.getAsJsonObject("entities").add("yuanxian:demon_wolf",DropRulesTest.diamond("append"));Files.writeString(file(),json.toString());var valid=store.reload();
        assertEquals(DropRules.Mode.APPEND,valid.rules().resolve(context).policy().mode());assertEquals(2,valid.revision());
        Files.writeString(file(),"{broken");assertThrows(IllegalArgumentException.class,store::reload);assertSame(valid,store.active());
        assertEquals(DropRules.Mode.APPEND,store.active().rules().resolve(context).policy().mode());assertEquals(DropConfigStore.Source.EXTERNAL,store.active().source());
    }
    @Test void invalidSettingsNeverPartiallyChangeSourceOrSnapshot()throws Exception{
        var store=store();external(store,true);Files.writeString(file(),custom().toString());var valid=store.reload();
        for(String text:new String[]{"{","{}","{\"use_external_config\":\"false\"}","{\"use_external_config\":null}","{\"use_external_config\":0}","{\"use_external_config\":false,\"extra\":true}","{\"use_external_config\":false,\"use_external_config\":true}","{\"use_external_config\":false} {}"}){
            Files.writeString(store.settingsFile(),text);assertThrows(IllegalArgumentException.class,store::reload,text);assertSame(valid,store.active(),text);
        }
    }
    @Test void failedEnableKeepsBundledRulesAndStartupFallbackHasBundledSource()throws Exception{
        var store=store();var valid=store.reload();Files.writeString(file(),"{broken");external(store,true);
        assertThrows(IllegalArgumentException.class,store::reload);assertSame(valid,store.active());
        var restarted=store();assertThrows(IllegalArgumentException.class,restarted::reload);
        assertEquals(0,restarted.active().revision());assertEquals(DropConfigStore.Source.BUNDLED,restarted.active().source());assertEquals(DropRules.Mode.INHERIT,wolf(restarted).mode());
    }
    @Test void oversizedInvalidUtf8AndDeletedFilesDoNotPartiallyApply()throws Exception{
        var store=store();external(store,true);Files.writeString(file(),custom().toString());var valid=store.reload();
        Files.writeString(file(),"x".repeat(DropRules.MAX_BYTES+1));assertThrows(IllegalArgumentException.class,store::reload);assertSame(valid,store.active());
        Files.write(file(),new byte[]{(byte)0xc3,(byte)0x28});assertThrows(java.io.IOException.class,store::reload);assertSame(valid,store.active());
        Files.delete(file());store.reload();assertTrue(Files.isRegularFile(file()));assertEquals(2,store.active().revision());assertEquals(DropRules.Mode.INHERIT,wolf(store).mode());
    }
    @Test void oversizedAndInvalidUtf8SettingsPreserveSnapshot()throws Exception{
        var store=store();var valid=store.reload();
        Files.writeString(store.settingsFile(),"x".repeat(DropRules.MAX_BYTES+1));assertThrows(IllegalArgumentException.class,store::reload);assertSame(valid,store.active());
        Files.write(store.settingsFile(),new byte[]{(byte)0xc3,(byte)0x28});assertThrows(java.io.IOException.class,store::reload);assertSame(valid,store.active());
    }
    @Test void removedSettingsRestoreDefaultBundledMode()throws Exception{
        var store=store();external(store,true);Files.writeString(file(),custom().toString());store.reload();Files.delete(store.settingsFile());store.reload();
        assertEquals(DropConfigStore.Source.BUNDLED,store.active().source());assertEquals(DropRules.Mode.INHERIT,wolf(store).mode());assertEquals(custom().toString(),Files.readString(file()));
    }
}
