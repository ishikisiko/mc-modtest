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
import java.util.ArrayList;
import java.util.List;
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
    /**
     * Hit confirmations per action and their spacing in ticks: one; two or three in the same frame
     * (one hit batch per target in one server tick); a third of a tick apart (a sweep's three
     * samples per tick); a tick apart (targets struck over the active ticks).
     */
    private static final float[][] CONFIRMATION_SPACINGS = {
            {}, {0.0F}, {0.0F, 0.0F}, {1.0F / 3.0F}, {1.0F / 3.0F, 1.0F / 3.0F}, {1.0F}, {1.0F, 1.0F}};

    /** Game-clock resets beyond the normal one tick: the time packet sets the clock by this much. */
    private static final int[] CLOCK_JUMPS = {1, 2, 5, 0, -1, -2};

    @Test
    void theDisplayedTickNeverRunsBackwardsAroundAHitConfirmation() {
        int replays = 0;
        for (FirstPersonSwingResources.WeaponRig rig : rigs()) {
            for (int index = 0; index < rig.style().moves().size(); index++) {
                AttackMoveDefinition definition = rig.style().move(index);
                FirstPersonSwing.Move move = rig.swing().move(index);
                float first = move.strikeStartTick() - 1.0F;
                float last = definition.activeEndTick() + 2.0F;
                for (float[] spacing : CONFIRMATION_SPACINGS) {
                    for (float framesPerTick : FRAMES_PER_TICK) {
                        for (float phase : PHASES) {
                            for (float arrival = first; arrival <= last; arrival += ARRIVAL_STEP) {
                                float[] arrivals = new float[spacing.length + 1];
                                arrivals[0] = arrival;
                                for (int next = 0; next < spacing.length; next++) {
                                    arrivals[next + 1] = arrivals[next] + spacing[next];
                                }
                                new Replay(definition, move, framesPerTick, phase, arrivals, Float.NaN, 0, 0).run();
                                replays++;
                            }
                        }
                    }
                }
            }
        }
        assertTrue(replays > 10000, "replayed " + replays);
    }

    /**
     * The server's time packet re-sets the client's game clock (by {@link #CLOCK_JUMPS} ticks) at
     * every tick of every move, before or after the authoritative START, with and without a hit-stop
     * running. After the START the drawn swing is exactly the one without the reset; before it the
     * START maps the server's start with the clock it reads, which is an authoritative correction,
     * slewed like any other: compared with the same offset never reset, a reset after the START
     * changes nothing on screen. Never a skip, never a hold longer than a frame outside the freeze, never
     * backwards, and the move ends on the mapped server total.
     */
    @Test
    void aGameClockResetIsNotElapsedSwingTime() {
        int replays = 0;
        float startArrival = 1.3F;
        for (FirstPersonSwingResources.WeaponRig rig : rigs()) {
            for (int index = 0; index < rig.style().moves().size(); index++) {
                AttackMoveDefinition definition = rig.style().move(index);
                FirstPersonSwing.Move move = rig.swing().move(index);
                float[][] confirmations = {
                        {}, {move.contactTick() - 0.6F}, {move.contactTick() + 0.4F, move.contactTick() + 1.0F}};
                for (float[] arrivals : confirmations) {
                    for (float framesPerTick : new float[] {1.5F, 2.25F, 6.0F}) {
                        for (float phase : new float[] {0.0F, 0.37F}) {
                            // A client whose game clock matches the server's.
                            List<Float> inSync = new Replay(definition, move, framesPerTick, phase, arrivals,
                                    startArrival, 0, Integer.MAX_VALUE).run();
                            for (int jump : CLOCK_JUMPS) {
                                // The same game-clock offset, never reset.
                                List<Float> offset = new Replay(definition, move, framesPerTick, phase, arrivals,
                                        startArrival, jump, Integer.MAX_VALUE).run();
                                for (int jumpTick = 1; jumpTick < definition.totalTicks(); jumpTick++) {
                                    List<Float> drawn = new Replay(definition, move, framesPerTick, phase, arrivals,
                                            startArrival, jump, jumpTick).run();
                                    String where = definition.id() + " jump " + jump + " at tick " + jumpTick
                                            + " fpt " + framesPerTick + " phase " + phase;
                                    if (jumpTick > startArrival) {
                                        // After the START: the swing is the one mapped with the offset clock,
                                        // the reset itself changes nothing on screen.
                                        assertEquals(offset, drawn, where + ": the reset after the START changed the swing");
                                    } else {
                                        // Before the START: the START maps the server's start with the corrected
                                        // clock, exactly as for a client that was never off.
                                        assertEquals(inSync, drawn, where + ": the START did not map with the corrected clock");
                                    }
                                    replays++;
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(replays > 5000, "replayed " + replays);
    }

    /**
     * One local action replayed the way the client runs it, through {@link LocalSwingTimeline} and
     * the {@link ClientCombatClock} in the animator's call order: per frame, queued packets first
     * (the time packet, the authoritative START, hit confirmations), then the frame's client ticks
     * (the clock advances, the swing-sound reader and the third-person rate reader read), then the
     * render reader. The move is predicted at local tick 0; the server started it at its tick 0.
     * Before {@code jumpTick} the client's game clock runs {@code jump} ticks behind the server's;
     * the time packet at {@code jumpTick} sets it right.
     */
    private static final class Replay {
        private final AttackMoveDefinition definition;
        private final FirstPersonSwing.Move move;
        private final float framesPerTick;
        private final float phase;
        private final float[] arrivals;
        private final float startArrival;
        private final int jump;
        private final int jumpTick;
        private final String where;

        Replay(AttackMoveDefinition definition, FirstPersonSwing.Move move, float framesPerTick, float phase,
                float[] arrivals, float startArrival, int jump, int jumpTick) {
            this.definition = definition;
            this.move = move;
            this.framesPerTick = framesPerTick;
            this.phase = phase;
            this.arrivals = arrivals;
            this.startArrival = startArrival;
            this.jump = jump;
            this.jumpTick = jumpTick;
            this.where = definition.id() + " fpt " + framesPerTick + " phase " + phase + " arrivals "
                    + java.util.Arrays.toString(arrivals) + " jump " + jump + " at " + jumpTick;
        }

        /** The drawn visual tick of every frame until the move ends. */
        List<Float> run() {
            int total = definition.totalTicks();
            float stopTicks = definition.feedback().hitStopTicks();
            ClientCombatClock.setForTest(0L);
            LocalSwingTimeline timeline = new LocalSwingTimeline(total, ClientCombatClock.ticks());
            SwingClock clock = timeline.clock();
            List<Float> drawnTicks = new ArrayList<>();
            float frameTicks = 1.0F / framesPerTick;
            float previousFrame = 0.0F;
            float shown = 0.0F;
            float shownReal = 0.0F;
            int confirmed = 0;
            boolean started = Float.isNaN(startArrival);
            boolean clockSet = jumpTick < 0;
            double mappedStart = 0.0;
            float frozenAt = Float.NaN;
            float stopStart = Float.NaN;
            for (int frame = 0; ; frame++) {
                float time = phase * frameTicks + frame * frameTicks;
                assertTrue(frame < 10000, where);
                // Queued packets: the game clock still holds the tick the previous frame drew in.
                long gameTime = (long) Math.floor(previousFrame) - (clockSet ? 0 : jump);
                if (!clockSet && time >= jumpTick) {
                    clockSet = true;
                    gameTime += jump;
                }
                if (!started && time >= startArrival) {
                    started = true;
                    mappedStart = ClientCombatClock.localTickAgo(ClientCombatClock.elapsedSince(0L, gameTime));
                    double shift = timeline.correct(mappedStart, ClientCombatClock.ticks());
                    assertTrue(LocalSwingTimeline.slewed(shift), where + ": start shift " + shift);
                }
                while (confirmed < arrivals.length && time >= arrivals[confirmed]) {
                    confirmed++;
                    boolean running = clock.hasHitStop();
                    float before = running ? clock.hitStopStart() : Float.NaN;
                    LocalSwingTimeline.Confirmation result =
                            timeline.confirm(ClientCombatClock.now(0.0F), move.contactTick(), stopTicks);
                    if (confirmed > 1 || running) {
                        assertTrue(Float.isNaN(result.start()), where + ": confirmation " + confirmed + " started another stop");
                        if (running) {
                            assertEquals(before, clock.hitStopStart(), 0.0F, where + ": a later confirmation moved the stop");
                            assertEquals(stopTicks, clock.hitStopTicks(), 0.0F, where + ": a later confirmation resized the stop");
                        } else {
                            assertFalse(clock.hasHitStop(), where + ": a later confirmation stopped a move the first could not");
                        }
                    } else if (!Float.isNaN(result.start())) {
                        stopStart = result.start();
                        frozenAt = Math.max(shown, move.contactTick());
                        assertTrue(result.start() >= shownReal - 1.0E-5F, where + ": the stop starts behind the frame on screen");
                        assertEquals(stopTicks, clock.hitStopTicks(), 1.0E-6F, where);
                    }
                }
                // This frame's client ticks: the clock advances first, then the readers.
                float partial = time - (float) Math.floor(time);
                for (int tick = (int) Math.floor(previousFrame) + 1; tick <= (int) Math.floor(time); tick++) {
                    ClientCombatClock.endOfTick(true, tick - (clockSet ? 0 : jump));
                    timeline.visualTick(ClientCombatClock.now(0.0F));
                    timeline.rate(ClientCombatClock.now(partial));
                }
                // Render.
                LocalSwingTimeline.Drawn drawn = timeline.frame(ClientCombatClock.now(partial));
                if (!drawn.drawn()) {
                    assertTrue(drawn.ended(), where + ": nothing drawn before the end at " + time);
                    double localNow = ClientCombatClock.now(partial);
                    assertTrue(localNow >= mappedStart + total - 1.0E-4, where + ": ended early at " + localNow);
                    assertTrue(localNow < mappedStart + total + RESYNC_LAG + frameTicks, where + ": ended late at " + localNow);
                    break;
                }
                float visual = drawn.tick();
                assertTrue(visual >= shown - 1.0E-5F,
                        where + ": the swing ran back from " + shown + " to " + visual + " at real " + time);
                boolean frozen = !Float.isNaN(stopStart) && clock.present() >= stopStart
                        && clock.present() < stopStart + stopTicks * SwingClock.FREEZE_FRACTION;
                if (frozen) {
                    assertEquals(frozenAt, visual, 1.0E-4F, where + ": the freeze holds the pose on screen");
                } else if (frame > 0 && visual < total) {
                    assertTrue(visual > shown, where + ": the swing held outside the freeze at real " + time);
                }
                if (frame > 0) {
                    // At most the catch-up rate after a stop, times the fastest start slew (two ticks over three).
                    float maxRate = Math.max(1.0F, clock.rate(total - 1.0E-3F)) * (1.0F + LocalSwingTimeline.RESYNC_SLEW_LIMIT_TICKS / LocalSwingTimeline.RESYNC_SLEW_TICKS);
                    assertTrue(visual - shown <= maxRate * frameTicks + 1.0E-4F,
                            where + ": the swing skipped " + (visual - shown) + " ticks in one frame at real " + time);
                }
                drawnTicks.add(visual);
                shown = visual;
                shownReal = clock.present();
                previousFrame = time;
            }
            if (!Float.isNaN(stopStart)) {
                assertTrue(stopStart + stopTicks <= total, where);
                assertFalse(clock.inHitStop(total), where);
            }
            assertEquals(total, clock.visualTick(total), 1.0E-3F, where + ": ends on the server total");
            return drawnTicks;
        }
    }

    /** A slewed start correction ends the move up to this much after the mapped total. */
    private static final float RESYNC_LAG = 0.0F;

    private static List<FirstPersonSwingResources.WeaponRig> rigs() {
        Map<ResourceLocation, FirstPersonSwingResources.WeaponRig> rigs =
                FirstPersonSwingResources.loadAll(CombatTestData.styles(), LiveSwingTimingTest::read);
        assertTrue(rigs.containsKey(CombatTestData.qingfeng().item()), "sword rig loads");
        assertTrue(rigs.containsKey(CombatTestData.lingxiao().item()), "spear rig loads");
        return List.copyOf(rigs.values());
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
