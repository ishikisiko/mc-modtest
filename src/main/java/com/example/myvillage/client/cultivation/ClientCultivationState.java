package com.example.myvillage.client.cultivation;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.network.CultivationTimeSnapshotPayload;
import com.example.myvillage.cultivation.meditation.MeditationStatus;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class ClientCultivationState {
    private static final AtomicReference<CultivationProfile> LATEST = new AtomicReference<>();
    private static final AtomicReference<CultivationTimeSnapshotPayload> TIME = new AtomicReference<>();
    private static final AtomicReference<MeditationStatus> MEDITATION = new AtomicReference<>();

    private ClientCultivationState() {
    }

    public static Optional<CultivationProfile> latest() {
        return Optional.ofNullable(LATEST.get());
    }

    public static Optional<CultivationTimeSnapshotPayload> time() {
        return Optional.ofNullable(TIME.get());
    }

    public static Optional<MeditationStatus> meditation() {
        return Optional.ofNullable(MEDITATION.get());
    }

    static void replace(CultivationProfile profile) {
        LATEST.set(profile);
    }

    static void replaceTime(CultivationTimeSnapshotPayload snapshot) {
        TIME.set(snapshot);
    }

    static void replaceMeditation(MeditationStatus status) {
        MEDITATION.set(status);
    }

    static void clear() {
        LATEST.set(null);
        TIME.set(null);
        MEDITATION.set(null);
    }
}
