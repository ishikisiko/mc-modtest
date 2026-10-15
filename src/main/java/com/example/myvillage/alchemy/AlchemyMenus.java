package com.example.myvillage.alchemy;

import com.example.myvillage.MyVillageMod;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AlchemyMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MyVillageMod.MOD_ID);
    public static final Supplier<MenuType<AlchemyMenu>> FURNACE = MENUS.register("alchemy_furnace",
            () -> new MenuType<>(AlchemyMenu::new, FeatureFlags.DEFAULT_FLAGS));
    private AlchemyMenus() {}
    public static void register(IEventBus bus) { MENUS.register(bus); }
}
