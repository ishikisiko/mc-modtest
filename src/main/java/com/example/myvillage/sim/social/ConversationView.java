package com.example.myvillage.sim.social;

import java.util.List;

/** Facts a dialogue may use. Reading this view does not count as having had a conversation. */
public record ConversationView(int personId, String playerId, String npcName, String playerName,
                               int familiarity, boolean metBefore, long lastConversationDay,
                               List<SocialMemoryView> memories) {
    public ConversationView {
        memories = List.copyOf(memories);
    }
}
