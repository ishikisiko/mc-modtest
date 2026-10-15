package com.example.myvillage.client.cultivation;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.network.CultivationTimeSnapshotPayload;
import com.example.myvillage.cultivation.meditation.MeditationStatus;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class ClientCultivationState {
    private static final AtomicReference<com.example.myvillage.cultivation.network.CultivationSnapshotPayload> LATEST = new AtomicReference<>();
    private static final AtomicReference<CultivationTimeSnapshotPayload> TIME = new AtomicReference<>();
    private static final AtomicReference<MeditationStatus> MEDITATION = new AtomicReference<>();

    private ClientCultivationState() {
    }

    /** The single owning snapshot, captured once when a panel frame needs several sections. */
    public static Optional<com.example.myvillage.cultivation.network.CultivationSnapshotPayload> snapshot() {
        return Optional.ofNullable(LATEST.get());
    }

    public static com.example.myvillage.cultivation.crafting.CraftingAptitudes crafting() {
        var value = LATEST.get();
        return value == null ? com.example.myvillage.cultivation.crafting.CraftingAptitudes.EMPTY : value.crafting();
    }

    public static int alchemyQualityCap() { var value = LATEST.get(); return value == null ? 0 : value.alchemyQualityCap(); }
    public static int forgingQualityCap() { var value = LATEST.get(); return value == null ? 0 : value.forgingQualityCap(); }

    public static Optional<CultivationProfile> latest() {
        return Optional.ofNullable(LATEST.get()).map(com.example.myvillage.cultivation.network.CultivationSnapshotPayload::profile);
    }

    public static com.example.myvillage.cultivation.fate.FateState fates() {
        var value = LATEST.get(); return value == null ? com.example.myvillage.cultivation.fate.FateState.empty() : value.fates();
    }
    public static int fateBudget() { var value = LATEST.get(); return value == null ? 8 : value.fateBudget(); }
    public static Optional<CultivationTimeSnapshotPayload> time() {
        return Optional.ofNullable(TIME.get());
    }

    public static Optional<MeditationStatus> meditation() {
        return Optional.ofNullable(MEDITATION.get());
    }

    static void replace(com.example.myvillage.cultivation.network.CultivationSnapshotPayload profile) {
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
