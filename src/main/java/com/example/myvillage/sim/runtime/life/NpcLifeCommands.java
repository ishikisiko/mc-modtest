package com.example.myvillage.sim.runtime.life;

import com.example.myvillage.sim.PersonView;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.life.LifePlace;
import com.example.myvillage.sim.life.LifeResidentView;
import com.example.myvillage.sim.life.LifeSite;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** Administrator-only registration and inspection. None of these commands builds or loads chunks. */
public final class NpcLifeCommands {
    public static final String KEY = "commands.myvillage.life.";
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final List<String> ROLES = List.of("innkeeper", "herbalist", "smith", "rogue", "visitor");

    private NpcLifeCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("life").requires(s -> s.hasPermission(2))
                .executes(c -> guarded(c.getSource(), () -> status(c.getSource())))
                .then(Commands.literal("bind")
                        .then(Commands.argument("site", StringArgumentType.word())
                                .then(Commands.argument("layout", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            try { return SharedSuggestionProvider.suggest(NpcLifeSites.layouts(c.getSource().getLevel()), b); }
                                            catch (RuntimeException ignored) { return b.buildFuture(); }
                                        })
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(c -> guarded(c.getSource(), () -> bind(c.getSource(),
                                                        arg(c, "site"), arg(c, "layout"), BlockPosArgument.getBlockPos(c, "pos"))))))))
                .then(Commands.literal("yunshan")
                        .executes(c -> guarded(c.getSource(), () -> yunshan(c.getSource()))))
                .then(Commands.literal("place")
                        .then(Commands.argument("site", StringArgumentType.word()).suggests(NpcLifeCommands::suggestSites)
                                .then(Commands.argument("place", StringArgumentType.word())
                                        .then(Commands.argument("kind", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(NpcLifeSites.KINDS, b))
                                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                        .executes(c -> guarded(c.getSource(), () -> place(c.getSource(),
                                                                arg(c, "site"), arg(c, "place"), arg(c, "kind"),
                                                                BlockPosArgument.getBlockPos(c, "pos")))))))))
                .then(Commands.literal("assign")
                        .then(Commands.argument("person", IntegerArgumentType.integer(1))
                                .then(Commands.argument("site", StringArgumentType.word()).suggests(NpcLifeCommands::suggestSites)
                                        .then(Commands.argument("role", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(ROLES, b))
                                                .then(Commands.argument("home", StringArgumentType.word()).suggests(NpcLifeCommands::suggestPlaces)
                                                        .then(Commands.argument("work", StringArgumentType.word()).suggests(NpcLifeCommands::suggestPlaces)
                                                                .executes(c -> guarded(c.getSource(), () -> assign(c.getSource(),
                                                                        IntegerArgumentType.getInteger(c, "person"), arg(c, "site"),
                                                                        arg(c, "role"), arg(c, "home"), arg(c, "work"))))))))))
                .then(Commands.literal("unassign")
                        .then(Commands.argument("person", IntegerArgumentType.integer(1))
                                .executes(c -> guarded(c.getSource(), () -> unassign(c.getSource(),
                                        IntegerArgumentType.getInteger(c, "person"))))))
                .then(Commands.literal("commerce")
                        .then(Commands.argument("site", StringArgumentType.word()).suggests(NpcLifeCommands::suggestSites)
                                .executes(c -> guarded(c.getSource(), () -> commerce(c.getSource(), arg(c, "site"))))))
                .then(Commands.literal("demo")
                        .then(Commands.argument("site", StringArgumentType.word()).suggests(NpcLifeCommands::suggestSites)
                                .executes(c -> guarded(c.getSource(), () -> demo(c.getSource(), arg(c, "site"))))))
                .then(Commands.literal("status")
                        .executes(c -> guarded(c.getSource(), () -> status(c.getSource())))
                        .then(Commands.argument("person", IntegerArgumentType.integer(1))
                                .executes(c -> guarded(c.getSource(), () -> personStatus(c.getSource(),
                                        IntegerArgumentType.getInteger(c, "person")))))
                        .then(Commands.literal("site")
                                .then(Commands.argument("site", StringArgumentType.word()).suggests(NpcLifeCommands::suggestSites)
                                        .executes(c -> guarded(c.getSource(), () -> siteStatus(c.getSource(), arg(c, "site")))))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestSites(
            CommandContext<CommandSourceStack> c, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        return SharedSuggestionProvider.suggest(WorldSimRuntime.sim().map(s -> s.lifeSites().stream().map(LifeSite::id).toList())
                .orElse(List.of()), b);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPlaces(
            CommandContext<CommandSourceStack> c, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        return SharedSuggestionProvider.suggest(WorldSimRuntime.sim().flatMap(s -> s.lifeSite(arg(c, "site")))
                .map(s -> s.places().stream().map(LifePlace::id).toList()).orElse(List.of()), b);
    }

    private static String arg(CommandContext<CommandSourceStack> c, String name) { return StringArgumentType.getString(c, name); }

    private static WorldSim sim() {
        return WorldSimRuntime.sim().orElseThrow(() -> new NpcLifeSites.SiteException("inactive"));
    }

    private static LifeSite site(WorldSim sim, String id) {
        return sim.lifeSite(id).orElseThrow(() -> new NpcLifeSites.SiteException("unknown_site", id));
    }

    private static int bind(CommandSourceStack source, String id, String layout, BlockPos origin) {
        LifeSite site = NpcLifeSites.bind(source.getLevel(), sim(), id, layout, origin);
        say(source, "bound", site.id(), site.places().size(), site.anchorX(), site.anchorY(), site.anchorZ());
        return siteStatus(source, site.id());
    }

    private static int commerce(CommandSourceStack source, String siteId) {
        WorldSim sim = sim();
        NpcLifeSites.observe(source.getServer(), sim, site(sim, siteId));
        List<Integer> added = NpcLifeSites.enableCommerce(source.getServer(), sim, siteId);
        var view = sim.commerce(siteId).orElseThrow();
        source.sendSuccess(() -> Component.translatable("commands.myvillage.life.commerce", siteId,
                added.size(), view.treasury(), view.transactions(), view.items().toString()), false);
        return 1;
    }

    private static int yunshan(CommandSourceStack source) {
        LifeSite site = NpcLifeSites.yunshan(source.getServer(), sim());
        say(source, "bound", site.id(), site.places().size(), site.anchorX(), site.anchorY(), site.anchorZ());
        return siteStatus(source, site.id());
    }

    private static int place(CommandSourceStack source, String siteId, String id, String kind, BlockPos at) {
        LifePlace place = NpcLifeSites.add(source.getLevel(), sim(), siteId, id, kind, at);
        say(source, "place_added", siteId, id, kind, place.x(), place.y(), place.z());
        if (kind.equals("gather")) say(source, "gather_notice");
        return 1;
    }

    private static int assign(CommandSourceStack source, int personId, String siteId, String role, String home, String work) {
        WorldSim sim = sim();
        LifeSite site = site(sim, siteId);
        validateAssignment(source, sim, site, personId, role, home, work);
        if (!sameAssignment(sim.life(personId), siteId, role, home, work)) {
            if (!sim.assignLife(personId, siteId, role, home, work))
                throw new NpcLifeSites.SiteException("assignment_rejected", personId);
            NpcLifeSites.dirty(source.getServer());
        }
        say(source, "assigned", personId, sim.person(personId).map(PersonView::name).orElse(""),
                siteId, role, home, work);
        return 1;
    }

    private static int unassign(CommandSourceStack source, int personId) {
        WorldSim sim = sim();
        if (sim.life(personId).isEmpty()) throw new NpcLifeSites.SiteException("not_assigned", personId);
        if (sim.unassignLife(personId)) NpcLifeSites.dirty(source.getServer());
        say(source, "unassigned", personId);
        return 1;
    }

    private static void validateAssignment(CommandSourceStack source, WorldSim sim, LifeSite site, int person,
                                           String role, String homeId, String workId) {
        if (!ROLES.contains(role)) throw new NpcLifeSites.SiteException("unknown_role", role);
        PersonView p = sim.person(person).filter(PersonView::alive)
                .orElseThrow(() -> new NpcLifeSites.SiteException("unknown_person", person));
        if (!p.regionId().equals(site.regionId()) || p.status().equals("travelling") || p.status().equals("secluded"))
            throw new NpcLifeSites.SiteException("person_unavailable", person);
        LifePlace home = site.place(homeId).orElseThrow(() -> new NpcLifeSites.SiteException("unknown_place", homeId));
        LifePlace work = site.place(workId).orElseThrow(() -> new NpcLifeSites.SiteException("unknown_place", workId));
        if (!home.kind().equals("rest")) throw new NpcLifeSites.SiteException("home_not_rest", homeId);
        if ((role.equals("innkeeper") || role.equals("herbalist") || role.equals("smith")) && !work.kind().equals("work")
                || role.equals("rogue") && !work.kind().equals("gather")
                || role.equals("visitor") && !List.of("market", "social", "cultivate").contains(work.kind()))
            throw new NpcLifeSites.SiteException("work_kind", workId, role);
        ServerLevel level = NpcLifeSites.level(source.getServer(), site);
        requireAvailable(level, home);
        requireAvailable(level, work);
        double distance = Math.sqrt(Math.pow((double) home.x() - work.x(), 2)
                + Math.pow((double) home.y() - work.y(), 2) + Math.pow((double) home.z() - work.z(), 2));
        if (distance > sim.lifeRules().maxPathDistance()) throw new NpcLifeSites.SiteException("work_too_far", workId);
        long occupants = sim.lifeResidents().stream().filter(r -> r.personId() != person && r.siteId().equals(site.id())
                && r.homeId().equals(homeId)).count();
        if (occupants >= home.capacity()) throw new NpcLifeSites.SiteException("home_occupied", homeId);
        NpcLifeSites.observe(source.getServer(), sim, site);
    }

    private static void requireAvailable(ServerLevel level, LifePlace place) {
        NpcLifeSites.Availability condition = level == null ? NpcLifeSites.Availability.UNLOADED : NpcLifeSites.availability(level, place);
        if (condition != NpcLifeSites.Availability.AVAILABLE)
            throw new NpcLifeSites.SiteException("place_unavailable", place.id(), condition.id());
    }

    private static boolean sameAssignment(Optional<LifeResidentView> existing, String site, String role, String home, String work) {
        return existing.filter(r -> r.siteId().equals(site) && r.role().equals(role)
                && r.homeId().equals(home) && r.workId().equals(work)).isPresent();
    }

    private record DemoRole(String role, String home, String work) {}
    private record Assignment(int personId, DemoRole role) {}

    /** All prerequisites are checked before assigning the first person; repeated demos are idempotent. */
    private static int demo(CommandSourceStack source, String siteId) {
        WorldSim sim = sim();
        LifeSite site = site(sim, siteId);
        LifePlace gather = site.places().stream().filter(p -> p.kind().equals("gather"))
                .sorted(Comparator.comparing(LifePlace::id)).findFirst()
                .orElseThrow(() -> new NpcLifeSites.SiteException("gather_required", siteId));
        List<DemoRole> roles = List.of(new DemoRole("innkeeper", "inn_rest", "inn_work"),
                new DemoRole("herbalist", "herbalist_rest", "herbalist_work"),
                new DemoRole("rogue", "guest_west", gather.id()),
                new DemoRole("visitor", "guest_east", "herb_market"));
        List<LifeResidentView> residents = sim.lifeResidents().stream().filter(r -> r.siteId().equals(siteId)).toList();
        List<DemoRole> pending = roles.stream().filter(role -> residents.stream().noneMatch(r ->
                sameAssignment(Optional.of(r), siteId, role.role(), role.home(), role.work()))).toList();
        if (pending.isEmpty()) { say(source, "demo_done", siteId, 0, residents.size()); return 1; }
        if (residents.size() + pending.size() > sim.lifeRules().maxResidentsPerSite())
            throw new NpcLifeSites.SiteException("demo_capacity", siteId, sim.lifeRules().maxResidentsPerSite());
        List<PersonView> candidates = sim.livingIn(site.regionId()).stream()
                .filter(p -> p.alive() && p.sectId() < 0 && p.rank().equals("rogue")
                        && !p.status().equals("travelling") && !p.status().equals("secluded") && sim.life(p.id()).isEmpty())
                .sorted(Comparator.comparingInt(PersonView::id)).toList();
        if (candidates.size() < pending.size())
            throw new NpcLifeSites.SiteException("demo_shortage", pending.size(), candidates.size(), site.regionId());
        List<Assignment> assignments = new ArrayList<>();
        for (int i = 0; i < pending.size(); i++) {
            DemoRole role = pending.get(i);
            int id = candidates.get(i).id();
            validateAssignment(source, sim, site, id, role.role(), role.home(), role.work());
            assignments.add(new Assignment(id, role));
        }
        int changed = 0;
        for (Assignment a : assignments) {
            DemoRole role = a.role();
            if (!sim.assignLife(a.personId(), siteId, role.role(), role.home(), role.work()))
                throw new NpcLifeSites.SiteException("demo_partial", changed, assignments.size());
            changed++;
            NpcLifeSites.dirty(source.getServer());
            say(source, "assigned", a.personId(), sim.person(a.personId()).map(PersonView::name).orElse(""),
                    siteId, role.role(), role.home(), role.work());
        }
        say(source, "demo_done", siteId, changed, residents.size() + changed);
        say(source, "gather_notice");
        return changed;
    }

    private static int status(CommandSourceStack source) {
        WorldSim sim = sim();
        say(source, "summary", sim.lifeSites().size(), sim.lifeResidents().size());
        for (LifeSite site : sim.lifeSites()) {
            long residents = sim.lifeResidents().stream().filter(r -> r.siteId().equals(site.id())).count();
            say(source, "site_summary", site.id(), site.name(), site.regionId(), site.dimension(),
                    site.anchorX(), site.anchorY(), site.anchorZ(), site.places().size(), residents);
        }
        return 1;
    }

    private static int siteStatus(CommandSourceStack source, String siteId) {
        WorldSim sim = sim();
        LifeSite site = site(sim, siteId);
        ServerLevel level = NpcLifeSites.level(source.getServer(), site);
        NpcLifeSites.observe(source.getServer(), sim, site);
        for (LifePlace place : site.places()) {
            String availability = level == null ? "unloaded" : NpcLifeSites.availability(level, place).id();
            say(source, "place_status", place.id(), place.kind(), availability, place.x(), place.y(), place.z(),
                    place.block().isEmpty() ? "-" : place.block());
        }
        for (LifeResidentView resident : sim.lifeResidents()) if (resident.siteId().equals(siteId))
            personStatus(source, resident.personId());
        sim.lifeSupply(siteId).ifPresent(supply -> say(source, "supply_status", siteId, supply.stock(),
                supply.medicine(), supply.treasury(), supply.gatheredToday(), supply.services()));
        return 1;
    }

    private static int personStatus(CommandSourceStack source, int person) {
        WorldSim sim = sim();
        LifeResidentView resident = sim.life(person).orElseThrow(() -> new NpcLifeSites.SiteException("not_assigned", person));
        say(source, "resident_status", person, sim.person(person).map(PersonView::name).orElse(""), resident.siteId(),
                resident.role(), resident.activity(), resident.targetId(), resident.reason());
        // A bounded developer snapshot: exact progress, resource balances and execution lease for QA.
        source.sendSuccess(() -> Component.literal("NPC_LIFE " + JSON.toJson(resident)), false);
        return 1;
    }

    @FunctionalInterface private interface Action { int run() throws Exception; }
    private static int guarded(CommandSourceStack source, Action action) {
        try { return action.run(); }
        catch (NpcLifeSites.SiteException failure) {
            source.sendFailure(Component.translatable(KEY + failure.key(), failure.arguments()));
            return 0;
        } catch (Exception failure) {
            source.sendFailure(Component.translatable(KEY + "failed", failure.getMessage()));
            return 0;
        }
    }

    private static void say(CommandSourceStack source, String suffix, Object... arguments) {
        source.sendSuccess(() -> Component.translatable(KEY + suffix, arguments), false);
    }
}
