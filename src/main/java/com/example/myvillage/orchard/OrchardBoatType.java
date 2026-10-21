package com.example.myvillage.orchard;

import java.util.function.Supplier;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;

/** Kept separate: enum initialization must not initialize deferred content too early. */
public final class OrchardBoatType {
    public static final EnumProxy<Boat.Type> NINGYUAN=new EnumProxy<>(Boat.Type.class,
            (Supplier<Block>)()->OrchardContent.PLANKS.get(),"myvillage:ningyuan",
            (Supplier<Item>)()->OrchardContent.BOAT.get(),(Supplier<Item>)()->OrchardContent.CHEST_BOAT.get(),
            (Supplier<Item>)()->Items.STICK,false);
    private OrchardBoatType() { }
}
