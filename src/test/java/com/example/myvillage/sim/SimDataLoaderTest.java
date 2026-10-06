package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.sim.data.SimDataLoader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class SimDataLoaderTest {

    /** The shipped opener, with one file's text rewritten. */
    private static SimData.ResourceOpener patched(String file, UnaryOperator<String> edit) {
        SimData.ResourceOpener base = SimFixtures.opener();
        return path -> {
            InputStream in = base.open(path);
            if (!path.equals(file) || in == null) {
                return in;
            }
            String text;
            try (in) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            return new ByteArrayInputStream(edit.apply(text).getBytes(StandardCharsets.UTF_8));
        };
    }

    @Test
    void shippedDataLoads() {
        SimData data = SimFixtures.data();
        assertEquals(4, data.realms().size());
        assertTrue(data.rules().tiers().keySet().containsAll(java.util.List.of("small", "medium", "large")));
        assertFalse(data.encounters().encounters().isEmpty());
        assertFalse(data.techniques().isEmpty());
        assertEquals(java.util.List.of("patrol_beasts", "tribute_stones", "courier_letter"),
                data.sectTasks().stream().map(com.example.myvillage.sim.data.ContentTables.SectTask::id).toList());
        assertEquals("courier", data.sectTask("courier_letter").kind());
    }

    @Test
    void unknownFieldNamesFileAndField() {
        SimDataException e = assertThrows(SimDataException.class, () -> WorldSim.loadData(
                patched(SimDataLoader.RULES, t -> t.replaceFirst("\"chronicle\": \\{", "\"chronicle\": {\"bogus\": 1, "))));
        assertEquals(SimDataLoader.RULES, e.file());
        assertEquals("chronicle.bogus", e.field());
    }

    @Test
    void badValueNamesFileAndField() {
        SimDataException e = assertThrows(SimDataException.class, () -> WorldSim.loadData(
                patched(SimDataLoader.REALMS, t -> t.replaceFirst("\"lifespan_years\": 120", "\"lifespan_years\": -5"))));
        assertEquals(SimDataLoader.REALMS, e.file());
        assertEquals("realms[0].lifespan_years", e.field());
    }

    @Test
    void duplicateKeyAndMissingFileAreRejected() {
        assertThrows(SimDataException.class, () -> WorldSim.loadData(
                patched(SimDataLoader.RULES, t -> t.replaceFirst("\\{", "{\"schema\": 1, "))));
        SimData.ResourceOpener base = SimFixtures.opener();
        SimDataException e = assertThrows(SimDataException.class, () -> WorldSim.loadData(
                path -> path.equals(SimDataLoader.ENCOUNTERS) ? null : base.open(path)));
        assertEquals(SimDataLoader.ENCOUNTERS, e.file());
    }

    @Test
    void heritageNamingAnUnknownTechniqueOrTooShortIsRejected() {
        SimDataException unknown = assertThrows(SimDataException.class, () -> WorldSim.loadData(
                patched(SimDataLoader.HERITAGES, t -> t.replaceFirst("\"gengjin_yinqi_fa\"", "\"no_such_art\""))));
        assertEquals(SimDataLoader.HERITAGES, unknown.file());
        assertTrue(unknown.field().endsWith("techniques"), unknown.field());
        SimDataException shortChain = assertThrows(SimDataException.class, () -> WorldSim.loadData(
                patched(SimDataLoader.HERITAGES, t -> t.replaceFirst(
                        "\\[\"gengjin_yinqi_fa\"[^\\]]*\\]", "[\"gengjin_yinqi_fa\"]"))));
        assertTrue(shortChain.field().endsWith("techniques"), shortChain.field());
    }

    @Test
    void missingSectTasksFileIsRejected() {
        SimData.ResourceOpener base = SimFixtures.opener();
        SimDataException e = assertThrows(SimDataException.class, () -> WorldSim.loadData(
                path -> path.equals(SimDataLoader.SECT_TASKS) ? null : base.open(path)));
        assertEquals(SimDataLoader.SECT_TASKS, e.file());
    }

    @Test
    void sectTaskWithAnUnknownKindIsRejected() {
        SimDataException e = assertThrows(SimDataException.class, () -> WorldSim.loadData(
                patched(SimDataLoader.SECT_TASKS, t -> t.replaceFirst("\"kind\": \"patrol\"", "\"kind\": \"hunt\""))));
        assertEquals(SimDataLoader.SECT_TASKS, e.file());
        assertEquals("tasks[0].kind", e.field());
    }

    @Test
    void heritageEffectTakesNoGradeOrAmount() {
        SimDataException e = assertThrows(SimDataException.class, () -> WorldSim.loadData(
                patched(SimDataLoader.ENCOUNTERS, t -> t.replaceFirst("\\{\"kind\": \"heritage\"\\}",
                        "{\"kind\": \"heritage\", \"grade\": \"di\"}"))));
        assertEquals(SimDataLoader.ENCOUNTERS, e.file());
    }

    @Test
    void encounterNamingAnUnknownRealmIsRejected() throws IOException {
        SimDataException e = assertThrows(SimDataException.class, () -> WorldSim.loadData(
                patched(SimDataLoader.ENCOUNTERS, t -> t.replaceFirst("\"realms\": \\[\\]", "\"realms\": [\"immortal\"]"))));
        assertTrue(e.field().endsWith("realms"), e.field());
    }
}
