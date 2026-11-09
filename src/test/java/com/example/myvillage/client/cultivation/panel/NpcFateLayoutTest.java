package com.example.myvillage.client.cultivation.panel;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.client.story.StoryLayout;
import org.junit.jupiter.api.Test;

class NpcFateLayoutTest {
    @Test void allCardsFitTheirColumnAndRemainReachableInNarrowScrollingBodies() {
        for (int[] screen : new int[][]{{320,240},{427,240},{640,360},{854,480}}) {
            var paper = StoryLayout.paper(screen[0], screen[1], 4);
            var body = paper.body();
            int total = NpcFateLayout.height(body.width());
            for (int i=0;i<3;i++) {
                var card = NpcFateLayout.card(body.x(), body.y(), body.width(), i);
                assertTrue(card.x()>=body.x() && card.x()+card.width()<=body.right());
                assertTrue(card.width()>0 && card.y()+card.height()<=body.y()+total);
                for (int j=i+1;j<3;j++) {
                    var other = NpcFateLayout.card(body.x(),body.y(),body.width(),j);
                    assertTrue(card.x()+card.width()<=other.x() || other.x()+other.width()<=card.x()
                            || card.y()+card.height()<=other.y() || other.y()+other.height()<=card.y());
                }
            }
            int scroll = StoryLayout.scroll(Integer.MAX_VALUE,total,body.height());
            var last = NpcFateLayout.card(body.x(),body.y(),body.width(),2);
            assertTrue(last.y()+last.height()-scroll<=body.bottom());
            assertTrue(body.bottom()<=paper.option(0).y());
        }
    }
    @Test void changesColumnsAtReadableWidthsAndRejectsNonexistentCards() {
        assertEquals(1,NpcFateLayout.columns(160));
        assertEquals(2,NpcFateLayout.columns(280));
        assertEquals(3,NpcFateLayout.columns(480));
        assertThrows(IllegalArgumentException.class,()->NpcFateLayout.card(0,0,0,0));
        assertThrows(IllegalArgumentException.class,()->NpcFateLayout.card(0,0,400,3));
    }
}
