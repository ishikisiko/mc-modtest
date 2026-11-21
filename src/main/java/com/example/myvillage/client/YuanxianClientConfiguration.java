package com.example.myvillage.client;

import com.example.myvillage.ModBrand;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.Util;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = ModBrand.NAMESPACE, dist = Dist.CLIENT)
public final class YuanxianClientConfiguration {
    private WeakReference<ConfigurationScreen> settingsScreen = new WeakReference<>(null);

    public YuanxianClientConfiguration(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> {
            var screen = new ConfigurationScreen(mod, parent);
            settingsScreen = new WeakReference<>(screen);
            return screen;
        });
        NeoForge.EVENT_BUS.addListener(this::addDropsFolder);
    }

    private void addDropsFolder(ScreenEvent.Init.Post event) {
        if (event.getScreen() != settingsScreen.get()) {
            return;
        }

        for (var widget : event.getListenersList()) {
            if (widget instanceof OptionsList list) {
                list.addSmall(Button.builder(Component.translatable("yuanxian.configuration.drops.folder"),
                        button -> openDropsFolder())
                        .width(ConfigurationScreen.BIG_BUTTON_WIDTH)
                        .tooltip(Tooltip.create(Component.translatable("yuanxian.configuration.drops.folder.tooltip")))
                        .build(), null);
                return;
            }
        }
    }

    private static void openDropsFolder() {
        Path config = FMLPaths.CONFIGDIR.get();
        Path drops = config.resolve(ModBrand.NAMESPACE);
        Util.getPlatform().openFile((Files.isDirectory(drops) ? drops : config).toFile());
    }
}
