package com.example.myvillage.sect;

import com.google.gson.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** 当前编译蓝图的只读空间清单；不重新推导台地、经架或站位。 */
public final class SectBlueprint {
    private static final String CATALOG = "data/myvillage/sect_blueprints/catalog.json";
    public record Tile(int x, int z, ResourceLocation template, String sha256) { }
    public record Shelf(BlockPos position, BlockPos reader, Direction facing) { }
    public final ResourceLocation id;
    public final String name, fingerprint, rock, surface;
    public final int width, height, depth, maxY, margin, vegetationClearance;
    public final BlockPos entry, steward;
    public final List<BlockPos> courtyard;
    public final List<Shelf> shelves;
    public final List<Tile> tiles;
    private final short[] terrain;

    private SectBlueprint(JsonObject o, String fingerprint, byte[] field) {
        if (integer(o,"schema") != 1) throw new IllegalArgumentException("宗门蓝图schema不支持");
        this.fingerprint = hash(fingerprint);
        id = ResourceLocation.parse(o.get("id").getAsString());
        if (!id.getNamespace().equals("myvillage") || !id.getPath().matches("[a-z0-9_]+"))
            throw new IllegalArgumentException("宗门蓝图身份无效");
        name = o.get("name").getAsString();
        JsonArray size = o.getAsJsonArray("size");
        if (size.size()!=3) throw new IllegalArgumentException("宗门尺寸无效");
        width=size.get(0).getAsInt();height=size.get(1).getAsInt();depth=size.get(2).getAsInt();
        if (width<16 || width>1024 || depth<16 || depth>1024 || width%16!=0 || depth%16!=0 || height<1 || height>512)
            throw new IllegalArgumentException("宗门尺寸越界");
        maxY=integer(o,"max_y");margin=integer(o,"blend_margin");vegetationClearance=integer(o,"vegetation_clearance");
        if (maxY<0 || maxY>=height || margin<1 || margin>64 || vegetationClearance<0 || vegetationClearance>128)
            throw new IllegalArgumentException("宗门高度或过渡参数越界");
        rock=o.get("rock").getAsString();surface=o.get("surface").getAsString();
        entry=position(o.getAsJsonArray("entry_anchor"));steward=position(o.getAsJsonArray("steward"));
        if (field.length != width*depth*2) throw new IllegalArgumentException("宗门高度场长度不符");
        terrain=new short[width*depth];ByteBuffer.wrap(field).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(terrain);
        for (short h:terrain) if (h<0 || h>maxY) throw new IllegalArgumentException("宗门高度场越界");
        Set<BlockPos> cells=new LinkedHashSet<>();
        for (JsonElement e:o.getAsJsonArray("courtyard_cells")) {
            BlockPos p=position(e.getAsJsonArray());
            if (!cells.add(p)) throw new IllegalArgumentException("宗门站位重复");
        }
        courtyard=List.copyOf(cells);
        if (!cells.contains(steward)) throw new IllegalArgumentException("执事不在活动区");
        List<Shelf> shelfRows=new ArrayList<>();
        for (JsonElement e:o.getAsJsonArray("shelves")) {
            JsonObject s=e.getAsJsonObject();Direction facing=Direction.byName(s.get("facing").getAsString());
            if (facing==null || facing.getAxis().isVertical()) throw new IllegalArgumentException("经架朝向无效");
            shelfRows.add(new Shelf(position(s.getAsJsonArray("position")),position(s.getAsJsonArray("reader")),facing));
        }
        if (shelfRows.isEmpty()) throw new IllegalArgumentException("宗门没有经架位置");
        shelves=List.copyOf(shelfRows);
        List<Tile> rows=new ArrayList<>();Set<Long> seen=new HashSet<>();
        for (JsonElement e:o.getAsJsonArray("tiles")) {
            JsonObject t=e.getAsJsonObject();int x=integer(t,"x"),z=integer(t,"z");
            ResourceLocation template=ResourceLocation.parse(t.get("template").getAsString());
            if (x<0 || z<0 || x>=width || z>=depth || x%16!=0 || z%16!=0 || !seen.add(key(x,z))
                    || !template.getNamespace().equals(id.getNamespace())
                    || !template.getPath().equals("sect_blueprints/"+id.getPath()+"/tile_"+x/16+"_"+z/16))
                throw new IllegalArgumentException("宗门分块身份、位置或重复项无效");
            rows.add(new Tile(x,z,template,hash(t.get("sha256").getAsString())));
        }
        if (rows.size()!=width/16*(depth/16)) throw new IllegalArgumentException("宗门分块不完整");
        tiles=List.copyOf(rows);
    }

    private static final class Current { static final SectBlueprint VALUE=load(); }
    public static SectBlueprint current() { return Current.VALUE; }
    public void requireLayout(String layout) {
        if (layout!=null && !layout.equals(id.toString())) throw new IllegalArgumentException("宗门蓝图不存在："+layout);
    }
    public int ground(int x,int z) { return terrain[Math.clamp(z,0,depth-1)*width+Math.clamp(x,0,width-1)]; }
    public BlockPos base(BlockPos anchor) { return anchor.subtract(entry); }
    public BlockPos world(BlockPos anchor,BlockPos local) { return base(anchor).offset(local); }
    public BlockPos fitAnchor(BlockPos anchor,int minimumY,int maximumY) {
        int y=Math.max(anchor.getY(),minimumY+1+entry.getY());
        if (y-entry.getY()+maxY>=maximumY) throw new IllegalArgumentException("宗门蓝图超出世界高度");
        return new BlockPos(anchor.getX(),y,anchor.getZ());
    }
    private BlockPos position(JsonArray a) {
        if (a==null || a.size()!=3) throw new IllegalArgumentException("宗门坐标无效");
        BlockPos p=new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());
        if (p.getX()<0 || p.getX()>=width || p.getY()<0 || p.getY()>=height || p.getZ()<0 || p.getZ()>=depth)
            throw new IllegalArgumentException("宗门坐标越界："+p);
        return p;
    }
    private static SectBlueprint load() {
        JsonObject c=json(readClasspath(CATALOG,1024*1024));
        if (integer(c,"schema")!=1) throw new IllegalArgumentException("宗门目录schema不支持");
        String defaultId=c.get("default").getAsString();
        JsonObject row=null;
        for (JsonElement e:c.getAsJsonArray("layouts")) if (defaultId.equals(e.getAsJsonObject().get("id").getAsString())) {
            if (row!=null) throw new IllegalArgumentException("默认宗门蓝图重复");
            row=e.getAsJsonObject();
        }
        if (row==null) throw new IllegalArgumentException("缺少默认宗门蓝图");
        ResourceLocation path=ResourceLocation.parse(row.get("resource").getAsString());
        byte[] raw=readClasspath(resourceFile(path),8*1024*1024);
        String fingerprint=row.get("sha256").getAsString();requireHash(raw,fingerprint);
        JsonObject o=json(raw);
        if (!defaultId.equals(o.get("id").getAsString())) throw new IllegalArgumentException("宗门目录与资源身份不一致");
        JsonObject terrain=o.getAsJsonObject("terrain");
        byte[] packed=readClasspath(resourceFile(ResourceLocation.parse(terrain.get("resource").getAsString())),8*1024*1024);
        requireHash(packed,terrain.get("sha256").getAsString());
        try (InputStream in=new GZIPInputStream(new ByteArrayInputStream(packed))) {
            return new SectBlueprint(o,fingerprint,limited(in,2*1024*1024));
        } catch (IOException ex) { throw new IllegalStateException("宗门高度场损坏",ex); }
    }
    static JsonObject json(byte[] raw) { return JsonParser.parseString(new String(raw,StandardCharsets.UTF_8)).getAsJsonObject(); }
    static int integer(JsonObject o,String key) { return o.get(key).getAsInt(); }
    static long key(int x,int z) { return ((long)x<<32)^(z&0xffffffffL); }
    public static String resourceFile(ResourceLocation id) { return "data/"+id.getNamespace()+"/"+id.getPath(); }
    public static byte[] readClasspath(String file,int limit) {
        try (InputStream in=SectBlueprint.class.getClassLoader().getResourceAsStream(file)) {
            if (in==null) throw new FileNotFoundException(file);
            return limited(in,limit);
        } catch (IOException ex) { throw new IllegalStateException("缺少或无法读取宗门资源："+file,ex); }
    }
    static byte[] limited(InputStream in,int limit) throws IOException {
        byte[] raw=in.readNBytes(limit+1);if (raw.length>limit) throw new IOException("宗门资源超出容量限制");return raw;
    }
    private static String hash(String hash) {
        if (!hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("宗门资源摘要无效");return hash;
    }
    public static void requireHash(byte[] raw,String expected) {
        try {
            String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
            if (!actual.equals(hash(expected))) throw new IllegalArgumentException("宗门资源摘要不一致");
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
