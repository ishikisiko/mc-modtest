package com.example.myvillage.sim.runtime.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.sim.PersonView;
import com.example.myvillage.sim.SectView;
import com.example.myvillage.sim.WorldSim;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

/** Hand-written codecs of the 天下 page's query and snapshot round-trip every shape. */
class WorldSimPayloadCodecTest {
    private static <T> T roundTrip(StreamCodec<FriendlyByteBuf, T> codec, T value) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buf, value);
            T back = codec.decode(buf);
            assertEquals(0, buf.readableBytes(), "the decoder reads everything the encoder wrote");
            return back;
        } finally {
            buf.release();
        }
    }

    private static void assertRoundTrips(WorldSimSnapshot snapshot) {
        WorldSimSnapshotPayload payload = new WorldSimSnapshotPayload(snapshot);
        assertEquals(payload, roundTrip(WorldSimSnapshotPayload.STREAM_CODEC, payload));
    }

    // ------------------------------------------------------------------ query

    @Test
    void everyQueryKindRoundTrips() {
        List<WorldSimQuery> queries = List.of(
                WorldSimQuery.overview(), WorldSimQuery.sects(), WorldSimQuery.sect(0), WorldSimQuery.sect(12_345),
                WorldSimQuery.personSearch("韩"), WorldSimQuery.personSearch("一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十一二"),
                WorldSimQuery.person(77), WorldSimQuery.person(-1), WorldSimQuery.chronicle(), WorldSimQuery.here());
        Set<WorldSimQuery.Kind> seen = queries.stream().map(WorldSimQuery::kind).collect(Collectors.toSet());
        assertEquals(Set.of(WorldSimQuery.Kind.values()), seen);
        for (WorldSimQuery q : queries) {
            WorldSimQueryPayload payload = new WorldSimQueryPayload(q);
            assertEquals(payload, roundTrip(WorldSimQueryPayload.STREAM_CODEC, payload));
        }
    }

    @Test
    void networkIdsAreFixedAndDistinct() {
        Set<Integer> ids = new java.util.HashSet<>();
        for (WorldSimQuery.Kind kind : WorldSimQuery.Kind.values()) {
            int id = WorldSimQueryPayload.networkId(kind);
            assertTrue(id >= 0 && id < 256);
            assertTrue(ids.add(id), "network id " + id + " is used twice");
            assertEquals(kind, WorldSimQueryPayload.kindOf(id));
        }
        assertEquals(0, WorldSimQueryPayload.networkId(WorldSimQuery.Kind.OVERVIEW));
        assertEquals(6, WorldSimQueryPayload.networkId(WorldSimQuery.Kind.HERE));
    }

    @Test
    void anUnknownKindIsRejected() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeByte(255);
            buf.writeVarInt(-1);
            buf.writeUtf("");
            assertThrows(IllegalArgumentException.class, () -> WorldSimQueryPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
        FriendlyByteBuf seven = new FriendlyByteBuf(Unpooled.buffer());
        try {
            seven.writeByte(7);
            seven.writeVarInt(-1);
            seven.writeUtf("");
            assertThrows(IllegalArgumentException.class, () -> WorldSimQueryPayload.STREAM_CODEC.decode(seven));
        } finally {
            seven.release();
        }
    }

    @Test
    void anOverlongSearchTextIsRejectedOnDecode() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeByte(WorldSimQueryPayload.networkId(WorldSimQuery.Kind.PERSON_SEARCH));
            buf.writeVarInt(-1);
            buf.writeUtf("x".repeat(WorldSimQuery.MAX_TEXT + 1));
            assertThrows(RuntimeException.class, () -> WorldSimQueryPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    // ------------------------------------------------------------------ snapshot

    @Test
    void theInactiveAnswerRoundTrips() {
        for (WorldSimQuery.Kind kind : WorldSimQuery.Kind.values()) {
            assertRoundTrips(WorldSimSnapshot.inactive(new WorldSimQuery(kind, 3, "名"), "data failed: 韩"));
        }
        assertRoundTrips(WorldSimSnapshots.inactive(WorldSimQuery.here(), null));
    }

    @Test
    void everyKindsFilledShapeFromALiveLedgerRoundTrips() {
        WorldSim sim = NetFixtures.world();
        SectView sect = sim.sects(false).get(0);
        List<PersonView> all = sim.findPersons("", Integer.MAX_VALUE);
        PersonView living = all.stream().filter(p -> p.alive() && !p.relations().isEmpty()).findFirst().orElseThrow();
        PersonView dead = all.stream().filter(p -> !p.alive() && p.killerId() >= 0).findFirst().orElseThrow();
        List<WorldSimQuery> queries = new ArrayList<>(List.of(
                WorldSimQuery.overview(), WorldSimQuery.sects(), WorldSimQuery.sect(sect.id()),
                WorldSimQuery.personSearch(living.name().substring(0, 1)), WorldSimQuery.person(living.id()),
                WorldSimQuery.person(dead.id()), WorldSimQuery.chronicle(), WorldSimQuery.here(),
                WorldSimQuery.sect(99_999)));
        for (SectView s : sim.sects(true)) {
            queries.add(WorldSimQuery.sect(s.id()));
        }
        for (WorldSimQuery q : queries) {
            Optional<String> here = q.kind() == WorldSimQuery.Kind.HERE ? Optional.of(sect.regionId()) : Optional.empty();
            WorldSimSnapshot s = WorldSimSnapshots.build(sim, NetFixtures.DAYS_PER_YEAR, 4321, false, 2,
                    NetFixtures.REGION_NAME, here, -1234.75, 98.5, q);
            assertRoundTrips(s);
        }
        assertRoundTrips(WorldSimSnapshots.build(sim, NetFixtures.DAYS_PER_YEAR, 0, true, 0, NetFixtures.REGION_NAME,
                Optional.empty(), 0, 0, WorldSimQuery.here()));
    }

    @Test
    void aHandMadeSnapshotWithEverySectionAndEdgeValuesRoundTrips() {
        WorldSimSnapshot.SectSummary summary = new WorldSimSnapshot.SectSummary(4, "青云宫", "中州", "", 0, "", -3,
                -100_008, 2_000_008, true, false, -1);
        WorldSimSnapshot.PersonSummary someone = new WorldSimSnapshot.PersonSummary(9, "韩清漪", "清漪真人", false,
                "golden_core", 2, -1, "", "rogue");
        WorldSimSnapshot.EventLine line = new WorldSimSnapshot.EventLine(Long.MAX_VALUE, -5, 3, -1,
                "world_sim.event.x.1", List.of("@world_sim.rank.elder", "", "韩清漪"), 12);
        WorldSimSnapshot.EventLine cause = new WorldSimSnapshot.EventLine(12, 0, 1, 9, "world_sim.event.y.2",
                List.of(), -1);
        for (String heritage : java.util.Arrays.asList("太白剑脉", null)) {
        WorldSimSnapshot full = new WorldSimSnapshot(
                new WorldSimQuery(WorldSimQuery.Kind.SECT, 4, ""), true, "", 7_000_000_000L, 600, 6,
                new WorldSimSnapshot.Overview("small", 120, 130,
                        List.of(new WorldSimSnapshot.RealmCount("qi_refining", 100),
                                new WorldSimSnapshot.RealmCount("nascent_soul", 0)),
                        3, 1, 400, 9_999_999_999L, true, 365, Long.MAX_VALUE),
                List.of(summary, summary),
                new WorldSimSnapshot.SectDetail(summary, 0, -1, "", 9, 2, "玄天宗", 777, Integer.MIN_VALUE, "焚天诀", heritage,
                        List.of(new WorldSimSnapshot.SectRelation(2, "玄天宗", -100, "war"),
                                new WorldSimSnapshot.SectRelation(5, "", 0, "none"))),
                List.of(someone),
                new WorldSimSnapshot.PersonDetail(someone, "female", -40, 700, "slain", 11, "某人", "heavenly",
                        List.of(10_000, 0, 0, 0, 0), 0.123456789, -1, "", "", "dead", "", 0,
                        List.of(new WorldSimSnapshot.PersonRelation(3, "师父", "master", -7))),
                List.of(line),
                List.of(cause),
                new WorldSimSnapshot.Region("zhongzhou", "中州", 5, -1, 99, 0, 100, true, 0));
        assertRoundTrips(full);
        assertEquals(cause, full.causeOf(line));
        }
    }
}
