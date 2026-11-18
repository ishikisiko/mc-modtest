package com.example.myvillage.enchanting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.Enchantment;

final class SealDraw {
    private SealDraw() {}

    static List<Holder<Enchantment>> choose(List<Holder<Enchantment>> available, List<Holder<Enchantment>> last,
            Holder<Enchantment> held, SealRules.Selection selection, RandomSource random) {
        var pool = new ArrayList<>(available);
        var result = new ArrayList<Holder<Enchantment>>();
        if (held != null && pool.remove(held)) result.add(held);
        if (!selection.first() && selection.method().equals("reserve") && pool.size() > 1) {
            for (var backup : last) {
                if (backup != null && (held == null || Enchantment.areCompatible(held, backup)) && pool.remove(backup)) {
                    result.add(backup);
                    break;
                }
            }
        }
        if (pool.isEmpty()) return List.of();
        while (result.size() < selection.slots() && !pool.isEmpty()) {
            List<Holder<Enchantment>> choices = pool;
            if (selection.method().equals("bundle") && result.size() < 2) {
                var paired = result.isEmpty()
                        ? pool.stream().filter(rune -> pool.stream().anyMatch(other -> !other.equals(rune)
                                && Enchantment.areCompatible(rune, other))).toList()
                        : pool.stream().filter(rune -> Enchantment.areCompatible(result.getFirst(), rune)).toList();
                if (!paired.isEmpty()) choices = paired;
            }
            int total = 0;
            var weights = new int[choices.size()];
            for (int i = 0; i < choices.size(); i++) {
                weights[i] = SealRules.RULES.weight(choices.get(i), selection, result);
                total += weights[i];
            }
            int ticket = random.nextInt(total);
            for (int i = 0; i < weights.length; i++) {
                ticket -= weights[i];
                if (ticket >= 0) continue;
                var chosen = choices.get(i);
                result.add(chosen);
                pool.remove(chosen);
                break;
            }
        }
        return result;
    }
}
