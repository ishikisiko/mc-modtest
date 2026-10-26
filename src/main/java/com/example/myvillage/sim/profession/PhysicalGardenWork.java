package com.example.myvillage.sim.profession;

/** One use receipt proposal for an observed crop. Runtime must verify the actual block in its callback. */
public record PhysicalGardenWork(int personId, long epoch, long revision, String siteId, String placeId,
        String herb, int cropX, int cropY, int cropZ, String activity, int expectedAge, long harvestSerial) { }
