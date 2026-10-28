package com.example.myvillage.worldplan.capital;

/** Stable core-region identities. Repeated outer terrain profiles are not additional regions. */
public enum CapitalRegion {
    ZHONGZHOU("zhongzhou"), DONGHAI("donghai"), XIMO("ximo"), NANJIANG("nanjiang"),
    BEIYUAN("beiyuan"), DONGHUANG("donghuang"), LINGYUE("lingyue"), MOYU("moyu");

    private final String id;

    CapitalRegion(String id) { this.id = id; }

    public String id() { return id; }

    public static CapitalRegion fromId(String id) {
        for (CapitalRegion region : values()) if (region.id.equals(id)) return region;
        throw new IllegalArgumentException("未知核心州域: " + id);
    }
}
