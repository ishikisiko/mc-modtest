package com.example.myvillage.network;

public final class FlyingSwordInputFlags {
    public static final int FORWARD = 1;
    public static final int BACKWARD = 1 << 1;
    public static final int LEFT = 1 << 2;
    public static final int RIGHT = 1 << 3;
    public static final int ASCEND = 1 << 4;
    public static final int DESCEND = 1 << 5;
    public static final int ALL = FORWARD | BACKWARD | LEFT | RIGHT | ASCEND | DESCEND;

    private FlyingSwordInputFlags() {
    }

    public static byte pack(
            boolean forward,
            boolean backward,
            boolean left,
            boolean right,
            boolean ascend,
            boolean descend) {
        int flags = 0;
        flags |= forward ? FORWARD : 0;
        flags |= backward ? BACKWARD : 0;
        flags |= left ? LEFT : 0;
        flags |= right ? RIGHT : 0;
        flags |= ascend ? ASCEND : 0;
        flags |= descend ? DESCEND : 0;
        return (byte) flags;
    }

    public static boolean hasOnlyKnownFlags(byte flags) {
        return (Byte.toUnsignedInt(flags) & ~ALL) == 0;
    }

    public static boolean has(byte flags, int flag) {
        return (Byte.toUnsignedInt(flags) & flag) != 0;
    }
}
