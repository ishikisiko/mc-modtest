package com.example.myvillage.client.entity.npc;

import com.example.myvillage.client.entity.beast.BeastAnimationFile;
import com.example.myvillage.client.entity.beast.BeastModelFile;
import com.example.myvillage.entity.beast.BeastDataException;
import com.example.myvillage.entity.beast.BeastJson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcResourceReferenceTest {
    private static final ResourceLocation ORIGINAL = ResourceLocation.parse("myvillage:npc/example_model.json");
    private static final ResourceLocation SHARED = ResourceLocation.parse("myvillage:npc/shared/"+"a".repeat(64)+"_model.json");
    private static JsonObject ref(String id) { var j=new JsonObject();j.addProperty("resource_ref",id);return j; }

    @Test void packedModelsAndAnimationsResolveToEveryUnchangedAuthoringPayload() throws Exception {
        var dir=Path.of("src/main/resources/assets/myvillage/npc");
        List<Path> files;
        try(var stream=Files.list(dir)) {
            files=stream.filter(p->p.toString().endsWith("_model.json")||p.toString().endsWith("_animations.json")).toList();
        }
        var counts=new HashMap<String,Integer>();
        for(var p:files) counts.merge(Files.readString(p),1,Integer::sum);
        int aliases=0;
        for(var p:files) {
            String original=Files.readString(p);
            var id=ResourceLocation.parse("myvillage:npc/"+p.getFileName());
            JsonObject root=classpath(id);
            boolean shared=counts.get(original)>1;
            assertEquals(shared,root.has("resource_ref"),p.toString());
            if(shared)aliases++;
            JsonObject resolved=NpcResourceReference.resolve(id,NpcResourceReferenceTest::classpath);
            assertEquals(JsonParser.parseString(original),resolved,p.toString());
            var entity=ResourceLocation.parse(resolved.get("id").getAsString());
            if(p.toString().endsWith("_model.json")) BeastModelFile.parse(id.toString(),entity,resolved);
            else BeastAnimationFile.parse(id.toString(),entity,resolved);
        }
        assertTrue(aliases>100,"The actual processResources output must be compact, not the authoring tree");
    }

    @Test void inlineOverridesAtTheOldPathWinAndReloadDoesNotUseACache() {
        var reads=new AtomicInteger();
        for(int i=1;i<=2;i++) {
            JsonObject got=NpcResourceReference.resolve(ORIGINAL,id->{
                assertEquals(ORIGINAL,id);var j=new JsonObject();j.addProperty("override",reads.incrementAndGet());return j;
            });
            assertEquals(i,got.get("override").getAsInt());
        }
    }

    @Test void oneHopReadsTheSharedPayload() {
        var payload=new JsonObject();payload.addProperty("bones","kept");
        assertSame(payload,NpcResourceReference.resolve(ORIGINAL,id->id.equals(ORIGINAL)?ref(SHARED.toString()):payload));
    }

    @Test void chainsCyclesWrongKindsAndNamespacesAreRejected() {
        assertThrows(BeastDataException.class,()->NpcResourceReference.resolve(ORIGINAL,id->ref(SHARED.toString())));
        for(String target:List.of("other:"+SHARED.getPath(),"myvillage:npc/../bad_model.json",
                SHARED.toString().replace("_model","_animations"),ORIGINAL.toString()))
            assertThrows(BeastDataException.class,()->NpcResourceReference.resolve(ORIGINAL,id->ref(target)));
    }

    @Test void mixedReferenceAndModelFieldsOrMissingTargetsDoNotSilentlyFallback() {
        var mixed=ref(SHARED.toString());mixed.addProperty("schema",1);
        assertThrows(BeastDataException.class,()->NpcResourceReference.resolve(ORIGINAL,id->mixed));
        assertThrows(BeastDataException.class,()->NpcResourceReference.resolve(ORIGINAL,id->{
            if(id.equals(ORIGINAL))return ref(SHARED.toString());
            throw new BeastDataException(id.toString(),"<file>","is missing");
        }));
    }

    private static JsonObject classpath(ResourceLocation id) {
        String path="/assets/"+id.getNamespace()+"/"+id.getPath();
        try(var in=NpcResourceReferenceTest.class.getResourceAsStream(path)) {
            assertNotNull(in,path);
            return BeastJson.parseStrict(path,new InputStreamReader(in,StandardCharsets.UTF_8));
        }catch(java.io.IOException e){throw new AssertionError(e);}
    }
}
