package com.example.myvillage.arrival;

/** Server-resolved identity. An empty town never invents a village name or a boundary. */
public record ArrivalLocation(String regionId, String regionName, String townId, String townName) {
    public static final int MAX_TEXT = 64;
    public static final int MAX_ID = 128;
    public static final ArrivalLocation EMPTY = new ArrivalLocation("", "", "", "");
    public ArrivalLocation {
        id(regionId); id(townId); text(regionName, true); text(townName, true);
        if (regionId.isEmpty() != regionName.isEmpty() || townId.isEmpty() != townName.isEmpty()
                || regionId.isEmpty() && !townId.isEmpty())
            throw new IllegalArgumentException("Incomplete arrival identity");
    }
    private static void id(String value) {
        if (value == null || value.length() > MAX_ID || !value.matches("[a-z0-9_./:-]*"))
            throw new IllegalArgumentException("Invalid arrival identity");
    }
    static void text(String value, boolean allowEmpty) {
        if (value == null || value.length() > MAX_TEXT || value.isBlank() && (!allowEmpty || !value.isEmpty())
                || value.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0x00a7))
            throw new IllegalArgumentException("Invalid arrival text");
    }
}
