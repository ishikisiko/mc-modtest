package com.example.myvillage.client.cultivation;

import com.example.myvillage.cultivation.network.MeditationIntentAction;
import com.example.myvillage.cultivation.network.MeditationIntentPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Objects;

final class ClientCultivationIntentSender {
    private ClientCultivationIntentSender() {
    }

    static boolean send(MeditationIntentAction action) {
        Objects.requireNonNull(action, "action");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return false;
        }
        PacketDistributor.sendToServer(new MeditationIntentPayload(action));
        return true;
    }
}
