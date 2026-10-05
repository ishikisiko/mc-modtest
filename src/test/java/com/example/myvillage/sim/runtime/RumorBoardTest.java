package com.example.myvillage.sim.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.myvillage.sim.SimEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Who hears a rumor, in which order, and how often. */
class RumorBoardTest {
    private static final Map<String, String> REGION = Map.of("a", "donghai", "b", "zhongzhou", "c", "donghai");
    private static final List<String> PLAYERS = List.of("a", "b", "c", "d");

    private static SimEvent event(int importance, String region) {
        return new SimEvent(1, 700, "test", importance, List.of(1), List.of(), region, -1,
                "world_sim.event.test", List.of());
    }

    private static List<String> audience(SimEvent e) {
        return RumorBoard.audience(e, PLAYERS, p -> Optional.ofNullable(REGION.get(p)));
    }

    @Test
    void majorEventsReachEveryoneWithThePlayersInTheRegionFirst() {
        assertEquals(List.of("a", "c", "b", "d"), audience(event(3, "donghai")));
        assertEquals(List.of("a", "b", "c", "d"), audience(event(3, "")));
    }

    @Test
    void notableEventsReachOnlyThePlayersInTheRegionAndMinorOnesNobody() {
        assertEquals(List.of("a", "c"), audience(event(2, "donghai")));
        assertEquals(List.of(), audience(event(2, "")));
        assertEquals(List.of(), audience(event(1, "donghai")));
    }

    @Test
    void eachPlayerHearsAtMostTheBudgetPerMinute() {
        RumorBoard<String, String> board = new RumorBoard<>();
        for (int i = 0; i < 5; i++) {
            board.offer("a", "r" + i, 10);
        }
        assertEquals(List.of("r0", "r1"), messages(board.due(1_000, 2)));
        assertEquals(List.of(), messages(board.due(30_000, 2)));
        assertEquals(List.of("r2", "r3"), messages(board.due(61_001, 2)));
        assertEquals(1, board.waiting("a"));
    }

    @Test
    void anOverflowingQueueDropsTheOldest() {
        RumorBoard<String, String> board = new RumorBoard<>();
        for (int i = 0; i < 10; i++) {
            board.offer("a", "r" + i, 3);
        }
        assertEquals(3, board.waiting("a"));
        assertEquals(7, board.dropped());
        assertEquals(List.of("r7", "r8", "r9"), messages(board.due(0, 60)));
    }

    @Test
    void rumorsAreReleasedInTheOrderTheyWereOffered() {
        RumorBoard<String, String> board = new RumorBoard<>();
        board.offer("c", "e1", 5);
        board.offer("a", "e1", 5);
        board.offer("a", "e2", 5);
        board.offer("c", "e2", 5);
        List<RumorBoard.Entry<String, String>> due = board.due(0, 5);
        assertEquals(List.of("c", "a", "a", "c"), due.stream().map(RumorBoard.Entry::player).toList());
        assertEquals(List.of("e1", "e1", "e2", "e2"), messages(due));
    }

    @Test
    void forgettingAPlayerDropsTheirQueueAndBudget() {
        RumorBoard<String, String> board = new RumorBoard<>();
        board.offer("a", "r0", 5);
        board.offer("a", "r1", 5);
        board.due(0, 1);
        board.forget("a");
        assertEquals(0, board.waiting("a"));
        board.offer("a", "r2", 5);
        assertEquals(List.of("r2"), messages(board.due(1, 1)));
    }

    private static List<String> messages(List<RumorBoard.Entry<String, String>> entries) {
        return entries.stream().map(RumorBoard.Entry::message).toList();
    }
}
