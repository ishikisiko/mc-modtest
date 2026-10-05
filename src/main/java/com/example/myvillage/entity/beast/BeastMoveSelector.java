package com.example.myvillage.entity.beast;

import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Picks the next move: a move is a candidate when the target's horizontal centre-to-centre
 * distance lies in its {@code use_range} and its cooldown is over, and no move may start until
 * {@code move_gap_ticks} have passed since the previous one ended. Candidates are picked by
 * {@code weight}. All ticks are the beast's own tick count.
 */
public final class BeastMoveSelector {
    /** No move is ready. */
    public static final int NONE = -1;

    private BeastMoveSelector() {
    }

    /**
     * @param readyAt per move index, the first beast tick at which its cooldown is over
     * @param lastMoveEnd the beast tick at which the previous move ended or was cancelled
     * @param random {@code bound -> [0, bound)}, for example {@code RandomSource::nextInt}
     * @return the chosen move index, or {@link #NONE}
     */
    public static int select(
            List<BeastMoveDefinition> moves,
            double distance,
            long now,
            long[] readyAt,
            long lastMoveEnd,
            int moveGapTicks,
            IntUnaryOperator random) {
        if (now - lastMoveEnd < moveGapTicks) {
            return NONE;
        }
        int totalWeight = 0;
        for (int index = 0; index < moves.size(); index++) {
            if (candidate(moves.get(index), distance, now, readyAt[index])) {
                totalWeight += moves.get(index).weight();
            }
        }
        if (totalWeight <= 0) {
            return NONE;
        }
        int roll = random.applyAsInt(totalWeight);
        for (int index = 0; index < moves.size(); index++) {
            BeastMoveDefinition move = moves.get(index);
            if (candidate(move, distance, now, readyAt[index])) {
                roll -= move.weight();
                if (roll < 0) {
                    return index;
                }
            }
        }
        throw new IllegalStateException("random returned a value outside [0, " + totalWeight + ")");
    }

    public static boolean candidate(BeastMoveDefinition move, double distance, long now, long readyAt) {
        return now >= readyAt && move.useRange().contains(distance);
    }
}
