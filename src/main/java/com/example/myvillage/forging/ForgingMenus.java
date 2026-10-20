package com.example.myvillage.forging;

import com.example.myvillage.MyVillageMod;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ForgingMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MyVillageMod.MOD_ID);
    public static final Supplier<MenuType<ForgingMenu>> TABLE = MENUS.register("forging_table",
            () -> new MenuType<>(ForgingMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final Supplier<MenuType<CopperInfusionMenu>> INFUSION = MENUS.register("copper_infusion",
            () -> new MenuType<>(CopperInfusionMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final Supplier<MenuType<WorkshopMenu>> WORKSHOP = MENUS.register("forging_workshop",
            () -> new MenuType<>(WorkshopMenu::new, FeatureFlags.DEFAULT_FLAGS));
    private ForgingMenus() {}
    public static void register(IEventBus bus) { MENUS.register(bus); }
}
