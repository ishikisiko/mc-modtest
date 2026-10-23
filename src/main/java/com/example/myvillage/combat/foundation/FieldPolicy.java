package com.example.myvillage.combat.foundation;

/** 与实体/事件无关的资格规则，供服务器适配和单元测试共同使用。 */
public final class FieldPolicy {
    private FieldPolicy() {}

    public static boolean allied(boolean self, boolean teamAlly, int sourceSect, int targetSect) {
        return self || teamAlly || sourceSect >= 0 && sourceSect == targetSect;
    }

    public static boolean permits(boolean friendly, boolean self, boolean allied, boolean mayHarm,
                                  boolean targetPlayer, boolean pvp, boolean mutualPvp,
                                  boolean creative, boolean alive, boolean spectator) {
        if (!alive || spectator) return false;
        if (friendly) return allied;
        return !self && !allied && !creative && mayHarm && (!targetPlayer || pvp && mutualPvp);
    }

    public static boolean alive(long now, long expiresAt, boolean online, boolean living,
                                boolean sameDimension, boolean centerLoaded) {
        return now < expiresAt && online && living && sameDimension && centerLoaded;
    }
}
