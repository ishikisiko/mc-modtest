package com.example.myvillage.sim.profession;
import java.util.List;
import java.util.Map;

public record ProfessionSupplyView(String siteId, Map<String,Integer> herbs, int legacyStock, List<GardenView> gardens) {
    public ProfessionSupplyView { herbs = Map.copyOf(herbs); gardens = List.copyOf(gardens); }
    public record GardenView(String placeId, String herb, long plantedAt, long matureAt, boolean tended) { }
}
