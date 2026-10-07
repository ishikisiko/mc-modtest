package com.example.myvillage.sim.runtime.player;

import com.example.myvillage.sim.PlayerMemberView;
import com.example.myvillage.sim.SimEvent;

/**
 * Which chronicle events a player hears as sect news (宗门消息, sect entry slice 4): told in chat on
 * the settled day wherever the player is ({@code message.myvillage.world.sect.news}). Pure, so it
 * is unit-tested; {@link WorldSimPlayers} sends the lines.
 */
public final class SectNews {
    /** The chat line of one news event: "【宗门】" and the event's own line. */
    public static final String NEWS_KEY = "message.myvillage.world.sect.news";
    /** Least importance of a news event (notable and major). */
    public static final int MIN_IMPORTANCE = 2;
    /** Event types about sects ({@code sect_destroyed}, {@code sect_split}, ...) start with this. */
    static final String SECT_TYPE_PREFIX = "sect";

    private SectNews() {
    }

    /**
     * Whether {@code e} is news for {@code me}: notable or major, not a {@code player_*} event (those
     * reach the player they name on their own), and either about the player's sect or, for a player
     * now in no sect, a {@code sect*} event about the sect they last left (the day it is destroyed
     * they have just become rogues).
     */
    public static boolean relevant(SimEvent e, PlayerMemberView me) {
        if (e.importance() < MIN_IMPORTANCE || e.type().startsWith(WorldSimPlayers.PLAYER_EVENT_PREFIX)) {
            return false;
        }
        if (me.inSect()) {
            return e.sects().contains(me.sectId());
        }
        return me.leftSectId() >= 0 && e.sects().contains(me.leftSectId()) && e.type().startsWith(SECT_TYPE_PREFIX);
    }
}
