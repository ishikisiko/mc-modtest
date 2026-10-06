package com.example.myvillage.sim;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Read-only view of a player's ledger record ({@code sim.model.PlayerMember}) with names resolved.
 * {@code sectName} and {@code masterName} are "" when there is no sect or master; {@code standings}
 * maps sect id to standing (交情, -100..100) in id order. {@code taskId} is "" without an open
 * task; {@code taskTargetSectId} -1 unless a courier task; {@code taskYear} the year the last task
 * was taken, or -1 (see {@code WorldSim.task} for the task joined with its data).
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
        int rootPeakBp,
        String taskId,
        int taskProgress,
        int taskTargetSectId,
        long taskYear) {

    public PlayerMemberView {
        borrowed = List.copyOf(borrowed);
        standings = Collections.unmodifiableMap(new TreeMap<>(standings));
    }

    public boolean inSect() {
        return sectId >= 0;
    }
}
