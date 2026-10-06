package com.example.myvillage.combat.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.DodgeDirection;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueEffects;
import com.example.myvillage.cultivation.data.TechniqueRequirements;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CombatDodgeServiceTest {
    private static final TechniqueEffects.Movement LIUYUN = new TechniqueEffects.Movement(3.5, 5, 6, 30);
    private static final TechniqueEffects.Movement TAXUE = new TechniqueEffects.Movement(4.5, 7, 10, 24);

    private final Map<ResourceLocation, TechniqueDefinition> registry = new HashMap<>();

    @Test
    void choosesTheHighestGradeMovementTechnique() {
        register("liuyun_bu", movement(1, LIUYUN));
        register("taxue_wuhen", movement(2, TAXUE));
        register("basic_breathing", technique(TechniqueCategory.CORE, 0, Optional.empty()));

        CombatDodgeService.Choice choice = choose("basic_breathing", "liuyun_bu", "taxue_wuhen").orElseThrow();
        assertEquals(id("taxue_wuhen"), choice.id());
        assertEquals(2, choice.grade());
        assertEquals(TAXUE, choice.movement());
        // Learning order does not matter.
        assertEquals(id("taxue_wuhen"), choose("taxue_wuhen", "liuyun_bu").orElseThrow().id());
    }

    @Test
    void equalGradesGoToTheSmallerId() {
        register("b_step", movement(1, TAXUE));
        register("a_step", movement(1, LIUYUN));
        assertEquals(id("a_step"), choose("b_step", "a_step").orElseThrow().id());
        assertEquals(id("a_step"), choose("a_step", "b_step").orElseThrow().id());
    }

    @Test
    void ignoresMovementTechniquesWithoutEffectsOtherCategoriesAndUnknownIds() {
        register("plain_step", technique(TechniqueCategory.MOVEMENT, 3, Optional.empty()));
        register("iron_body", technique(TechniqueCategory.BODY, 4, Optional.empty()));
        register("liuyun_bu", movement(1, LIUYUN));

        assertEquals(id("liuyun_bu"), choose("plain_step", "iron_body", "missing", "liuyun_bu").orElseThrow().id());
        assertTrue(choose("plain_step", "iron_body", "missing").isEmpty());
        assertTrue(choose().isEmpty());
    }

    @Test
    void preconditionsReportStateThenModeThenFooting() {
        assertEquals(Optional.empty(), CombatDodgeService.preconditionFailure(true, false, true, true));
        assertEquals(Optional.of(CombatDodgeService.Rejection.STATE),
                CombatDodgeService.preconditionFailure(false, false, false, false));
        assertEquals(Optional.of(CombatDodgeService.Rejection.STATE),
                CombatDodgeService.preconditionFailure(true, true, false, false));
        assertEquals(Optional.of(CombatDodgeService.Rejection.MODE),
                CombatDodgeService.preconditionFailure(true, false, false, false));
        assertEquals(Optional.of(CombatDodgeService.Rejection.AIRBORNE),
                CombatDodgeService.preconditionFailure(true, false, true, false));
    }

    @Test
    void invulnerableWindowIsOpenForExactlyItsTicks() {
        long start = 1_000L;
        long end = CombatDodgeService.windowEnd(start, 5);
        assertEquals(1_005L, end);
        assertTrue(CombatDodgeService.windowOpen(start, end));
        assertTrue(CombatDodgeService.windowOpen(start + 4, end));
        assertFalse(CombatDodgeService.windowOpen(start + 5, end));
        // A zero window protects nothing.
        assertFalse(CombatDodgeService.windowOpen(start, CombatDodgeService.windowEnd(start, 0)));
        assertEquals(Long.MAX_VALUE, CombatDodgeService.windowEnd(Long.MAX_VALUE - 2, 5));
    }

    @Test
    void presentationCoversTheWindowAndLastsAtLeastSixTicks() {
        assertEquals(6, CombatDodgeService.durationTicks(0));
        assertEquals(6, CombatDodgeService.durationTicks(5));
        assertEquals(7, CombatDodgeService.durationTicks(7));
    }

    @Test
    void cooldownAcceptsTheNextDodgeOnItsReadyTick() {
        long ready = CombatDodgeService.windowEnd(200L, 24);
        assertFalse(CombatDodgeService.ready(200L, ready));
        assertFalse(CombatDodgeService.ready(223L, ready));
        assertTrue(CombatDodgeService.ready(224L, ready));
        assertEquals(19L, CombatDodgeService.remaining(205L, ready));
        assertEquals(0L, CombatDodgeService.remaining(300L, ready));

        CombatDodgeService.DodgeState state = new CombatDodgeService.DodgeState(
                200L, 207L, 207L, 224L, id("taxue_wuhen"));
        assertFalse(state.expired(210L));
        assertTrue(state.expired(224L));
    }

    @Test
    void masteryRisesByOneAndSaturates() {
        assertEquals(1L, CombatDodgeService.nextMastery(0L));
        assertEquals(Long.MAX_VALUE, CombatDodgeService.nextMastery(Long.MAX_VALUE));
    }

    @Test
    void forwardIsTheMinecraftHorizontalUnitVector() {
        Vec3 south = CombatDodgeService.forward(0.0F);
        assertEquals(0.0, south.x, 1.0E-9);
        assertEquals(1.0, south.z, 1.0E-9);
        Vec3 west = CombatDodgeService.forward(90.0F);
        assertEquals(-1.0, west.x, 1.0E-9);
        assertEquals(0.0, west.y, 1.0E-9);
    }

    @Test
    void logLinesFollowTheCaptureFormat() {
        assertEquals(
                "DODGE_DEBUG player=Dev t=1234 result=started technique=myvillage:taxue_wuhen dir=LEFT "
                        + "yaw=-90.00 distance=4.50 invuln=7 cooldown=24",
                CombatDodgeService.startedLine(
                        "Dev", 1234L, id("taxue_wuhen"), DodgeDirection.LEFT, -90.0F, 4.5, 7, 24));
        assertEquals(
                "DODGE_DEBUG player=Dev t=1240 result=rejected reason=COOLDOWN",
                CombatDodgeService.rejectedLine("Dev", 1240L, CombatDodgeService.Rejection.COOLDOWN));
        assertEquals(
                "DODGE_DEBUG player=Dev t=1236 cancelled_damage=6.00 source=minecraft:mob_attack",
                CombatDodgeService.cancelledLine("Dev", 1236L, 6.0F, "minecraft:mob_attack"));
        assertEquals(
                List.of("STATE", "MODE", "AIRBORNE", "NO_TECHNIQUE", "COOLDOWN", "TIMING", "BLOCKED"),
                java.util.Arrays.stream(CombatDodgeService.Rejection.values()).map(Enum::name).toList());
    }

    private Optional<CombatDodgeService.Choice> choose(String... learned) {
        return CombatDodgeService.chooseTechnique(
                java.util.Arrays.stream(learned).map(CombatDodgeServiceTest::id).toList(),
                key -> Optional.ofNullable(registry.get(key)));
    }

    private void register(String path, TechniqueDefinition definition) {
        registry.put(id(path), definition);
    }

    private static TechniqueDefinition movement(int grade, TechniqueEffects.Movement movement) {
        return technique(TechniqueCategory.MOVEMENT, grade, Optional.of(new TechniqueEffects(
                Optional.empty(), Optional.empty(), Optional.of(movement), Optional.empty())));
    }

    private static TechniqueDefinition technique(
            TechniqueCategory category, int grade, Optional<TechniqueEffects> effects) {
        return new TechniqueDefinition(
                "cultivation.technique.test",
                category,
                grade,
                List.of(),
                TechniqueRequirements.none(),
                Optional.empty(),
                Optional.empty(),
                effects);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("myvillage", path);
    }
}
