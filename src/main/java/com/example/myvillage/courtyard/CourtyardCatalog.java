package com.example.myvillage.courtyard;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Immutable registry catalogue. Generated together with the baked block models. */
public final class CourtyardCatalog {
    public static final String RESOURCE = "/data/myvillage/courtyard/pieces.json";

    public record Box(int x0, int y0, int z0, int x1, int y1, int z1) {
        public Box {
            if (x0<0 || y0<0 || z0<0 || x1>16 || y1>16 || z1>16 || x0>=x1 || y0>=y1 || z0>=z1)
                throw new IllegalArgumentException("Invalid courtyard box");
        }
        public Box clockwise() { return new Box(16-z1,y0,x0,16-z0,y1,x1); }
        public Box reflected() { return new Box(16-x1,y0,z0,16-x0,y1,z1); }
        public VoxelShape shape() { return Block.box(x0,y0,z0,x1,y1,z1); }
    }

    public static final class Piece {
        private final String id, family, sound;
        private final float strength;
        private final boolean full;
        private final int light;
        private final List<Box> boxes;
        private final VoxelShape[][] shapes = new VoxelShape[2][4];

        Piece(String id, String family, String sound, float strength, boolean full, int light, List<Box> boxes) {
            if (!id.matches("courtyard_[a-z]+_[0-9a-f]{12}") || boxes.isEmpty() || boxes.size()>64
                    || !Float.isFinite(strength) || strength<=0 || light<0 || light>15)
                throw new IllegalArgumentException("Invalid courtyard piece: "+id);
            this.id=id;this.family=family;this.sound=sound;this.strength=strength;this.full=full;this.light=light;
            this.boxes=List.copyOf(boxes);
            // Immutable cache: no hot-path Shapes.or / optimization during rendering or walking.
            for (int m=0;m<2;m++) {
                var rotated=boxes.stream().map(b->b).toList();
                if (m==1) rotated=rotated.stream().map(Box::reflected).toList();
                for (int turn=0;turn<4;turn++) {
                    VoxelShape shape=Shapes.empty();
                    for (Box box:rotated) shape=Shapes.or(shape,box.shape());
                    shapes[m][turn]=shape.optimize();
                    rotated=rotated.stream().map(Box::clockwise).toList();
                }
            }
        }
        public String id(){return id;}
        public String family(){return family;}
        public String sound(){return sound;}
        public float strength(){return strength;}
        public boolean full(){return full;}
        public int light(){return light;}
        public List<Box> boxes(){return boxes;}
        public VoxelShape shape(Direction facing, boolean mirrored) { return shapes[mirrored?1:0][turn(facing)]; }
    }

    public static int turn(Direction facing) {
        return switch(facing){case NORTH->0;case EAST->1;case SOUTH->2;case WEST->3;default->throw new IllegalArgumentException("Horizontal facing required");};
    }
    public static Direction facing(int turn) { return switch(Math.floorMod(turn,4)){case 0->Direction.NORTH;case 1->Direction.EAST;case 2->Direction.SOUTH;default->Direction.WEST;}; }
    private static final class Holder { static final Map<String,Piece> PIECES=load(); }
    public static Collection<Piece> all(){return Holder.PIECES.values();}
    public static Piece get(String id) {
        Piece piece=Holder.PIECES.get(id);
        if (piece==null) throw new IllegalArgumentException("Unknown courtyard piece "+id);
        return piece;
    }
    private static Map<String,Piece> load() {
        try(var stream=CourtyardCatalog.class.getResourceAsStream(RESOURCE)) {
            if(stream==null)throw new IllegalStateException("Missing "+RESOURCE);
            var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("format").getAsInt()!=1)throw new IllegalStateException("Unknown courtyard catalogue format");
            Map<String,Piece> result=new LinkedHashMap<>();
            for(var element:root.getAsJsonArray("pieces")) {
                var p=element.getAsJsonObject();List<Box> boxes=new ArrayList<>();
                for(var elementBox:p.getAsJsonArray("boxes")) {
                    var b=elementBox.getAsJsonArray();if(b.size()!=6)throw new IllegalArgumentException("Invalid box length");
                    int[] v=new int[6];for(int i=0;i<6;i++){double n=b.get(i).getAsDouble();if(n!=(int)n)throw new IllegalArgumentException("Fractional voxel");v[i]=(int)n;}
                    boxes.add(new Box(v[0],v[1],v[2],v[3],v[4],v[5]));
                }
                var piece=new Piece(p.get("id").getAsString(),p.get("family").getAsString(),p.get("sound").getAsString(),p.get("strength").getAsFloat(),p.get("full").getAsBoolean(),p.get("light").getAsInt(),boxes);
                if(result.put(piece.id(),piece)!=null)throw new IllegalStateException("Duplicate courtyard ID");
            }
            if(result.isEmpty())throw new IllegalStateException("Empty courtyard catalogue");
            return Collections.unmodifiableMap(result);
        } catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private CourtyardCatalog(){}
}
