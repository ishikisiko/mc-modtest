package com.example.myvillage.combat;
import com.example.myvillage.cultivation.data.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SkillBindingRulesTest {
    private static ResourceLocation id(String s){return ResourceLocation.parse("myvillage:"+s);}
    private static TechniqueDefinition wave(String skill,String school){
        return new TechniqueDefinition("test",TechniqueCategory.ACTIVE,1,List.of(),TechniqueRequirements.none(),
                Optional.of(id(school)),Optional.empty(),Optional.of(new TechniqueEffects(Optional.empty(),
                Optional.of(new TechniqueEffects.Active(id(skill),8,1)),Optional.empty(),Optional.empty())));
    }
    @Test void requiresLearnedImplementedSkillAndMatchingDefinition() {
        var id=id("jianqi_jue");var definition=wave("jianqi_jue","sword");
        assertFalse(SkillBindingRules.assignable(id,Set.of(),definition));
        assertFalse(SkillBindingRules.assignable(id,Set.of(id),null));
        assertFalse(SkillBindingRules.assignable(id,Set.of(id),wave("missing","sword")));
        assertFalse(SkillBindingRules.assignable(id,Set.of(id),wave("jianqi_jue","spear")));
        assertTrue(SkillBindingRules.assignable(id,Set.of(id),definition));
    }
}
