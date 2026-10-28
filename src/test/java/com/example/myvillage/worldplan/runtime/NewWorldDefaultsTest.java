package com.example.myvillage.worldplan.runtime;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NewWorldDefaultsTest {
    @Test void omittedTypeDefaultsToCultivationWithoutChangingOtherSettingsOrCaller() {
        Properties p = new Properties(); p.setProperty("level-name", "existing-name"); p.setProperty("level-seed", "123");
        Properties result = NewWorldDefaults.server(p);
        assertEquals("myvillage:cultivation", result.getProperty("level-type"));
        assertEquals("123", result.getProperty("level-seed"));
        assertEquals("existing-name", result.getProperty("level-name"));
        assertNull(p.getProperty("level-type"));
    }
    @Test void explicitNormalFlatLegacyAndThirdPartyChoicesArePreserved() {
        for (String type : new String[]{"minecraft:normal", "minecraft:flat", "default", "largebiomes", "other:custom", "myvillage:cultivation"}) {
            Properties p = new Properties(); p.setProperty("level-type", type);
            assertSame(p, NewWorldDefaults.server(p)); assertEquals(type, p.getProperty("level-type"));
        }
    }
    @Test void blankTypeIsAnUnsetDefaultAndInheritedChoiceIsRespected() {
        Properties p = new Properties(); p.setProperty("level-type", "  ");
        assertEquals(NewWorldDefaults.PRESET, NewWorldDefaults.server(p).getProperty("level-type"));
        Properties inherited = new Properties(); inherited.setProperty("level-type", "minecraft:normal");
        Properties child = new Properties(inherited);
        assertSame(child, NewWorldDefaults.server(child));
    }
}
