package com.example.myvillage.sim.life;

import com.example.myvillage.sim.engine.SimContext;
import com.example.myvillage.sim.model.Person;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Persistent short plans shared by embodied and distant residents. Mutated through WorldSim. */
public final class LifeEngine {
    public static final Set<String> ROLES = Set.of("innkeeper", "herbalist", "rogue", "visitor");
    public static final Set<String> PHASES = Set.of("waiting", "moving", "working");
    public static final Set<String> FAILURES = Set.of("facility_missing", "target_blocked", "path_unavailable",
            "path_timeout", "stuck", "target_unloaded", "too_far", "stale_target");

    private LifeEngine() { }

    public static boolean registerSite(SimContext ctx, LifeSite site) {
        LifeState state = ctx.state.life;
        LifeSite old = state.sites.get(site.id());
        if (site.equals(old)) return false;
        if (old != null) throw new IllegalArgumentException("site_already_bound");
        if (!ctx.hasRegion(site.regionId())) throw new IllegalArgumentException("unknown_region");
        if (state.sites.size() >= rules(ctx).maxSites()) throw new IllegalArgumentException("site_limit");
        if (site.places().size() > rules(ctx).maxPlacesPerSite()) throw new IllegalArgumentException("place_limit");
        state.sites.put(site.id(), site);
        LifeState.Supply supply = new LifeState.Supply();
        supply.treasury = rules(ctx).initialTreasury();
        state.supplies.put(site.id(), supply);
        return true;
    }

    public static boolean addPlace(SimContext ctx, String siteId, LifePlace place) {
        LifeSite site = requireSite(ctx, siteId);
        LifePlace old = site.place(place.id()).orElse(null);
        if (place.equals(old)) return false;
        if (old != null) throw new IllegalArgumentException("place_already_bound");
        if (site.places().size() >= rules(ctx).maxPlacesPerSite()) throw new IllegalArgumentException("place_limit");
        List<LifePlace> places = new ArrayList<>(site.places());
        places.add(place);
        ctx.state.life.sites.put(siteId, new LifeSite(site.id(), site.name(), site.dimension(), site.regionId(),
                site.anchorX(), site.anchorY(), site.anchorZ(), places));
        return true;
    }

    public static boolean assign(SimContext ctx, int id, String siteId, String role, String homeId, String workId) {
        Person person = ctx.state.persons.get(id);
        if (person == null) throw new IllegalArgumentException("npc_not_alive");
        LifeSite site = requireSite(ctx, siteId);
        if (!ROLES.contains(role)) throw new IllegalArgumentException("unknown_role");
        LifePlace home = site.place(homeId).orElseThrow(() -> new IllegalArgumentException("unknown_home"));
        LifePlace work = site.place(workId).orElseThrow(() -> new IllegalArgumentException("unknown_work"));
        if (!"rest".equals(home.kind())) throw new IllegalArgumentException("home_must_be_rest");
        if (!Set.of("work", "gather", "cultivate", "market", "social", "entry").contains(work.kind())) {
            throw new IllegalArgumentException("invalid_work_kind");
        }
        LifeResident old = ctx.state.life.residents.get(id);
        if (old != null) {
            if (old.siteId.equals(siteId) && old.role.equals(role) && old.homeId.equals(homeId)
                    && old.workId.equals(workId)) return false;
            throw new IllegalArgumentException("resident_already_assigned");
        }
        if (!person.regionId.equals(site.regionId())) throw new IllegalArgumentException("npc_not_local");
        if (person.status.equals("travelling") || person.status.equals("secluded")) {
            throw new IllegalArgumentException("npc_not_settled");
        }
        long residents = ctx.state.life.residents.values().stream().filter(r -> r.siteId.equals(siteId)).count();
        if (residents >= rules(ctx).maxResidentsPerSite()) throw new IllegalArgumentException("resident_limit");
        long housed = ctx.state.life.residents.values().stream()
                .filter(r -> r.siteId.equals(siteId) && r.homeId.equals(homeId)).count();
        if (housed >= home.capacity()) throw new IllegalArgumentException("home_full");
        LifeResident resident = new LifeResident();
        resident.personId = id;
        resident.siteId = siteId;
        resident.role = role;
        resident.homeId = homeId;
        resident.workId = workId;
        resident.x = home.x(); resident.y = home.y(); resident.z = home.z();
        resident.coins = rules(ctx).initialCoins();
        ctx.state.life.residents.put(id, resident);
        // The town owns this person's location until unassigned; sect membership remains independent.
        person.homeRegionId = site.regionId();
        person.destRegionId = "";
        person.status = "at_sect";
        person.statusUntil = -1;
        person.quarryId = -1;
        return true;
    }

    public static boolean unassign(SimContext ctx, int id) {
        LifeResident resident = ctx.state.life.residents.remove(id);
        if (resident == null) return false;
        Person person = ctx.state.persons.get(id);
        if (person != null && person.sectId >= 0 && ctx.sect(person.sectId) != null) {
            person.homeRegionId = ctx.sect(person.sectId).homeRegionId;
        }
        return true;
    }

    /** Unloaded is absence of a new observation, never proof that a building has disappeared. */
    public static boolean availability(SimContext ctx, String siteId, String placeId, String status) {
        requireSite(ctx, siteId).place(placeId).orElseThrow(() -> new IllegalArgumentException("unknown_place"));
        if ("unloaded".equals(status)) return false;
        if (!Set.of("available", "blocked").contains(status)) throw new IllegalArgumentException("invalid_availability");
        String key = LifeState.placeKey(siteId, placeId);
        return !status.equals(ctx.state.life.availability.put(key, status));
    }

    public static long activate(SimContext ctx, int id) {
        LifeResident r = requireResident(ctx, id);
        if (r.embodied) throw new IllegalArgumentException("resident_already_embodied");
        long next = Math.incrementExact(ctx.state.life.nextEpoch);
        r.epoch = ctx.state.life.nextEpoch;
        ctx.state.life.nextEpoch = next;
        r.embodied = true;
        return r.epoch;
    }

    public static boolean checkpoint(SimContext ctx, int id, long epoch, int x, int y, int z) {
        LifePlace.requirePosition(x, y, z);
        LifeResident r = lease(ctx, id, epoch);
        if (r == null || (r.x == x && r.y == y && r.z == z)) return false;
        r.x = x; r.y = y; r.z = z;
        return true;
    }

    public static boolean release(SimContext ctx, int id, long epoch, int x, int y, int z) {
        LifeResident r = lease(ctx, id, epoch);
        if (r == null) return false;
        checkpoint(ctx, id, epoch, x, y, z);
        r.embodied = false;
        r.pauseTicks = 0;
        if ("moving".equals(r.phase)) {
            LifePlace place = requireSite(ctx, r.siteId).place(r.targetId).orElse(null);
            if (place != null) {
                // Actual movement replaced the estimate. Work progress is deliberately left intact.
                r.progressTicks = 0;
                r.requiredTicks = travelTicks(ctx, r, place);
            }
        }
        return true;
    }

    public static boolean arrive(SimContext ctx, int id, long epoch, long revision) {
        LifeResident r = lease(ctx, id, epoch);
        if (r == null || r.revision != revision || !"moving".equals(r.phase) || r.pauseTicks > 0) return false;
        LifePlace p = requireSite(ctx, r.siteId).place(r.targetId).orElse(null);
        if (p == null || !knownAvailable(ctx, r.siteId, p.id())) return false;
        // Runtime checks exact body distance; this also rejects unrelated block checkpoints.
        int reach = (int) Math.ceil(rules(ctx).arrivalDistance());
        if (Math.abs((long) r.x - p.x()) > reach || Math.abs((long) r.y - p.y()) > reach
                || Math.abs((long) r.z - p.z()) > reach) return false;
        working(ctx, r, p);
        return true;
    }

    public static boolean fail(SimContext ctx, int id, long epoch, long revision, String reason) {
        LifeResident r = lease(ctx, id, epoch);
        if (r == null || r.revision != revision || "waiting".equals(r.phase)) return false;
        if (!FAILURES.contains(reason)) throw new IllegalArgumentException("invalid_failure_reason");
        fail(ctx, r, reason, Math.max(0, ctx.state.life.clockTicks));
        return true;
    }

    public static boolean pause(SimContext ctx, int id, long ticks) {
        LifeResident r = requireResident(ctx, id);
        if (ticks < 0 || ticks > rules(ctx).conversationPauseTicks()) throw new IllegalArgumentException("invalid_pause");
        if (!r.embodied || ticks <= r.pauseTicks) return false;
        r.pauseTicks = ticks;
        return true;
    }

    public static boolean observed(SimContext ctx, int id, boolean observed) {
        LifeResident r = requireResident(ctx, id);
        if (r.observed == observed) return false;
        r.observed = observed;
        return true;
    }

    /** Rebase while globally paused. No offline wall-clock catch-up or surprise resumption payout. */
    public static boolean clock(SimContext ctx, long now, int ticksPerDay, boolean paused) {
        if (now < 0 || ticksPerDay < 1) throw new IllegalArgumentException("invalid_life_clock");
        LifeState state = ctx.state.life;
        long previous = state.clockTicks;
        boolean changed = state.ticksPerDay != ticksPerDay || previous != now;
        state.ticksPerDay = ticksPerDay;
        state.clockTicks = now;
        changed |= state.residents.entrySet().removeIf(e -> !ctx.state.persons.containsKey(e.getKey()));
        if (paused || previous < 0 || now <= previous) return changed;
        long elapsed = Math.min(now - previous, rules(ctx).catchUpTicks());
        long at = now - elapsed;
        while (at < now) {
            long untilDay = ticksPerDay - Math.floorMod(at, ticksPerDay);
            long step = Math.min(now - at, Math.min(untilDay, rules(ctx).runtimeIntervalTicks()));
            for (LifeResident r : state.residents.values()) advance(ctx, r, at, step);
            at += step;
        }
        return changed;
    }

    /** The daily cultivation engine consumes only the time this resident actually cultivated. */
    public static double takeCultivationShare(SimContext ctx, int id) {
        LifeResident r = ctx.state.life.residents.get(id);
        if (r == null) return 1.0;
        double share = Math.min(1.0, r.cultivationTicks / (double) ctx.state.life.ticksPerDay);
        r.cultivationTicks = 0;
        return share;
    }

    /** Combat owns navigation and earnings until explicit release; conversations keep their bounded pause. */
    public static void combat(SimContext ctx, int id, boolean active) {
        LifeResident r = ctx.state.life.residents.get(id);
        if (r == null) return;
        r.pauseTicks = 0;
        r.reason = active ? "combat" : "combat_return";
        if (!active) {
            // A new route/arrival is required even when the old job's site still exists.
            r.phase = "waiting";
            r.targetId = "";
            r.progressTicks = 0;
            r.requiredTicks = 0;
            r.retryAfter = 0;
            r.revision = Math.incrementExact(r.revision);
        }
    }

    private static void advance(SimContext ctx, LifeResident r, long at, long elapsed) {
        // A nearby resident waiting for a safe body or a free avatar slot must not work invisibly.
        if (ctx.state.combat.engaged.contains(r.personId) || (r.observed && !r.embodied)) return;
        var combatProfile = ctx.state.combat.profiles.get(r.personId);
        if (!r.embodied && combatProfile != null && combatProfile.body() != null
                && combatProfile.body().unresolved()) return;
        long left = elapsed;
        int transitions = 0;
        while (left > 0 && transitions < rules(ctx).maxTransitionsPerTick()) {
            if (r.pauseTicks > 0) {
                long wait = Math.min(left, r.pauseTicks);
                r.pauseTicks -= wait; left -= wait; at += wait;
                continue;
            }
            if ("waiting".equals(r.phase)) {
                if (r.retryAfter > at) {
                    long wait = Math.min(left, r.retryAfter - at);
                    left -= wait; at += wait;
                    continue;
                }
                if (!choose(ctx, r, at)) {
                    r.retryAfter = add(at, rules(ctx).retryTicks());
                    continue;
                }
                transitions++;
            }
            LifePlace place = requireSite(ctx, r.siteId).place(r.targetId).orElse(null);
            if (place == null || !knownAvailable(ctx, r.siteId, r.targetId)) {
                fail(ctx, r, place == null ? "stale_target" : "target_blocked", at);
                transitions++;
                continue;
            }
            if ("moving".equals(r.phase) && r.embodied) return;
            if ("moving".equals(r.phase) && r.blockedTarget.equals(r.targetId)) {
                fail(ctx, r, "path_unavailable", at);
                transitions++;
                continue;
            }
            long used = Math.min(left, Math.max(0, r.requiredTicks - r.progressTicks));
            r.progressTicks += used; at += used; left -= used;
            if ("working".equals(r.phase) && "cultivate".equals(r.activity)) {
                r.cultivationTicks = Math.min(ctx.state.life.ticksPerDay, add(r.cultivationTicks, used));
            }
            if (r.progressTicks < r.requiredTicks) return;
            if ("moving".equals(r.phase)) {
                working(ctx, r, place);
            } else {
                complete(ctx, r, at);
            }
            transitions++;
        }
    }

    private static boolean choose(SimContext ctx, LifeResident r, long at) {
        Person p = ctx.state.persons.get(r.personId);
        LifeRules rules = rules(ctx);
        LifeSite site = requireSite(ctx, r.siteId);
        double dayFraction = Math.floorMod(at, ctx.state.life.ticksPerDay) / (double) ctx.state.life.ticksPerDay;
        var combat = ctx.state.combat.profiles.get(r.personId);
        if (combat != null && combat.health() < combat.maximumHealth()
                && begin(ctx, r, "rest", site.place(r.homeId).orElse(null), "combat_recovery")) return true;
        boolean night = dayFraction >= rules.restStartFraction() && dayFraction < rules.restEndFraction();
        if (night || r.fatigue >= rules.fatigueThreshold() || p.injury >= rules.injuryRestThreshold()) {
            if (begin(ctx, r, "rest", site.place(r.homeId).orElse(null),
                    p.injury >= rules.injuryRestThreshold() ? "injured" : night ? "night" : "tired")) return true;
        }
        LifeState.Supply supply = supply(ctx, r.siteId, at);
        if (r.herbs > 0 && supply.treasury >= rules.herbPrice() && supply.stock < rules.maxInventory()
                && beginKind(ctx, r, "deliver", "market", "sell_herbs")) return true;
        if (r.role.equals("herbalist") && supply.stock > 0 && supply.medicine < rules.maxInventory()
                && begin(ctx, r, "work", site.place(r.workId).orElse(null), "prepare_medicine")) return true;
        if (r.role.equals("innkeeper") && !r.previousActivity.equals("work")
                && begin(ctx, r, "work", site.place(r.workId).orElse(null), "inn_service")) return true;
        if ((r.role.equals("rogue") || r.role.equals("visitor")) && r.coins < rules.desiredCoins()
                && r.herbs < rules.maxInventory() && supply.gatheredToday < rules.gatherQuotaPerDay()
                && beginKind(ctx, r, "gather", "gather", "earn_living")) return true;
        // A short completed plan gets reconsidered; stable identity traits bias the quiet alternatives.
        boolean preferSocial = r.previousActivity.equals("cultivate")
                || (!r.previousActivity.equals("social") && p.wanderlust > p.ambition);
        if (preferSocial && beginKind(ctx, r, "social", "social", "meet_neighbors")) return true;
        if (r.coins >= rules.cultivationCost() && supply.treasury <= rules.maxInventory() - rules.cultivationCost()
                && beginKind(ctx, r, "cultivate", "cultivate", "cultivate")) return true;
        if (beginKind(ctx, r, "social", "social", "meet_neighbors")) return true;
        if (begin(ctx, r, "rest", site.place(r.homeId).orElse(null), "idle_rest")) return true;
        r.reason = "no_available_place";
        return false;
    }

    private static boolean beginKind(SimContext ctx, LifeResident r, String activity, String kind, String reason) {
        for (LifePlace place : requireSite(ctx, r.siteId).places()) {
            if (place.kind().equals(kind) && begin(ctx, r, activity, place, reason)) return true;
        }
        return false;
    }

    private static boolean begin(SimContext ctx, LifeResident r, String activity, LifePlace place, String reason) {
        if (place == null || !knownAvailable(ctx, r.siteId, place.id())
                || (!r.embodied && r.blockedTarget.equals(place.id()))) return false;
        long users = ctx.state.life.residents.values().stream().filter(other -> other.personId != r.personId
                && other.siteId.equals(r.siteId) && other.targetId.equals(place.id())
                && !other.phase.equals("waiting")).count();
        if (users >= place.capacity()) return false;
        r.revision = Math.incrementExact(r.revision);
        r.phase = "moving";
        r.activity = activity;
        r.targetId = place.id();
        r.progressTicks = 0;
        r.requiredTicks = travelTicks(ctx, r, place);
        r.reason = reason;
        return true;
    }

    private static long travelTicks(SimContext ctx, LifeResident r, LifePlace p) {
        double dx = (double) r.x - p.x(), dy = (double) r.y - p.y(), dz = (double) r.z - p.z();
        return Math.max(1, (long) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) * rules(ctx).travelTicksPerBlock()));
    }

    private static void working(SimContext ctx, LifeResident r, LifePlace place) {
        r.phase = "working";
        r.progressTicks = 0;
        r.requiredTicks = rules(ctx).duration(r.activity);
        r.x = place.x(); r.y = place.y(); r.z = place.z();
        if (r.blockedTarget.equals(place.id())) r.blockedTarget = "";
        if ("cultivate".equals(r.activity)) {
            int cost = rules(ctx).cultivationCost();
            if (r.coins < cost) {
                fail(ctx, r, "target_blocked", Math.max(0, ctx.state.life.clockTicks));
                r.reason = "cannot_afford_cultivation";
            } else {
                transferToSite(ctx, r, cost);
            }
        }
    }

    private static void complete(SimContext ctx, LifeResident r, long at) {
        if (r.completedRevision == r.revision) throw new IllegalStateException("life_activity_already_completed");
        LifeRules rules = rules(ctx);
        LifeState.Supply supply = supply(ctx, r.siteId, at);
        switch (r.activity) {
            case "gather" -> {
                int units = Math.min(rules.gatherUnits(), Math.min(rules.gatherQuotaPerDay() - supply.gatheredToday,
                        rules.maxInventory() - r.herbs));
                units = Math.max(0, units);
                r.herbs += units;
                supply.gatheredToday += units;
            }
            case "deliver" -> {
                int units = Math.min(r.herbs, Math.min(supply.treasury / rules.herbPrice(),
                        Math.min(rules.maxInventory() - supply.stock, (rules.maxInventory() - r.coins) / rules.herbPrice())));
                r.herbs -= units; supply.stock += units;
                int payment = units * rules.herbPrice();
                supply.treasury -= payment; r.coins += payment;
            }
            case "work" -> {
                boolean useful = r.role.equals("innkeeper");
                if (r.role.equals("herbalist") && supply.stock > 0 && supply.medicine < rules.maxInventory()) {
                    supply.stock--; supply.medicine++; useful = true;
                }
                if (useful) {
                    supply.services = add(supply.services, 1);
                    int wage = Math.min(rules.workWage(), Math.min(supply.treasury, rules.maxInventory() - r.coins));
                    supply.treasury -= wage; r.coins += wage;
                }
            }
            case "rest" -> {
                r.fatigue = Math.max(0, r.fatigue - rules.restRecovery());
                transferToSite(ctx, r, Math.min(r.coins, rules.restCost()));
            }
            case "cultivate", "social" -> { }
            default -> throw new IllegalStateException("unknown_life_activity");
        }
        if (!r.activity.equals("rest")) r.fatigue = Math.min(100, r.fatigue + rules.fatiguePerActivity());
        r.completedRevision = r.revision;
        r.completedActivities = add(r.completedActivities, 1);
        r.previousActivity = r.activity;
        r.previousTarget = r.targetId;
        r.phase = "waiting";
        r.targetId = "";
        r.progressTicks = 0;
        r.requiredTicks = 0;
        r.retryAfter = at;
    }

    private static void transferToSite(SimContext ctx, LifeResident r, int amount) {
        LifeState.Supply supply = ctx.state.life.supplies.get(r.siteId);
        int accepted = Math.min(amount, rules(ctx).maxInventory() - supply.treasury);
        r.coins -= accepted; supply.treasury += accepted;
    }

    private static LifeState.Supply supply(SimContext ctx, String siteId, long at) {
        LifeState.Supply supply = ctx.state.life.supplies.get(siteId);
        long day = at / ctx.state.life.ticksPerDay;
        if (day > supply.quotaDay) {
            supply.quotaDay = day;
            supply.gatheredToday = 0;
        }
        return supply;
    }

    private static void fail(SimContext ctx, LifeResident r, String reason, long at) {
        if (!r.targetId.isEmpty() && !reason.equals("target_unloaded")) r.blockedTarget = r.targetId;
        r.phase = "waiting";
        r.targetId = "";
        r.progressTicks = 0;
        r.requiredTicks = 0;
        r.reason = reason;
        r.retryAfter = add(at, rules(ctx).retryTicks());
    }

    private static boolean knownAvailable(SimContext ctx, String siteId, String placeId) {
        return "available".equals(ctx.state.life.availability.get(LifeState.placeKey(siteId, placeId)));
    }

    private static LifeRules rules(SimContext ctx) { return ctx.data.lifeRules(); }

    private static LifeSite requireSite(SimContext ctx, String id) {
        LifeSite site = ctx.state.life.sites.get(id);
        if (site == null) throw new IllegalArgumentException("unknown_site");
        return site;
    }

    private static LifeResident requireResident(SimContext ctx, int id) {
        LifeResident r = ctx.state.life.residents.get(id);
        if (r == null || !ctx.state.persons.containsKey(id)) throw new IllegalArgumentException("not_living_resident");
        return r;
    }

    private static LifeResident lease(SimContext ctx, int id, long epoch) {
        LifeResident r = ctx.state.life.residents.get(id);
        return r != null && ctx.state.persons.containsKey(id) && r.embodied && r.epoch == epoch ? r : null;
    }

    private static long add(long value, long increment) {
        return increment > Long.MAX_VALUE - value ? Long.MAX_VALUE : value + increment;
    }
}
