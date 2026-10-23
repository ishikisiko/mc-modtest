package com.example.myvillage.sim.life;

import java.util.HashSet;
import java.util.List;

public record StoryCastDefinition(String storyId, LifeSite site, List<StoryActorDefinition> actors) {
    public StoryCastDefinition {
        LifePlace.requireId(storyId);
        actors = List.copyOf(actors);
        if (actors.isEmpty() || actors.size() > 32) throw new IllegalArgumentException("invalid_story_cast_size");
        var keys = new HashSet<String>();
        var homes = new HashSet<String>();
        var names = new HashSet<String>();
        var receptions = new HashSet<String>();
        long guides = actors.stream().filter(actor -> actor.role().equals("guide")).count();
        if (guides > 1) throw new IllegalArgumentException("duplicate_story_guide");
        for (var actor : actors) {
            if (!keys.add(actor.key()) || !homes.add(actor.homeId()) || !names.add(actor.surname() + actor.given()))
                throw new IllegalArgumentException("duplicate_story_actor");
            if (!receptions.add(actor.initialPlaceId())
                    || !actor.returnPlaceId().isEmpty() && !receptions.add(actor.returnPlaceId()))
                throw new IllegalArgumentException("duplicate_story_reception");
            if (site.place(actor.homeId()).filter(p -> p.kind().equals("rest")).isEmpty()
                    || site.place(actor.initialPlaceId()).filter(p -> p.kind().equals("social")).isEmpty()
                    || (!actor.returnPlaceId().isEmpty() && site.place(actor.returnPlaceId())
                        .filter(p -> p.kind().equals("social")).isEmpty()))
                throw new IllegalArgumentException("invalid_story_actor_place");
        }
    }
}
