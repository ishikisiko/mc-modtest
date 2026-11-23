package com.example.myvillage.alchemy;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicinePersistenceTest {
    private static CompoundTag saved(String id, MedicineState state) {
        CompoundTag doses = new CompoundTag();
        MedicineRuntime.write(doses, id, state);
        return doses;
    }

    @Test void qingzhangQualityDurationSurvivesReadAndBoundsCorruptSaves() {
        var rules=AlchemyRules.get();
        int[] durations={600,750,900,1200};
        for(int q=1;q<=4;q++){
            var state=new MedicineState(450,"qingzhang",durations[q-1]-20,0,q);
            var doses = saved("qingzhang", state);
            assertEquals(state, MedicineRuntime.read(doses, rules, "qingzhang"));
            var corrupt = doses.getCompound("qingzhang");
            corrupt.putInt("effect_ticks", Integer.MAX_VALUE);
            assertEquals(durations[q-1], MedicineRuntime.read(doses, rules, "qingzhang").effectTicks());
            corrupt.remove("quality");
            assertEquals(600, MedicineRuntime.read(doses, rules, "qingzhang").effectTicks());
        }
        // Existing active doses keep their remaining time; upgrading never refills a dose.
        var old=new MedicineState(0,"qingzhang",350,0,4);
        assertEquals(old, MedicineRuntime.read(saved("qingzhang", old), rules, "qingzhang"));
    }
    @Test void saveLoadPreservesPartialIntervalWithoutOfflineAdvancement() {
        var state = new MedicineState(452, "yangyuan", 52, 8);
        CompoundTag saved = saved("yangyuan", state);
        assertEquals(state, MedicineRuntime.read(saved.copy(), AlchemyRules.get(), "yangyuan"));
        assertEquals(state, MedicineRuntime.read(saved.copy(), AlchemyRules.get(), "yangyuan"));
        var next = MedicineRuntime.read(saved, AlchemyRules.get(), "yangyuan").tick(20);
        assertFalse(next.heal());
        assertEquals(new MedicineState(451, "yangyuan", 51, 9), next.next());
    }

    @Test void malformedSaveCannotCreateUnknownOrOverlongBenefits() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("cooldown_ticks", Integer.MAX_VALUE);
        tag.putInt("effect_ticks", Integer.MAX_VALUE);
        CompoundTag doses = new CompoundTag();
        doses.put("huiling", tag);
        doses.put("ningshen", tag.copy());
        var state = MedicineRuntime.read(doses, AlchemyRules.get(), "huiling");
        assertEquals(AlchemyRules.get().cooldownTicks(), state.cooldownTicks());
        assertEquals("", state.effect());
        doses.getCompound("ningshen").putInt("healing_elapsed", Integer.MAX_VALUE);
        state = MedicineRuntime.read(doses, AlchemyRules.get(), "ningshen");
        assertEquals(AlchemyRules.get().effects().ningshen().durationTicks(), state.effectTicks());
        assertEquals(0, state.healingElapsed());
    }
    @Test void sustainedQualitySurvivesSaveRestartAndOnlineTicks() {
        for (String effect : new String[] {"yangyuan", "ningshen"}) {
            for (int quality = 1; quality <= 4; quality++) {
                MedicineState state = new MedicineState(452, effect, 52, effect.equals("yangyuan") ? 8 : 0, quality);
                CompoundTag saved = saved(effect, state);
                assertEquals(quality, saved.getCompound(effect).getInt("quality"));
                MedicineState loaded = MedicineRuntime.read(saved.copy(), AlchemyRules.get(), effect);
                assertEquals(state, loaded);
                assertEquals(quality, loaded.tick(20).next().quality());
                assertEquals(1, loaded.clearBenefit().quality());
            }
        }
    }

    @Test void reformationMedicineBeginsPersistsAndTicksWithoutHealing() {
        var rules = AlchemyRules.get();
        var reformation = com.example.myvillage.cultivation.nascent.NascentRules.get();
        var dose = MedicineState.EMPTY.begin("xuying", rules.cooldownTicks(),
                reformation.value("reformation_pill_ticks"), 4);
        var loaded = MedicineRuntime.read(saved("xuying", dose), rules, "xuying");
        assertEquals(dose, loaded);
        var next = loaded.tick(20);
        assertFalse(next.heal());
        assertEquals(0, next.next().healingElapsed());
        assertEquals(loaded.effectTicks() - 1, next.next().effectTicks());
        assertEquals(3.0, reformation.reformationMultiplier(next.next().quality()));
    }

    @Test void legacyAndCorruptQualityFallBackToBaseline() {
        CompoundTag doses = saved("yangyuan", new MedicineState(452, "yangyuan", 52, 8, 4));
        CompoundTag saved = doses.getCompound("yangyuan");
        saved.remove("quality");
        assertEquals(1, MedicineRuntime.read(doses, AlchemyRules.get(), "yangyuan").quality());
        for (int invalid : new int[] {Integer.MIN_VALUE, 0, 5, Integer.MAX_VALUE}) {
            saved.putInt("quality", invalid);
            assertEquals(1, MedicineRuntime.read(doses, AlchemyRules.get(), "yangyuan").quality());
        }
        saved.putString("quality", "4");
        assertEquals(1, MedicineRuntime.read(doses, AlchemyRules.get(), "yangyuan").quality());
        saved.putDouble("quality", 4.0);
        assertEquals(1, MedicineRuntime.read(doses, AlchemyRules.get(), "yangyuan").quality());
    }

    @Test void medicinesKeepIndependentEffectsAndCooldownsAcrossDeathAndReload() {
        var rules = AlchemyRules.get();
        var yangyuan = new MedicineState(452, "yangyuan", 52, 8, 2);
        var ningshen = new MedicineState(0, "ningshen", 560, 0, 4);
        var zhuji = new MedicineState(570, "", 0, 0);
        CompoundTag doses = saved("yangyuan", yangyuan);
        assertTrue(MedicineRuntime.read(doses, rules, "ningshen").available());
        MedicineRuntime.write(doses, "ningshen", ningshen);
        MedicineRuntime.write(doses, "zhuji", zhuji);
        CompoundTag restored = doses.copy();
        assertEquals(yangyuan, MedicineRuntime.read(restored, rules, "yangyuan"));
        assertEquals(ningshen, MedicineRuntime.read(restored, rules, "ningshen"));
        assertEquals(zhuji, MedicineRuntime.read(restored, rules, "zhuji"));
        assertTrue(MedicineRuntime.read(restored, rules, "buji").available());
        MedicineRuntime.clearBenefits(restored, rules);
        assertEquals(yangyuan.clearBenefit(), MedicineRuntime.read(restored, rules, "yangyuan"));
        assertFalse(restored.contains("ningshen"));
        assertEquals(zhuji, MedicineRuntime.read(restored, rules, "zhuji"));
    }
}
