package com.example.myvillage.talisman;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TalismanBatchTest {
    @Test void frozenQualityAndPaidProgressSurviveSerializationAndResume() {
        var batch = new TalismanBatch(UUID.randomUUID(), 2, 91, 4, 4, 17, 40, 1);
        var restored = TalismanBatch.load(batch.save()); assertEquals(batch, restored);
        assertEquals(18, restored.advance().pulses()); assertEquals(4, restored.advance().quality());
        assertEquals(batch.owner(), restored.advance().owner()); assertEquals(40, restored.advance().required());
    }
    @Test void qualityCannotExceedFrozenCeilingAndACompletedDraftCannotAdvanceTwice() {
        assertThrows(IllegalArgumentException.class, () -> new TalismanBatch(UUID.randomUUID(), 0, 0, 1, 4, 0, 40, 1));
        assertThrows(IllegalArgumentException.class, () -> new TalismanBatch(UUID.randomUUID(), 3, 90, 4, 4, 0, 40, 1));
        var finalPulse = new TalismanBatch(UUID.randomUUID(), 0, 70, 3, 3, 39, 40, 1).advance();
        assertEquals(40, finalPulse.pulses()); assertThrows(IllegalStateException.class, finalPulse::advance);
    }
}
