package com.example.myvillage.client.combat;

import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.combat.definition.CombatTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdvancedArtClientStateTest {
    @AfterEach void clear() { ClientCombatState.clear(); }
    @Test void everyExternalArtResetsNextBasicAttackAndRefusesBufferedChains() {
        var styles=CombatTestData.styles();
        for(var art:ArtDefinition.all()) for(var phase:art.phases()) {
            var ref=styles.move(phase.moveId()).orElseThrow();
            var move=phase.move(styles,1);
            ClientCombatState.preparePrediction(100,ref.style());
            ClientCombatState.trackLocalAction(ref.style(),ref.index(),100,10);
            ClientCombatState.externalMove(move);
            ClientCombatState.confirmPrediction(0);
            assertEquals(move,ClientCombatState.localMove().orElseThrow());
            assertFalse(ClientCombatState.bufferClick(100+move.bufferStartTick(),ref.style()));
            assertEquals(-1,ClientCombatState.chainDue(100+move.chainTick(),ref.style()));
            ClientCombatState.completeAction(100+move.totalTicks());
            assertEquals(0,ClientCombatState.preparePrediction(101+move.totalTicks(),ref.style()));
            ClientCombatState.clear();
        }
    }
}
