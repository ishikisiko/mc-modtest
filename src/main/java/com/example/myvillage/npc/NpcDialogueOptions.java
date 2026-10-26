package com.example.myvillage.npc;

import com.example.myvillage.story.net.StoryDialoguePayload.Option;
import com.example.myvillage.story.net.StoryDialoguePayload;
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

    public record Page(List<String> lines, List<Option> options) {
        public Page { lines = List.copyOf(lines); options = List.copyOf(options); }
    }

    /** Status uses the existing paper and one-use, server-offered page options. */
    public static Page statusPage(List<String> lines, int index) {
        var bounded = new ArrayList<String>();
        for (String line : lines) {
            if (line.isEmpty()) bounded.add(line);
            for (int from = 0; from < line.length();) {
                int end = Math.min(from + StoryDialoguePayload.MAX_LINE, line.length());
                if (end < line.length() && Character.isHighSurrogate(line.charAt(end - 1))
                        && Character.isLowSurrogate(line.charAt(end))) end--;
                bounded.add(line.substring(from, end));
                from = end;
            }
        }
        int last = Math.max(0, (bounded.size() - 1) / StoryDialoguePayload.MAX_LINES);
        int page = Math.clamp(index, 0, last);
        int start = page * StoryDialoguePayload.MAX_LINES;
        var options = new ArrayList<Option>();
        if (page > 0) options.add(new Option("status_" + (page - 1), "上一页近况"));
        if (page < last) options.add(new Option("status_" + (page + 1), "更多近况"));
        options.add(new Option("home", "返回交谈"));
        options.add(new Option("close", "告辞"));
        return new Page(bounded.subList(start, Math.min(start + StoryDialoguePayload.MAX_LINES, bounded.size())), options);
    }
}
