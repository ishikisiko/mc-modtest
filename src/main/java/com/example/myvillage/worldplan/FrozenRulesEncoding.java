package com.example.myvillage.worldplan;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Bounded ASCII transport so frozen V4 city inputs fit NBT's per-string UTF limit. */
public final class FrozenRulesEncoding {
    private static final int MAX_TEXT = 65_000;
    private static final int MAX_RAW = 4 * 1024 * 1024;
    private FrozenRulesEncoding() { }
    public static String encode(String json) {
        byte[] raw = json.getBytes(StandardCharsets.UTF_8);
        if (raw.length > MAX_RAW) throw new IllegalArgumentException("Frozen rules exceed decompressed limit");
        try {
            var bytes = new ByteArrayOutputStream();
            try (var gzip = new GZIPOutputStream(bytes)) { gzip.write(raw); }
            String text = "gzip:" + Base64.getEncoder().encodeToString(bytes.toByteArray());
            if (text.length() > MAX_TEXT) throw new IllegalArgumentException("Frozen rules exceed NBT string limit");
            return text;
        } catch (IOException e) { throw new IllegalStateException("Cannot encode frozen rules", e); }
    }
    public static String decode(String text) {
        if (!text.startsWith("gzip:") || text.length() > MAX_TEXT)
            throw new IllegalArgumentException("V4 requires bounded compressed frozen rules");
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(text.substring(5))))) {
            byte[] raw = gzip.readNBytes(MAX_RAW + 1);
            if (raw.length > MAX_RAW) throw new IllegalArgumentException("Frozen rules inflate beyond limit");
            return new String(raw, StandardCharsets.UTF_8);
        } catch (IOException e) { throw new IllegalArgumentException("Corrupt frozen rules", e); }
    }
}
