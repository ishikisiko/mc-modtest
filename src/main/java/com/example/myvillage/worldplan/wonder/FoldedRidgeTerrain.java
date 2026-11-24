package com.example.myvillage.worldplan.wonder;

import net.minecraft.core.BlockPos;

import java.io.DataInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;

final class FoldedRidgeTerrain {
    private static final String RESOURCE = "/data/yuanxian/worldplan/folded_ridge.bin.gz";
    record Plant(int x, int y, int z, int material) {}

    final int width, height, depth, x0, z0, step, peak;
    final BlockPos entry;
    final List<Plant> plants;
    private final byte[] columns;
    private final short[] bottom, firstFloor, top;
    private final int[] nearest;
    private final byte[] edgeDistance;
    final int apron;

    static FoldedRidgeTerrain load(String digest, int blend) {
        try (var resource = FoldedRidgeTerrain.class.getResourceAsStream(RESOURCE)) {
            if (resource == null) throw new IOException("缺少折山岭地形资源");
            byte[] bytes = resource.readNBytes(4 * 1024 * 1024 + 1);
            if (bytes.length > 4 * 1024 * 1024 || !HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)).equals(digest)) {
                throw new IOException("折山岭地形与冻结摘要不一致");
            }
            try (var data = new DataInputStream(new GZIPInputStream(new java.io.ByteArrayInputStream(bytes)))) {
                return new FoldedRidgeTerrain(data, blend);
            }
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("无法读取折山岭地形", failure);
        }
    }

    private FoldedRidgeTerrain(DataInputStream data, int blend) throws IOException {
        if (data.readInt() != 0x46524431) throw new IOException("折山岭地形格式错误");
        width = data.readUnsignedShort();
        height = data.readUnsignedShort();
        depth = data.readUnsignedShort();
        x0 = data.readShort();
        z0 = data.readShort();
        step = data.readUnsignedShort();
        int count = data.readInt();
        entry = new BlockPos(data.readShort(), data.readShort(), data.readShort());
        if (width < 1 || width > 320 || depth < 1 || depth > 320 || height < 8 || height > 240
                || step != 2 || count < 1 || count > 4096) throw new IOException("折山岭地形尺寸错误");
        int area = width * depth;
        columns = data.readNBytes(area * height);
        if (columns.length != area * height) throw new IOException("折山岭岩柱数据不完整");
        bottom = new short[area];
        firstFloor = new short[area];
        top = new short[area];
        int high = 0;
        for (int column = 0; column < area; column++) {
            int low = -1;
            for (int y = 0; y < height; y++) {
                int value = material(column, y);
                if (value > 12) throw new IOException("折山岭岩层编号无效");
                if (value != 0) {
                    if (low < 0) low = y;
                    top[column] = (short)(y+1);
                } else if (low >= 0 && firstFloor[column] == 0) {
                    firstFloor[column] = (short)y;
                }
            }
            bottom[column] = (short)low;
            if (firstFloor[column] == 0) firstFloor[column] = top[column];
            high = Math.max(high, top[column]);
        }
        peak = high;
        var placements = new ArrayList<Plant>(count);
        for (int i = 0; i < count; i++) {
            var plant = new Plant(data.readShort(), data.readShort(), data.readShort(), data.readUnsignedByte());
            if (plant.y < 0 || plant.y >= height || plant.material < 13 || plant.material > 19)
                throw new IOException("折山岭植物坐标无效");
            placements.add(plant);
        }
        if (data.read() != -1) throw new IOException("折山岭地形数据有多余字节");
        plants = List.copyOf(placements);
        apron = (blend + step - 1) / step;
        nearest = new int[(width + apron*2) * (depth + apron*2)];
        edgeDistance = new byte[nearest.length];
        findEdges();
    }

    int material(int column, int y) {
        return y >= 0 && y < height ? Byte.toUnsignedInt(columns[column * height + y]) : 0;
    }

    int bottom(int column) { return bottom[column]; }
    int ground(int column) { return firstFloor[column]; }
    int top(int column) { return top[column]; }

    int expandedIndex(int x, int z) {
        int i = Math.floorDiv(x - x0, step) + apron;
        int j = Math.floorDiv(z - z0, step) + apron;
        int stride = width + apron*2;
        return i >= 0 && i < stride && j >= 0 && j < depth + apron*2 ? j * stride + i : -1;
    }

    int nearest(int index) { return nearest[index]; }
    int edgeDistance(int index) { return edgeDistance[index]; }

    private void findEdges() {
        Arrays.fill(nearest, -1);
        var queue = new ArrayDeque<Integer>();
        int stride = width + apron*2;
        for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++) {
            int column = z * width + x;
            if (bottom[column] < 0) continue;
            int i = (z + apron) * stride + x + apron;
            nearest[i] = column;
            queue.add(i);
        }
        while (!queue.isEmpty()) {
            int i = queue.remove();
            int distance = edgeDistance[i] + 1;
            if (distance > apron) continue;
            if (i % stride > 0) grow(queue, i, i-1, distance);
            if (i % stride < stride-1) grow(queue, i, i+1, distance);
            if (i >= stride) grow(queue, i, i-stride, distance);
            if (i < nearest.length - stride) grow(queue, i, i+stride, distance);
        }
    }

    private void grow(ArrayDeque<Integer> queue, int from, int index, int distance) {
        if (nearest[index] != -1) return;
        nearest[index] = nearest[from];
        edgeDistance[index] = (byte)distance;
        queue.add(index);
    }
}
