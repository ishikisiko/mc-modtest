package com.example.myvillage.worldplan.capital;

/** A versioned asset-set identity, distinct from a city's persistent ledger identity and level. */
public record CapitalLayoutIdentity(String layoutId, String sha256) {
    public CapitalLayoutIdentity {
        CapitalJson.id(layoutId);
        if (sha256 == null || !sha256.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("冻结城市布局必须携带真实资源集合的 SHA-256");
    }
}
