package com.example.myvillage.sim.life;

/** Durable identity and shared place. Tombstones retain this binding forever. */
public record StoryActorBinding(String storyId, String actorKey, int personId, String siteId,
        String initialPlaceId, String currentPlaceId, String returnPlaceId, String policy,
        long revision, boolean returned) {
    public StoryActorBinding {
        LifePlace.requireId(storyId);
        LifePlace.requireId(actorKey);
        LifePlace.requireId(siteId);
        LifePlace.requireId(initialPlaceId);
        LifePlace.requireId(currentPlaceId);
        if (!returnPlaceId.isEmpty()) LifePlace.requireId(returnPlaceId);
        if (personId < 1 || revision < 0 || !policy.equals("scripted_only")
                || (returned ? returnPlaceId.isEmpty() || !currentPlaceId.equals(returnPlaceId)
                             : !currentPlaceId.equals(initialPlaceId)))
            throw new IllegalArgumentException("invalid_story_actor_binding");
    }
    public String key() { return key(storyId, actorKey); }
    public static String key(String story, String actor) { return story + "|" + actor; }
}
