package com.example.myvillage.npc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Paper pages contain strings: resolve their existing labels using the authored Chinese table,
 * rather than the dedicated server's default language. No second translation catalogue. */
public final class NpcDialogueText {
    private static final JsonObject CHINESE = load();
    private NpcDialogueText() { }
    public static String of(Component component) {
        if (!(component.getContents() instanceof TranslatableContents text)) return component.getString();
        if (!CHINESE.has(text.getKey())) return "近况未明";
        Object[] args = text.getArgs().clone();
        for (int i = 0; i < args.length; i++) if (args[i] instanceof Component nested) args[i] = of(nested);
        return String.format(Locale.ROOT, CHINESE.get(text.getKey()).getAsString(), args);
    }
    private static JsonObject load() {
        String path = "/assets/myvillage/lang/zh_cn.json";
        try (var input = NpcDialogueText.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing " + path);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException ex) { throw new IllegalStateException(path, ex); }
    }
}
