package com.example.myvillage.client.cultivation;

import com.example.myvillage.cultivation.CultivationProfile;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class ClientCultivationState {
    private static final AtomicReference<CultivationProfile> LATEST = new AtomicReference<>();

    private ClientCultivationState() {
    }

    public static Optional<CultivationProfile> latest() {
        return Optional.ofNullable(LATEST.get());
    }

    static void replace(CultivationProfile profile) {
        LATEST.set(profile);
    }

    static void clear() {
        LATEST.set(null);
    }
}
