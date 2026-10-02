package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.CombatStyleDefinition;
import com.example.myvillage.combat.definition.CombatTestData;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The local combat clock: when it advances, how server ticks enter it, and what a reset does. */
final class ClientCombatClockTest {
    @AfterEach
    void clearState() {
        ClientCombatState.clear();
        ClientCombatClock.setForTest(0L);
    }

    @Test
    void aServerTickEntersAsTicksElapsedByTheGameClockNeverAhead() {
        ClientCombatClock.setForTest(500L);
        assertEquals(2L, ClientCombatClock.elapsedSince(1000L, 1002L));
        assertEquals(0L, ClientCombatClock.elapsedSince(1003L, 1002L), "a start the clock has not reached is now");
        assertEquals(498L, ClientCombatClock.localTickAgo(2L));
        assertEquals(500L, ClientCombatClock.localTickAgo(-3L));
        ClientCombatClock.endOfTick(true, 1003L);
        assertEquals(501.25, ClientCombatClock.now(0.25F), 1.0E-9);
    }

    /**
     * The count advances at the end of the client tick, in the first Post handler, like the level's
     * game time (which advances late in the tick). A prediction made during the tick (key
     * handling) then reads the count the next START maps the server's start to. Advancing in Pre
     * would put every predicted start one tick ahead of the server's.
     */
    @Test
    void theCountAdvancesAtTheEndOfTheTickBeforeOtherCombatHandlers() throws Exception {
        List<Method> tickHandlers = new ArrayList<>();
        for (Method method : ClientCombatEvents.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(SubscribeEvent.class) && method.getParameterCount() == 1
                    && ClientTickEvent.class.isAssignableFrom(method.getParameterTypes()[0])) {
                tickHandlers.add(method);
            }
        }
        Method advance = ClientCombatEvents.class.getDeclaredMethod("advanceCombatClock", ClientTickEvent.Post.class);
        assertTrue(Modifier.isStatic(advance.getModifiers()));
        assertEquals(EventPriority.HIGHEST, advance.getAnnotation(SubscribeEvent.class).priority());
        for (Method handler : tickHandlers) {
            assertEquals(ClientTickEvent.Post.class, handler.getParameterTypes()[0],
                    handler.getName() + " handles a Pre tick: the combat clock and its readers run at the end of the tick");
            if (!handler.equals(advance)) {
                assertTrue(handler.getAnnotation(SubscribeEvent.class).priority().ordinal()
                        > EventPriority.HIGHEST.ordinal(), handler.getName() + " may run before the clock advances");
            }
        }
        // Minecraft's order in a frame: packets, then ticks (key handling, entities, level time,
        // then the Post handlers), then the render. A click predicted during the tick and the
        // server's START for it (the server started it in the tick it received the click, read
        // here one tick later) map to the same local tick.
        ClientCombatClock.setForTest(40L);
        long gameTime = 2000L;
        long predictedAt = ClientCombatClock.ticks();      // key handling, before this tick's Post
        gameTime++;                                         // the level ticks
        ClientCombatClock.endOfTick(true, gameTime);        // Post
        long serverStart = 2000L;                           // the server's tick for the same click
        long mapped = ClientCombatClock.localTickAgo(ClientCombatClock.elapsedSince(serverStart, gameTime));
        assertEquals(predictedAt, mapped, "prediction and authoritative start disagree by the tick phase");
    }

    @Test
    void pausedOrFrozenTicksNeitherAdvanceNorReportAReset() {
        assertTrue(ClientCombatClock.levelRan(true, false, true));
        assertTrue(!ClientCombatClock.levelRan(true, true, true), "paused");
        assertTrue(!ClientCombatClock.levelRan(true, false, false), "tick-frozen");
        assertTrue(!ClientCombatClock.levelRan(false, false, true), "no level");

        ClientCombatClock.setForTest(10L);
        assertNull(ClientCombatClock.endOfTick(true, 100L), "first reading");
        assertNull(ClientCombatClock.endOfTick(true, 101L));
        // Frozen for three ticks: game time stands still; nothing advances, nothing is reported.
        for (int tick = 0; tick < 3; tick++) {
            assertNull(ClientCombatClock.endOfTick(false, 101L));
        }
        assertEquals(12L, ClientCombatClock.ticks());
        // A time packet during the freeze is not a reset of a running clock.
        assertNull(ClientCombatClock.endOfTick(false, 103L));
        assertNull(ClientCombatClock.endOfTick(true, 104L), "resumes one tick after the frozen value");
        ClientCombatClock.Reset reset = ClientCombatClock.endOfTick(true, 107L);
        assertNotNull(reset);
        assertEquals(104L, reset.from());
        assertEquals(107L, reset.to());
        assertEquals(14L, reset.local());
        assertNotNull(ClientCombatClock.endOfTick(true, 107L), "a set-back of one tick (time repeated)");
        ClientCombatClock.forgetGameTime();
        assertNull(ClientCombatClock.endOfTick(true, 5000L), "a new level after respawn or a dimension change");
    }

    /**
     * The chain is predicted chainTick local ticks after the server's start as mapped on arrival.
     * The client's game clock is {@code drift} ticks off the server's until a time packet sets it
     * right. Reset before the START: the mapping reads the corrected clock and the chain lands on
     * the server's chain tick exactly. Reset after the START: the reset moves nothing (the same as
     * never resetting); the drift read at the START stays in the mapping when the client is ahead.
     */
    @Test
    void theStartMapsWithTheGameClockOfItsArrivalAndALaterResetMovesNothing() {
        CombatStyleDefinition spear = CombatTestData.basicSpear();
        int chainTick = spear.move(1).chainTick();
        int inSync = predictedChain(spear, 0, Integer.MAX_VALUE);
        assertEquals(100 + chainTick, inSync, "the server chains at its start plus the chain tick");
        for (int drift : new int[] {2, 1, 0, -1, -2}) {
            int neverReset = predictedChain(spear, drift, Integer.MAX_VALUE);
            // The START reads one tick of latency plus the drift, never less than nothing.
            assertEquals(101 - Math.max(0, 1 + drift) + chainTick, neverReset, "drift " + drift);
            assertEquals(inSync, predictedChain(spear, drift, 98), "reset before the START, drift " + drift);
            assertEquals(inSync, predictedChain(spear, drift, 102), "reset in the START's own frame, before it");
            for (int resetAt = 103; resetAt <= 112; resetAt++) {
                assertEquals(neverReset, predictedChain(spear, drift, resetAt),
                        "reset at local tick " + resetAt + " after the START, drift " + drift);
            }
        }
    }

    /**
     * The client ticks in lockstep with the server (server tick = local tick + 1000); its game
     * clock reads {@code drift} ahead of the server's until the time packet at local tick
     * {@code resetAt}. The server started the sweep at its tick 1100; the START arrives a tick
     * later, at the start of the frame after local tick 101 (game time still that tick's).
     */
    private static int predictedChain(CombatStyleDefinition spear, int drift, int resetAt) {
        ClientCombatState.clear();
        ClientCombatClock.setForTest(95L);
        long gameTime = 95L + 1000L + drift;
        for (long local = 96; local <= 160; local++) {
            // Packets at the start of the frame: the time packet, then the START.
            if (local == resetAt) {
                gameTime = local - 1 + 1000L;
            }
            if (local == 102) {
                long elapsed = ClientCombatClock.elapsedSince(1100L, gameTime);
                ClientCombatState.trackLocalAction(spear, 1, ClientCombatClock.localTickAgo(elapsed), 7L);
                ClientCombatState.confirmPrediction(2);
                ClientCombatState.bufferClick(ClientCombatClock.ticks() + 5, spear);
            }
            // The tick: the level's game time advances, then the clock at the end of the tick.
            gameTime++;
            ClientCombatClock.endOfTick(true, gameTime);
            if (local > 102 && ClientCombatState.chainDue(ClientCombatClock.ticks(), spear) >= 0) {
                return (int) ClientCombatClock.ticks();
            }
        }
        throw new AssertionError("no chain predicted");
    }
}
