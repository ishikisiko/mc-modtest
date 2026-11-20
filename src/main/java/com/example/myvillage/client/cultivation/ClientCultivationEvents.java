package com.example.myvillage.client.cultivation;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.client.sim.ClientWorldSimState;
import com.example.myvillage.cultivation.network.CultivationSnapshotReceiver;
import com.example.myvillage.cultivation.network.CultivationTimeSnapshotReceiver;
import com.example.myvillage.cultivation.network.MeditationStatusReceiver;
import com.example.myvillage.cultivation.network.MeditationIntentAction;
import com.example.myvillage.cultivation.meditation.MeditationStatus;
import com.example.myvillage.cultivation.meditation.MeditationStopReason;
import com.example.myvillage.cultivation.nascent.NascentTrial;
import com.example.myvillage.cultivation.network.SpiritTrialIntentPayload;
import com.example.myvillage.cultivation.network.SpiritTrialStatusPayload;
import com.example.myvillage.cultivation.tribulation.SpiritTrial;
import net.neoforged.neoforge.network.PacketDistributor;
import com.example.myvillage.cultivation.network.NascentStatusPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.Locale;

@EventBusSubscriber(
        modid = MyVillageMod.MOD_ID,
        value = Dist.CLIENT)
public final class ClientCultivationEvents {
    private static NascentStatusPayload nascent = new NascentStatusPayload(NascentTrial.Phase.INACTIVE, 0);

    private static SpiritTrialStatusPayload spirit = SpiritTrialStatusPayload.IDLE;

    static {
        SpiritTrialStatusPayload.installReceiver(value -> spirit = value);
        NascentStatusPayload.installReceiver(value -> nascent = value);
        CultivationSnapshotReceiver.install(ClientCultivationState::replace);
        CultivationTimeSnapshotReceiver.install(ClientCultivationState::replaceTime);
        MeditationStatusReceiver.install(ClientCultivationEvents::receiveMeditationStatus);
        com.example.myvillage.cultivation.network.AwakeningScreenReceiver.install(ClientAwakening::open);
    }

    /** Eagerly installs owning-client receivers from the actual client mod entry point. */
    public static void initialize() { }

    public static NascentStatusPayload nascent() { return nascent; }
    public static boolean spiritTrialActive() { return spirit.active(); }

    public static boolean opensBreakthroughChoices() {
        var level = Minecraft.getInstance().level;
        var profile = ClientCultivationState.latest().orElse(null);
        return level != null && profile != null && !spirit.active() && nascent.phase() == NascentTrial.Phase.INACTIVE
                && !com.example.myvillage.cultivation.CultivationRecovery.canRestore(level.registryAccess(), profile)
                && (NascentTrial.source(profile) || SpiritTrial.canChoose(level.registryAccess(), profile)
                    || com.example.myvillage.cultivation.tribulation.TribulationKind.canChoose(profile));
    }

    private ClientCultivationEvents() {
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        nascent = new NascentStatusPayload(NascentTrial.Phase.INACTIVE, 0);
        spirit = SpiritTrialStatusPayload.IDLE;
        ClientCultivationState.clear();
        ClientAwakening.clear();
        ClientWorldSimState.clear();
        com.example.myvillage.client.atlas.AtlasTextures.clear();
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (ClientCultivationKeyMappings.OPEN_PROFILE.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new CultivationProfileScreen());
            }
        }
        consumeIntent(
                minecraft,
                ClientCultivationKeyMappings.START_NORMAL_MEDITATION,
                MeditationIntentAction.START_NORMAL);
        consumeIntent(
                minecraft,
                ClientCultivationKeyMappings.START_SPIRIT_MEDITATION,
                MeditationIntentAction.START_SPIRIT);
        consumeIntent(
                minecraft,
                ClientCultivationKeyMappings.STOP_MEDITATION,
                MeditationIntentAction.STOP);
        consumeIntent(
                minecraft,
                ClientCultivationKeyMappings.START_ADVANCEMENT,
                MeditationIntentAction.START_BREAKTHROUGH);
    }

    private static void consumeIntent(
            Minecraft minecraft,
            net.minecraft.client.KeyMapping key,
            MeditationIntentAction action) {
        while (key.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                if (action == MeditationIntentAction.START_BREAKTHROUGH && spirit.active()) {
                    if (spirit.action() != SpiritTrialStatusPayload.Action.NONE) {
                        PacketDistributor.sendToServer(new SpiritTrialIntentPayload(spirit.attempt(), spirit.strike(), spirit.action()));
                    }
                } else if (action == MeditationIntentAction.START_BREAKTHROUGH && opensBreakthroughChoices()) {
                    minecraft.setScreen(CultivationProfileScreen.breakthroughs());
                } else {
                    ClientCultivationIntentSender.send(action);
                }
            }
        }
    }

    private static void receiveMeditationStatus(MeditationStatus status) {
        ClientCultivationState.replaceMeditation(status);
        if (status.reason() == MeditationStopReason.NONE) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            String key = "message.yuanxian.cultivation.session."
                    + status.reason().name().toLowerCase(Locale.ROOT);
            minecraft.player.displayClientMessage(Component.translatable(key), true);
        }
    }
}
