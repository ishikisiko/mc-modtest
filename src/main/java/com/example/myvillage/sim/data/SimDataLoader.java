package com.example.myvillage.sim.data;

import com.example.myvillage.sim.SimData;
import com.example.myvillage.sim.SimDataException;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Reads the seven world-sim data files through a caller-supplied opener, so the same code reads the
 * mod jar at runtime and the source tree in tests and the CLI. Any problem throws a
 * {@link SimDataException} naming the file and field; there is no partial result.
 */
public final class SimDataLoader {
    public static final String RULES = SimData.DIRECTORY + "rules.json";
    public static final String REALMS = SimData.DIRECTORY + "realms.json";
    public static final String ENCOUNTERS = SimData.DIRECTORY + "encounters.json";
    public static final String NAMES = SimData.DIRECTORY + "names.json";
    public static final String TECHNIQUES = SimData.DIRECTORY + "techniques.json";
    public static final String LORE = SimData.DIRECTORY + "lore.json";
    public static final String HERITAGES = SimData.DIRECTORY + "heritages.json";

    private SimDataLoader() {
    }

    public static SimData load(SimData.ResourceOpener opener) {
        RealmTable realms = RealmTable.parse(REALMS, read(opener, REALMS));
        Rules rules = Rules.parse(RULES, read(opener, RULES), realms);
        EncounterTable encounters = EncounterTable.parse(ENCOUNTERS, read(opener, ENCOUNTERS), realms);
        ContentTables.Names names = ContentTables.parseNames(SimJson.Fields.root(NAMES, read(opener, NAMES),
                Set.of("schema", "surnames", "given_male", "given_female", "given_neutral", "dao_titles",
                        "sect_prefixes", "sect_suffixes")));
        var techniques = ContentTables.parseTechniques(
                SimJson.Fields.root(TECHNIQUES, read(opener, TECHNIQUES), Set.of("schema", "techniques")));
        ContentTables.Lore lore = ContentTables.parseLore(
                SimJson.Fields.root(LORE, read(opener, LORE), Set.of("schema", "artifacts", "sites", "beasts")));
        var heritages = ContentTables.parseHeritages(
                SimJson.Fields.root(HERITAGES, read(opener, HERITAGES), Set.of("schema", "heritages")), techniques);
        for (int i = 0; i < encounters.encounters().size(); i++) {
            EncounterTable.Encounter e = encounters.encounters().get(i);
            if (e.siteKind() != null && lore.sites().stream().noneMatch(s -> s.kind().equals(e.siteKind()))) {
                throw new SimDataException(ENCOUNTERS, "encounters[" + i + "].site_kind",
                        "no site of kind " + e.siteKind() + " in " + LORE);
            }
        }
        if (heritages.isEmpty() && encounters.encounters().stream().anyMatch(e -> e.has("heritage"))) {
            throw new SimDataException(ENCOUNTERS, "effects", "a heritage effect needs at least one heritage in " + HERITAGES);
        }
        return new SimData(rules, realms, encounters, names, techniques, lore, heritages);
    }

    private static JsonObject read(SimData.ResourceOpener opener, String file) {
        InputStream stream;
        try {
            stream = opener.open(file);
        } catch (IOException exception) {
            throw new SimDataException(file, "<file>", "cannot open: " + exception.getMessage(), exception);
        }
        if (stream == null) {
            throw new SimDataException(file, "<file>", "is missing");
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return SimJson.parseStrict(file, reader);
        } catch (IOException exception) {
            throw new SimDataException(file, "<root>", "cannot read: " + exception.getMessage(), exception);
        }
    }
}
