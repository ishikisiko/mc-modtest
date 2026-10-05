package com.example.myvillage.sim;

import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.data.EncounterTable;
import com.example.myvillage.sim.data.RealmTable;
import com.example.myvillage.sim.data.Rules;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Everything the world sim reads from {@code data/myvillage/world_sim/}: the core's rules, realms
 * and encounters plus the content tables (names, techniques, lore). Load it with
 * {@link WorldSim#loadData}; it is immutable.
 */
public record SimData(
        Rules rules,
        RealmTable realms,
        EncounterTable encounters,
        ContentTables.Names names,
        List<ContentTables.Technique> techniques,
        ContentTables.Lore lore) {

    public static final String DIRECTORY = "data/myvillage/world_sim/";

    /** Opens one data path (relative, no leading slash); returns null when it does not exist. */
    @FunctionalInterface
    public interface ResourceOpener {
        InputStream open(String path) throws IOException;
    }

    /** Technique by id, or null. */
    public ContentTables.Technique technique(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (ContentTables.Technique t : techniques) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }

    /** Artifact by id, or null. */
    public ContentTables.Artifact artifact(String id) {
        for (ContentTables.Artifact a : lore.artifacts()) {
            if (a.id().equals(id)) {
                return a;
            }
        }
        return null;
    }
}
