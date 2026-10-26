package com.example.myvillage.sim.profession;
import java.util.List;
import java.util.Map;

public record ProfessionSupplyView(String siteId, Map<String,Integer> herbs, int legacyStock, List<GardenView> gardens) {
    public ProfessionSupplyView { herbs = Map.copyOf(herbs); gardens = List.copyOf(gardens); }
    public record GardenView(String placeId, String herb, long plantedAt, long matureAt, boolean tended,
            boolean physical, int cropX, int cropY, int cropZ, int observedAge, long harvestSerial) {
        public GardenView(String placeId, String herb, long plantedAt, long matureAt, boolean tended) {
            this(placeId, herb, plantedAt, matureAt, tended, false, 0, 0, 0, -2, 0);
        }
    }
}
