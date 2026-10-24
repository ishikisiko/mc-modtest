package com.example.myvillage.regiongen;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BayTerracesLayoutTest {
    private String source() throws Exception {
        try (var input = getClass().getResourceAsStream(BayTerracesLayout.RESOURCE)) {
            assertNotNull(input);return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test void allRotatedCellsRetainOneChunkOwnerIncludingNegativeWorldCoordinates() {
        var layout=BayTerracesLayout.current();
        for (int turn=0;turn<4;turn++) for (int tz=0;tz<layout.size;tz+=16) for (int tx=0;tx<layout.size;tx+=16) {
            var chunks=new HashSet<BayTerracesLayout.Point>();
            for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++) {
                var p=layout.rotate(tx+dx,tz+dz,turn);
                assertEquals(new BayTerracesLayout.Point(tx+dx,tz+dz),layout.inverse(p.x(),p.z(),turn));
                chunks.add(new BayTerracesLayout.Point(Math.floorDiv(p.x()-640,16),Math.floorDiv(p.z()-112,16)));
            }
            assertEquals(1,chunks.size());
        }
    }

    @Test void outermostColumnsPreserveNativeHeightAndCoreHasExactAuthoredHeight() {
        var layout=BayTerracesLayout.current();
        for(int natural:new int[]{40,64,90,132})for(int yOffset:new int[]{-2,0,50}) {
            for(int k=-layout.margin;k<layout.size+layout.margin;k++) {
                assertEquals(natural,layout.surface(-layout.margin,k,natural,yOffset));
                assertEquals(natural,layout.surface(layout.size+layout.margin-1,k,natural,yOffset));
                assertEquals(natural,layout.surface(k,-layout.margin,natural,yOffset));
                assertEquals(natural,layout.surface(k,layout.size+layout.margin-1,natural,yOffset));
            }
            assertEquals(layout.sourceHeight(130,143)+yOffset,layout.surface(130,143,natural,yOffset));
        }
    }

    @Test void terrainInterpolationIsBoundedAndRotationDoesNotChangeItsWeight() {
        var layout=BayTerracesLayout.current();
        for(int z=-layout.margin;z<layout.size+layout.margin;z++)for(int x=-layout.margin;x<layout.size+layout.margin;x++) {
            double weight=layout.weight(x,z);assertTrue(weight>=0&&weight<=1);
            for(int turn=0;turn<4;turn++) {
                var p=layout.rotate(x,z,turn);assertEquals(weight,layout.weight(p.x(),p.z()));
            }
            int source=layout.sourceHeight(x,z),height=layout.surface(x,z,110,0);
            assertTrue(height>=Math.min(source,110)&&height<=Math.max(source,110));
        }
    }

    @Test void seedAndSiteBindingReplaysAndHasDifferentOrientations() {
        var layout=BayTerracesLayout.current();var turns=new HashSet<Long>();
        for(long seed:new long[]{0,1,-1,20261025,Long.MIN_VALUE,Long.MAX_VALUE})for(int cx:new int[]{-640,-1,0,1,640}) {
            long value=layout.siteSeed(seed,cx,-cx);
            assertEquals(value,layout.siteSeed(seed,cx,-cx));turns.add(value&3);
        }
        assertEquals(4,turns.size());
    }

    @Test void dataBoundsRejectTruncatedTerrainAndOutOfRangeReferenceFootprints() throws Exception {
        var data=JsonParser.parseString(source()).getAsJsonObject();
        var truncated=data.deepCopy();truncated.getAsJsonArray("heights").remove(0);
        assertThrows(IllegalArgumentException.class,()->new BayTerracesLayout(truncated.toString()));
        var tooLarge=data.deepCopy();tooLarge.getAsJsonObject("worldgen").addProperty("margin",32);
        assertThrows(IllegalArgumentException.class,()->new BayTerracesLayout(tooLarge.toString()));
        var fractional=data.deepCopy();fractional.getAsJsonObject("worldgen").addProperty("blend_width",2.5);
        assertThrows(IllegalArgumentException.class,()->new BayTerracesLayout(fractional.toString()));
        var duplicate=data.deepCopy();duplicate.getAsJsonArray("tiles").add(duplicate.getAsJsonArray("tiles").get(0).deepCopy());
        assertThrows(IllegalArgumentException.class,()->new BayTerracesLayout(duplicate.toString()));
    }

    @Test void callerCannotChangeSitingSamples() {
        var layout=BayTerracesLayout.current();int expected=layout.sampleOffsets()[0];
        int[] values=layout.sampleOffsets();values[0]=999;assertEquals(expected,layout.sampleOffsets()[0]);
    }
}
