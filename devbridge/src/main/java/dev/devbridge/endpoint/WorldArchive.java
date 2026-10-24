package dev.devbridge.endpoint;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipInputStream;

/** Imports one checksummed world into a new directory; never replaces an existing save. */
final class WorldArchive {
    static final int MAX_ZIP = 64 * 1024 * 1024;
    private WorldArchive() {}

    static int importNew(Path saves, String folder, byte[] archive, String sha256) throws Exception {
        Path destination = WorldFiles.child(saves, "folder", folder);
        if (archive.length > MAX_ZIP) throw new IOException("archive exceeds 64 MiB");
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive));
        if (!actual.equalsIgnoreCase(sha256)) throw new IOException("archive SHA-256 mismatch");
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new IOException("destination already exists; nothing replaced");
        Files.createDirectories(saves);
        Path stage = Files.createTempDirectory(saves, ".import-");
        int files = 0, entries = 0;
        long expanded = 0;
        Set<String> seen = new HashSet<>();
        try {
            try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    if (++entries > 20000) throw new IOException("too many ZIP entries");
                    String name = entry.getName();
                    if (entry.isDirectory()) name = name.substring(0, name.length()-1);
                    for (String component : name.split("/", -1)) WorldFiles.checkName("archive component", component);
                    if (!seen.add(name.toLowerCase(Locale.ROOT))) throw new IOException("duplicate ZIP path");
                    Path target = stage.resolve(name).normalize();
                    if (!target.startsWith(stage)) throw new IOException("ZIP path escapes staging");
                    if (name.equals(WorldFiles.LOCK) || name.equals(WorldFiles.MARKER)) continue;
                    if (entry.isDirectory()) { Files.createDirectories(target); continue; }
                    Files.createDirectories(target.getParent());
                    try (var out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                        byte[] buffer = new byte[65536];
                        for (int n; (n = zip.read(buffer)) != -1;) {
                            expanded += n;
                            if (expanded > 512L * 1024 * 1024) throw new IOException("expanded archive exceeds 512 MiB");
                            out.write(buffer, 0, n);
                        }
                    }
                    files++;
                }
            }
            try (var level = Files.newInputStream(stage.resolve("level.dat"))) {
                if (level.read() != 0x1f || level.read() != 0x8b) throw new IOException("missing gzipped level.dat at archive root");
            }
            Files.move(stage, destination);
            return files;
        } finally {
            if (Files.exists(stage)) {
                try (var paths = Files.walk(stage)) {
                    for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
                }
            }
        }
    }
}
