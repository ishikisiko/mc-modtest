package com.example.myvillage.client.boss;

import com.google.gson.JsonParser;
import java.awt.Font;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossBarTypographyTest {
    @Test void hudFontProvidersResolvePackagedFontsAndCoverChineseAndNumbers() throws Exception {
        for (String id : new String[]{"boss_title", "boss_detail"}) {
            String root = "/assets/myvillage/font/";
            try (var json = getClass().getResourceAsStream(root + id + ".json")) {
                assertNotNull(json);
                var providers = JsonParser.parseReader(new InputStreamReader(json, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonArray("providers");
                var ttf = providers.get(0).getAsJsonObject();
                assertEquals("ttf", ttf.get("type").getAsString());
                String file = ttf.get("file").getAsString().split(":", 2)[1];
                try (var license = getClass().getResourceAsStream(root + file.replace(".ttf", "-ofl.txt"))) {
                    assertNotNull(license);
                    assertTrue(new String(license.readAllBytes(), StandardCharsets.UTF_8).contains("SIL OPEN FONT LICENSE"));
                }
                try (var input = getClass().getResourceAsStream(root + file)) {
                    assertNotNull(input);
                    Font font = Font.createFont(Font.TRUETYPE_FONT, input);
                    assertEquals(-1, font.canDisplayUpTo("雷角山君筑基初期一阶段二阶段狂怒雷角完整已断煞0123456789% Thunderhorn"));
                }
                assertEquals("minecraft:default", providers.get(1).getAsJsonObject().get("id").getAsString());
            }
        }
    }
}
