package com.example.myvillage.story.entrance;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** All authored coordinates and tuning values live in qianzhen_entrance.json. */
public record EntranceRules(boolean initializeFlatWorlds, List<Point> candidates, Point roadAnchor, int spawnBuffer, int roadHalfWidth,
        int sceneMargin, int clearance, int maxCut, int maxFill, int foundationDepth, int maxChunks, int maxRouteLength,
        int maxBlockQueries, int maxBlockWrites, int gateClearance, int lampSpacing, int hintRadius, int passTicks, List<String> signLines,
        String nearbyHint, String recruitment, String safety, String enteredProtection, String leftProtection) {
    public record Point(int x, int z) {}
    public EntranceRules { candidates = List.copyOf(candidates); signLines = List.copyOf(signLines); }
    public static EntranceRules read(Reader reader) {
        JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
        if (integer(o, "schema", 1, 1) != 1) throw new IllegalArgumentException("entrance schema");
        var candidates = new ArrayList<Point>();
        for (JsonElement e : o.getAsJsonArray("candidates")) candidates.add(point(e));
        if (candidates.isEmpty() || candidates.size() > 8 || candidates.stream().distinct().count() != candidates.size())
            throw new IllegalArgumentException("entrance candidate budget");
        var lines = new ArrayList<String>();
        for (var e : o.getAsJsonArray("sign_lines")) {
            String line = e.getAsString();
            if (line.isBlank() || line.length() > 32) throw new IllegalArgumentException("entrance sign line");
            lines.add(line);
        }
        if (lines.size() != 4) throw new IllegalArgumentException("entrance sign needs four lines");
        var r = new EntranceRules(bool(o,"initialize_flat_worlds"),candidates, point(o.get("road_anchor")), integer(o,"spawn_buffer",8,64),
                integer(o,"road_half_width",1,3), integer(o,"scene_margin",1,8), integer(o,"clearance",3,6),
                integer(o,"max_cut",0,64), integer(o,"max_fill",0,64),integer(o,"foundation_depth",2,8), integer(o,"max_chunks",1,512),
                integer(o,"max_route_length",16,1024), integer(o,"max_block_queries",1,5_000_000),integer(o,"max_block_writes",1,5_000_000),
                integer(o,"gate_clearance",64,512),
                integer(o,"lamp_spacing",4,32), integer(o,"hint_radius",4,64), integer(o,"pass_ticks",10,100),
                lines,text(o,"nearby_hint"),text(o,"recruitment"),text(o,"safety"),
                text(o,"entered_protection"),text(o,"left_protection"));
        for (Point p : candidates) if (p.x <= r.spawnBuffer + r.sceneMargin + r.roadHalfWidth)
            throw new IllegalArgumentException("entrance candidates must leave the spawn pad and approach from west");
        return r;
    }
    private static boolean bool(JsonObject o,String key) {
        var e=o.get(key);if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException(key);return e.getAsBoolean();
    }
    private static Point point(JsonElement e) {
        var a = e.getAsJsonArray();
        if (a.size()!=2) throw new IllegalArgumentException("entrance x/z pair");
        return new Point(bounded(a.get(0),-1024,1024),bounded(a.get(1),-1024,1024));
    }
    private static int integer(JsonObject o,String key,int min,int max) { return bounded(o.get(key),min,max); }
    private static int bounded(JsonElement e,int min,int max) {
        if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("entrance integer");
        int n;try { n=e.getAsBigDecimal().intValueExact(); } catch(ArithmeticException bad) {throw new IllegalArgumentException("entrance integer",bad);}
        if(n<min||n>max)throw new IllegalArgumentException("entrance integer budget");return n;
    }
    private static String text(JsonObject o,String key) {
        var e=o.get(key);if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw new IllegalArgumentException(key);
        String s=e.getAsString();if(s.isBlank()||s.length()>512)throw new IllegalArgumentException(key);return s;
    }
}
