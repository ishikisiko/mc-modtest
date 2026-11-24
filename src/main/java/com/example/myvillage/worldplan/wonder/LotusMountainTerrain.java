package com.example.myvillage.worldplan.wonder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/** The authored profiles share each pair of columns and retain one-block approach steps. */
final class LotusMountainTerrain {
    private static final String RESOURCE = "/data/yuanxian/worldplan/lotus_mountain.bin.gz";
    private static final int MAGIC = 0x4c544d31;

    record Profile(int blend, byte[] materials, int groundY, int surfaceY) {
        BlockState block(int y, int bedY, BlockState[] palette) {
            if (y < bedY) return Blocks.STONE.defaultBlockState();
            return y >= surfaceY ? palette[0] : palette[Byte.toUnsignedInt(materials[y - bedY])];
        }
    }

    final int minX, maxX, minZ, maxZ, bedY, topY, arenaX, arenaY, arenaZ;
    final BlockState[] palette;
    private final Profile[] profiles;
    private final int width;

    static LotusMountainTerrain load(String expectedSha) {
        try (InputStream resource = LotusMountainTerrain.class.getResourceAsStream(RESOURCE)) {
            if (resource == null) throw new IOException("缺少石莲山地形资源");
            byte[] compressed = resource.readNBytes(1024 * 1024 + 1);
            if (compressed.length > 1024 * 1024) throw new IOException("石莲山地形资源超出大小限制");
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(compressed));
            if (!expectedSha.equals(sha)) throw new IOException("石莲山地形与冻结规则的摘要不同");
            try (var input = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(compressed)))) {
                return new LotusMountainTerrain(input);
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法读取石莲山地形", e);
        }
    }

    private LotusMountainTerrain(DataInputStream input) throws IOException {
        if (input.readInt() != MAGIC) throw new IOException("石莲山地形格式无效");
        minX = input.readShort() * 2;
        maxX = input.readShort() * 2 + 1;
        minZ = input.readShort() * 2;
        maxZ = input.readShort() * 2 + 1;
        bedY = input.readShort();
        topY = input.readShort();
        arenaX = input.readShort();
        arenaY = input.readShort();
        arenaZ = input.readShort();
        width = (maxX - minX + 1) / 2;
        int depth = (maxZ - minZ + 1) / 2;
        if (width < 1 || width > 256 || depth < 1 || depth > 256
            || bedY >= 0 || topY - bedY < 1 || topY - bedY > 255) {
            throw new IOException("石莲山地形尺寸无效");
        }
        int namesLength = input.readUnsignedShort();
        if (namesLength > 4096) throw new IOException("石莲山方块表超出长度限制");
        String[] names = new String(input.readNBytes(namesLength), StandardCharsets.US_ASCII).split("\n");
        if (names.length > 255 || !names[0].equals("air")) throw new IOException("石莲山方块表无效");
        palette = new BlockState[names.length];
        for (int i = 0; i < names.length; i++) {
            var id = ResourceLocation.withDefaultNamespace(names[i]);
            if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IOException("未知石莲山方块 " + id);
            BlockState state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
            palette[i] = state.hasProperty(LeavesBlock.PERSISTENT) ? state.setValue(LeavesBlock.PERSISTENT, true) : state;
        }
        profiles = new Profile[width * depth];
        for (int i = 0; i < profiles.length; i++) {
            int length = input.readUnsignedByte();
            if (length == 0) continue;
            int blend = input.readUnsignedByte();
            byte[] materials = input.readNBytes(length);
            if (materials.length != length || bedY + length > topY) throw new IOException("石莲山地形柱无效");
            int groundY = bedY;
            for (int j = 0; j < length; j++) {
                int code = Byte.toUnsignedInt(materials[j]);
                if (code >= palette.length) throw new IOException("石莲山地形柱引用未知方块");
                BlockState state = palette[code];
                if (state.blocksMotion() && !(state.getBlock() instanceof LeavesBlock) && !state.is(Blocks.SPRUCE_LOG)) {
                    groundY = bedY + j + 1;
                }
            }
            profiles[i] = new Profile(blend, materials, groundY, bedY + length);
        }
        if (input.read() != -1 || profile(arenaX, arenaZ) == null) throw new IOException("石莲山地形资源结尾无效");
    }

    Profile profile(int x, int z) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) return null;
        return profiles[((z - minZ) / 2) * width + (x - minX) / 2];
    }
}
