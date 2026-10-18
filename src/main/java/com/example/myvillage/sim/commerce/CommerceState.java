package com.example.myvillage.sim.commerce;

import java.util.TreeMap;
import java.util.TreeSet;

/** Typed goods and commercial receipts in the SAME world ledger as residents and supplies. */
public final class CommerceState {
    public static final int MAX_MARKETS=256, MAX_ITEMS=128, MAX_COOLDOWNS=4096;
    public final TreeMap<String,Market> markets=new TreeMap<>();
    /** Shared across inns, persistent, using the life calendar rather than /time. */
    public final TreeMap<String,Long> restUntil=new TreeMap<>();
    public static final class Market {
        public long revision;
        public long transactions;
        public final TreeMap<String,Integer> items=new TreeMap<>();
        /** An assigned (later dead/unassigned) shopkeeper is NOT silently recreated. */
        public final TreeSet<String> staffedRoles=new TreeSet<>();
    }
}
