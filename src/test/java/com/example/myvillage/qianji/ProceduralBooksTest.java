package com.example.myvillage.qianji;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProceduralBooksTest {
    private JsonObject vectors()throws Exception {
        try(var in=getClass().getResourceAsStream("/qianji/procedural-books-vectors.json")){
            assertNotNull(in);return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void javaGenerationMatchesEveryPythonReferenceVector()throws Exception {
        var rules=ProceduralBooks.rules();
        for(var value:vectors().getAsJsonArray("vectors")){
            var row=value.getAsJsonObject();var region=ProceduralBooks.Region.fromJson(row.getAsJsonArray("region"));
            var actual=ProceduralBooks.generate(region,row.get("pattern").getAsInt(),rules);
            JsonArray expected=row.getAsJsonArray("books");assertEquals(expected.size(),actual.size());
            for(int i=0;i<actual.size();i++){
                var a=actual.get(i);var b=expected.get(i).getAsJsonArray();
                assertEquals(List.of(b.get(0).getAsInt(),b.get(1).getAsInt(),b.get(2).getAsInt(),b.get(3).getAsInt(),b.get(4).getAsInt(),b.get(5).getAsInt()),
                        List.of(a.x0(),a.y0(),a.z0(),a.x1(),a.y1(),a.z1()));
                assertEquals(b.get(6).getAsString(),a.role());
            }
        }
        for(var value:vectors().getAsJsonArray("patterns")){
            var row=value.getAsJsonObject();assertEquals(row.get("pattern").getAsInt(),ProceduralBooks.pattern(
                    row.get("horizontal").getAsInt(),row.get("y").getAsInt(),row.get("row_y").getAsInt(),rules));
        }
    }
    @Test void verticalFragmentsShareTheSameWorldRowSeed(){
        var rules=ProceduralBooks.rules();
        for(int horizontal=-32;horizontal<32;horizontal++)assertEquals(ProceduralBooks.pattern(horizontal,25,12,rules),ProceduralBooks.pattern(horizontal,26,-4,rules));
    }
    @Test void invalidGeometryAndUnboundedParametersAreRejected(){
        assertThrows(IllegalArgumentException.class,()->new ProceduralBooks.Region(0,0,0,17,16,16,0,0));
        assertThrows(IllegalArgumentException.class,()->new ProceduralBooks.Region(0,0,0,16,16,16,4,0));
        assertThrows(IllegalArgumentException.class,()->new ProceduralBooks.Rules("procedural_books",List.of(0),List.of(14),List.of("book_blue"),1,16,4));
        assertThrows(IllegalArgumentException.class,()->new ProceduralBooks.Rules("procedural_books",List.of(4),List.of(14),List.of("book_blue"),1,16,100));
    }
}
