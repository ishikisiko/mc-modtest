package com.example.myvillage.story;

import java.io.*;
import java.nio.file.*;

final class StoryTestData {
    static StoryDefinition definition() {
        try(var r=Files.newBufferedReader(Path.of("src/main/resources/data/myvillage/story/qianzhen_chapter_1.json"))) {return StoryDefinition.parse(r);}
        catch(IOException e){throw new UncheckedIOException(e);}
    }
    static StoryEngine.Plan act(StoryDefinition d,StoryProgress p,String id) {
        var a=d.actions().get(id);return StoryEngine.reduce(d,p,id,a.kind(),a.kind().equals("investigate")?a.target():a.role(),"qianzhen_test","minecraft:overworld");
    }
    static StoryProgress run(String... ids) {
        var d=definition();var p=StoryProgress.EMPTY;
        for(var id:ids) {var plan=act(d,p,id);if(!plan.changed())throw new AssertionError("action refused: "+id+" at "+p.stage());p=plan.progress();}
        return p;
    }
}
