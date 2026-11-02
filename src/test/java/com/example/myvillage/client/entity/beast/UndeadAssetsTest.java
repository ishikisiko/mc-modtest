package com.example.myvillage.client.entity.beast;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.entity.beast.BeastDefinitions;
import com.example.myvillage.entity.undead.UndeadRules;
import java.io.*;
import java.nio.file.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class UndeadAssetsTest {
    @Test void everyRegionalModelBakesAndMatchesItsServerMoves() throws Exception {
        for(var v:UndeadRules.get().variants()) {
            var id=ResourceLocation.fromNamespaceAndPath("myvillage",v.id());
            String prefix="src/main/resources/assets/myvillage/beast/"+v.id();
            try(var mr=Files.newBufferedReader(Path.of(prefix+"_model.json"));var ar=Files.newBufferedReader(Path.of(prefix+"_animations.json"))) {
                var model=BeastModelFile.parse(prefix+"_model.json",id,mr);
                var animations=BeastAnimationFile.parse(prefix+"_animations.json",id,ar);
                var definition=BeastDefinitions.bundled().require(id);
                animations.check(prefix,model,definition);
                assertEquals(model.bones().size(),model.bonesOf(model.toLayerDefinition().bakeRoot()).size(),v.id());
                assertTrue(BeastGait.rates(model,animations,definition,"walk","run").walkFootSpeed()>0,v.id());
                assertEquals(v.data().getAsJsonObject("attributes").get("max_health").getAsDouble(),definition.attributes().maxHealth());
                assertEquals(v.actions().size(),definition.moves().size());
                for(var action:v.actions())assertEquals(action.totalTicks()/20f,animations.clip(action.animation()).orElseThrow().length(),.0001f,v.id());
            }
        }
    }
}
