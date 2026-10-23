package com.example.myvillage.combat;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SkillBindingsTest {
    private static ResourceLocation id(String s){return ResourceLocation.parse("myvillage:"+s);}
    @Test void oldSaveUsesAutomaticArtsAndEmptyExtraSlots() {
        var old=CombatPreference.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString("{\"combat_mode\":\"cultivation\"}")).getOrThrow();
        assertEquals(SkillBindings.defaults(),old.skillBindings());
    }
    @Test void loadoutSurvivesCodecAndModeChanges() {
        var configured=new CombatPreference(CombatMode.CULTIVATION,SkillBindings.defaults().with(0,id("jianqi_jue")).with(5,id("liuyun_bu")));
        var encoded=CombatPreference.CODEC.encodeStart(JsonOps.INSTANCE,configured).getOrThrow();
        assertEquals(configured,CombatPreference.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow());
        assertEquals(configured.skillBindings(),configured.toggled().skillBindings());
    }
    @Test void movingASkillClearsItsPreviousSlotWithoutMutatingOriginal() {
        var before=SkillBindings.defaults().with(2,id("jianqi_jue"));var after=before.with(4,id("jianqi_jue"));
        assertEquals(id("jianqi_jue"),before.slots().get(2));assertEquals(SkillBindings.EMPTY,after.slots().get(2));
        assertEquals(id("jianqi_jue"),after.slots().get(4));
    }
    @Test void malformedPersistedBindingsCannotGrowOrUseReservedAutoSlots() {
        assertThrows(IllegalArgumentException.class,()->new SkillBindings(List.of()));
        assertThrows(IllegalArgumentException.class,()->SkillBindings.defaults().with(2,SkillBindings.AUTO));
        assertThrows(IllegalArgumentException.class,()->new SkillBindings(Collections.nCopies(6,id("jianqi_jue"))));
        assertThrows(IllegalArgumentException.class,()->SkillBindings.defaults().with(6,id("jianqi_jue")));
    }
    @Test void sixBindingsHaveDistinctHudSlotsAroundTheTwoNativeActions() {
        assertEquals(List.of(0,1,4,5,6,7),java.util.stream.IntStream.range(0,6).map(SkillBindings::hudIndex).boxed().toList());
    }
    @Test void fateAbilityUsesExistingSlotsAndCodecWithoutChangingAutomaticDefaults() {
        var original=SkillBindings.defaults();
        var bound=original.with(2,id("wood_root"));
        var roundTrip=SkillBindings.CODEC.parse(JsonOps.INSTANCE,
                SkillBindings.CODEC.encodeStart(JsonOps.INSTANCE,bound).getOrThrow()).getOrThrow();
        assertEquals(bound,roundTrip);
        assertEquals(SkillBindings.AUTO,roundTrip.slots().get(0));
        assertEquals(SkillBindings.AUTO,roundTrip.slots().get(1));
        assertEquals(SkillBindings.EMPTY,original.slots().get(2));
        assertEquals(SkillBindings.EMPTY,bound.with(5,id("wood_root")).slots().get(2));
        assertEquals(id("wood_root"),bound.with(5,id("wood_root")).slots().get(5));
    }
}
