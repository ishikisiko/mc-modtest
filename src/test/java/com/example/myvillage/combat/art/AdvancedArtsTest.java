package com.example.myvillage.combat.art;

import com.example.myvillage.combat.definition.*;
import com.example.myvillage.combat.session.*;
import com.example.myvillage.combat.network.*;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Full-project tests: run through the focused Modal preset, not the lightweight JDK harness. */
class AdvancedArtsTest {
    @Test void everyFamilyGetsTwoDistinctSlotsWithoutChangingWaveRegistry() {
        var arts=ArtDefinition.all();assertEquals(6,arts.size());
        for(String family:List.of("sword","spear","fist")) {
            assertNotEquals(ArtDefinition.forSlot(family,0),ArtDefinition.forSlot(family,1));
            assertTrue(ArtDefinition.forSlot(family,0).isPresent());
            assertTrue(ArtDefinition.forSlot(family,1).isPresent());
            assertTrue(ArtDefinition.forSlot(family,2).isEmpty());
        }
        assertEquals(1,CombatStyles.bundled().skills().size());
        assertEquals("jianqi_jue",CombatStyles.bundled().skills().getFirst().id().getPath());
    }
    @Test void manualCodecAndCostMatchRuntime() throws Exception {
        for(var art:ArtDefinition.all()) {
            var json=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/myvillage/myvillage/technique/"+art.id().getPath()+".json")));
            var technique=TechniqueDefinition.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
            assertEquals(art.qiCost(),technique.effects().orElseThrow().active().orElseThrow().qiCost());
            assertEquals(art.id(),technique.effects().orElseThrow().active().orElseThrow().skill());
            assertEquals(Optional.of(art.school()),technique.school());
            assertEquals(2,technique.grade());
            assertEquals("myvillage:qi_refining_4",json.getAsJsonObject().getAsJsonObject("requirements").get("minimum_stage").getAsString());
        }
    }
    @Test void phasesShareAuthorityAndPreserveAuthoredBaselineAnimation() {
        var styles=CombatTestData.styles();
        for(var art:ArtDefinition.all()) {
            var ref=styles.move(art.phases().getFirst().moveId()).orElseThrow();
            var session=new CombatSession(ref.style());
            long time=100,revision=0;
            for(var phase:art.phases()) {
                var base=styles.move(phase.moveId()).orElseThrow().move();
                var move=phase.move(styles,1);
                assertEquals(base.animation(),move.animation());
                assertEquals(base.totalTicks(),move.totalTicks());
                assertEquals(base.activeStartTick(),move.activeStartTick());
                var start=session.startExternal(move,time,ResourceLocation.parse("myvillage:test_weapon"),ResourceLocation.parse("minecraft:overworld"),35);
                assertTrue(start.revision()>revision);revision=start.revision();
                assertTrue(session.markAttempted(10));assertFalse(session.markAttempted(10));
                assertEquals(CombatSession.IntentDecision.REJECTED_TIMING,session.acceptIntent(time+move.bufferStartTick(),start.weaponId(),start.worldId(),35).decision());
                assertTrue(session.tick(time+move.totalTicks()-1).stop().isEmpty());
                assertTrue(session.tick(time+move.totalTicks()).stop().isPresent());
                assertFalse(session.hasActiveAction());assertEquals(0,session.nextMoveIndex());
                time+=move.totalTicks();
            }
        }
    }
    @Test void interruptionDoesNotLeaveBufferedBasicAttack() {
        var art=ArtDefinition.all().getFirst();var styles=CombatTestData.styles();
        var phase=art.phases().getFirst();
        var session=new CombatSession(styles.move(phase.moveId()).orElseThrow().style());
        session.startExternal(phase.move(styles,1),100,CombatTestData.QINGFENG_SWORD,ResourceLocation.parse("minecraft:overworld"),0);
        assertTrue(session.interrupt(CombatStopReason.WEAPON_CHANGED).isPresent());
        assertFalse(session.isExternal());assertFalse(session.hasActiveAction());assertFalse(session.bufferedIntent());
        assertTrue(session.tick(1000).start().isEmpty());
    }
    @Test void slotPayloadIsExactlyOneByteAndRejectsOutOfRange() {
        assertEquals(1,CombatArtIntentPayload.class.getRecordComponents().length);
        for(int slot=0;slot<2;slot++) {
            var buf=buffer();
            try {
                CombatArtIntentPayload.STREAM_CODEC.encode(buf,new CombatArtIntentPayload(slot));
                assertEquals(1,buf.readableBytes());
                assertEquals(slot,CombatArtIntentPayload.STREAM_CODEC.decode(buf).slot());
                assertEquals(0,buf.readableBytes());
            } finally {buf.release();}
        }
        for(int invalid:List.of(-1,2,255,Integer.MAX_VALUE))
            assertThrows(IllegalArgumentException.class,()->new CombatArtIntentPayload(invalid));
        var buf=buffer();try {
            buf.writeByte(255);assertThrows(IllegalArgumentException.class,()->CombatArtIntentPayload.STREAM_CODEC.decode(buf));
        } finally {buf.release();}
    }
    @Test void boundedPresentationPayloadRoundTrips() {
        for(int kind=0;kind<=CombatArtStatePayload.COOLDOWN;kind++) {
            var cue=new CombatArtStatePayload(42,ArtDefinition.all().getFirst().id(),kind,1,8,3,200,new Vec3(1,2,3),45,150);
            var buf=buffer();try {
                CombatArtStatePayload.STREAM_CODEC.encode(buf,cue);
                assertEquals(cue,CombatArtStatePayload.STREAM_CODEC.decode(buf));assertEquals(0,buf.readableBytes());
            } finally {buf.release();}
        }
        assertThrows(IllegalArgumentException.class,()->new CombatArtStatePayload(42,ArtDefinition.all().getFirst().id(),0,0,0,1,1,Vec3.ZERO,Float.NaN,1));
    }
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY,ConnectionType.NEOFORGE);
    }
}
