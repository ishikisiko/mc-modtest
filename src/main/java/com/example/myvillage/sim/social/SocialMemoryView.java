package com.example.myvillage.sim.social;

/** One remembered interaction; display text is resolved by the caller's language resources. */
public record SocialMemoryView(ActorRef other, String kind, long day) {
    public static final String CONVERSATION = "conversation";
}
