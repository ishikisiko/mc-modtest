package dev.devbridge.endpoint;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
public class WorldArchiveCheck {
    static byte[] zip(Map<String,byte[]> entries) throws Exception {
        var out = new ByteArrayOutputStream();
        try(var z = new ZipOutputStream(out)) {for(var e:entries.entrySet()) {z.putNextEntry(new ZipEntry(e.getKey()));z.write(e.getValue());z.closeEntry();}}
        return out.toByteArray();
    }
    static String sha(byte[] data) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
    static void reject(Path dir,String folder,byte[] data,String digest) throws Exception {
        try {WorldArchive.importNew(dir,folder,data,digest);throw new AssertionError("unexpected accepted archive");}
        catch(AssertionError e){throw e;}catch(Exception expected){}
    }
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("world-import-check");
        byte[] level={(byte)0x1f,(byte)0x8b,0};
        byte[] valid=zip(Map.of("level.dat",level,"region/r.0.0.mca",new byte[]{1}));
        WorldArchive.importNew(dir,"new-world",valid,sha(valid));
        reject(dir,"new-world",valid,sha(valid));
        reject(dir,"bad-hash",valid,"0".repeat(64));
        for(String name:new String[]{"../escape","a/../../escape","C:/escape","a\\escape","CON","a./b"}) {
            byte[] bad=zip(Map.of("level.dat",level,name,new byte[]{1}));
            reject(dir,"unsafe",bad,sha(bad));
            if(Files.exists(dir.resolve("unsafe")))throw new AssertionError("partial world published");
        }
        byte[] missing=zip(Map.of("region/a",new byte[]{1}));reject(dir,"missing-level",missing,sha(missing));
        var collision=new LinkedHashMap<String,byte[]>();collision.put("level.dat",level);collision.put("A",level);collision.put("a",level);
        byte[] duplicate=zip(collision);reject(dir,"duplicate",duplicate,sha(duplicate));
        byte[] marker=zip(Map.of("level.dat",level,"devbridge-world.json",level,"session.lock",level));
        WorldArchive.importNew(dir,"imported",marker,sha(marker));
        if(Files.exists(dir.resolve("imported/devbridge-world.json"))||Files.exists(dir.resolve("imported/session.lock")))throw new AssertionError("imported ownership or lock");
        if(!java.util.Arrays.equals(Files.readAllBytes(dir.resolve("new-world/level.dat")),level))throw new AssertionError("original changed");
        try(var paths=Files.walk(dir)){for(Path p:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);}
        System.out.println("WorldArchiveCheck: new import, no replacement, hashes, paths, duplicates, missing root and ownership passed");
    }
}
