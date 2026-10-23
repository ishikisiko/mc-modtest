package com.example.myvillage.combat;
import com.example.myvillage.cultivation.data.*;
import com.example.myvillage.cultivation.fate.FateState;
import com.example.myvillage.cultivation.fate.FateDefinition;
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
    @Test void confirmedFateGrantsActiveBindingWithoutInventingLearnedTechnique() {
        var ability=id("wood_root");
        var selected=List.of("myvillage:wood_spirit_body","myvillage:robust","myvillage:bright");
        var fates=new FateState(1,1,selected,"",selected,true,0,0,0,0);
        var enabled=new FateDefinition("天命名称","天命描述",0,1,"体质",true,Map.of(),Optional.empty(),0,5);
        var disabled=new FateDefinition("天命名称","天命描述",0,1,"体质",false,Map.of(),Optional.empty(),0,5);
        var definitions=Map.of("myvillage:wood_spirit_body",enabled);
        assertEquals(SkillBindingRules.Kind.FATE,SkillBindingRules.kind(ability,null));
        assertTrue(SkillBindingRules.assignable(ability,Set.of(),null,fates,definitions));
        assertFalse(SkillBindingRules.assignable(ability,Set.of(ability),null));
        assertFalse(SkillBindingRules.assignable(ability,Set.of(ability),null,FateState.empty(),definitions));
        assertFalse(SkillBindingRules.assignable(ability,Set.of(),null,fates.resetChoices(),definitions));
        assertFalse(SkillBindingRules.assignable(id("metal_spirit_body"),Set.of(),null,fates,definitions));
        assertFalse(SkillBindingRules.assignable(ability,Set.of(ability),null,fates,Map.of()));
        assertFalse(SkillBindingRules.assignable(ability,Set.of(ability),null,fates,
                Map.of("myvillage:wood_spirit_body",disabled)));
    }
}
