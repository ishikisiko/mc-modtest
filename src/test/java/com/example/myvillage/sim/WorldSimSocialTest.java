package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.PlayerMember;
import com.example.myvillage.sim.model.Tombstone;
import com.example.myvillage.sim.model.WorldState;
import com.example.myvillage.sim.social.ActorRef;
import com.example.myvillage.sim.social.ConversationView;
import com.example.myvillage.sim.social.SocialCodec;
import com.example.myvillage.sim.social.SocialEngine;
import com.example.myvillage.sim.social.SocialMemoryView;
import com.example.myvillage.sim.social.SocialState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Social identity, real conversation memory, save migration and replay safety. */
class WorldSimSocialTest {
    private static final String ALICE = "00000000-0000-0000-0000-00000000000a";
    private static final String BOB = "00000000-0000-0000-0000-00000000000b";
    private static final int NPC = 1;

    private static WorldState world() {
        WorldState world = new WorldState();
        Person person = new Person();
        person.id = NPC;
        person.surname = "林";
        person.given = "溪";
        world.persons.put(NPC, person);
        return world;
    }

    private static SocialState social(WorldState world) {
        SocialState social = new SocialState();
        SocialEngine.registerPlayerActor(social, world, ALICE, "Alice");
        return social;
    }

    private static boolean talk(SocialState social, WorldState world) {
        return SocialEngine.rememberConversation(social, world, NPC, ALICE, 3, 2, 10);
    }

    @Test
    void typedActorRefsUseCanonicalUuidAndRejectNamesOrShortUuidAliases() {
        ActorRef ref = ActorRef.player(ALICE.toUpperCase(java.util.Locale.ROOT));
        assertEquals(ActorRef.player(ALICE), ref);
        assertEquals(ref, ActorRef.parse(ref.stableKey()));
        assertEquals(ActorRef.npc(NPC), ActorRef.parse("npc:1"));
        assertThrows(IllegalArgumentException.class, () -> ActorRef.player("Alice"));
        assertThrows(IllegalArgumentException.class, () -> ActorRef.player("0-0-0-0-a"));
        assertThrows(IllegalArgumentException.class, () -> ActorRef.npc(0));
        assertThrows(IllegalArgumentException.class, () -> ActorRef.parse("npc:01"));
        assertThrows(IllegalStateException.class, ref::personId);
    }

    @Test
    void registeringAnUnaffiliatedPlayerNeverCreatesMembershipOrNpcPopulation() {
        WorldState world = world();
        SocialState social = new SocialState();
        assertTrue(SocialEngine.actor(social, world, ActorRef.player(ALICE)).isEmpty());
        assertTrue(SocialEngine.registerPlayerActor(social, world, ALICE, "Alice"));
        assertFalse(SocialEngine.registerPlayerActor(social, world, ALICE, "Alice"));
        assertEquals(-1, SocialEngine.actor(social, world, ActorRef.player(ALICE)).orElseThrow().sectId());
        assertTrue(world.playerMembers.isEmpty());
        assertEquals(1, world.persons.size());
        assertTrue(world.chronicle.isEmpty(), "registration must not fill the permanent chronicle");
    }

    @Test
    void inspectionDoesNotCreateAMemoryAndDifferentUuidsWithTheSameNameStayDistinct() {
        WorldState world = world();
        SocialState social = social(world);
        JsonObject before = SocialCodec.write(social);
        ConversationView context = SocialEngine.conversationContext(social, world, NPC, ALICE);
        assertFalse(context.metBefore());
        assertEquals(-1, context.lastConversationDay());
        assertEquals(0, context.familiarity());
        assertEquals(before, SocialCodec.write(social));
        talk(social, world);
        SocialEngine.registerPlayerActor(social, world, BOB, "Alice");
        assertFalse(SocialEngine.conversationContext(social, world, NPC, BOB).metBefore());
    }

    @Test
    void sameDayReplaysAndReloadCannotFarmFamiliarityWhileTheNextDayCan() {
        WorldState world = world();
        SocialState social = social(world);
        assertTrue(talk(social, world));
        assertFalse(talk(social, world));
        SocialState restored = SocialCodec.read(SocialCodec.write(social));
        assertFalse(talk(restored, world));
        ConversationView sameDay = SocialEngine.conversationContext(restored, world, NPC, ALICE);
        assertTrue(sameDay.metBefore());
        assertEquals(2, sameDay.familiarity());
        assertEquals(0, sameDay.lastConversationDay());
        world.day++;
        assertTrue(talk(restored, world));
        assertEquals(4, SocialEngine.conversationContext(restored, world, NPC, ALICE).familiarity());
    }

    @Test
    void renamePreservesTheUuidRelationshipAndViewsRemainImmutable() {
        WorldState world = world();
        SocialState social = social(world);
        talk(social, world);
        ConversationView before = SocialEngine.conversationContext(social, world, NPC, ALICE);
        assertTrue(SocialEngine.registerPlayerActor(social, world, ALICE.toUpperCase(java.util.Locale.ROOT), "新道号"));
        ConversationView renamed = SocialEngine.conversationContext(social, world, NPC, ALICE);
        assertEquals("新道号", renamed.playerName());
        assertEquals(before.familiarity(), renamed.familiarity());
        assertEquals(before.memories(), renamed.memories());
        assertEquals("Alice", before.playerName(), "earlier views are snapshots");
        assertThrows(UnsupportedOperationException.class, () -> renamed.memories().clear());
        var reverse = SocialEngine.relation(social, ActorRef.player(ALICE), ActorRef.npc(NPC)).orElseThrow();
        assertEquals(ActorRef.npc(NPC), reverse.memories().get(0).other());
        assertEquals(renamed.familiarity(), reverse.familiarity());
    }

    @Test
    void memoriesAreBoundedWithoutForgettingAcquaintanceOrReopeningPastDays() {
        WorldState world = world();
        SocialState social = social(world);
        for (int day = 0; day < 9; day++) {
            world.day = day;
            assertTrue(SocialEngine.rememberConversation(social, world, NPC, ALICE, 2, 3, 5));
        }
        social = SocialCodec.read(SocialCodec.write(social));
        ConversationView context = SocialEngine.conversationContext(social, world, NPC, ALICE);
        assertEquals(5, context.familiarity());
        assertEquals(List.of(8L, 7L), context.memories().stream().map(SocialMemoryView::day).toList());
        assertEquals(0, SocialEngine.relation(social, ActorRef.npc(NPC), ActorRef.player(ALICE))
                .orElseThrow().firstConversationDay());
        world.day = 0;
        assertFalse(talk(social, world), "evicted history must not reopen an old interaction day");
        assertEquals(5, SocialEngine.conversationContext(social, world, NPC, ALICE).familiarity());
    }

    @Test
    void invalidAndDeadNpcsCannotGainMemoriesAndDeathNeverResurrectsThem() {
        WorldState world = world();
        SocialState social = social(world);
        assertThrows(IllegalArgumentException.class,
                () -> SocialEngine.rememberConversation(social, world, 99, ALICE, 3, 2, 10));
        assertThrows(IllegalArgumentException.class,
                () -> SocialEngine.rememberConversation(social, world, NPC, BOB, 3, 2, 10));
        talk(social, world);
        world.persons.remove(NPC);
        Tombstone dead = new Tombstone();
        dead.id = NPC;
        dead.name = "林溪";
        world.tombstones.put(NPC, dead);
        assertFalse(SocialEngine.actor(social, world, ActorRef.npc(NPC)).orElseThrow().alive());
        JsonObject before = SocialCodec.write(social);
        assertEquals(SocialEngine.NPC_NOT_ALIVE, assertThrows(IllegalArgumentException.class,
                () -> talk(social, world)).getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> SocialEngine.conversationContext(social, world, NPC, ALICE));
        assertEquals(before, SocialCodec.write(social));
        assertTrue(world.persons.isEmpty());
    }

    @Test
    void legacyMembershipBackfillKeepsMembershipAndANewerObservedName() {
        WorldState world = world();
        PlayerMember member = new PlayerMember();
        member.playerId = ALICE;
        member.playerName = "OldName";
        member.sectId = 7;
        member.contribution = 23;
        member.joinedDay = 0;
        world.playerMembers.put(ALICE, member);
        SocialState social = SocialCodec.read(null);
        assertTrue(SocialEngine.backfillMembers(social, world));
        assertEquals(7, SocialEngine.actor(social, world, ActorRef.player(ALICE)).orElseThrow().sectId());
        SocialEngine.registerPlayerActor(social, world, ALICE, "NewName");
        assertFalse(SocialEngine.backfillMembers(social, world));
        assertEquals("NewName", SocialEngine.actor(social, world, ActorRef.player(ALICE)).orElseThrow().name());
        assertEquals("OldName", member.playerName);
        assertEquals(23, member.contribution);
        assertEquals(1, world.playerMembers.size());
    }

    @Test
    void canonicalCodecDoesNotDependOnRegistrationOrSameDayConversationOrder() {
        WorldState world = world();
        SocialState a = new SocialState();
        SocialState b = new SocialState();
        SocialEngine.registerPlayerActor(a, world, ALICE, "Alice");
        SocialEngine.registerPlayerActor(a, world, BOB, "Bob");
        SocialEngine.registerPlayerActor(b, world, BOB, "Bob");
        SocialEngine.registerPlayerActor(b, world, ALICE, "Alice");
        talk(a, world);
        SocialEngine.rememberConversation(a, world, NPC, BOB, 3, 2, 10);
        SocialEngine.rememberConversation(b, world, NPC, BOB, 3, 2, 10);
        talk(b, world);
        assertEquals(SocialCodec.write(a).toString(), SocialCodec.write(b).toString());
        assertEquals(SocialCodec.write(a), SocialCodec.write(SocialCodec.read(SocialCodec.write(a))));
    }

    @Test
    void codecRejectsDuplicateUuidPairsFractionalValuesAndUnboundedMemories() {
        WorldState world = world();
        SocialState social = social(world);
        talk(social, world);
        JsonObject duplicates = SocialCodec.write(social);
        JsonObject player = duplicates.getAsJsonArray("players").get(0).getAsJsonObject().deepCopy();
        player.addProperty("actor", "player:" + ALICE.toUpperCase(java.util.Locale.ROOT));
        duplicates.getAsJsonArray("players").add(player);
        assertThrows(SimFormatException.class, () -> SocialCodec.read(duplicates));
        JsonObject pairs = SocialCodec.write(social);
        pairs.getAsJsonArray("acquaintances").add(pairs.getAsJsonArray("acquaintances").get(0).deepCopy());
        assertThrows(SimFormatException.class, () -> SocialCodec.read(pairs));
        JsonObject fractional = SocialCodec.write(social);
        fractional.getAsJsonArray("acquaintances").get(0).getAsJsonObject().addProperty("familiarity", 1.5);
        assertThrows(SimFormatException.class, () -> SocialCodec.read(fractional));
        JsonObject excessive = SocialCodec.write(social);
        JsonArray memories = new JsonArray();
        for (int i = 0; i <= SocialEngine.MAX_MEMORY_LIMIT; i++) memories.add(i);
        JsonObject relation = excessive.getAsJsonArray("acquaintances").get(0).getAsJsonObject();
        relation.add("conversation_days", memories);
        relation.addProperty("last_conversation_day", SocialEngine.MAX_MEMORY_LIMIT);
        assertThrows(SimFormatException.class, () -> SocialCodec.read(excessive));
    }

    @Test
    void facadeRegistersAnUnaffiliatedPlayerAndPreservesMemoryAcrossFullWorldSave() {
        long seed = 91;
        WorldSim sim = SimFixtures.genesis(seed, "small", 6);
        int npc = sim.findPersons("", 1).get(0).id();
        assertTrue(sim.registerPlayerActor(ALICE, "Alice"));
        assertTrue(sim.playerMember(ALICE).isEmpty());
        assertFalse(sim.conversationContext(npc, ALICE).metBefore());
        assertTrue(sim.rememberConversation(npc, ALICE));
        var memory = sim.conversationContext(npc, ALICE);
        WorldSim restored = WorldSim.fromBytes(sim.toBytes(), SimFixtures.graph(seed), SimFixtures.data());
        assertEquals(memory, restored.conversationContext(npc, ALICE));
        assertFalse(restored.rememberConversation(npc, ALICE));
        assertTrue(restored.playerMember(ALICE).isEmpty());
        assertTrue(restored.registerPlayerActor(ALICE, "NewName"));
        assertEquals("NewName", restored.actor(ActorRef.player(ALICE)).orElseThrow().name());
    }

    @Test
    void versionThreeMembershipSavesBackfillIdentityWithoutChangingTheMember() {
        long seed = 92;
        WorldSim sim = SimFixtures.genesis(seed, "small", 6);
        sim.joinSect(ALICE, "Alice", sim.sects(false).get(0).id(),
                new PlayerQualification("mortal", 1, true, 2500), true);
        PlayerMemberView member = sim.playerMember(ALICE).orElseThrow();
        JsonObject old = JsonParser.parseString(new String(sim.toBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        old.addProperty("version", 3);
        old.remove("social");
        WorldSim restored = WorldSim.fromBytes(old.toString().getBytes(StandardCharsets.UTF_8),
                SimFixtures.graph(seed), SimFixtures.data());
        assertEquals(member, restored.playerMember(ALICE).orElseThrow());
        assertEquals("Alice", restored.actor(ActorRef.player(ALICE)).orElseThrow().name());
    }
}
