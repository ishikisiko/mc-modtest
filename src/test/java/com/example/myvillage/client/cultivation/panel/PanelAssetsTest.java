package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PanelAssetsTest {
    @Test void titleFallbackPreservesBodyAppearanceWithoutSharingMutableTrueTypeProviders() throws Exception {
        try(var body=getClass().getResourceAsStream("/assets/myvillage/font/panel_body.json");
                var title=getClass().getResourceAsStream("/assets/myvillage/font/panel_title.json")) {
            var bodyProviders=JsonParser.parseReader(new InputStreamReader(body,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("providers");
            var titleProviders=JsonParser.parseReader(new InputStreamReader(title,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("providers");
            assertEquals(bodyProviders.size()+1,titleProviders.size());
            for(int i=0;i<bodyProviders.size();i++)assertEquals(bodyProviders.get(i),titleProviders.get(i+1));
            assertEquals("myvillage:brush.ttf",titleProviders.get(0).getAsJsonObject().get("file").getAsString());
            assertTrue(titleProviders.asList().stream().noneMatch(p->p.getAsJsonObject().has("id")
                    && p.getAsJsonObject().get("id").getAsString().equals("myvillage:panel_body")));
        }
    }
    private PanelAssets.Look bundled() {
        var stream=getClass().getResourceAsStream("/assets/myvillage/cultivation_panel.json");
        assertNotNull(stream);
        return PanelAssets.decode(JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject());
    }
    @Test void onlyTheSixApprovedArtsOverrideBroadCategorySymbols() {
        var look=bundled();assertEquals(6,look.techniques().size());
        for(var entry:look.techniques().entrySet()){
            assertEquals(entry.getValue(),look.iconFor(entry.getKey(),TechniqueCategory.ACTIVE));
            assertEquals(256,entry.getValue().size());
            assertNotEquals(look.categories().get("active"),entry.getValue());
        }
        for(String id:new String[]{"tuna_xinfa","qingmu_changchun_gong","new_core_variant"})
            assertEquals(look.categories().get("core"),look.iconFor(ResourceLocation.fromNamespaceAndPath("myvillage",id),TechniqueCategory.CORE));
        assertEquals(look.categories().get("active"),look.iconFor(ResourceLocation.parse("addon:new_art"),TechniqueCategory.ACTIVE));
        assertEquals(look.categories().get("unknown"),look.iconFor(ResourceLocation.parse("addon:missing"),null));
    }
    @Test void GradeIdentityAndTextureResourcesSurvivePackaging() {
        var look=bundled();assertEquals(5,look.grades().size());
        assertEquals(5,look.grades().stream().map(PanelAssets.Grade::color).distinct().count());
        var all=new java.util.ArrayList<>(look.techniques().values());all.addAll(look.categories().values());all.addAll(look.navigation().values());
        for(var icon:all){
            String file="/assets/"+icon.texture().getNamespace()+"/"+icon.texture().getPath();
            assertNotNull(getClass().getResource(file),file);
            assertNotNull(getClass().getResource(file+".mcmeta"),file);
        }
    }
}
