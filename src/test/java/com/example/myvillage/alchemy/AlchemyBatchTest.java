package com.example.myvillage.alchemy;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AlchemyBatchTest {
    private static final UUID BREWER = UUID.fromString("619a2fa3-76e3-45c1-b1fa-5136ef930224");

    @Test void startRequiresEveryAuthorityAndMaterialGateBeforeAnyQualityCanBeDrawn() {
        assertNull(AlchemyBatch.rejection(true, false, true, true, true, true));
        assertEquals(AlchemyBatch.Rejection.INVALID, AlchemyBatch.rejection(false, false, true, true, true, true));
        assertEquals(AlchemyBatch.Rejection.BUSY, AlchemyBatch.rejection(true, true, true, true, true, true));
        assertEquals(AlchemyBatch.Rejection.UNAWAKENED, AlchemyBatch.rejection(true, false, false, true, true, true));
        assertEquals(AlchemyBatch.Rejection.INGREDIENTS, AlchemyBatch.rejection(true, false, true, false, true, true));
        assertEquals(AlchemyBatch.Rejection.FUEL, AlchemyBatch.rejection(true, false, true, true, false, true));
        assertEquals(AlchemyBatch.Rejection.OUTPUT, AlchemyBatch.rejection(true, false, true, true, true, false));
        // Even a matching pill stack must be rejected: compatibility must not reveal a future roll.
        assertEquals("message.myvillage.alchemy.start_output",
                AlchemyBatch.rejection(true, false, true, true, true, false).messageKey());
        // A second actor cannot replace an active brewer, even if their own aptitude is missing.
        assertEquals(AlchemyBatch.Rejection.BUSY, AlchemyBatch.rejection(true, true, false, true, true, true));
    }

    @Test void repeatedSaveLoadPreservesBrewerFrozenValuesAndTheExactHiddenOutcome() {
        for (int cap = 1; cap <= 4; cap++) for (int quality = 1; quality <= cap; quality++) {
            AlchemyBatch original = new AlchemyBatch(BREWER, "huiling", 100, cap, quality);
            AlchemyBatch current = original;
            for (int restart = 0; restart < 10; restart++) {
                current = AlchemyBatch.read(current.save().copy()).orElseThrow();
                assertEquals(original, current);
            }
        }
    }

    @Test void incompleteLegacyAndCorruptSavedDecisionsCannotResume() {
        assertTrue(AlchemyBatch.read(new CompoundTag()).isEmpty());
        CompoundTag good = new AlchemyBatch(BREWER, "ningshen", 81, 4, 3).save();
        for (String required : new String[]{"Version", "Brewer", "Recipe", "Score", "Cap", "Quality"}) {
            CompoundTag missing = good.copy(); missing.remove(required);
            assertTrue(AlchemyBatch.read(missing).isEmpty(), required);
        }
        for (int value : new int[]{Integer.MIN_VALUE, -1, 0, 5, Integer.MAX_VALUE}) {
            CompoundTag corrupt = good.copy(); corrupt.putInt("Quality", value);
            assertTrue(AlchemyBatch.read(corrupt).isEmpty(), "quality " + value);
        }
        CompoundTag exceedsCap = good.copy(); exceedsCap.putInt("Cap", 2);
        assertTrue(AlchemyBatch.read(exceedsCap).isEmpty());
        CompoundTag wrongType = good.copy(); wrongType.putFloat("Quality", 3.9F);
        assertTrue(AlchemyBatch.read(wrongType).isEmpty());
        CompoundTag badBrewer = good.copy(); badBrewer.putIntArray("Brewer", new int[]{1, 2});
        assertTrue(AlchemyBatch.read(badBrewer).isEmpty());
        CompoundTag future = good.copy(); future.putInt("Version", 2);
        assertTrue(AlchemyBatch.read(future).isEmpty());
        CompoundTag score = good.copy(); score.putInt("Score", 101);
        assertTrue(AlchemyBatch.read(score).isEmpty());
    }

    @Test void blockedProgressAndRestartsDoNotChangeTheSavedDecision() {
        AlchemyBatch decision = new AlchemyBatch(BREWER, "yangyuan", 100, 4, 4);
        BrewingProgress progress = new BrewingProgress();
        progress.restore(decision.recipe(), 2, 5);
        for (int i = 0; i < 100; i++) {
            assertFalse(progress.advance(decision.recipe(), 5, false, false));
            decision = AlchemyBatch.read(decision.save()).orElseThrow();
        }
        assertEquals(2, progress.ticks()); assertEquals(4, decision.quality()); assertEquals(BREWER, decision.brewer());
        assertFalse(progress.advance(decision.recipe(), 5, false, true));
        assertFalse(progress.advance(decision.recipe(), 5, false, true));
        assertTrue(progress.advance(decision.recipe(), 5, false, true));
    }
}
