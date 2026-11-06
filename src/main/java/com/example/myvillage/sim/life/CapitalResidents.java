package com.example.myvillage.sim.life;

import com.example.myvillage.sim.SimFormatException;
import com.example.myvillage.sim.engine.SimContext;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.WorldState;
import java.util.*;

/** A single atomic reservation for the spawn capital, independent of loaded shops or vacancies. */
public final class CapitalResidents {
    private CapitalResidents() {}
    public static final Set<String> ROLES = Set.of("guide", "innkeeper", "herbalist", "smith");
    public static String key(String site, String role) { return site + "|" + role; }

    public static boolean ensure(SimContext ctx, String siteId, List<CapitalResidentDefinition> definitions) {
        var life = ctx.state.life;
        var site = life.sites.get(siteId);
        if (site == null || definitions.size() != ROLES.size()
                || !new HashSet<>(definitions.stream().map(CapitalResidentDefinition::role).toList()).equals(ROLES))
            throw new IllegalArgumentException("invalid_capital_roster");
        long bound = definitions.stream().filter(d -> life.capitalResidents.containsKey(key(siteId, d.role()))).count();
        if (bound == definitions.size()) return false; // A vacancy/death/unassignment never creates another person.
        if (bound != 0) throw new IllegalStateException("partial_capital_roster");
        var chosen = new LinkedHashMap<CapitalResidentDefinition, Integer>();
        var homes = new HashMap<String, Long>();
        int additions = 0;
        for (var d : definitions) {
            var home = site.place(d.home()).orElseThrow(() -> new IllegalArgumentException("capital_home_missing"));
            var work = site.place(d.work()).orElseThrow(() -> new IllegalArgumentException("capital_work_missing"));
            if (!home.kind().equals("rest") || !(d.role().equals("guide")
                    ? Set.of("social", "entry").contains(work.kind()) : work.kind().equals("work")))
                throw new IllegalArgumentException("capital_place_kind");
            long dx = (long)home.x() - work.x(), dy = (long)home.y() - work.y(), dz = (long)home.z() - work.z();
            if (dx*dx + dy*dy + dz*dz > (long)ctx.data.lifeRules().maxPathDistance()*ctx.data.lifeRules().maxPathDistance())
                throw new IllegalArgumentException("capital_home_work_distance");
            var existing = life.residents.values().stream().filter(r -> r.siteId.equals(siteId) && r.role.equals(d.role())).toList();
            if (existing.size() > 1) throw new IllegalArgumentException("duplicate_capital_role");
            if (!existing.isEmpty()) { chosen.put(d, existing.getFirst().personId); continue; }
            if (d.role().equals("guide") && life.guideAssignments.containsKey(siteId)) {
                chosen.put(d, life.guideAssignments.get(siteId)); continue;
            }
            var market = ctx.state.commerce.markets.get(siteId);
            if (market != null && market.staffedRoles.contains(d.role()))
                throw new IllegalArgumentException("capital_role_previously_vacated");
            int realm = ctx.realms.indexOf(d.realm());
            if (realm < 0 || d.stage() > ctx.realms.get(realm).lastStage()) throw new IllegalArgumentException("capital_realm");
            Math.subtractExact(ctx.day(), Math.multiplyExact((long)d.ageYears(), ctx.dpy));
            long occupancy = homes.computeIfAbsent(home.id(), h -> life.residents.values().stream()
                    .filter(r -> r.siteId.equals(siteId) && r.homeId.equals(h)).count());
            if (occupancy >= home.capacity()) throw new IllegalArgumentException("capital_home_full");
            homes.put(home.id(), occupancy + 1);
            chosen.put(d, -1); additions++;
        }
        long residents = life.residents.values().stream().filter(r -> r.siteId.equals(siteId)).count();
        if (residents + additions > ctx.data.lifeRules().maxResidentsPerSite()) throw new IllegalArgumentException("capital_resident_limit");
        int end = Math.addExact(ctx.state.nextPersonId, additions);
        for (int id = ctx.state.nextPersonId; id < end; id++)
            if (id < 1 || ctx.state.persons.containsKey(id) || ctx.state.tombstones.containsKey(id))
                throw new IllegalStateException("capital_person_collision");
        var adopted = new HashSet<Integer>();
        for (var entry : chosen.entrySet()) {
            int id = entry.getValue(); if (id < 0) continue;
            if (!adopted.add(id) || id < 1 || id >= ctx.state.nextPersonId
                    || ctx.state.persons.containsKey(id) == ctx.state.tombstones.containsKey(id))
                throw new IllegalStateException("capital_adopted_identity_missing");
            var resident = life.residents.get(id);
            if (resident != null && (!resident.siteId.equals(siteId) || !resident.role.equals(entry.getKey().role())))
                throw new IllegalStateException("capital_adopted_identity_conflict");
        }
        // All checks precede writes. No availability or chunk-loading observation can interrupt this transaction.
        for (var entry : chosen.entrySet()) {
            var d = entry.getKey(); int id = entry.getValue();
            if (id < 0) {
                var p = new Person(); p.id = ctx.state.nextPersonId++;
                p.surname = d.surname(); p.given = d.given(); p.gender = d.gender();
                p.birthDay = ctx.day() - (long)d.ageYears()*ctx.dpy;
                p.root = d.root().stream().mapToInt(Integer::intValue).toArray();
                p.realm = ctx.realms.indexOf(d.realm()); p.stage = d.stage();
                p.regionId = site.regionId(); p.homeRegionId = site.regionId(); p.joinedDay = ctx.day();
                p.ambition = d.traits().get(0); p.aggression = d.traits().get(1); p.caution = d.traits().get(2);
                p.wanderlust = d.traits().get(3); p.loyalty = d.traits().get(4);
                com.example.myvillage.sim.engine.NpcCultivation.initialize(ctx, p);
                ctx.state.persons.put(p.id, p); ctx.usedNames().add(p.name());
                LifeEngine.assign(ctx, p.id, siteId, d.role(), d.home(), d.work());
                var at = site.place(LifeEngine.merchantDaytime(ctx) ? d.work() : d.home()).orElseThrow();
                var resident = life.residents.get(p.id); resident.x = at.x(); resident.y = at.y(); resident.z = at.z();
                id = p.id;
            }
            life.capitalResidents.put(key(siteId, d.role()), id);
        }
        ctx.membershipChanged();
        return true;
    }

    public static void validate(WorldState state) {
        var ids = new HashSet<Integer>(); var sites = new TreeMap<String, Set<String>>();
        for (var e : state.life.capitalResidents.entrySet()) {
            int split = e.getKey().lastIndexOf('|');
            String site = split < 1 ? "" : e.getKey().substring(0, split);
            String role = split < 1 ? "" : e.getKey().substring(split + 1);
            int id = e.getValue();
            if (!state.life.sites.containsKey(site) || !ROLES.contains(role) || !ids.add(id)
                    || id < 1 || id >= state.nextPersonId
                    || state.persons.containsKey(id) == state.tombstones.containsKey(id))
                throw new SimFormatException("life: invalid capital resident " + e.getKey());
            var r = state.life.residents.get(id);
            if (r != null && (!r.siteId.equals(site) || !r.role.equals(role)))
                throw new SimFormatException("life: conflicting capital resident " + e.getKey());
            sites.computeIfAbsent(site, k -> new HashSet<>()).add(role);
        }
        if (sites.values().stream().anyMatch(s -> !s.equals(ROLES))) throw new SimFormatException("life: partial capital roster");
    }
}
