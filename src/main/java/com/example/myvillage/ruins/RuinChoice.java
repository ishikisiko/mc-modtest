package com.example.myvillage.ruins;

import java.util.UUID;

/** A shared, once-only site choice. Pending confirmation is player-bound and never saved. */
public final class RuinChoice {
    public enum Choice { CHARGED, GARDEN, VAULT, EXHAUSTED }
    private Choice choice;
    private UUID pendingPlayer;
    private Choice pendingChoice;
    private long pendingTick;

    public RuinChoice(String saved) {
        try { choice = Choice.valueOf(saved); }
        catch (IllegalArgumentException | NullPointerException e) { choice = Choice.EXHAUSTED; }
    }
    public Choice choice() { return choice; }
    public boolean charged() { return choice == Choice.CHARGED; }
    public boolean confirm(UUID player, Choice requested, long tick, int window) {
        if (!charged() || player == null || (requested != Choice.GARDEN && requested != Choice.VAULT)) return false;
        if (player.equals(pendingPlayer) && requested == pendingChoice && tick > pendingTick
                && tick - pendingTick <= window) {
            pendingPlayer = null; pendingChoice = null;
            return true;
        }
        pendingPlayer = player; pendingChoice = requested; pendingTick = tick;
        return false;
    }
    public boolean commit(Choice requested) {
        if (!charged() || (requested != Choice.GARDEN && requested != Choice.VAULT)) return false;
        choice = requested; pendingPlayer = null; pendingChoice = null;
        return true;
    }
}
