package com.example.myvillage.sim.engine;

import com.example.myvillage.aptitude.AptitudeRules;
import com.example.myvillage.aptitude.AptitudeValues;
import com.example.myvillage.sim.model.Person;
import java.util.UUID;

/** NPC 资质由身份生成一次，日常培养和所有修改保留在命簿中。 */
public final class NpcAptitudes {
    private NpcAptitudes() { }
    public static void initialize(SimContext ctx, Person p) {
        var generated = AptitudeValues.generate(ctx.state.seed, new UUID(0, p.id), p.realm, true);
        var values = new java.util.TreeMap<>(generated.values());
        AptitudeRules.get().elements().forEach(values::remove);
        p.aptitudes = new AptitudeValues(values);
    }
    public static void practice(SimContext ctx, Person p) {
        if (Math.floorMod(ctx.day() + p.id, AptitudeRules.get().growth("npc_practice_days")) != 0) return;
        var technique = ctx.technique(p);
        if (technique != null) {
            int element = Person.ELEMENTS.indexOf(technique.element());
            if (element >= 0) p.root[element] = AptitudeRules.add(p.root[element], AptitudeRules.get().growth("npc_practice_gain"));
        }
        String skill = p.aptitudes.values().entrySet().stream().max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey).orElse("sword");
        p.aptitudes = p.aptitudes.with(skill, AptitudeRules.add(p.aptitudes.get(skill), AptitudeRules.get().growth("npc_practice_gain")));
    }
}
