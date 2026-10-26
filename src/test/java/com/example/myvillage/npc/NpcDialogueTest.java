package com.example.myvillage.npc;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.story.StorySessions;
import com.example.myvillage.story.net.StoryDialoguePayload;
import com.example.myvillage.story.net.StoryIntentPayload;
import com.example.myvillage.portrait.PortraitAssign;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class NpcDialogueTest {
    @Test void headlessPagesResolveChineseLabelsWithoutTheServersLocale() {
        assertEquals("炼气",NpcDialogueText.of(com.example.myvillage.sim.runtime.WorldSimText.realm("qi_refining")));
        assertEquals("采药",NpcDialogueText.of(com.example.myvillage.sim.runtime.life.NpcLifeInteraction.activityText("gather")));
        assertEquals("正在赶路",NpcDialogueText.of(net.minecraft.network.chat.Component.translatable("message.myvillage.life.phase.moving")));
        assertEquals("近况未明",NpcDialogueText.of(net.minecraft.network.chat.Component.translatable("world_sim.realm.unknown")));
        assertEquals("炼制丹药",NpcDialogueText.of(com.example.myvillage.sim.runtime.life.NpcLifeInteraction.activityText("brew")));
        assertTrue(NpcDialogueText.of(com.example.myvillage.sim.runtime.life.NpcLifeInteraction.reasonText("brew_pills")).contains("丹方"));
    }
    private static List<String> ids(List<StoryDialoguePayload.Option> options) { return options.stream().map(StoryDialoguePayload.Option::id).toList(); }
    @Test void temporaryRoguesCannotGainServicesEvenWithForgedRoleFlags() {
        assertTrue(NpcDialogueOptions.services(new NpcDialogueOptions.Capabilities(false,true,true,true,true,true)).isEmpty());
        assertEquals(List.of("chat","status","services_0","close"), ids(NpcDialogueOptions.home()));
        assertEquals(List.of("home"), ids(NpcDialogueOptions.servicesPage(new NpcDialogueOptions.Capabilities(false,false,false,false,false,false),0)));
    }
    @Test void sectMemberTopicDoesNotGrantOfficeAndIndependentServicesCompose() {
        assertEquals(List.of("sect_topic"), ids(NpcDialogueOptions.services(new NpcDialogueOptions.Capabilities(true,true,false,false,false,false))));
        assertEquals(List.of("story","trade","guide","sect_topic","sect_office"), ids(NpcDialogueOptions.services(new NpcDialogueOptions.Capabilities(true,true,true,true,true,true))));
        assertFalse(ids(NpcDialogueOptions.services(new NpcDialogueOptions.Capabilities(true,false,true,false,false,false))).contains("sect_office"));
    }
    @Test void allCapabilityCombinationsStayBoundedAndEveryServiceIsReachable() {
        for (int bits=0;bits<64;bits++) {
            var caps=new NpcDialogueOptions.Capabilities((bits&1)!=0,(bits&2)!=0,(bits&4)!=0,(bits&8)!=0,(bits&16)!=0,(bits&32)!=0);
            var seen=new HashSet<String>();
            for(int i=0;i<3;i++) {
                var options=NpcDialogueOptions.servicesPage(caps,i);
                assertTrue(options.size()<=4);
                assertEquals(options.size(),new HashSet<>(ids(options)).size());
                assertTrue(ids(options).contains("home"));
                seen.addAll(ids(options));
            }
            assertTrue(seen.containsAll(ids(NpcDialogueOptions.services(caps))));
        }
    }
    @Test void LongProfessionStatusIsReachableThroughBoundedPaperPages() {
        var lines = java.util.stream.IntStream.range(0, 37).mapToObj(i -> "药圃近况 " + i).toList();
        var seen = new ArrayList<String>();
        int page = 0;
        do {
            var current = NpcDialogueOptions.statusPage(lines, page);
            assertTrue(current.lines().size() <= StoryDialoguePayload.MAX_LINES);
            assertTrue(current.options().size() <= StoryDialoguePayload.MAX_OPTIONS);
            assertEquals(current.options().size(), new HashSet<>(ids(current.options())).size());
            assertTrue(ids(current.options()).containsAll(List.of("home", "close")));
            seen.addAll(current.lines());
            if (!ids(current.options()).contains("status_" + (page + 1))) break;
            page++;
        } while (page < 20);
        assertEquals(lines, seen);
        assertEquals(NpcDialogueOptions.statusPage(lines, 0), NpcDialogueOptions.statusPage(lines, -1));
        assertEquals(NpcDialogueOptions.statusPage(lines, page), NpcDialogueOptions.statusPage(lines, Integer.MAX_VALUE));
        String longLine = "药".repeat(255) + "🌿" + "药".repeat(300);
        var split = NpcDialogueOptions.statusPage(List.of(longLine), 0).lines();
        assertEquals(longLine, String.join("", split));
        assertTrue(split.stream().allMatch(s -> s.length() <= StoryDialoguePayload.MAX_LINE));
        assertFalse(Character.isHighSurrogate(split.getFirst().charAt(split.getFirst().length() - 1)));
        assertEquals(List.of("home", "close"), ids(NpcDialogueOptions.statusPage(List.of(), 0).options()));
    }

    @Test void StatusPaginationCannotSkipOfferedPagesOrReuseAChangedLifeLease() {
        var sessions = new StorySessions();
        UUID player = UUID.randomUUID(), entity = UUID.randomUUID();
        var identity = new StorySessions.Identity(entity, 12, 7, 21, "-1:rogue:at_sect", "minecraft:overworld", 0,
                "town:herb_child:40");
        var first = sessions.open(player, identity, 100, 100, Set.of("status_1", "home", "close"), false);
        assertNull(sessions.consume(player, first.nonce(), "status_5", identity, 101, true));
        assertNotNull(sessions.consume(player, first.nonce(), "status_1", identity, 101, true));
        var second = sessions.open(player, identity, 101, 100, Set.of("status_0", "status_2", "home", "close"), false);
        assertNull(sessions.consume(player, first.nonce(), "status_1", identity, 102, true));
        var replaced = new StorySessions.Identity(entity, 12, 7, 21, identity.role(), identity.dimension(), 0,
                "town:herb_child:41");
        assertNull(sessions.consume(player, second.nonce(), "status_2", replaced, 102, true));
    }
    private static StorySessions.Identity identity(UUID entity,int person,long epoch,String role,String dimension) {
        return new StorySessions.Identity(entity,12,person,epoch,role,dimension,0,"");
    }
    @Test void nonceRejectsReplaysUnseenOptionsOtherPlayerAndBodyReplacement() {
        var sessions=new StorySessions();var player=UUID.randomUUID();var entity=UUID.randomUUID();
        var id=identity(entity,-1,-1,"rogue","minecraft:overworld");
        var session=sessions.open(player,id,100,100,Set.of("chat","close"),false);
        assertNull(sessions.consume(UUID.randomUUID(),session.nonce(),"chat",id,101,true));
        assertNull(sessions.consume(player,session.nonce(),"sect_office",id,101,true));
        assertNotNull(sessions.consume(player,session.nonce(),"chat",id,101,true));
        assertNull(sessions.consume(player,session.nonce(),"chat",id,101,true));
        session=sessions.open(player,id,100,100,Set.of("chat"),false);
        assertNull(sessions.consume(player,session.nonce(),"chat",identity(UUID.randomUUID(),-1,-1,"rogue","minecraft:overworld"),101,true));
    }
    @Test void changesOfLeaseDimensionRoleExpiryAndEnvironmentInvalidateCapability() {
        UUID entity=UUID.randomUUID(),player=UUID.randomUUID();
        var id=identity(entity,7,21,"1:elder:at_sect","minecraft:overworld");
        for(var changed:List.of(identity(entity,7,22,id.role(),id.dimension()),identity(entity,8,21,id.role(),id.dimension()),
                identity(entity,7,21,"2:outer:at_sect",id.dimension()),identity(entity,7,21,id.role(),"minecraft:the_nether"))) {
            var sessions=new StorySessions();var s=sessions.open(player,id,100,100,Set.of("trade"),false);
            assertNull(sessions.consume(player,s.nonce(),"trade",changed,101,true));
        }
        for (boolean environment:List.of(false,true)) {
            var sessions=new StorySessions();var s=sessions.open(player,id,100,100,Set.of("trade"),false);
            assertNull(sessions.consume(player,s.nonce(),"trade",id,environment?200:101,environment));
        }
    }
    @Test void delayedCloseCannotCloseNewPageAndLogoutDropsCapability() {
        var sessions=new StorySessions();UUID player=UUID.randomUUID();var id=identity(UUID.randomUUID(),-1,-1,"rogue","minecraft:overworld");
        var a=sessions.open(player,id,100,100,Set.of("close"),false);
        var b=sessions.open(player,id,101,100,Set.of("close"),false);
        assertNull(sessions.consume(player,a.nonce(),"close",id,102,true));
        assertEquals(b,sessions.current(player));
        sessions.forget(player);assertNull(sessions.current(player));
    }
    @Test void independentWireChannelsRoundTripChineseAndRetainBounds() {
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY,ConnectionType.NEOFORGE);
        try {
            var portrait=PortraitAssign.of(11,false,"qi_refining","rogue",-1,20,new int[]{2000,2000,2000,2000,2000},new int[]{50,50,50,50,50},0,true);
            var page=new NpcDialoguePayload(new StoryDialoguePayload(12,42,"散修","无名散修",portrait,"交谈",List.of("道友近来可好？"),NpcDialogueOptions.home()));
            NpcDialoguePayload.STREAM_CODEC.encode(b,page);assertEquals(page,NpcDialoguePayload.STREAM_CODEC.decode(b));
            var intent=new NpcDialogueIntent(new StoryIntentPayload(12,42,"chat"));
            NpcDialogueIntent.STREAM_CODEC.encode(b,intent);assertEquals(intent,NpcDialogueIntent.STREAM_CODEC.decode(b));
            assertEquals(0,b.readableBytes());
            b.clear();b.writeVarInt(12);b.writeLong(42);b.writeUtf("bad option");
            assertThrows(DecoderException.class,()->NpcDialogueIntent.STREAM_CODEC.decode(b));
            assertNotEquals(NpcDialoguePayload.TYPE,StoryDialoguePayload.TYPE);
        } finally { b.release(); }
    }
    @Test void professionDetailsRoundTripEveryStatusPageWithinTheExistingWireBounds() {
        var resident = new com.example.myvillage.sim.profession.ProfessionView(7, "spirit_grower", 0, 0, 21, 1, 0, 0,
                Map.of("myvillage:spirit_leaf", 2), 3);
        var gardens = java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> new com.example.myvillage.sim.profession.ProfessionSupplyView.GardenView("garden_" + i,
                        "myvillage:spirit_leaf", 50, 200, i % 2 == 0)).toList();
        var lines = new ArrayList<>(NpcProfessionText.resident(resident));
        lines.addAll(NpcProfessionText.supply(new com.example.myvillage.sim.profession.ProfessionSupplyView("town",
                Map.of("myvillage:spirit_leaf", 4), 5, gardens), 100));
        var portrait = PortraitAssign.of(7, false, "qi_refining", "rogue", -1, 20,
                new int[]{2000,2000,2000,2000,2000}, new int[]{50,50,50,50,50}, 0, true);
        var b = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        try {
            var seen = new ArrayList<String>();
            for (int i = 0; i < 20; i++) {
                var page = NpcDialogueOptions.statusPage(lines, i);
                var payload = new NpcDialoguePayload(new StoryDialoguePayload(12, 42 + i, "灵植师", "药圃居民",
                        portrait, "询问近况", page.lines(), page.options()));
                NpcDialoguePayload.STREAM_CODEC.encode(b, payload);
                assertEquals(payload, NpcDialoguePayload.STREAM_CODEC.decode(b));
                assertEquals(0, b.readableBytes()); b.clear();
                seen.addAll(page.lines());
                if (!ids(page.options()).contains("status_" + (i + 1))) break;
            }
            assertEquals(lines, seen);
        } finally { b.release(); }
    }
    @Test void authoredRulesAreLoadedAndRejectNonfiniteOrUnboundedValues() {
        var r=NpcInteractionRules.get();assertTrue(r.wanderRadius()>0);assertTrue(r.pauseTicks()<=r.sessionTicks());
        assertThrows(IllegalArgumentException.class,()->new NpcInteractionRules(1,Double.NaN,600,100,4,256,12));
        assertThrows(IllegalArgumentException.class,()->new NpcInteractionRules(1,6,600,100,4,256,0));
        assertThrows(IllegalArgumentException.class,()->new NpcInteractionRules(1,6,600,601,4,256,12));
    }
}
