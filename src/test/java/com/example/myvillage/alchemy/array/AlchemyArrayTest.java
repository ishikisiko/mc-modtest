package com.example.myvillage.alchemy.array;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class AlchemyArrayTest {
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    static final class Grid implements AlchemyArrayPattern.View {
        final Map<BlockPos,AlchemyArrayPattern.Part> parts=new HashMap<>();final Set<BlockPos> unloaded=new HashSet<>();
        public boolean loaded(BlockPos p){return !unloaded.contains(p);}
        public AlchemyArrayPattern.Part part(BlockPos p){assertTrue(loaded(p),"Must not query unloaded position");return parts.getOrDefault(p,new AlchemyArrayPattern.Part(AlchemyArrayRules.Role.AIR,Direction.NORTH));}
        void add(BlockPos origin,Direction facing){for(var c:AlchemyArrayRules.get().cells())parts.put(AlchemyArrayPattern.worldPos(origin,facing,c),new AlchemyArrayPattern.Part(c.role(),AlchemyArrayPattern.outward(facing,c.role())));}
    }
    @Test void fourRotationsPreserveAll27CellsAndOutwardPorts(){
        for(Direction facing:Direction.Plane.HORIZONTAL){Grid g=new Grid();BlockPos root=new BlockPos(-16,80,15);g.add(root,facing);
            assertEquals(27,g.parts.size());assertTrue(AlchemyArrayPattern.check(g,root,facing).formed());
            var output=AlchemyArrayRules.get().cells().stream().filter(c->c.role()==AlchemyArrayRules.Role.OUTPUT).findFirst().orElseThrow();
            assertEquals(-1,output.y(),"Output must allow a vanilla hopper below the array");
            assertEquals(facing.getClockWise(),g.part(AlchemyArrayPattern.worldPos(root,facing,output)).facing());
        }
    }
    @Test void missingCasingBlockedAirWrongPortAndUnloadedBoundaryRejectWithoutReadingUnloadedBlocks(){
        BlockPos root=new BlockPos(15,70,15);Direction facing=Direction.NORTH;
        for(var cell:AlchemyArrayRules.get().cells()){
            Grid g=new Grid();g.add(root,facing);BlockPos p=AlchemyArrayPattern.worldPos(root,facing,cell);
            g.unloaded.add(p);assertFalse(AlchemyArrayPattern.check(g,root,facing).formed());
            g.unloaded.clear();g.parts.put(p,new AlchemyArrayPattern.Part(cell.role()==AlchemyArrayRules.Role.AIR?AlchemyArrayRules.Role.CASING:AlchemyArrayRules.Role.AIR,facing));
            var check=AlchemyArrayPattern.raw(g,root,facing);assertFalse(check.formed());assertEquals(1,check.mismatchCount());assertEquals(cell,AlchemyArrayRules.get().cells().get(check.mismatchIndex()));
        }
        Grid wrong=new Grid();wrong.add(root,facing);var in=AlchemyArrayRules.get().cells().stream().filter(c->c.role()==AlchemyArrayRules.Role.INPUT).findFirst().orElseThrow();
        wrong.parts.put(AlchemyArrayPattern.worldPos(root,facing,in),new AlchemyArrayPattern.Part(in.role(),facing));assertFalse(AlchemyArrayPattern.check(wrong,root,facing).formed());
    }
    @Test void adjacentIndependentRotationsDoNotTriggerFalseSharedOwnership(){
        for(Direction facing:Direction.Plane.HORIZONTAL)for(Direction other:Direction.Plane.HORIZONTAL){
            Grid grid=new Grid();BlockPos first=new BlockPos(0,64,0),second=new BlockPos(5,64,0);grid.add(first,facing);grid.add(second,other);
            assertTrue(AlchemyArrayPattern.check(grid,first,facing).formed());assertTrue(AlchemyArrayPattern.check(grid,second,other).formed());
        }
    }
    @Test void outputPlanningPreservesComponentsConservesCountsAndNeverPartiallyMutates(){
        ItemStack base=new ItemStack(Items.APPLE,63),special=new ItemStack(Items.APPLE,62);special.set(DataComponents.CUSTOM_NAME,Component.literal("上品"));
        ItemStack result=special.copyWithCount(4);List<ItemStack> outputs=List.of(base,special,ItemStack.EMPTY,ItemStack.EMPTY);
        var planned=AlchemyArrayTransactions.outputPlan(outputs,result,64);assertNotNull(planned);
        assertEquals(63,base.getCount());assertEquals(62,special.getCount());assertEquals(4,result.getCount());
        assertEquals(129,planned.stream().mapToInt(ItemStack::getCount).sum());assertEquals(63,planned.get(0).getCount());assertEquals(64,planned.get(1).getCount());assertTrue(ItemStack.isSameItemSameComponents(result,planned.get(2)));
        assertNull(AlchemyArrayTransactions.outputPlan(List.of(base,new ItemStack(Items.STONE,64),new ItemStack(Items.DIRT,64),new ItemStack(Items.COBBLESTONE,64)),result,64));assertEquals(63,base.getCount());
    }
    @Test void reservedInputsPermitAdditionsButRejectCountReductionAndIdentityReplacement(){
        ItemStack herb=new ItemStack(Items.APPLE,1),fuel=new ItemStack(Items.EMERALD,3);var required=List.of(herb,fuel);
        assertTrue(AlchemyArrayTransactions.reservedPresent(List.of(herb.copyWithCount(64),fuel.copyWithCount(64)),required));
        assertFalse(AlchemyArrayTransactions.reservedPresent(List.of(herb,fuel.copyWithCount(2)),required));
        ItemStack renamed=herb.copy();renamed.set(DataComponents.CUSTOM_NAME,Component.literal("替换"));assertFalse(AlchemyArrayTransactions.reservedPresent(List.of(renamed,fuel),required));
    }
    @Test void malformedSchemaDuplicateCoordinatesFractionalIntervalsAndUnknownFieldsReject()throws Exception{
        String text;try(var stream=AlchemyArrayTest.class.getResourceAsStream("/data/myvillage/alchemy/array.json")){text=new String(Objects.requireNonNull(stream).readAllBytes(),StandardCharsets.UTF_8);}
        assertEquals(27,AlchemyArrayRules.parse(new StringReader(text)).cells().size());
        JsonObject original=JsonParser.parseString(text).getAsJsonObject();
        for(String field:List.of("version","smoke_interval_ticks","light_level","assembly_ticks")){
            JsonObject bad=original.deepCopy();bad.addProperty(field,1.5);assertThrows(IllegalArgumentException.class,()->AlchemyArrayRules.parse(new StringReader(bad.toString())));
        }
        for(String field:List.of("ring_speed_degrees","core_speed_degrees","core_bob")){
            JsonObject bad=original.deepCopy();bad.addProperty(field,-0.01);assertThrows(IllegalArgumentException.class,()->AlchemyArrayRules.parse(new StringReader(bad.toString())));
            JsonObject wrongType=original.deepCopy();wrongType.addProperty(field,"fast");assertThrows(IllegalArgumentException.class,()->AlchemyArrayRules.parse(new StringReader(wrongType.toString())));
        }
        JsonObject tooFast=original.deepCopy();tooFast.addProperty("core_speed_degrees",20.01);assertThrows(IllegalArgumentException.class,()->AlchemyArrayRules.parse(new StringReader(tooFast.toString())));
        JsonObject tooHigh=original.deepCopy();tooHigh.addProperty("core_bob",0.26);assertThrows(IllegalArgumentException.class,()->AlchemyArrayRules.parse(new StringReader(tooHigh.toString())));
        JsonObject duplicate=original.deepCopy();duplicate.getAsJsonArray("cells").set(1,duplicate.getAsJsonArray("cells").get(0));assertThrows(IllegalArgumentException.class,()->AlchemyArrayRules.parse(new StringReader(duplicate.toString())));
        JsonObject unknown=original.deepCopy();unknown.addProperty("extra",1);assertThrows(IllegalArgumentException.class,()->AlchemyArrayRules.parse(new StringReader(unknown.toString())));
        assertThrows(IllegalArgumentException.class,()->AlchemyArrayRules.parse(new StringReader(text.replace("\"version\": 1","\"version\": 1, \"version\": 1"))));
    }
}
