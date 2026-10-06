package com.example.myvillage.sim;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Read-only view of a player's ledger record ({@code sim.model.PlayerMember}) with names resolved.
 * {@code sectName} and {@code masterName} are "" when there is no sect or master; {@code standings}
 * maps sect id to standing (交情, -100..100) in id order.
 */
public record PlayerMemberView(
        String playerId,
        String playerName,
        int sectId,
        String sectName,
        String rank,
        long joinedDay,
        int masterId,
        String masterName,
        int contribution,
        List<String> borrowed,
        Map<Integer, Integer> standings,
        int leftSectId,
        long leftDay,
        String realmId,
        int stageIndex,
        boolean awakened,
        int rootPeakBp) {

    public PlayerMemberView {
        borrowed = List.copyOf(borrowed);
        standings = Collections.unmodifiableMap(new TreeMap<>(standings));
    }

    public boolean inSect() {
        return sectId >= 0;
    }
}
