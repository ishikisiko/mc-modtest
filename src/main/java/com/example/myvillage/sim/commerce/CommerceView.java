package com.example.myvillage.sim.commerce;

import java.util.Map;
import java.util.Set;

public record CommerceView(String siteId,long revision,long transactions,int treasury,
        Map<String,Integer> items,Set<String> staffedRoles) {
    public CommerceView { items=Map.copyOf(items); staffedRoles=Set.copyOf(staffedRoles); }
}
