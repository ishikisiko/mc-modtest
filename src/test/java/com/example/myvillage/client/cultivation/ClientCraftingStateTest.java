package com.example.myvillage.client.cultivation;

import com.example.myvillage.client.cultivation.panel.PanelContext;
import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.crafting.CraftingAptitudes;
import com.example.myvillage.cultivation.fate.FateState;
import com.example.myvillage.cultivation.network.CultivationSnapshotPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientCraftingStateTest {
    @AfterEach void disconnect() { ClientCultivationState.clear(); }

    @Test void owningSnapshotSuppliesBothProfessionsAndClearsOnDisconnect() {
        var aptitudes = new CraftingAptitudes(1, true, 31, 94, 90);
        var snapshot = new CultivationSnapshotPayload(CultivationProfile.defaultProfile(), FateState.empty(), 8,
                aptitudes, 1, 4, 4);
        ClientCultivationState.replace(snapshot);
        assertSame(snapshot, ClientCultivationState.snapshot().orElseThrow());
        assertEquals(aptitudes, ClientCultivationState.crafting());
        assertEquals(1, ClientCultivationState.alchemyQualityCap());
        assertEquals(4, ClientCultivationState.forgingQualityCap());
        var frame = PanelContext.capture(null, null);
        assertSame(snapshot.profile(), frame.profile());
        assertEquals(4, frame.talismanQualityCap());
        assertEquals(aptitudes, frame.crafting());
        assertEquals(1, frame.alchemyQualityCap()); assertEquals(4, frame.forgingQualityCap());
        ClientCultivationState.clear();
        assertTrue(ClientCultivationState.snapshot().isEmpty());
        assertEquals(CraftingAptitudes.EMPTY, ClientCultivationState.crafting());
        assertEquals(0, ClientCultivationState.alchemyQualityCap());
        assertEquals(0, ClientCultivationState.forgingQualityCap());
        var cleared = PanelContext.capture(null, null);
        assertEquals(0, cleared.talismanQualityCap());
        assertEquals(4, frame.talismanQualityCap()); // Captured frame never mixes two snapshots.
        assertNull(cleared.profile()); assertEquals(CraftingAptitudes.EMPTY, cleared.crafting());
    }

    @Test void aNewSnapshotReplacesAllCraftingReadoutsTogether() {
        ClientCultivationState.replace(new CultivationSnapshotPayload(CultivationProfile.defaultProfile(),
                FateState.empty(), 8, new CraftingAptitudes(1, true, 8, 80), 1, 3));
        var replacement = new CultivationSnapshotPayload(CultivationProfile.defaultProfile(),
                FateState.empty(), 8, new CraftingAptitudes(1, true, 100, 80), 4, 3);
        ClientCultivationState.replace(replacement);
        var frame = PanelContext.capture(null, null);
        assertEquals(replacement.crafting(), frame.crafting());
        assertEquals(4, frame.alchemyQualityCap()); assertEquals(3, frame.forgingQualityCap());
    }
}
