package com.example.myvillage.combat.runtime;

import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.entity.PartEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.ToIntFunction;

/** Parts own geometry and hurt routing; their parent owns permissions, health and hit budgets. */
public final class CombatTargets {
    private CombatTargets() {}

    public static Entity owner(Entity target) {
        return target instanceof PartEntity<?> part ? part.getParent() : target;
    }

    public static int hitId(Entity target) {
        return owner(target).getId();
    }

    public static boolean physicalTarget(Entity target) {
        // A multipart parent's broad box is not a hurt box. In particular, hurting the dragon
        // parent routes to its body even when the actual head/wing was never intersected.
        return !target.isMultipartEntity();
    }

    /** Contacts are already ordered by actual contact distance before applying the owner budget. */
    static <T> List<T> firstDistinct(List<T> contacts, ToIntFunction<T> ownerId, int maximum) {
        if (maximum <= 0) return List.of();
        var owners = new HashSet<Integer>();
        var result = new ArrayList<T>();
        for (T contact : contacts) {
            if (!owners.add(ownerId.applyAsInt(contact))) continue;
            result.add(contact);
            if (result.size() == maximum) break;
        }
        return List.copyOf(result);
    }
}
