package com.example.myvillage.sim.social;

/**
 * Read-only actor identity. For NPCs alive is the ledger's life state; for registered players it
 * only means that the social identity exists, never that the player is online or physically alive.
 */
public record ActorView(ActorRef ref, String name, boolean alive, int sectId) {
}
