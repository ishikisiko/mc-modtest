package com.example.myvillage.sim.runtime.commerce;

import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CommerceMenus {
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,"myvillage");
    public static final Supplier<MenuType<CommerceMenu>> SHOP=MENUS.register("npc_commerce",()->IMenuTypeExtension.create(CommerceMenu::new));
    private CommerceMenus() { }
    public static void register(IEventBus bus){MENUS.register(bus);}
}
