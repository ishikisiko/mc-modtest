package com.example.myvillage.orchard;

/** A single, saved crop cycle: enrichment can only be purchased at the bud stage. */
public record FruitCycle(int age, boolean enriched) {
    public FruitCycle { if (age<0 || age>2) throw new IllegalArgumentException("Fruit age"); }
    public boolean canEnrich() { return age==0 && !enriched; }
    public FruitCycle enrich() { return canEnrich() ? new FruitCycle(age,true) : this; }
    public FruitCycle grow() { return age<2 ? new FruitCycle(age+1,enriched) : this; }
    public boolean ripe() { return age==2; }
    public FruitCycle afterHarvest() { return ripe() ? new FruitCycle(0,false) : this; }
}
