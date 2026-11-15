package com.example.myvillage.drops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/** A rejected candidate never replaces the live immutable snapshot. */
public final class DropConfigStore {
    public record Snapshot(long revision,String sha256,DropRules rules) {}
    private final Path file;
    private final DropRules.Catalog catalog;
    private volatile Snapshot active;
    public DropConfigStore(Path file,String defaults,DropRules.Catalog catalog){this.file=file;this.catalog=catalog;active=new Snapshot(0,hash(defaults),DropRules.parse(defaults,catalog));}
    public Path file(){return file;}
    public Snapshot active(){return active;}
    public synchronized Snapshot reload()throws IOException{
        Files.createDirectories(file.getParent());
        if(!Files.exists(file))try{Files.writeString(file,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(active.rules().json())+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(FileAlreadyExistsException ignored){}
        if(Files.size(file)>DropRules.MAX_BYTES)throw new IllegalArgumentException("$：文件超过1 MiB");
        byte[] bytes;try(var in=Files.newInputStream(file)){bytes=in.readNBytes(DropRules.MAX_BYTES+1);}if(bytes.length>DropRules.MAX_BYTES)throw new IllegalArgumentException("$：文件超过1 MiB");
        String text=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        DropRules parsed=DropRules.parse(text,catalog);var next=new Snapshot(active.revision()+1,hash(text),parsed);
        active=next;return next;
    }
    public static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
