package com.example.myvillage.client.cultivation;

import com.example.myvillage.cultivation.network.CoreTechniqueSwitchPayload;
import com.example.myvillage.cultivation.network.MeditationIntentAction;
import com.example.myvillage.cultivation.network.MeditationIntentPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Objects;

public final class ClientCultivationIntentSender {
    private ClientCultivationIntentSender() {
    }

    public static boolean send(MeditationIntentAction action) {
        Objects.requireNonNull(action, "action");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return false;
        }
        PacketDistributor.sendToServer(new MeditationIntentPayload(action));
        return true;
    }

    /**
     * Asks the server to run another learned core technique (运转此心法). The id is the only data;
     * the server checks it and answers with a new snapshot, or with nothing when it refuses.
     */
    public static boolean sendCoreSwitch(ResourceLocation techniqueId) {
        Objects.requireNonNull(techniqueId, "techniqueId");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return false;
        }
        PacketDistributor.sendToServer(new CoreTechniqueSwitchPayload(techniqueId));
        return true;
    }
}
