package com.example.myvillage.worldplan.capital;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 只扩展显式版本，不能把跨城、跨版本或任意路径当作合法资源。 */
class CapitalGameBundleVersionTest {
    private static final String CITY = "capital_donghai";

    @Test void bothExplicitVersionsRetainTheExistingPathGrammar() {
        for (String version : new String[]{"v1", "v2", "v3"}) {
            assertEquals(version, CapitalGameBundle.manifestVersion(CITY, "myvillage:capitals/" + version + "/" + CITY + "/city.json"));
            assertDoesNotThrow(() -> CapitalGameBundle.requireTerrainPath(CITY, version, "myvillage:capitals/" + version + "/" + CITY + "/terrain.bin.gz"));
            assertDoesNotThrow(() -> CapitalGameBundle.requireTerrainPath(CITY, version, "myvillage:capitals/" + version + "/" + CITY + "/legacy_ground.bin.gz"));
            assertDoesNotThrow(() -> CapitalGameBundle.requireTilePath(CITY, version, "myvillage:province_capitals/" + version + "/" + CITY + "/tile_0_0"));
        }
    }

    @Test void mixedAssetVersionsCityIdsAndTraversalAreRejected() {
        for (String path : new String[]{"myvillage:capitals/v3/" + CITY + "/city.json", "myvillage:capitals/v2/capital_ximo/city.json", "other:capitals/v2/" + CITY + "/city.json", "myvillage:capitals/v2/" + CITY + "/../city.json"})
            assertThrows(IllegalArgumentException.class, () -> CapitalGameBundle.manifestVersion(CITY, path));
        for (String path : new String[]{"myvillage:capitals/v1/" + CITY + "/terrain.bin.gz", "myvillage:capitals/v2/capital_ximo/terrain.bin.gz", "myvillage:capitals/v2/" + CITY + "/../terrain.bin.gz"})
            assertThrows(IllegalArgumentException.class, () -> CapitalGameBundle.requireTerrainPath(CITY, "v2", path));
        for (String path : new String[]{"myvillage:province_capitals/v1/" + CITY + "/tile_0_0", "myvillage:province_capitals/v2/capital_ximo/tile_0_0", "myvillage:province_capitals/v2/" + CITY + "/../tile_0_0"})
            assertThrows(IllegalArgumentException.class, () -> CapitalGameBundle.requireTilePath(CITY, "v2", path));
        assertThrows(IllegalArgumentException.class, () -> CapitalGameBundle.requireTerrainPath(CITY, "v3", "myvillage:capitals/v3/" + CITY + "/terrain.bin.gz"));
        assertThrows(IllegalArgumentException.class, () -> CapitalGameBundle.requireTilePath(CITY, "v3", "myvillage:province_capitals/v3/" + CITY + "/tile_0_0"));
    }
}
