package com.example.myvillage.sim.life;

import com.example.myvillage.sim.SimFormatException;
import com.example.myvillage.sim.engine.SimContext;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.WorldState;
import java.util.HashSet;
import java.util.Optional;

/** Pure ledger transactions, exposed to runtime exclusively by WorldSim. */
public final class StoryActors {
    private StoryActors() {}

    public static Optional<StoryActorBinding> forPerson(WorldState world, int id) {
        return world.life.storyActors.values().stream().filter(b -> b.personId() == id).findFirst();
    }

    public static boolean protectedPerson(WorldState world, int id) {
        return forPerson(world, id).filter(b -> b.policy().equals("scripted_only")).isPresent();
    }

    /** Read-only preflight; true means a new cast would be created, false means already bound. */
    public static boolean preflight(SimContext ctx, StoryCastDefinition cast) {
        var life = ctx.state.life;
        var site = cast.site();
        var oldSite = life.sites.get(site.id());
        if (oldSite != null && !oldSite.equals(site)) throw new IllegalArgumentException("story_site_already_bound");
        if (!ctx.hasRegion(site.regionId())) throw new IllegalArgumentException("unknown_story_region");
        if (oldSite == null && life.sites.size() >= ctx.data.lifeRules().maxSites())
            throw new IllegalArgumentException("story_site_limit");
        if (site.places().size() > ctx.data.lifeRules().maxPlacesPerSite())
            throw new IllegalArgumentException("story_place_limit");
        int existing = 0;
        for (var def : cast.actors()) {
            int realm = ctx.realms.indexOf(def.realm());
            if (realm < 0 || def.stage() > ctx.realms.get(realm).lastStage())
                throw new IllegalArgumentException("invalid_story_realm");
            Math.subtractExact(ctx.day(), Math.multiplyExact((long) def.ageYears(), ctx.dpy));
            var binding = life.storyActors.get(StoryActorBinding.key(cast.storyId(), def.key()));
            if (binding == null) continue;
            existing++;
            if (!binding.siteId().equals(site.id()) || !binding.initialPlaceId().equals(def.initialPlaceId())
                    || !binding.returnPlaceId().equals(def.returnPlaceId()) || !binding.policy().equals(def.policy()))
                throw new IllegalArgumentException("story_definition_changed:" + def.key());
            var person = ctx.state.persons.get(binding.personId());
            if (person == null) {
                if (!ctx.state.tombstones.containsKey(binding.personId())) throw new IllegalStateException("story_identity_missing");
                continue; // A real tombstone is a final identity, never an invitation to create a replacement.
            }
            var resident = life.residents.get(person.id);
            if (resident == null || !resident.siteId.equals(site.id()) || !resident.role.equals(def.role())
                    || !resident.homeId.equals(def.homeId()) || !resident.workId.equals(binding.currentPlaceId())
                    || !person.name().equals(def.surname() + def.given()) || !person.gender.equals(def.gender())
                    || person.realm != realm || person.stage != def.stage())
                throw new IllegalStateException("story_identity_conflict:" + def.key());
        }
        long boundCount = life.storyActors.values().stream().filter(b -> b.storyId().equals(cast.storyId())).count();
        if (boundCount != existing || existing != 0 && existing != cast.actors().size())
            throw new IllegalStateException("partial_story_cast");
        if (existing == cast.actors().size()) return false;
        long residentCount = life.residents.values().stream().filter(r -> r.siteId.equals(site.id())).count();
        if (residentCount + cast.actors().size() > ctx.data.lifeRules().maxResidentsPerSite())
            throw new IllegalArgumentException("story_resident_limit");
        int next = Math.addExact(ctx.state.nextPersonId, cast.actors().size());
        if (ctx.state.nextPersonId < 1) throw new IllegalStateException("invalid_next_person_id");
        for (int id = ctx.state.nextPersonId; id < next; id++)
            if (ctx.state.persons.containsKey(id) || ctx.state.tombstones.containsKey(id))
                throw new IllegalStateException("story_person_id_collision");
        for (var def : cast.actors()) {
            long occupants = life.residents.values().stream().filter(r -> r.siteId.equals(site.id())
                    && r.homeId.equals(def.homeId())).count();
            if (occupants >= site.place(def.homeId()).orElseThrow().capacity())
                throw new IllegalArgumentException("story_home_full");
            if (def.role().equals("guide") && life.guideAssignments.containsKey(site.id()))
                throw new IllegalArgumentException("story_guide_conflict");
        }
        return true;
    }

    /** Revalidate before any write; repeated binding neither heals nor resets nor resurrects. */
    public static boolean ensure(SimContext ctx, StoryCastDefinition cast) {
        if (!preflight(ctx, cast)) return false;
        var life = ctx.state.life;
        var site = cast.site();
        LifeEngine.registerSite(ctx, site);
        for (var def : cast.actors()) {
            Person p = new Person();
            p.id = ctx.state.nextPersonId++;
            p.surname = def.surname(); p.given = def.given(); p.gender = def.gender();
            p.birthDay = ctx.day() - (long) def.ageYears() * ctx.dpy;
            p.root = def.root().stream().mapToInt(Integer::intValue).toArray();
            p.realm = ctx.realms.indexOf(def.realm()); p.stage = def.stage();
            p.regionId = site.regionId(); p.homeRegionId = site.regionId(); p.joinedDay = ctx.day();
            p.ambition = def.traits().get(0); p.aggression = def.traits().get(1);
            p.caution = def.traits().get(2); p.wanderlust = def.traits().get(3); p.loyalty = def.traits().get(4);
            ctx.state.persons.put(p.id, p);
            ctx.usedNames().add(p.name());
            LifeResident r = new LifeResident();
            r.personId = p.id; r.siteId = site.id(); r.role = def.role(); r.homeId = def.homeId();
            r.workId = def.initialPlaceId();
            var place = site.place(r.workId).orElseThrow();
            r.x = place.x(); r.y = place.y(); r.z = place.z(); r.reason = "story_reception";
            life.residents.put(p.id, r);
            if (r.role.equals("guide")) life.guideAssignments.put(site.id(), p.id);
            var binding = new StoryActorBinding(cast.storyId(), def.key(), p.id, site.id(),
                    def.initialPlaceId(), def.initialPlaceId(), def.returnPlaceId(), def.policy(), 0, false);
            life.storyActors.put(binding.key(), binding);
        }
        ctx.membershipChanged();
        return true;
    }

    /** The caller must first withdraw the shared body, releasing BOTH life and combat ownership. */
    public static boolean returnActor(SimContext ctx, String story, String actor, long expectedRevision) {
        var key = StoryActorBinding.key(story, actor);
        var b = ctx.state.life.storyActors.get(key);
        if (b == null || !ctx.state.persons.containsKey(b.personId())) return false;
        if (b.returned()) return true;
        var r = ctx.state.life.residents.get(b.personId());
        var profile = ctx.state.combat.profiles.get(b.personId());
        if (b.revision() != expectedRevision || b.returnPlaceId().isEmpty() || r == null || r.embodied
                || ctx.state.combat.leases.containsKey(b.personId()) || ctx.state.combat.engaged.contains(b.personId())
                || profile != null && profile.body() != null && (profile.body().unresolved() || profile.body().fallDistance() > 0)) return false;
        var place = ctx.state.life.sites.get(b.siteId()).place(b.returnPlaceId()).orElseThrow();
        long revision = Math.incrementExact(r.revision);
        long nextEpoch = Math.incrementExact(ctx.state.life.nextEpoch);
        var moved = new StoryActorBinding(b.storyId(), b.actorKey(), b.personId(), b.siteId(), b.initialPlaceId(),
                place.id(), b.returnPlaceId(), b.policy(), Math.incrementExact(b.revision()), true);
        // All throwing validation completed. Old life and combat tokens can never commit hereafter.
        r.epoch = ctx.state.life.nextEpoch; ctx.state.life.nextEpoch = nextEpoch;
        r.revision = revision; r.workId = place.id(); r.x = place.x(); r.y = place.y(); r.z = place.z();
        r.phase = "waiting"; r.activity = "social"; r.targetId = ""; r.progressTicks = 0;
        r.requiredTicks = 0; r.pauseTicks = 0; r.retryAfter = 0; r.blockedTarget = "";
        r.previousActivity = ""; r.previousTarget = ""; r.reason = "story_returned";
        ctx.state.life.storyActors.put(key, moved);
        return true;
    }

    public static void validate(WorldState world) {
        var people = new HashSet<Integer>();
        for (var entry : world.life.storyActors.entrySet()) {
            var b = entry.getValue();
            var site = world.life.sites.get(b.siteId());
            var r = world.life.residents.get(b.personId());
            boolean alive = world.persons.containsKey(b.personId()), dead = world.tombstones.containsKey(b.personId());
            if (!entry.getKey().equals(b.key()) || !people.add(b.personId()) || world.nextPersonId <= b.personId()
                    || alive == dead || site == null
                    || site.place(b.initialPlaceId()).filter(p -> p.kind().equals("social")).isEmpty()
                    || site.place(b.currentPlaceId()).filter(p -> p.kind().equals("social")).isEmpty()
                    || (!b.returnPlaceId().isEmpty() && site.place(b.returnPlaceId()).filter(p -> p.kind().equals("social")).isEmpty())
                    || alive && (r == null || !r.siteId.equals(b.siteId()) || !r.workId.equals(b.currentPlaceId())))
                throw new SimFormatException("life: invalid story identity " + entry.getKey());
        }
    }
}
