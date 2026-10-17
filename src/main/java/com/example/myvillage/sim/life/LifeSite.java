package com.example.myvillage.sim.life;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** Immutable settlement binding. Registration never places blocks or creates people. */
public record LifeSite(String id, String name, String dimension, String regionId,
                       int anchorX, int anchorY, int anchorZ, List<LifePlace> places) {
    public LifeSite {
        LifePlace.requireId(id);
        LifePlace.requireId(regionId);
        if (name == null || name.isBlank() || name.length() > 128) throw new IllegalArgumentException("Invalid site name");
        if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid dimension");
        LifePlace.requirePosition(anchorX, anchorY, anchorZ);
        places = List.copyOf(places);
        if (places.size() > 256) throw new IllegalArgumentException("Too many places");
        var ids = new HashSet<String>();
        for (LifePlace p : places) if (!ids.add(p.id())) throw new IllegalArgumentException("Duplicate place: " + p.id());
    }

    public Optional<LifePlace> place(String placeId) {
        return places.stream().filter(p -> p.id().equals(placeId)).findFirst();
    }
}
