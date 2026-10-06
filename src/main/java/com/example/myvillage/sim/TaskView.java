package com.example.myvillage.sim;

/**
 * Read-only view of a player's sect task (宗门事务, sect entry slice 3): the task row of
 * {@code world_sim/sect_tasks.json} joined with the progress kept on the player's ledger record.
 *
 * @param id             the task id ({@code sect_tasks.json})
 * @param kind           {@code patrol}, {@code tribute} or {@code courier}
 * @param count          what the task asks for (beasts, spirit stones, letters)
 * @param contribution   the contribution it pays on completion
 * @param progress       progress so far (always 0 for tribute: the stones are checked at turn-in)
 * @param targetSectId   the courier's destination sect, or -1
 * @param targetSectName its name, or ""
 * @param year           the sim year the task was taken
 * @param ready          the ledger alone says it can be turned in ({@code progress >= count});
 *                       always false for tribute, which the runtime judges from the inventory
 */
public record TaskView(
        String id,
        String kind,
        int count,
        int contribution,
        int progress,
        int targetSectId,
        String targetSectName,
        long year,
        boolean ready) {
}
