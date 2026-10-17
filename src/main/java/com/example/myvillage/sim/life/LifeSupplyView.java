package com.example.myvillage.sim.life;

/** Abstract settlement supplies. They are not player ItemStacks or harvested crop blocks. */
public record LifeSupplyView(int stock, int medicine, int treasury, int gatheredToday, long services) { }
