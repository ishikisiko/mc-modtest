package com.example.myvillage.sim.runtime;

import com.example.myvillage.region.runtime.RegionRuntimeService;
import com.example.myvillage.sim.Overview;
import com.example.myvillage.sim.PersonView;
import com.example.myvillage.sim.RegionView;
import com.example.myvillage.sim.SectView;
import com.example.myvillage.sim.SimDate;
import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.runtime.avatar.GateBuilder;
import com.example.myvillage.sim.runtime.net.WorldSimSnapshots;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /myvillage world ...} — read-only views of the world ledger plus the admin actions
 * {@code pause}, {@code resume} and {@code advance <days>}. Registered under the {@code /myvillage}
 * root (permission 2). Prose (chronicle lines, realm, rank and status words) goes through language
 * keys so each client reads its own language; names are Chinese literals in every language.
 *
 * <ul>
 *   <li>{@code info} — era date, sim day, calendar day, pending days, paused?, population, sects, tier</li>
 *   <li>{@code sects [all]} — active sects (or all, with the destroyed)</li>
 *   <li>{@code sect <id|name>} — one sect in detail</li>
 *   <li>{@code sect <id> build [here]} — build the sect's compound (山门) at its ledger gate, or move the gate
 *       to the caller first ({@code here}), record the realization, and let its avatars appear
 *       ({@link GateBuilder}); synchronous, it can take a minute</li>
 *   <li>{@code person <name>} — people whose name or Daoist title contains the text, living first</li>
 *   <li>{@code chronicle [n]} — the latest n notable events (importance 2+), newest last</li>
 *   <li>{@code here} — the caller's region: sects seated there, notable people present, recent events</li>
 *   <li>{@code pause} / {@code resume} — stop or restart settlement (saved; the calendar keeps running,
 *       resuming does not catch up)</li>
 *   <li>{@code advance <days>} — settle 1..3650 days now, even while paused</li>
 * </ul>
 */
public final class WorldSimCommands {
    private static final int DEFAULT_CHRONICLE = 10;
    private static final int MAX_CHRONICLE = 50;
    private static final int PERSON_MATCHES = 5;
    private static final int LIST_PEOPLE = 8;

    private WorldSimCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("world")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> info(ctx.getSource()))
                .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
                .then(Commands.literal("sects")
                        .executes(ctx -> sects(ctx.getSource(), false))
                        .then(Commands.literal("all").executes(ctx -> sects(ctx.getSource(), true))))
                .then(Commands.literal("sect")
                        // before "query": for "sect 3 build" brigadier keeps the first of two full parses
                        .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                .executes(ctx -> sect(ctx.getSource(), String.valueOf(IntegerArgumentType.getInteger(ctx, "id"))))
                                .then(Commands.literal("build")
                                        .executes(ctx -> buildGate(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "id"), false))
                                        .then(Commands.literal("here")
                                                .executes(ctx -> buildGate(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "id"), true)))))
                        .then(Commands.argument("query", StringArgumentType.greedyString())
                                .executes(ctx -> sect(ctx.getSource(), StringArgumentType.getString(ctx, "query")))))
                .then(Commands.literal("person")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> person(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("chronicle")
                        .executes(ctx -> chronicle(ctx.getSource(), DEFAULT_CHRONICLE))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_CHRONICLE))
                                .executes(ctx -> chronicle(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("here").executes(ctx -> here(ctx.getSource())))
                .then(Commands.literal("pause").executes(ctx -> setPaused(ctx.getSource(), true)))
                .then(Commands.literal("resume").executes(ctx -> setPaused(ctx.getSource(), false)))
                .then(Commands.literal("advance")
                        .then(Commands.argument("days", IntegerArgumentType.integer(1, WorldSimDriver.MAX_ADVANCE_DAYS))
                                .executes(ctx -> advance(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "days")))));
    }

    // ------------------------------------------------------------------ views

    private static int info(CommandSourceStack source) {
        Optional<WorldSimDriver> active = active(source);
        if (active.isEmpty()) {
            return 0;
        }
        WorldSimDriver driver = active.get();
        WorldSim sim = driver.sim();
        int dpy = WorldSimRuntime.daysPerYear();
        Overview o = sim.overview(dpy);
        SimDate date = o.date();
        long calendarDay = WorldSimRuntime.calendarDay(source.getServer());
        send(source, () -> WorldSimText.line("info.date", WorldSimText.date(date), date.dayOfYear() + 1, dpy, o.day()));
        send(source, () -> WorldSimText.line("info.settlement", calendarDay, driver.pendingDays(),
                WorldSimText.line(driver.paused() ? "paused" : "running")));
        send(source, () -> WorldSimText.line("info.population", WorldSimText.line("tier." + o.tierId()),
                o.population(), o.targetPopulation(), o.deadCount()));
        List<Component> realms = new ArrayList<>();
        for (Map.Entry<String, Integer> e : o.livingByRealm().entrySet()) {
            realms.add(WorldSimText.realm(e.getKey()).append(" " + e.getValue()));
        }
        send(source, () -> WorldSimText.line("info.realms", joined(realms)));
        send(source, () -> WorldSimText.line("info.sects", o.activeSects(), o.destroyedSects(), o.eventCount()));
        List<Component> top = new ArrayList<>();
        for (int id : o.topPersonIds()) {
            sim.person(id).ifPresent(p -> top.add(personShort(p)));
        }
        if (!top.isEmpty()) {
            send(source, () -> WorldSimText.line("info.top", joined(top)));
        }
        return 1;
    }

    private static int sects(CommandSourceStack source, boolean includeDestroyed) {
        Optional<WorldSimDriver> active = active(source);
        if (active.isEmpty()) {
            return 0;
        }
        List<SectView> sects = active.get().sim().sects(includeDestroyed);
        send(source, () -> WorldSimText.line(includeDestroyed ? "sects.header_all" : "sects.header", sects.size()));
        for (SectView s : sects) {
            send(source, () -> sectLine(s));
        }
        return sects.size();
    }

    private static int sect(CommandSourceStack source, String query) {
        Optional<WorldSimDriver> active = active(source);
        if (active.isEmpty()) {
            return 0;
        }
        WorldSim sim = active.get().sim();
        int dpy = WorldSimRuntime.daysPerYear();
        Optional<SectView> found = findSect(sim, query.trim());
        if (found.isEmpty()) {
            source.sendFailure(WorldSimText.line("sect.not_found", query));
            return 0;
        }
        SectView s = found.get();
        send(source, () -> WorldSimText.line("sect.header", s.id(), s.name(), WorldSimText.sectState(s.state()),
                WorldSimRuntime.regionName(s.regionId())));
        send(source, () -> WorldSimText.line("sect.founded", dateOf(sim, s.foundedDay(), dpy), orNone(s.founderName())));
        if (s.parentSectId() >= 0) {
            String parent = sim.sect(s.parentSectId()).map(SectView::name).orElse("#" + s.parentSectId());
            send(source, () -> WorldSimText.line("sect.parent", parent));
        }
        if (!s.state().equals("active")) {
            send(source, () -> WorldSimText.line("sect.destroyed", dateOf(sim, s.destroyedDay(), dpy)));
        }
        send(source, () -> WorldSimText.line("sect.master", orNone(s.masterName())));
        send(source, () -> WorldSimText.line("sect.stats", s.memberCount(),
                s.topRealmId().isEmpty() ? WorldSimText.line("none") : WorldSimText.realm(s.topRealmId()),
                fmt(s.resources()), fmt(s.prestige())));
        send(source, () -> WorldSimText.line("sect.technique", orNone(s.signatureTechniqueName())));
        send(source, () -> WorldSimText.line("sect.heritage", orNone(s.heritageName())));
        send(source, () -> WorldSimText.line("sect.gate", s.gateX(), s.gateZ(),
                WorldSimText.line(s.gateRealized() ? "gate.realized" : "gate.unrealized")));
        for (SectView.Relation r : s.relations()) {
            String other = sim.sect(r.otherSectId()).map(SectView::name).orElse("#" + r.otherSectId());
            send(source, () -> WorldSimText.line("sect.relation", other, r.value(),
                    Component.translatableWithFallback(WorldSimText.KEY + "relation." + r.state(), r.state())));
        }
        List<PersonView> members = sim.membersAt(s.id());
        if (!members.isEmpty()) {
            List<PersonView> sorted = strongestFirst(members);
            List<Component> names = new ArrayList<>();
            for (int i = 0; i < Math.min(LIST_PEOPLE, sorted.size()); i++) {
                names.add(personShort(sorted.get(i)));
            }
            send(source, () -> WorldSimText.line("sect.members", members.size(), joined(names)));
        }
        return 1;
    }

    private static int person(CommandSourceStack source, String name) {
        Optional<WorldSimDriver> active = active(source);
        if (active.isEmpty()) {
            return 0;
        }
        WorldSim sim = active.get().sim();
        int dpy = WorldSimRuntime.daysPerYear();
        String fragment = name.trim();
        List<PersonView> found = sim.findPersons(fragment, PERSON_MATCHES + 1);
        if (found.isEmpty()) {
            source.sendFailure(WorldSimText.line("person.not_found", fragment));
            return 0;
        }
        for (int i = 0; i < Math.min(PERSON_MATCHES, found.size()); i++) {
            PersonView p = found.get(i);
            send(source, () -> WorldSimText.line(p.alive() ? "person.header" : "person.header_dead",
                    p.id(), p.name(), p.title().isEmpty() ? "" : " " + p.title()));
            long ageDays = (p.alive() ? sim.day() : p.deathDay()) - p.birthDay();
            send(source, () -> WorldSimText.line("person.realm", WorldSimText.stage(p.realmId(), p.stage()),
                    WorldSimText.rootGrade(p.rootGrade()), Math.floorDiv(ageDays, dpy)));
            Component sect = p.sectId() >= 0 && !p.sectName().isEmpty()
                    ? WorldSimText.line("person.of_sect", WorldSimText.rank(p.rank()), p.sectName())
                    : WorldSimText.rank(p.rank());
            if (p.alive()) {
                send(source, () -> WorldSimText.line("person.place", sect,
                        WorldSimRuntime.regionName(p.regionId()), WorldSimText.status(p.status())));
                send(source, () -> WorldSimText.line("person.technique", orNone(p.techniqueName()),
                        p.masterId() >= 0 ? nameOf(sim, p.masterId()) : WorldSimText.line("none")));
                int friends = 0;
                int enemies = 0;
                int disciples = 0;
                for (PersonView.Relation r : p.relations()) {
                    switch (r.kind()) {
                        case "friend" -> friends++;
                        case "enemy" -> enemies++;
                        case "disciple" -> disciples++;
                        default -> {
                        }
                    }
                }
                int f = friends;
                int e = enemies;
                int d = disciples;
                send(source, () -> WorldSimText.line("person.relations", d, f, e));
            } else {
                send(source, () -> WorldSimText.line("person.death", sect, dateOf(sim, p.deathDay(), dpy),
                        WorldSimText.cause(p.deathCause())));
                if (p.killerId() >= 0) {
                    send(source, () -> WorldSimText.line("person.killer", nameOf(sim, p.killerId())));
                }
            }
        }
        if (found.size() > PERSON_MATCHES) {
            send(source, () -> WorldSimText.line("person.more", PERSON_MATCHES));
        }
        return found.size();
    }

    private static int chronicle(CommandSourceStack source, int count) {
        Optional<WorldSimDriver> active = active(source);
        if (active.isEmpty()) {
            return 0;
        }
        WorldSim sim = active.get().sim();
        int dpy = WorldSimRuntime.daysPerYear();
        List<SimEvent> events = sim.recentEvents(2, count);
        send(source, () -> WorldSimText.line("chronicle.header", events.size()));
        for (SimEvent e : events) {
            send(source, () -> WorldSimText.datedEvent(e, sim.prehistoryDays(), dpy));
        }
        return events.size();
    }

    private static int here(CommandSourceStack source) {
        Optional<WorldSimDriver> active = active(source);
        if (active.isEmpty()) {
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(WorldSimText.line("here.player_only"));
            return 0;
        }
        Optional<String> regionId = RegionRuntimeService.currentRegion(player);
        if (regionId.isEmpty()) {
            source.sendFailure(WorldSimText.line("here.outside"));
            return 0;
        }
        WorldSim sim = active.get().sim();
        int dpy = WorldSimRuntime.daysPerYear();
        RegionView r = sim.region(regionId.get(), dpy);
        send(source, () -> WorldSimText.line("here.header", r.displayName(), r.tier(), r.qiLo(), r.qiHi(),
                r.dangerLo(), r.dangerHi(), r.livingCount()));
        if (r.sectIds().isEmpty()) {
            send(source, () -> WorldSimText.line("here.no_sects"));
        }
        for (int id : r.sectIds()) {
            sim.sect(id).ifPresent(s -> {
                double dx = s.gateX() + 0.5 - player.getX();
                double dz = s.gateZ() + 0.5 - player.getZ();
                long distance = Math.round(Math.sqrt(dx * dx + dz * dz));
                send(source, () -> WorldSimText.line("here.sect", s.id(), s.name(), orNone(s.masterName()),
                        s.memberCount(), s.gateX(), s.gateZ(), distance,
                        WorldSimText.line(s.gateRealized() ? "gate.realized" : "gate.unrealized")));
            });
        }
        List<PersonView> present = new ArrayList<>();
        for (PersonView p : sim.findPersons("", Integer.MAX_VALUE)) {
            if (p.alive() && p.regionId().equals(r.id())) {
                present.add(p);
            }
        }
        List<PersonView> sorted = strongestFirst(present);
        List<Component> names = new ArrayList<>();
        for (int i = 0; i < Math.min(LIST_PEOPLE, sorted.size()); i++) {
            names.add(personShort(sorted.get(i)));
        }
        if (!names.isEmpty()) {
            send(source, () -> WorldSimText.line("here.people", joined(names)));
        }
        List<SimEvent> recent = r.recentEvents();
        for (SimEvent e : recent.subList(Math.max(0, recent.size() - 3), recent.size())) {
            send(source, () -> WorldSimText.datedEvent(e, sim.prehistoryDays(), dpy));
        }
        return 1;
    }

    // ------------------------------------------------------------------ admin

    private static int setPaused(CommandSourceStack source, boolean paused) {
        if (active(source).isEmpty()) {
            return 0;
        }
        boolean changed = WorldSimRuntime.setPaused(paused);
        String key = paused ? (changed ? "paused_now" : "already_paused") : (changed ? "resumed_now" : "already_running");
        source.sendSuccess(() -> WorldSimText.line(key), changed);
        return changed ? 1 : 0;
    }

    private static int advance(CommandSourceStack source, int days) {
        Optional<WorldSimDriver> active = active(source);
        if (active.isEmpty()) {
            return 0;
        }
        WorldSim sim = active.get().sim();
        long[] notable = {0};
        long t0 = System.nanoTime();
        long events;
        try {
            events = WorldSimRuntime.advance(days, day -> {
                for (SimEvent e : day) {
                    if (e.importance() >= 2) {
                        notable[0]++;
                    }
                }
            });
        } catch (RuntimeException ex) {
            source.sendFailure(WorldSimText.line("advance.failed", String.valueOf(ex.getMessage())));
            return 0;
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        int dpy = WorldSimRuntime.daysPerYear();
        long total = events;
        send(source, () -> WorldSimText.line("advance.done", days, WorldSimText.date(sim.date(dpy)), sim.day(),
                total, notable[0], ms), true);
        return days;
    }

    private static int buildGate(CommandSourceStack source, int sectId, boolean here) {
        return GateBuilder.build(source, sectId, here);
    }

    // ------------------------------------------------------------------ helpers

    private static Optional<WorldSimDriver> active(CommandSourceStack source) {
        Optional<WorldSimDriver> driver = WorldSimRuntime.driver();
        if (driver.isEmpty()) {
            String reason = WorldSimRuntime.inactiveReason();
            source.sendFailure(WorldSimText.line("inactive", reason == null ? "?" : reason));
        }
        return driver;
    }

    private static Optional<SectView> findSect(WorldSim sim, String query) {
        try {
            Optional<SectView> byId = sim.sect(Integer.parseInt(query));
            if (byId.isPresent()) {
                return byId;
            }
        } catch (NumberFormatException ignored) {
            // a name
        }
        List<SectView> all = sim.sects(true);
        for (SectView s : all) {
            if (s.name().equals(query)) {
                return Optional.of(s);
            }
        }
        for (SectView s : all) {
            if (s.name().contains(query) && s.state().equals("active")) {
                return Optional.of(s);
            }
        }
        for (SectView s : all) {
            if (s.name().contains(query)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    private static MutableComponent sectLine(SectView s) {
        MutableComponent line = WorldSimText.line("sects.line", s.id(), s.name(),
                WorldSimRuntime.regionName(s.regionId()), orNone(s.masterName()), s.memberCount(),
                s.topRealmId().isEmpty() ? WorldSimText.line("none") : WorldSimText.realm(s.topRealmId()),
                fmt(s.prestige()), s.gateX(), s.gateZ(),
                WorldSimText.line(s.gateRealized() ? "gate.realized" : "gate.unrealized"));
        if (!s.state().equals("active")) {
            line.append(" ").append(WorldSimText.sectState(s.state()));
        }
        return line;
    }

    /** 名字 (境界层次, 宗门 身份). */
    private static Component personShort(PersonView p) {
        MutableComponent c = Component.literal(p.name() + (p.title().isEmpty() ? "" : " " + p.title()) + " (")
                .append(WorldSimText.stage(p.realmId(), p.stage()));
        if (p.sectId() >= 0 && !p.sectName().isEmpty()) {
            c.append(", " + p.sectName() + " ").append(WorldSimText.rank(p.rank()));
        } else {
            c.append(", ").append(WorldSimText.rank(p.rank()));
        }
        return c.append(")");
    }

    /** Living people sorted by realm, then stage, strongest first; ties by id. */
    private static List<PersonView> strongestFirst(List<PersonView> people) {
        List<String> order = WorldSimRuntime.sim().map(WorldSim::realmIds).orElse(List.of());
        return WorldSimSnapshots.strongestFirst(people, order);
    }

    private static Component nameOf(WorldSim sim, int personId) {
        return Component.literal(sim.person(personId).map(PersonView::name).orElse("#" + personId));
    }

    private static Component dateOf(WorldSim sim, long day, int dpy) {
        return WorldSimText.date(SimDate.of(day, sim.prehistoryDays(), dpy));
    }

    private static Component orNone(String text) {
        return text == null || text.isEmpty() ? WorldSimText.line("none") : Component.literal(text);
    }

    private static Component joined(List<Component> parts) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(parts.get(i));
        }
        return out;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private static void send(CommandSourceStack source, Supplier<Component> line) {
        source.sendSuccess(line, false);
    }

    private static void send(CommandSourceStack source, Supplier<Component> line, boolean broadcast) {
        source.sendSuccess(line, broadcast);
    }
}
