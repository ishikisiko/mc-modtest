package com.example.myvillage.drops;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DropConfigStoreTest {
    @TempDir Path dir;
    @Test void firstRunCreatesEditableDefaultsAndDoesNotOverwriteExistingFile()throws Exception{
        var file=dir.resolve("myvillage/monster_drops.json");var store=new DropConfigStore(file,DropRulesTest.defaults(),DropRulesTest.catalog());
        assertEquals(1,store.reload().revision());assertTrue(Files.readString(file).contains("\n  \"schema\""));
        var custom=DropRulesTest.json();custom.getAsJsonObject("entities").add("myvillage:demon_wolf",DropRulesTest.diamond("replace"));Files.writeString(file,custom.toString());
        var next=new DropConfigStore(file,DropRulesTest.defaults(),DropRulesTest.catalog());next.reload();assertEquals(DropRules.Mode.REPLACE,next.active().rules().resolve(DropRulesTest.context("myvillage:demon_wolf",DropRules.Scene.NORMAL)).policy().mode());
    }
    @Test void reloadChangesExistingContextAndRejectedInputKeepsSameSnapshot()throws Exception{
        var file=dir.resolve("drops.json");var store=new DropConfigStore(file,DropRulesTest.defaults(),DropRulesTest.catalog());store.reload();var context=DropRulesTest.context("myvillage:demon_wolf",DropRules.Scene.NORMAL);
        assertEquals(DropRules.Mode.INHERIT,store.active().rules().resolve(context).policy().mode());
        var j=DropRulesTest.json();j.getAsJsonObject("entities").add("myvillage:demon_wolf",DropRulesTest.diamond("append"));Files.writeString(file,j.toString());var valid=store.reload();
        assertEquals(DropRules.Mode.APPEND,valid.rules().resolve(context).policy().mode());assertEquals(2,valid.revision());
        Files.writeString(file,"{broken");assertThrows(IllegalArgumentException.class,store::reload);assertSame(valid,store.active());
        assertEquals(DropRules.Mode.APPEND,store.active().rules().resolve(context).policy().mode());
    }
    @Test void oversizedInvalidUtf8AndDeletedFilesDoNotPartiallyApply()throws Exception{
        var file=dir.resolve("drops.json");var store=new DropConfigStore(file,DropRulesTest.defaults(),DropRulesTest.catalog());var valid=store.reload();
        Files.writeString(file,"x".repeat(DropRules.MAX_BYTES+1));assertThrows(IllegalArgumentException.class,store::reload);assertSame(valid,store.active());
        Files.write(file,new byte[]{(byte)0xc3,(byte)0x28});assertThrows(java.io.IOException.class,store::reload);assertSame(valid,store.active());
        Files.delete(file);store.reload();assertTrue(Files.isRegularFile(file));assertEquals(2,store.active().revision());
    }
}
