package com.example.myvillage.cultivation.fate;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SteleSessionBookTest {
    private static final UUID PLAYER = new UUID(1, 2);
    private static final UUID OTHER = new UUID(3, 4);
    private static final String DIMENSION = "minecraft:overworld";

    @Test void ordinaryMutationsRequireAnActivePlayerOwnedSession() {
        var book = new SteleSessionBook();
        assertFalse(book.validate(PLAYER, 0, DIMENSION, true, true, true));
        var session = book.open(PLAYER, DIMENSION, 123);
        assertTrue(session.token() > 0);
        assertTrue(book.validate(PLAYER, session.token(), DIMENSION, true, true, true));
        assertFalse(book.validate(OTHER, session.token(), DIMENSION, true, true, true));
        assertEquals(123, book.current(PLAYER).position());
    }

    @Test void delayedCloseAndMutationCannotAffectAReopenedSession() {
        var book = new SteleSessionBook();
        var first = book.open(PLAYER, DIMENSION, 123);
        var reopened = book.open(PLAYER, DIMENSION, 456);
        assertTrue(reopened.token() > first.token());
        assertFalse(book.close(PLAYER, first.token()));
        assertFalse(book.validate(PLAYER, first.token(), DIMENSION, true, true, true));
        assertEquals(reopened, book.current(PLAYER));
        assertTrue(book.close(PLAYER, reopened.token()));
        assertFalse(book.validate(PLAYER, reopened.token(), DIMENSION, true, true, true));
    }

    @Test void invalidEnvironmentPermanentlyRevokesTheSessionUntilAnotherClick() {
        var book = new SteleSessionBook();
        // Runtime supplies alive/not removed/not spectator, exact loaded block and interaction range.
        for (int invalid = 0; invalid < 4; invalid++) {
            var session = book.open(PLAYER, DIMENSION, 123);
            assertFalse(book.validate(PLAYER, session.token(), invalid == 0 ? "minecraft:the_nether" : DIMENSION,
                    invalid != 1, invalid != 2, invalid != 3));
            assertNull(book.current(PLAYER));
            assertFalse(book.validate(PLAYER, session.token(), DIMENSION, true, true, true));
        }
    }

    @Test void lifecycleForgetAndStopClearWithoutReusingOldTokens() {
        var book = new SteleSessionBook();
        var old = book.open(PLAYER, DIMENSION, 123);
        book.forget(PLAYER);
        assertFalse(book.validate(PLAYER, old.token(), DIMENSION, true, true, true));
        book.open(OTHER, DIMENSION, 456);
        book.clear();
        assertTrue(book.snapshot().isEmpty());
        var next = book.open(PLAYER, DIMENSION, 123);
        assertTrue(next.token() > old.token());
    }
}
