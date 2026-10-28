package com.example.myvillage.cultivation.meditation;

import com.example.myvillage.cultivation.network.MeditationPosePayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-owned transient presentation epochs; progression remains entirely in MeditationManager. */
public final class MeditationVisuals {
    private static final Map<UUID, MeditationPosePayload> ACTIVE = new HashMap<>();
    private static long revision;

    public static void changed(ServerPlayer player, MeditationStatus status) {
        boolean active = status.state().active();
        MeditationPosePayload previous = ACTIVE.get(player.getUUID());
        if(active == (previous != null)) return; // Settlement/progress messages never restart an animation.
        MeditationPosePayload pose = new MeditationPosePayload(player.getId(), player.getUUID(),
                player.level().dimension().location(), ++revision, player.level().getGameTime(),
                MeditationManager.PREPARATION_TICKS, active, !active && graceful(status.reason()));
        if(active) ACTIVE.put(player.getUUID(), pose); else ACTIVE.remove(player.getUUID());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, pose);
    }

    public static void startTracking(ServerPlayer watcher, ServerPlayer player) {
        MeditationPosePayload pose = ACTIVE.get(player.getUUID());
        if(pose != null) PacketDistributor.sendToPlayer(watcher, pose);
    }

    public static boolean graceful(MeditationStopReason reason) {
        return switch(reason) {
            case REQUESTED, STUDY_GATE, STUDY_COMPLETE, PRACTICE_COMPLETE,
                    PRACTICE_RESOURCES_EXHAUSTED, SPIRIT_RESOURCES_EXHAUSTED,
                    ADVANCEMENT_COMPLETED, FOUNDATION_FAILED, STAGE_NOT_CULTIVATABLE -> true;
            default -> false;
        };
    }
    public static void reset() { ACTIVE.clear(); revision = 0; }
    private MeditationVisuals() {}
}
