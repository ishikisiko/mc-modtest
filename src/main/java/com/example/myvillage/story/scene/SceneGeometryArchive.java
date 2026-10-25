package com.example.myvillage.story.scene;

import com.example.myvillage.worldplan.FrozenRulesEncoding;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/** Read-only, version-exact geometry. Historical templates are never required or replayed. */
final class SceneGeometryArchive {
    private static final Map<String,String> LEGACY=manifest();
    private static Map<String,String> manifest() {
        String path="/data/myvillage/story/entrance_legacy/provenance.json";
        try(var in=SceneGeometryArchive.class.getResourceAsStream(path)) {
            if(in==null)throw new IllegalStateException("缺少阡镇旧布局清单");
            byte[] raw=in.readNBytes(16_385);if(raw.length>16_384)throw new IllegalStateException("阡镇旧布局清单超限");
            var root=JsonParser.parseString(new String(raw,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("schema").getAsInt()!=1)throw new IllegalStateException("阡镇旧布局清单版本非法");
            var entries=new HashMap<String,String>();
            for(var row:root.getAsJsonArray("snapshots")) {
                var o=row.getAsJsonObject();String version=o.get("version").getAsString(),sha=o.get("sha256").getAsString();
                if(!version.matches("[A-Za-z0-9_.-]{1,80}")||!sha.matches("[0-9a-f]{64}")||entries.put(version,sha)!=null||entries.size()>16)
                    throw new IllegalStateException("阡镇旧布局清单条目非法");
            }
            return Map.copyOf(entries);
        }catch(IOException bad){throw new IllegalStateException("无法读取阡镇旧布局清单",bad);}
    }
    private static final Map<String,SceneLayout> ARCHIVE=new HashMap<>();
    private SceneGeometryArchive() {}
    static String freeze(SceneLayout layout) {
        var gson=new GsonBuilder().registerTypeAdapter(SceneLayout.Pos.class,(JsonSerializer<SceneLayout.Pos>)(p,t,c)->{
            var a=new JsonArray();a.add(p.x());a.add(p.y());a.add(p.z());return a;
        }).create();
        var root=gson.toJsonTree(layout).getAsJsonObject();root.addProperty("schema",1);
        if(layout.facilities().isEmpty())root.remove("facilities");
        return FrozenRulesEncoding.encode(gson.toJson(root));
    }
    static SceneLayout thaw(String encoded) {return SceneLayout.parse(JsonParser.parseString(FrozenRulesEncoding.decode(encoded)).getAsJsonObject());}
    static SceneLayout legacy(String version) {
        if(!LEGACY.containsKey(version))throw new IllegalStateException("未知阡镇旧布局版本："+version+"；请保留存档并恢复匹配 jar，未重放模板。");
        return ARCHIVE.computeIfAbsent(version,key->{
            String name="/data/myvillage/story/entrance_legacy/"+key+".json.gz";
            try(var resource=SceneGeometryArchive.class.getResourceAsStream(name)) {
                if(resource==null)throw new IllegalStateException("缺少阡镇旧布局快照："+key);
                try(var gzip=new GZIPInputStream(resource)) {
                    byte[] bytes=gzip.readNBytes(4*1024*1024+1);
                    if(bytes.length>4*1024*1024)throw new IllegalStateException("阡镇旧布局快照超限");
                    try {
                        if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(LEGACY.get(key)))
                            throw new IllegalStateException("阡镇旧布局快照摘要不符");
                    }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
                    SceneLayout layout=SceneLayout.parse(JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject());
                    if(!layout.version().equals(key))throw new IllegalStateException("阡镇旧布局快照身份不符");return layout;
                }
            }catch(IOException bad){throw new IllegalStateException("无法读取阡镇旧布局快照",bad);}
        });
    }
}
