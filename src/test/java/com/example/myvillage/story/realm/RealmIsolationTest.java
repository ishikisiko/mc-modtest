package com.example.myvillage.story.realm;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RealmIsolationTest {
    private final UUID alice=UUID.fromString("00000000-0000-0000-0000-000000000001"),bob=UUID.fromString("00000000-0000-0000-0000-000000000002");
    @Test void ownershipIsExactAndTemplateAndMalformedKeysNeverBecomePublic(){
        assertTrue(RealmIdentity.permits(alice,RealmIdentity.dimension(alice)));
        assertFalse(RealmIdentity.permits(bob,RealmIdentity.dimension(alice)));
        for(String key:new String[]{RealmIdentity.TEMPLATE,"myvillage:qianzhen/not-a-uuid","myvillage:qianzhen/0-0-0-0-1",RealmIdentity.dimension(alice)+"/extra"})
            assertFalse(RealmIdentity.permits(alice,key),key);
        assertTrue(RealmIdentity.permits(alice,"minecraft:overworld"));
        assertNotEquals(RealmIdentity.cast(alice),RealmIdentity.cast(bob));
    }
    private RealmSaves.Location point(String dimension,double x){return new RealmSaves.Location(dimension,x,64,12,90,0);}
    @Test void roundTripRetainsTwoIndependentOwnersAndResumePositions(){
        var saves=new RealmSaves();
        var a=new RealmSaves.Entry(alice,"ximo",point("minecraft:overworld",123),point(RealmIdentity.dimension(alice),42));
        var b=new RealmSaves.Entry(bob,"beiyuan",point("minecraft:the_nether",87),null);
        saves.put(a);saves.put(b);
        var loaded=RealmSaves.load(saves.save(new CompoundTag(),null),null);
        assertEquals(a,loaded.get(alice));assertEquals(b,loaded.get(bob));
        loaded.put(new RealmSaves.Entry(alice,a.region(),a.outside(),point(RealmIdentity.dimension(alice),80)));
        assertEquals(b,loaded.get(bob));assertEquals(42,saves.get(alice).inside().x());
    }
    @Test void duplicateCorruptAndCrossOwnerSavesCannotResetOrBorrowARealm(){
        assertThrows(IllegalArgumentException.class,()->new RealmSaves.Entry(alice,"ximo",point(RealmIdentity.dimension(bob),1),null));
        assertThrows(IllegalArgumentException.class,()->new RealmSaves.Entry(alice,"ximo",point("minecraft:overworld",1),point(RealmIdentity.dimension(bob),2)));
        assertThrows(IllegalArgumentException.class,()->point("minecraft:overworld",Double.NaN));
        var saves=new RealmSaves();saves.put(new RealmSaves.Entry(alice,"ximo",point("minecraft:overworld",1),null));
        var tag=saves.save(new CompoundTag(),null);var list=(ListTag)tag.get("realms");list.add(list.getCompound(0).copy());
        assertThrows(IllegalStateException.class,()->RealmSaves.load(tag,null));
        var corrupt=saves.save(new CompoundTag(),null);corrupt.getList("realms",10).getCompound(0).getCompound("outside").remove("x");
        assertThrows(IllegalStateException.class,()->RealmSaves.load(corrupt,null));
        assertThrows(IllegalStateException.class,()->RealmSaves.load(new CompoundTag(),null));
    }
}
