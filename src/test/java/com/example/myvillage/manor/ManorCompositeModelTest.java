package com.example.myvillage.manor;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.client.model.CompositeModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ManorCompositeModelTest {
    private static JsonObject resource(String path) throws Exception {
        try(var stream=ManorCompositeModelTest.class.getResourceAsStream(path)) {
            assertNotNull(stream,path);
            return JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test void actualNeoForgeLoaderReadsEveryPackagedMixedMaterialModel() throws Exception {
        if(LoadingModList.get()==null)LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        JsonDeserializationContext context=new JsonDeserializationContext() {
            @SuppressWarnings("unchecked")
            @Override public <T> T deserialize(JsonElement json,Type type) throws JsonParseException {
                if(type!=BlockModel.class)throw new JsonParseException("Unexpected child model type "+type);
                return (T)BlockModel.fromString(json.toString());
            }
        };
        int loaded=0;
        for(var entry:resource("/data/myvillage/lord_manors/catalog.json").getAsJsonArray("banks")) {
            var bank=entry.getAsJsonObject();String id=bank.get("id").getAsString();
            for(int shape=0;shape<bank.getAsJsonArray("profiles").size();shape++) {
                var json=resource("/assets/myvillage/models/block/"+id+"/shape_"+shape+".json");
                if(!json.has("loader"))continue;
                assertEquals("neoforge:composite",json.get("loader").getAsString());
                var actual=CompositeModel.Loader.INSTANCE.read(json,context);
                assertEquals(json.getAsJsonObject("children").keySet(),actual.getConfigurableComponentNames());
                loaded++;
            }
        }
        assertTrue(loaded>0,"必须覆盖真实的混合材质精雕模型");
    }
}
