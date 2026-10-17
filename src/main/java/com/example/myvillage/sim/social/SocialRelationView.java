package com.example.myvillage.sim.social;

import java.util.List;

/** One NPC/player acquaintance, viewed from either participant, with newest memories first. */
public record SocialRelationView(ActorRef observer, ActorRef other, int familiarity,
                                 long firstConversationDay, long lastConversationDay,
                                 List<SocialMemoryView> memories) {
    public SocialRelationView {
        memories = List.copyOf(memories);
    }
}
