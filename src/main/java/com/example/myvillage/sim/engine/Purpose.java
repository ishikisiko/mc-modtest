package com.example.myvillage.sim.engine;

/**
 * Explicit purpose codes for {@link com.example.myvillage.sim.SimRng#at}. Never reuse or renumber a
 * code: each one names one kind of decision, so adding a behaviour does not reshuffle the others.
 */
public final class Purpose {
    private Purpose() {
    }

    // Genesis (day 0): 100..199
    public static final int GENESIS_SECT_REGION = 101;
    public static final int GENESIS_GATE = 102;
    public static final int GENESIS_SECT_NAME = 103;
    public static final int GENESIS_PERSON = 104;
    public static final int GENESIS_TECHNIQUE = 105;
    public static final int GENESIS_MENTOR = 106;
    public static final int GENESIS_FOUNDER = 107;
    public static final int GENESIS_ROGUE = 108;

    // Person, daily: 200..299
    public static final int CULTIVATE = 201;
    public static final int BREAKTHROUGH_ATTEMPT = 202;
    public static final int BREAKTHROUGH_ROLL = 203;
    public static final int BREAKTHROUGH_DEATH = 204;
    public static final int DAO_NAME = 205;
    public static final int TRAVEL = 206;
    public static final int FORTUNE = 207;
    public static final int MEETING = 208;
    public static final int COMBAT = 209;
    public static final int REVENGE = 210;
    public static final int DANGER = 211;
    public static final int SECLUSION = 212;
    public static final int DESERTION = 213;
    public static final int FOUNDING = 214;

    // Sect, yearly: 300..399
    public static final int SECT_RECRUIT_COUNT = 301;
    public static final int SECT_RECRUIT = 302;
    public static final int SECT_MENTOR = 303;
    public static final int SECT_SUCCESSION = 304;
    public static final int SECT_RELATIONS = 305;
    public static final int SECT_WAR = 306;
    public static final int SECT_SPLIT = 307;
    public static final int SECT_DECLINE = 308;

    // World: 400..499
    public static final int ENTRANT = 401;
    public static final int ENTRANT_NAME = 402;
    public static final int SECT_NAME = 403;
    public static final int FOUNDING_GATE = 404;

    // Text: 500..599
    public static final int TEXT_VARIANT = 501;

    // Checkpoint-2 additions (never renumber the ones above)
    public static final int BEAST = 215;
    public static final int FORTUNE_PICK = 216;
    public static final int FORTUNE_EFFECT = 217;
    public static final int MEETING_PARTNER = 218;
    public static final int MEETING_OUTCOME = 219;
    public static final int COMBAT_OUTCOME = 220;
    public static final int REVENGE_SEEK = 221;
    public static final int TRAVEL_MOVE = 222;
    public static final int ROGUE_JOIN = 223;
    public static final int SECT_SCHISM = 309;
    public static final int SECT_BATTLE = 310;
    public static final int SECT_TRUCE = 311;
    public static final int SECT_FOUND_REGION = 312;

    // Heritage additions (0.37.0)
    public static final int GENESIS_HERITAGE = 109;
}
