package com.example.myvillage.worldplan;

import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.zip.GZIPInputStream;

/** Vertical runs in the authored model, indexed by local column. */
public final class SectForms {
    private static final int ROW_BYTES = 10;
    private final int width, depth;
    private final int[] columns;
    private final byte[] runs;
    private final List<Material> materials;

    record Material(String state, boolean landform) {}
    @FunctionalInterface public interface Visitor {
        void accept(int bottom, int end, String state);
    }

    private SectForms(int width, int depth, int maxY, List<Material> materials, byte[] runs) {
        this.width = width;
        this.depth = depth;
        this.materials = materials;
        this.runs = runs;
        columns = new int[width * depth + 1];
        int lastColumn = -1, end = 0;
        var data = ByteBuffer.wrap(runs).order(ByteOrder.LITTLE_ENDIAN);
        for (int row = 0; data.hasRemaining(); row++) {
            int x = Short.toUnsignedInt(data.getShort()), z = Short.toUnsignedInt(data.getShort());
            int bottom = Short.toUnsignedInt(data.getShort()), size = Short.toUnsignedInt(data.getShort());
            int material = Short.toUnsignedInt(data.getShort());
            int column = z * width + x;
            if (x >= width || z >= depth || bottom + size > maxY + 1 || size == 0 ||
                material >= materials.size() || column < lastColumn || (column == lastColumn && bottom < end))
                throw new IllegalArgumentException("宗门形制分段坐标或次序无效");
            while (lastColumn < column) columns[++lastColumn] = row;
            end = bottom + size;
        }
        while (lastColumn < width * depth) columns[++lastColumn] = data.position() / ROW_BYTES;
    }

    static SectForms read(int width, int depth, int maxY, JsonObject form) {
        if (form == null) return null;
        var palette = form.getAsJsonArray("materials").asList().stream().map(element -> {
            var row = element.getAsJsonObject();
            return new Material(row.get("state").getAsString(), row.get("landform").getAsBoolean());
        }).toList();
        if (palette.isEmpty() || palette.size() > 16384) throw new IllegalArgumentException("宗门形制材质无效");
        byte[] payload = SectTerrain.verifiedResource(form.get("resource").getAsString(), form.get("sha256").getAsString(), 8 * 1024 * 1024);
        try (var stream = new GZIPInputStream(new ByteArrayInputStream(payload))) {
            int count = form.get("runs").getAsInt();
            if (count < 1 || count > 2_000_000) throw new IllegalArgumentException("宗门形制分段数量无效");
            byte[] data = stream.readNBytes(count * ROW_BYTES + 1);
            if (data.length != count * ROW_BYTES) throw new IllegalArgumentException("宗门形制长度不符");
            return new SectForms(width, depth, maxY, palette, data);
        } catch (IOException e) { throw new IllegalArgumentException("无法读取宗门形制", e); }
    }

    private int ushort(int i) {
        return Byte.toUnsignedInt(runs[i]) | (Byte.toUnsignedInt(runs[i + 1]) << 8);
    }

    public void visit(int x, int z, boolean landform, Visitor visitor) {
        if (x < 0 || z < 0 || x >= width || z >= depth) return;
        int column = x + z * width;
        for (int row = columns[column]; row < columns[column + 1]; row++) {
            int offset = row * ROW_BYTES;
            var material = materials.get(ushort(offset + 8));
            if (material.landform != landform) continue;
            int start = ushort(offset + 4);
            visitor.accept(start, start + ushort(offset + 6), material.state);
        }
    }
}
