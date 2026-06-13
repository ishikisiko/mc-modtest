package com.example.myvillage;

import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resource-pack style mod: ships myvillage structure NBTs and functions as
 * datapack resources. No blocks, items, or worldgen registered yet.
 */
@Mod(MyVillageMod.MOD_ID)
public final class MyVillageMod {
    public static final String MOD_ID = "myvillage";
    private static final Logger LOGGER = LoggerFactory.getLogger(MyVillageMod.class);

    public MyVillageMod() {
        LOGGER.info("MyVillage resource mod loaded");
    }
}
