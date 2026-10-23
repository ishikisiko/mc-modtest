package com.example.myvillage.cultivation.fate.ability;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class FateAbilityCooldownsTest {
    private static final UUID PLAYER = UUID.fromString("3b3f335d-11b4-4cba-88fe-a7b1657a6ed4");
    private static final ResourceLocation ABILITY = ResourceLocation.parse("myvillage:wood_root");

    @Test void oldWorldWithoutAbilityDataStartsEmpty() {
        var data = FateAbilityCooldowns.load(new CompoundTag(), null);
        assertEquals(0, data.remaining(PLAYER, ABILITY, 0));
        assertTrue(data.save(new CompoundTag(), null).getCompound("players").isEmpty());
    }

    @Test void savedCooldownKeepsItsDeadlineAcrossReplacementAndRestart() {
        var data = new FateAbilityCooldowns();
        data.start(PLAYER, ABILITY, 1000, 400);
        assertTrue(data.isDirty());
        var restored = FateAbilityCooldowns.load(data.save(new CompoundTag(), null), null);
        assertEquals(400, restored.remaining(PLAYER, ABILITY, 1000));
        assertEquals(300, restored.remaining(PLAYER, ABILITY, 1100));
        assertEquals(1, restored.remaining(PLAYER, ABILITY, 1399));
        assertEquals(0, restored.remaining(PLAYER, ABILITY, 1400));
        assertEquals(0, restored.remaining(UUID.randomUUID(), ABILITY, 1000));
        assertEquals(0, restored.remaining(PLAYER, ResourceLocation.parse("myvillage:other"), 1000));
    }

    @Test void repeatedWritesCannotRefreshAndUnknownValidIdsRoundTrip() {
        var data = new FateAbilityCooldowns();
        data.start(PLAYER, ABILITY, 100, 400);
        data.start(PLAYER, ABILITY, 100, 20);
        assertEquals(400, data.remaining(PLAYER, ABILITY, 100));
        var unknown = ResourceLocation.parse("future:unknown_ability");
        data.start(PLAYER, unknown, 100, 40);
        var restored = FateAbilityCooldowns.load(data.save(new CompoundTag(), null), null);
        assertEquals(400, restored.remaining(PLAYER, ABILITY, 100));
        assertEquals(40, restored.remaining(PLAYER, unknown, 100));
    }

    @Test void expirationPrunesPlayersWithoutTouchingUnexpiredCooldowns() {
        var data = new FateAbilityCooldowns();
        data.start(PLAYER, ABILITY, 100, 40);
        var other = UUID.randomUUID();
        data.start(other, ABILITY, 100, 100);
        data.setDirty(false);
        data.prune(140);
        assertTrue(data.isDirty());
        var players = data.save(new CompoundTag(), null).getCompound("players");
        assertFalse(players.contains(PLAYER.toString()));
        assertTrue(players.contains(other.toString()));
        assertEquals(60, data.remaining(other, ABILITY, 140));
    }

    @Test void corruptRowsAreIgnoredWithoutErasingValidNeighbors() {
        var abilities = new CompoundTag();
        abilities.putLong(ABILITY.toString(), 500);
        abilities.putLong("bad ability", 500);
        abilities.putLong("myvillage:negative", -1);
        abilities.putString("myvillage:string", "500");
        var players = new CompoundTag();
        players.put(PLAYER.toString(), abilities);
        players.put("not-a-uuid", abilities.copy());
        var root = new CompoundTag(); root.put("players", players);
        var data = FateAbilityCooldowns.load(root, null);
        assertEquals(400, data.remaining(PLAYER, ABILITY, 100));
        assertTrue(data.isDirty());
        assertEquals(1, data.save(new CompoundTag(), null).getCompound("players").getCompound(PLAYER.toString()).size());
    }

    @Test void saturatedClockNeverWrapsIntoAReadyCooldown() {
        var data = new FateAbilityCooldowns();
        data.start(PLAYER, ABILITY, Long.MAX_VALUE - 5, 400);
        assertEquals(5, data.remaining(PLAYER, ABILITY, Long.MAX_VALUE - 5));
        assertEquals(0, data.remaining(PLAYER, ABILITY, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> data.start(PLAYER, ABILITY, -1, 400));
        assertThrows(IllegalArgumentException.class, () -> data.start(PLAYER, ABILITY, 100, 0));
    }
}
