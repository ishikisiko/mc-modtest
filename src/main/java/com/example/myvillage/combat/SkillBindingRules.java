package com.example.myvillage.combat;

import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;

/** Shared classification. Only implemented active executors are assignable. */
public final class SkillBindingRules {
    public enum Kind { ART, WAVE, MOVEMENT, NONE }
    private SkillBindingRules(){}
    public static boolean assignable(ResourceLocation id,java.util.Set<ResourceLocation> learned,TechniqueDefinition definition) {
        return learned.contains(id) && kind(id,definition)!=Kind.NONE;
    }
    public static Kind kind(ResourceLocation id,TechniqueDefinition definition) {
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
