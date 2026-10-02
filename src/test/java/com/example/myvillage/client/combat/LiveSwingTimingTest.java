package com.example.myvillage.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.CombatTestData;
import com.example.myvillage.combat.definition.WeaponDefinition;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Replays a live action the way the client runs it: frames rendered at a partial tick, client
 * ticks reading the tick start, and the server's hit confirmation handled at the start of a frame,
 * before that frame's client tick. For every move of every bundled weapon and a grid of arrival
 * times and frame rates, the displayed tick never moves backwards, the stop freezes the pose on
 * screen (or the contact pose when the blade has not reached it), lasts the move's full hit-stop,
 * and the move still ends on the server total.
 */
final class LiveSwingTimingTest {
    /** Frames per tick: 30 fps and 60 fps at 20 TPS, 45 fps, and 30 fps at /tick rate 5. */
    private static final float[] FRAMES_PER_TICK = {1.5F, 2.25F, 3.0F, 6.0F};
    private static final float[] PHASES = {0.0F, 0.13F, 0.37F, 0.71F};
    private static final float ARRIVAL_STEP = 0.05F;

    @Test
    void theDisplayedTickNeverRunsBackwardsAroundAHitConfirmation() {
        Map<ResourceLocation, FirstPersonSwingResources.WeaponRig> rigs =
                FirstPersonSwingResources.loadAll(CombatTestData.styles(), LiveSwingTimingTest::read);
        assertTrue(rigs.containsKey(CombatTestData.qingfeng().item()), "sword rig loads");
        assertTrue(rigs.containsKey(CombatTestData.lingxiao().item()), "spear rig loads");
        int replays = 0;
        for (FirstPersonSwingResources.WeaponRig rig : rigs.values()) {
            for (int index = 0; index < rig.style().moves().size(); index++) {
                AttackMoveDefinition definition = rig.style().move(index);
                FirstPersonSwing.Move move = rig.swing().move(index);
                float first = move.strikeStartTick() - 1.0F;
                float last = definition.activeEndTick() + 2.0F;
                for (float framesPerTick : FRAMES_PER_TICK) {
                    for (float phase : PHASES) {
                        for (float arrival = first; arrival <= last; arrival += ARRIVAL_STEP) {
                            replay(definition, move, framesPerTick, phase, arrival);
                            replays++;
                        }
                    }
                }
            }
        }
        assertTrue(replays > 2000, "replayed " + replays);
    }

    /** One action from tick 0 to its end, with the confirmation arriving at real tick {@code arrival}. */
    private static void replay(
            AttackMoveDefinition definition, FirstPersonSwing.Move move, float framesPerTick, float phase, float arrival) {
        String where = definition.id() + " fpt " + framesPerTick + " phase " + phase + " arrival " + arrival;
        int total = definition.totalTicks();
        float stopTicks = definition.feedback().hitStopTicks();
        SwingClock clock = new SwingClock(total);
        float frameTicks = 1.0F / framesPerTick;
        float previousFrame = 0.0F;
        float shown = 0.0F;
        float shownReal = 0.0F;
        boolean confirmed = false;
        float frozenAt = Float.NaN;
        float stopStart = Float.NaN;
        int frame = 0;
        while (true) {
            float time = phase * frameTicks + frame * frameTicks;
            frame++;
            if (time >= total) {
                break;
            }
            // Start of the frame: queued packets run before this frame's client ticks, so the
            // game time still holds the tick the previous frame was drawn in.
            if (!confirmed && time >= arrival) {
                confirmed = true;
                float reading = (float) Math.floor(previousFrame);
                clock.advance(reading);
                float start = clock.confirmHit(move.contactTick(), stopTicks);
                if (!Float.isNaN(start)) {
                    stopStart = start;
                    frozenAt = Math.max(shown, move.contactTick());
                    assertTrue(start >= shownReal - 1.0E-5F, where + ": the stop starts behind the frame on screen");
                    assertEquals(stopTicks, clock.hitStopTicks(), 1.0E-6F, where);
                }
            }
            // This frame's client ticks read each new tick start (the swing sound cue).
            for (int tick = (int) Math.floor(previousFrame) + 1; tick <= (int) Math.floor(time); tick++) {
                clock.advance(tick);
            }
            float visual = clock.visualTick(clock.advance(time));
            assertTrue(visual >= shown - 1.0E-5F,
                    where + ": the swing ran back from " + shown + " to " + visual + " at real " + time);
            if (!Float.isNaN(stopStart) && time >= stopStart
                    && time < stopStart + stopTicks * SwingClock.FREEZE_FRACTION) {
                assertEquals(frozenAt, visual, 1.0E-4F, where + ": the freeze holds the pose on screen");
            }
            shown = visual;
            shownReal = time;
            previousFrame = time;
        }
        if (!Float.isNaN(stopStart)) {
            assertTrue(stopStart + stopTicks <= total, where);
            assertFalse(clock.inHitStop(total), where);
        }
        assertEquals(total, clock.visualTick(clock.advance(total)), 1.0E-3F, where + ": ends on the server total");
    }

    private static Optional<JsonObject> read(WeaponDefinition weapon, ResourceLocation location) {
        Path file = CombatTestData.assetPath(location);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
