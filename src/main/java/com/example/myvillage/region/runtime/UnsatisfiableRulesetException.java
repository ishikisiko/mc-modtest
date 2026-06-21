package com.example.myvillage.region.runtime;

/**
 * Raised when the authored ruleset/catalog cannot yield a legal region graph.
 * Java mirror of {@code UnsatisfiableRuleset} in
 * {@code tools/buildgen/region_topology.py}.
 *
 * <p>Generation is constructive (no re-roll), so this only fires on bad
 * <em>input</em>, never mid-generation — it is the explicit report the design
 * requires instead of looping.
 */
public final class UnsatisfiableRulesetException extends RuntimeException {
    public UnsatisfiableRulesetException(String message) {
        super(message);
    }
}
