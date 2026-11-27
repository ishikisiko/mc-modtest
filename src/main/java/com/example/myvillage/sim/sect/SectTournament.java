package com.example.myvillage.sim.sect;

import com.example.myvillage.sim.SimFormatException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;

/** Persistent monthly roster and round-robin results. Physical bodies and running clocks live on the server. */
public final class SectTournament {
    public static final int MAX_GROUPS_PER_SECT = 1024;

    public enum Outcome { PENDING, FIRST, SECOND, DRAW, VOID }
    public enum Enrollment { ENTERED, ALREADY_ENTERED, NOT_MEMBER, NOT_OUTER, BELOW_THRESHOLD, NO_OPPONENTS, CLOSED }

    public record Entry(int person, String player, String name, String realm, int stage,
                        long joined, boolean promotion) {
        public Entry {
            if ((person > 0) == !player.isEmpty() || name.isBlank() || name.length() > 64
                    || realm.isBlank() || stage < 0 || player.length() > 64
                    || (player.isEmpty() ? person <= 0 || joined != -1 : person != -1 || joined < 0 || !promotion))
                throw new IllegalArgumentException("无效的大比参赛人");
        }
        public boolean isPlayer() { return !player.isEmpty(); }
        public String key() { return isPlayer() ? player : "npc:" + person; }
    }

    public record Ticket(long month, int sect, int group, int bout) { }
    public record Fixture(Ticket ticket, Entry first, Entry second) {
        public boolean hasPlayer() { return first.isPlayer() || second.isPlayer(); }
    }
    public record Score(Entry opponent, Outcome outcome, boolean playerWon) { }
    public record Standing(int wins, int requiredWins, boolean finished, boolean promoted, List<Score> scores) {
        public Standing { scores = List.copyOf(scores); }
    }

    public static final class Group {
        public record Bout(int first, int second, Outcome outcome) { }
        public final List<Entry> entries;
        private final List<Bout> bouts = new ArrayList<>();
        private int settled;
        private int promoted;

        public Group(List<Entry> entries) {
            this.entries = List.copyOf(entries);
            var keys = new HashSet<String>();
            if (entries.size() < 3 || entries.size() > 4 || entries.stream().anyMatch(e -> !keys.add(e.key()))
                    || entries.stream().filter(Entry::isPlayer).count() > 1
                    || entries.stream().noneMatch(Entry::promotion)) throw new IllegalArgumentException("无效的大比分组");
            for (int a = 0; a < entries.size(); a++) for (int b = a + 1; b < entries.size(); b++)
                bouts.add(new Bout(a, b, Outcome.PENDING));
        }

        public List<Bout> bouts() { return List.copyOf(bouts); }
        public Bout bout(int index) { return bouts.get(index); }
        public int size() { return bouts.size(); }
        public boolean finished() { return bouts.stream().noneMatch(b -> b.outcome() == Outcome.PENDING); }
        public boolean hasPlayer() { return entries.stream().anyMatch(Entry::isPlayer); }
        public boolean settled(int entry) { return (settled & (1 << entry)) != 0; }
        public boolean promoted(int entry) { return (promoted & (1 << entry)) != 0; }

        public boolean resolve(int index, Outcome outcome) {
            Bout old = bouts.get(index);
            if (outcome == Outcome.PENDING || old.outcome() != Outcome.PENDING) return false;
            bouts.set(index, new Bout(old.first(), old.second(), outcome));
            return true;
        }

        public void settle(int entry, boolean promotion) {
            settled |= 1 << entry;
            if (promotion) promoted |= 1 << entry;
        }

        public List<Score> scores(int entry) {
            var score = new ArrayList<Score>(entries.size() - 1);
            for (Bout b : bouts) {
                if (b.first() == entry) score.add(new Score(entries.get(b.second()), b.outcome(), b.outcome() == Outcome.FIRST));
                if (b.second() == entry) score.add(new Score(entries.get(b.first()), b.outcome(), b.outcome() == Outcome.SECOND));
            }
            return List.copyOf(score);
        }
    }

    public long month = -1;
    public final TreeMap<Integer, List<Group>> sects = new TreeMap<>();

    public JsonObject write() {
        var root = new JsonObject();
        root.addProperty("month", month);
        var sectRows = new JsonArray();
        sects.forEach((sect, groups) -> {
            var row = new JsonObject();
            row.addProperty("sect", sect);
            var groupRows = new JsonArray();
            for (Group group : groups) {
                var data = new JsonObject();
                var entries = new JsonArray();
                for (Entry entry : group.entries) {
                    var e = new JsonObject();
                    e.addProperty("person", entry.person());
                    e.addProperty("player", entry.player());
                    e.addProperty("name", entry.name());
                    e.addProperty("realm", entry.realm());
                    e.addProperty("stage", entry.stage());
                    e.addProperty("joined", entry.joined());
                    e.addProperty("promotion", entry.promotion());
                    entries.add(e);
                }
                data.add("entries", entries);
                var results = new JsonArray();
                group.bouts.forEach(b -> results.add(b.outcome().name()));
                data.add("results", results);
                data.addProperty("settled", group.settled);
                data.addProperty("promoted", group.promoted);
                groupRows.add(data);
            }
            row.add("groups", groupRows);
            sectRows.add(row);
        });
        root.add("sects", sectRows);
        return root;
    }

    public static SectTournament read(JsonObject root) {
        var state = new SectTournament();
        try {
            state.month = root.get("month").getAsLong();
            if (state.month < -1) throw new IllegalArgumentException("月份无效");
            for (JsonElement raw : root.getAsJsonArray("sects")) {
                var row = raw.getAsJsonObject();
                int sect = row.get("sect").getAsInt();
                var groups = row.getAsJsonArray("groups");
                if (state.month < 0 || sect <= 0 || state.sects.containsKey(sect)
                        || groups.isEmpty() || groups.size() > MAX_GROUPS_PER_SECT) throw new IllegalArgumentException("大比宗门无效");
                var loaded = new ArrayList<Group>();
                var registered = new HashSet<String>();
                for (JsonElement data : groups) {
                    Group group = readGroup(data.getAsJsonObject());
                    if (group.entries.stream().filter(Entry::promotion).anyMatch(e -> !registered.add(e.key())))
                        throw new IllegalArgumentException("同一届重复分组");
                    loaded.add(group);
                }
                state.sects.put(sect, loaded);
            }
        } catch (RuntimeException error) {
            throw new SimFormatException("大比存档无效：" + error.getMessage());
        }
        return state;
    }

    private static Group readGroup(JsonObject data) {
        var entries = new ArrayList<Entry>();
        var rows = data.getAsJsonArray("entries");
        if (rows.size() < 3 || rows.size() > 4) throw new IllegalArgumentException("分组人数无效");
        for (var value : rows) {
            var e = value.getAsJsonObject();
            entries.add(new Entry(e.get("person").getAsInt(), e.get("player").getAsString(), e.get("name").getAsString(),
                    e.get("realm").getAsString(), e.get("stage").getAsInt(), e.get("joined").getAsLong(),
                    e.get("promotion").getAsBoolean()));
        }
        var group = new Group(entries);
        var results = data.getAsJsonArray("results");
        if (results.size() != group.size()) throw new IllegalArgumentException("场次数量无效");
        for (int i = 0; i < results.size(); i++) group.resolve(i, Outcome.valueOf(results.get(i).getAsString()));
        group.settled = data.get("settled").getAsInt();
        group.promoted = data.get("promoted").getAsInt();
        if (group.settled < 0 || group.settled >= 1 << entries.size() || (group.promoted & ~group.settled) != 0)
            throw new IllegalArgumentException("结算标记无效");
        for (int i = 0; i < entries.size(); i++) {
            var scores = group.scores(i);
            if (group.settled(i) != scores.stream().noneMatch(s -> s.outcome() == Outcome.PENDING)
                    || group.promoted(i) && (!entries.get(i).promotion() || scores.stream().noneMatch(Score::playerWon)))
                throw new IllegalArgumentException("晋升标记与战绩不符");
        }
        return group;
    }
}
