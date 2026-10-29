package com.example.myvillage.worldplan.capital;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 只扩展显式版本，不能把跨城、跨版本或任意路径当作合法资源。 */
class CapitalGameBundleVersionTest {
    private static final String CITY = "capital_donghai";
    private static final String[] VERSIONS = {"v1", "v2", "v3", "v4"};

    @Test void allExplicitVersionsRetainTheExistingPathGrammar() {
        for (String version : VERSIONS) {
            assertEquals(version, CapitalGameBundle.manifestVersion(CITY, "myvillage:capitals/" + version + "/" + CITY + "/city.json"));
            assertDoesNotThrow(() -> CapitalGameBundle.requireTerrainPath(CITY, version, "myvillage:capitals/" + version + "/" + CITY + "/terrain.bin.gz"));
            assertDoesNotThrow(() -> CapitalGameBundle.requireTerrainPath(CITY, version, "myvillage:capitals/" + version + "/" + CITY + "/legacy_ground.bin.gz"));
            assertDoesNotThrow(() -> CapitalGameBundle.requireTilePath(CITY, version, "myvillage:province_capitals/" + version + "/" + CITY + "/tile_0_0"));
        }
    }

    @Test void mixedAssetVersionsCityIdsAndTraversalAreRejected() {
        for (String version : VERSIONS) {
            for (String cityPath : new String[]{"capital_ximo", CITY + "/.."}) {
                assertThrows(IllegalArgumentException.class, () -> CapitalGameBundle.manifestVersion(CITY, "myvillage:capitals/" + version + "/" + cityPath + "/city.json"));
                assertThrows(IllegalArgumentException.class, () -> CapitalGameBundle.requireTerrainPath(CITY, version, "myvillage:capitals/" + version + "/" + cityPath + "/terrain.bin.gz"));
                assertThrows(IllegalArgumentException.class, () -> CapitalGameBundle.requireTilePath(CITY, version, "myvillage:province_capitals/" + version + "/" + cityPath + "/tile_0_0"));
            }
            assertThrows(IllegalArgumentException.class, () -> CapitalGameBundle.manifestVersion(CITY, "other:capitals/" + version + "/" + CITY + "/city.json"));
            assertThrows(IllegalArgumentException.class, () -> CapitalGameBundle.requireTerrainPath(CITY, version, "other:capitals/" + version + "/" + CITY + "/terrain.bin.gz"));
            assertThrows(IllegalArgumentException.class, () -> CapitalGameBundle.requireTilePath(CITY, version, "other:province_capitals/" + version + "/" + CITY + "/tile_0_0"));
            for (String otherVersion : VERSIONS) {
                if (version.equals(otherVersion)) continue;
                assertThrows(IllegalArgumentException.class, () -> CapitalGameBundle.requireTerrainPath(CITY, version, "myvillage:capitals/" + otherVersion + "/" + CITY + "/terrain.bin.gz"));
                assertThrows(IllegalArgumentException.class, () -> CapitalGameBundle.requireTilePath(CITY, version, "myvillage:province_capitals/" + otherVersion + "/" + CITY + "/tile_0_0"));
            }
        }
    }

    @Test void unsupportedVersionsAreRejected() {
        for (String version : new String[]{"v0", "v5"}) {
            assertThrows(IllegalArgumentException.class, () -> CapitalGameBundle.manifestVersion(CITY, "myvillage:capitals/" + version + "/" + CITY + "/city.json"));
            assertThrows(IllegalArgumentException.class, () -> CapitalGameBundle.requireTerrainPath(CITY, version, "myvillage:capitals/" + version + "/" + CITY + "/terrain.bin.gz"));
            assertThrows(IllegalArgumentException.class, () -> CapitalGameBundle.requireTilePath(CITY, version, "myvillage:province_capitals/" + version + "/" + CITY + "/tile_0_0"));
        }
    }
}
