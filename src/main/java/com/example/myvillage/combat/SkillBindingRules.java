package com.example.myvillage.combat;

import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.fate.FateState;
import com.example.myvillage.cultivation.fate.FateDefinition;
import com.example.myvillage.cultivation.fate.ability.FateAbilityDefinition;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;

/** Shared classification. Only implemented active executors are assignable. */
public final class SkillBindingRules {
    public enum Kind { ART, WAVE, MOVEMENT, FATE, NONE }
    private SkillBindingRules(){}
    public static boolean assignable(ResourceLocation id,java.util.Set<ResourceLocation> learned,TechniqueDefinition definition) {
        var kind=kind(id,definition);
        return learned.contains(id) && kind!=Kind.NONE && kind!=Kind.FATE;
    }
    /** 天命资格与功法学习独立，未知或未确认的天命不能借已学 ID 冒充。 */
    public static boolean assignable(ResourceLocation id,java.util.Set<ResourceLocation> learned,
            TechniqueDefinition definition,FateState fates,java.util.Map<String,FateDefinition> fateDefinitions) {
        return FateAbilityDefinition.find(id).map(ability->ability.granted(fates,fateDefinitions.get(ability.requiredFate().toString())))
                .orElseGet(()->assignable(id,learned,definition));
    }
    public static Kind kind(ResourceLocation id,TechniqueDefinition definition) {
        if(FateAbilityDefinition.find(id).isPresent())return Kind.FATE;
        if(definition==null)return Kind.NONE;
        if(definition.category()==TechniqueCategory.MOVEMENT && definition.effects().flatMap(e->e.movement()).isPresent())return Kind.MOVEMENT;
        if(definition.category()!=TechniqueCategory.ACTIVE)return Kind.NONE;
        var active=definition.effects().flatMap(e->e.active());
        if(active.isEmpty())return Kind.NONE;
        var art=ArtDefinition.find(id);
        if(art.filter(a->active.get().skill().equals(a.id()) && active.get().qiCost()==a.qiCost()
                && definition.school().equals(Optional.of(a.school()))).isPresent())return Kind.ART;
        return CombatStyles.bundled().skill(active.get().skill())
                .filter(s->definition.school().equals(Optional.of(s.school()))).isPresent()?Kind.WAVE:Kind.NONE;
    }
}
