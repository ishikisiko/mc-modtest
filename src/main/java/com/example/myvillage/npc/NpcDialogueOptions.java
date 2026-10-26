package com.example.myvillage.npc;

import com.example.myvillage.story.net.StoryDialoguePayload.Option;
import java.util.ArrayList;
import java.util.List;

/** Pure composition: sect membership supplies a topic, an actual office supplies authority. */
public final class NpcDialogueOptions {
    public record Capabilities(boolean ledger, boolean sect, boolean office, boolean commerce,
                               boolean story, boolean guide) { }
    private NpcDialogueOptions() { }
    public static List<Option> services(Capabilities c) {
        if (!c.ledger()) return List.of();
        var out = new ArrayList<Option>();
        if (c.story()) out.add(new Option("story", "谈谈眼下的事"));
        if (c.commerce()) out.add(new Option("trade", "交易与服务"));
        if (c.guide()) out.add(new Option("guide", "请教行路与修行"));
        if (c.sect()) out.add(new Option("sect_topic", "谈谈宗门"));
        if (c.sect() && c.office()) out.add(new Option("sect_office", "宗门事务"));
        return List.copyOf(out);
    }
    public static List<Option> home() {
        return List.of(new Option("chat", "闲聊"), new Option("status", "询问近况"),
                new Option("services_0", "事务"), new Option("close", "告辞"));
    }
    public static List<Option> servicesPage(Capabilities c, int index) {
        var all = services(c);
        int start = Math.clamp(index, 0, Math.max(0, (all.size() - 1) / 2)) * 2;
        var out = new ArrayList<>(all.subList(start, Math.min(start + 2, all.size())));
        if (start + 2 < all.size()) out.add(new Option("services_" + (start / 2 + 1), "更多事务"));
        out.add(new Option("home", "返回交谈"));
        return List.copyOf(out);
    }
}
