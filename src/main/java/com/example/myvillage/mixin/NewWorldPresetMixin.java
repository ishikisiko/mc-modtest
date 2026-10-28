package com.example.myvillage.mixin;

import com.example.myvillage.worldplan.runtime.NewWorldDefaults;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** openFresh is not used by recreate/load. Apply once, never on tab changes or data-pack reload. */
@Mixin(CreateWorldScreen.class)
public abstract class NewWorldPresetMixin {
    @Inject(method="openFresh", at=@At("TAIL"))
    private static void myvillage$default(Minecraft minecraft, Screen previous, CallbackInfo ci) {
        if (!(minecraft.screen instanceof CreateWorldScreen screen)) return;
        var state = screen.getUiState();
        var registry = state.getSettings().worldgenLoadContext().registryOrThrow(Registries.WORLD_PRESET);
        var key = ResourceKey.create(Registries.WORLD_PRESET, ResourceLocation.parse(NewWorldDefaults.PRESET));
        registry.getHolder(key).ifPresentOrElse(preset -> {
            // setWorldType updates BOTH the selected UI entry and the actual dimension settings.
            state.setWorldType(new WorldCreationUiState.WorldTypeEntry(preset));
            org.slf4j.LoggerFactory.getLogger(CreateWorldScreen.class).info(
                    "MYVILLAGE_NEW_WORLD_DEFAULT preset={} generator={}", NewWorldDefaults.PRESET,
                    state.getSettings().selectedDimensions().overworld().getClass().getName());
        }, () -> org.slf4j.LoggerFactory.getLogger(CreateWorldScreen.class).warn(
                "修仙大陆预设不可用，保留新建界面的原始选择"));
    }
}
