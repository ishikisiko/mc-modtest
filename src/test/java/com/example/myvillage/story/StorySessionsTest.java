package com.example.myvillage.story;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorySessionsTest {
    private final UUID player=new UUID(1,2),other=new UUID(3,4);
    private StorySessions.Identity identity(long epoch,long revision){return new StorySessions.Identity(new UUID(5,6),10,20,epoch,"guide","minecraft:overworld",revision,"qianzhen_test");}
    @Test void nonceIsOneUseAndOldPacketCannotEraseNewPage() {
        var b=new StorySessions();var first=b.open(player,identity(1,0),0,600,Set.of("learn","close"),false);
        assertNull(b.consume(player,first.nonce(),"accept",identity(1,0),1,true));
        assertNotNull(b.consume(player,first.nonce(),"learn",identity(1,0),1,true));
        assertNull(b.consume(player,first.nonce(),"learn",identity(1,0),1,true));
        var second=b.open(player,identity(1,0),1,600,Set.of("accept","close"),true);
        assertNotEquals(first.nonce(),second.nonce());assertNull(b.consume(player,first.nonce(),"close",identity(1,0),2,true));assertSame(second,b.current(player));
        assertNotNull(b.consume(player,second.nonce(),"accept",identity(1,0),2,true));
    }
    @Test void rejectsDistanceEpochRevisionExpiryAndOtherPlayer() {
        for(int scenario=0;scenario<5;scenario++) {
            var b=new StorySessions();var s=b.open(player,identity(7,9),10,20,Set.of("accept"),true);
            var actual=scenario==1?identity(8,9):scenario==2?identity(7,10):identity(7,9);
            assertNull(b.consume(scenario==4?other:player,s.nonce(),"accept",actual,scenario==3?30:11,scenario!=0));
        }
    }
    @Test void entityPersonRoleSiteAndDimensionAreBound() {
        var base=identity(1,2);
        var alternatives=List.of(new StorySessions.Identity(new UUID(8,9),10,20,1,"guide",base.dimension(),2,base.site()),
                new StorySessions.Identity(base.entity(),11,20,1,"guide",base.dimension(),2,base.site()),
                new StorySessions.Identity(base.entity(),10,21,1,"guide",base.dimension(),2,base.site()),
                new StorySessions.Identity(base.entity(),10,20,1,"warden",base.dimension(),2,base.site()),
                new StorySessions.Identity(base.entity(),10,20,1,"guide","minecraft:the_nether",2,base.site()),
                new StorySessions.Identity(base.entity(),10,20,1,"guide",base.dimension(),2,"other_site"));
        for(var actual:alternatives) {var b=new StorySessions();var s=b.open(player,base,0,600,Set.of("accept"),true);assertNull(b.consume(player,s.nonce(),"accept",actual,1,true));}
    }
    @Test void multiplayerSessionsAreIndependentAndForgetRevokes() {
        var b=new StorySessions();var a=b.open(player,identity(1,0),0,600,Set.of("accept"),true);var c=b.open(other,identity(1,0),0,600,Set.of("accept"),true);
        assertNull(b.consume(other,a.nonce(),"accept",identity(1,0),1,true));assertSame(c,b.current(other));
        b.forget(player);assertNull(b.consume(player,a.nonce(),"accept",identity(1,0),1,true));assertNotNull(b.consume(other,c.nonce(),"accept",identity(1,0),1,true));
        b.open(player,identity(1,0),0,600,Set.of("accept"),true);b.clear();assertNull(b.current(player));
    }
}
