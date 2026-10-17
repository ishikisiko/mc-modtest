package com.example.myvillage.cultivation.qi;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.SpiritualRoot;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.fate.FateDefinition;
import com.example.myvillage.cultivation.fate.FateState;
import com.example.myvillage.cultivation.network.CultivationSnapshotPayload;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Real fate codec + registry ownership and persisted/owning snapshot boundaries. */
class OpenMeridiansTest {
    private static FateDefinition definition;
    private static ResourceLocation id(String value) { return ResourceLocation.parse("myvillage:" + value); }
    private static final List<String> PICKS = List.of("myvillage:open_meridians", "myvillage:robust", "myvillage:bright");

    @BeforeAll static void setup() throws Exception {
        if (net.neoforged.fml.loading.LoadingModList.get() == null)
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        definition = FateDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/myvillage/myvillage/innate_fate/open_meridians.json")))).getOrThrow();
    }

    private static FateState selected() {
        return new FateState(1, 1, PICKS, "", PICKS, true, 0, 0, 0, 0);
    }

    private static CultivationProfile profile() {
        return CultivationProfile.defaultProfile().withRealmAndStage(id("test_realm"), id("test_stage"))
                .withSpiritualRoot(new SpiritualRoot(Map.of(id("fire"), 10000)))
                .withCurrentSpiritualPower(119);
    }

    private static RegistryAccess registries(FateDefinition fate) {
        var realms = new MappedRegistry<>(ModCultivationRegistries.REALMS, Lifecycle.stable());
        var stages = List.of(new RealmStageDefinition(id("test_stage"), "stage.test", 0,
                        Optional.empty(), Optional.empty(), Optional.empty(), 100),
                new RealmStageDefinition(id("test_stage_2"), "stage.test2", 1,
                        Optional.empty(), Optional.empty(), Optional.empty(), 200));
        Registry.registerForHolder(realms, id("test_realm"),
                new RealmDefinition("realm.test", 0, 100, stages, Optional.empty()));
        realms.freeze();
        var fates = new MappedRegistry<>(ModCultivationRegistries.INNATE_FATES, Lifecycle.stable());
        if (fate != null) Registry.registerForHolder(fates, id("open_meridians"), fate);
        fates.freeze();
        return new RegistryAccess.ImmutableRegistryAccess(List.of(realms, fates));
    }

    @Test void realDefinitionGrantsFortyPercentAndRefundsOneQuarterOfFinalPayment() {
        assertEquals(.4, definition.effect("power_cap_bonus"));
        assertEquals(.25, definition.effect("skill_refund_fraction"));
        var access = registries(definition);
        assertEquals(100, SpiritualPower.cap(access, profile()));
        assertEquals(140, CultivationService.spiritualPowerCap(access, profile(), selected()));
        // The old stage-cap path would erase 19 legitimate power before this spend/recovery.
        long cap = SpiritualPower.cap(access, profile(), selected());
        long paid = 7; // e.g. final channeling cost after a nominal cost of 8.
        long spent = SpiritualPower.spend(profile().currentSpiritualPower(), cap, paid).orElseThrow();
        assertEquals(112, spent);
        assertEquals(1, OpenMeridians.refund(paid, definition.effect("skill_refund_fraction")));
        assertEquals(140, SpiritualPower.recover(spent, cap, Long.MAX_VALUE));
        assertEquals(119, profile().currentSpiritualPower(), "capacity growth must not refill the immutable balance");
    }

    @Test void unconfirmedUnknownDisabledAndUnawakenedChoicesNeverGrantCapacity() {
        var access = registries(definition);
        assertEquals(100, SpiritualPower.cap(access, profile(), FateState.empty()));
        assertEquals(100, SpiritualPower.cap(registries(null), profile(), selected()));
        assertEquals(100, SpiritualPower.cap(access, profile().withoutSpiritualRoot(), selected()));
        var disabled = new FateDefinition(definition.translationKey(), definition.descriptionKey(),
                definition.tier(), definition.cost(), definition.group(), false, definition.effects(),
                definition.element(), definition.minimumShare(), definition.maximumRoots());
        assertEquals(100, SpiritualPower.cap(registries(disabled), profile(), selected()));
        assertEquals(100, SpiritualPower.clamp(119,
                SpiritualPower.cap(access, profile(), FateState.empty())));
    }

    @Test void replacementStageAndRootDriveCapacityRatherThanTheOldProfile() {
        var access = registries(definition);
        var advanced = profile().withRealmAndStage(id("test_realm"), id("test_stage_2"));
        assertEquals(280, CultivationService.spiritualPowerCap(access, advanced, selected()));
        assertEquals(119, advanced.currentSpiritualPower());
        assertEquals(200, CultivationService.spiritualPowerCap(access, advanced.withoutSpiritualRoot(), selected()));
        assertEquals(0, SpiritualPower.cap(access, profile().withRealmAndStage(id("missing"), id("missing")), selected()));
    }

    @Test void recoveryClockUsesEffectiveCapAndFreshLifecycleRetainsTheFullDelay() {
        var rules = new QiRules(1, 10, 1, 2);
        long cap = SpiritualPower.cap(registries(definition), profile(), selected());
        var timer = new QiRegeneration(rules);
        assertEquals(0, timer.tick(rules, 10000, false, profile().currentSpiritualPower() >= cap));
        assertEquals(10, timer.tick(rules, 10000, false, profile().currentSpiritualPower() >= cap));
        long balance = SpiritualPower.recover(profile().currentSpiritualPower(), cap, 10);
        assertEquals(129, balance, "being above the old base cap must not suppress regeneration");
        assertEquals(140, SpiritualPower.recover(balance, cap, 100));
        var relogged = new QiRegeneration(rules);
        assertEquals(2, relogged.ticksRemaining());
        assertEquals(119, profile().currentSpiritualPower(), "constructing a lifecycle clock grants no power");
    }

    @Test void saveAndOwningNetworkRoundTripsKeepBalanceAndAllowIdenticalClientCapLookup() {
        var access = registries(definition);
        var savedProfile = CultivationProfile.CODEC.parse(JsonOps.INSTANCE,
                CultivationProfile.CODEC.encodeStart(JsonOps.INSTANCE, profile()).getOrThrow()).getOrThrow();
        var savedFates = FateState.CODEC.parse(JsonOps.INSTANCE,
                FateState.CODEC.encodeStart(JsonOps.INSTANCE, selected()).getOrThrow()).getOrThrow();
        assertEquals(119, savedProfile.currentSpiritualPower());
        assertEquals(140, SpiritualPower.cap(access, savedProfile, savedFates));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), access, ConnectionType.NEOFORGE);
        try {
            var snapshot = new CultivationSnapshotPayload(savedProfile, savedFates, 8);
            CultivationSnapshotPayload.STREAM_CODEC.encode(buffer, snapshot);
            var client = CultivationSnapshotPayload.STREAM_CODEC.decode(buffer);
            assertEquals(snapshot, client);
            assertEquals(140, SpiritualPower.cap(access, client.profile(), client.fates()));
            assertEquals(119, client.profile().currentSpiritualPower());
        } finally { buffer.release(); }
    }

    @Test void exactFlooringAndSaturationAvoidFloatingPointLossOrNegativeOverflow() {
        assertEquals(140, OpenMeridians.capacity(100, .4));
        assertEquals(1, OpenMeridians.capacity(1, .4));
        assertEquals(Long.MAX_VALUE, OpenMeridians.capacity(Long.MAX_VALUE, .4));
        assertEquals(0, OpenMeridians.capacity(0, .4));
        assertEquals(0, OpenMeridians.refund(0, .25));
        assertEquals(0, OpenMeridians.refund(3, .25));
        assertEquals(2, OpenMeridians.refund(8, .25));
        assertEquals(Long.MAX_VALUE / 4, OpenMeridians.refund(Long.MAX_VALUE, .25));
        assertThrows(IllegalArgumentException.class, () -> OpenMeridians.capacity(100, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> OpenMeridians.refund(-1, .25));
    }
}
