package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.cultivation.NpcHeartLoadout;
import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.cultivation.SpiritualPowerMath;
import com.example.myvillage.sim.data.NpcHeartCatalogue;
import com.example.myvillage.sim.data.NpcHeartCatalogue.Entry;
import com.example.myvillage.sim.model.Person;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

/** 命簿处理研读与装配，近场化身从命簿取得同一份选择。 */
public final class NpcHeartTraining {
    private NpcHeartTraining() { }

    public static void found(SimContext ctx, Person person, String id) {
        if (ctx.data.npcHearts().get(id) != null) person.hearts = person.hearts.find(id);
    }

    public static void study(SimContext ctx, Person person) {
        var catalogue = ctx.data.npcHearts();
        if (catalogue.entries().isEmpty()) return;
        var previous = person.hearts;
        var learned = new LinkedHashSet<>(previous.learned());
        var books = new LinkedHashSet<>(previous.found());
        books.add(NpcHeartCatalogue.BASIC_BREATHING);
        books.add(person.techniqueId);
        if (person.sectId >= 0) books.addAll(PlayerAffairs.borrowable(ctx, ctx.sect(person.sectId), person.rank));
        var available = books.stream().map(catalogue::get)
                .filter(entry -> entry != null && entry.eligible(ctx.realms, person)).toList();
        // 先研读已满足前置的心法，再处理本轮新解锁的同支传承。
        boolean learnedMore;
        do {
            learnedMore = false;
            for (var entry : available) {
                if ((entry.previous().isEmpty() || learned.contains(entry.previous())) && learned.add(entry.id()))
                    learnedMore = true;
            }
        } while (learnedMore);

        var priority = Comparator.comparingInt(Entry::grade)
                .thenComparing(entry -> entry.id().equals(person.techniqueId))
                .thenComparingInt(entry -> affinity(person, entry))
                .thenComparingLong(entry -> SimRng.mix(ctx.state.seed ^ ((long) person.id << 32) ^ entry.id().hashCode()))
                .thenComparing(Entry::id);
        var equipped = new LinkedHashMap<String, Entry>();
        for (String id : learned) {
            var entry = catalogue.get(id);
            if (entry != null && entry.eligible(ctx.realms, person)) {
                equipped.merge(entry.slot(), entry, (old, next) -> priority.compare(old, next) >= 0 ? old : next);
            }
        }
        person.hearts = new NpcHeartLoadout(previous.found().stream().filter(id -> !learned.contains(id)).toList(),
                List.copyOf(learned), id(equipped.get("attack")), id(equipped.get("defense")), id(equipped.get("spirit")));
        if (person.hearts.equals(previous)) {
            person.hearts = previous;
            return;
        }
        if (!person.hearts.spirit().equals(previous.spirit()))
            person.spiritualPower = SpiritualPowerMath.clamp(person.spiritualPower, NpcCultivation.powerCap(ctx, person));
        if (!person.hearts.defense().equals(previous.defense())) {
            double delta = catalogue.maxHealth(person.hearts.defense()) - catalogue.maxHealth(previous.defense());
            NpcCultivation.resizeCombatHealth(ctx, person, delta, false);
        }
    }

    private static int affinity(Person person, Entry entry) {
        int index = Person.ELEMENTS.indexOf(entry.element());
        return index < 0 ? 0 : person.root[index];
    }

    private static String id(Entry entry) {
        return entry == null ? "" : entry.id();
    }
}
