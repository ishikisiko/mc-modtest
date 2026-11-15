package com.example.myvillage.drops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

/** The bundled rules are authoritative unless the server explicitly enables an external override. */
public final class DropConfigStore {
    public enum Source {
        BUNDLED, EXTERNAL;
        public String id(){return name().toLowerCase(java.util.Locale.ROOT);}
    }
    public record Snapshot(long revision,String sha256,DropRules rules,Source source) {}
    private static final String DEFAULT_SETTINGS="{\n  \"use_external_config\": false\n}\n";
    private final Path file;
    private final Path settingsFile;
    private final DropRules.Catalog catalog;
    private final Snapshot bundled;
    private volatile Snapshot active;

    public DropConfigStore(Path file,String defaults,DropRules.Catalog catalog){
        this.file=file;this.settingsFile=file.resolveSibling("monster_drops_settings.json");this.catalog=catalog;
        bundled=new Snapshot(0,hash(defaults),DropRules.parse(defaults,catalog),Source.BUNDLED);active=bundled;
    }
    public Path file(){return file;}
    public Path settingsFile(){return settingsFile;}
    public Snapshot active(){return active;}

    public synchronized Snapshot reload()throws IOException{
        createIfAbsent(settingsFile,DEFAULT_SETTINGS);
        var settings=DropRules.strict(read(settingsFile));
        if(!settings.keySet().equals(Set.of("use_external_config")))
            throw new IllegalArgumentException("monster_drops_settings.json：仅允许且必须包含 use_external_config");
        var enabled=settings.get("use_external_config");
        if(!enabled.isJsonPrimitive()||!enabled.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException("monster_drops_settings.json：use_external_config 应为 true 或 false");
        Snapshot next;
        if(enabled.getAsBoolean()){
            createIfAbsent(file,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(bundled.rules().json())+"\n");
            String text=read(file);
            var parsed=DropRules.parse(text,catalog);
            next=new Snapshot(active.revision()+1,hash(text),parsed,Source.EXTERNAL);
        }else{
            next=new Snapshot(active.revision()+1,bundled.sha256(),bundled.rules(),Source.BUNDLED);
        }
        // The source and its fully validated rules are published together.
        active=next;return next;
    }
    private static void createIfAbsent(Path path,String text)throws IOException{
        Files.createDirectories(path.getParent());
        if(!Files.exists(path))try{Files.writeString(path,text,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(FileAlreadyExistsException ignored){}
    }
    private static String read(Path path)throws IOException{
        if(Files.size(path)>DropRules.MAX_BYTES)throw new IllegalArgumentException(path.getFileName()+"：文件超过1 MiB");
        byte[] bytes;try(var in=Files.newInputStream(path)){bytes=in.readNBytes(DropRules.MAX_BYTES+1);}
        if(bytes.length>DropRules.MAX_BYTES)throw new IllegalArgumentException(path.getFileName()+"：文件超过1 MiB");
        return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
    }
    public static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
