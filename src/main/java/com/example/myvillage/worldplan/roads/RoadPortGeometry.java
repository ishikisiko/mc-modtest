package com.example.myvillage.worldplan.roads;

/** 四向城门的同一坐标约定；门内只读，写权限从城域外第一列开始。 */
public final class RoadPortGeometry {
    public enum Facing {
        NORTH(0,-1),EAST(1,0),SOUTH(0,1),WEST(-1,0);
        public final int dx,dz;
        Facing(int x,int z){dx=x;dz=z;}
        public static Facing parse(String name){try{return valueOf(name.toUpperCase(java.util.Locale.ROOT));}catch(RuntimeException e){throw new IllegalArgumentException("出口朝向不合法："+name);}}
    }
    public record Frame(int gateX,int gateZ,int edgeX,int edgeZ,int dx,int dz,int insideRows,int escapeDistance) {
        public int tx(){return dz;}
        public int tz(){return -dx;}
        public RoadAStar.Point edge(){return new RoadAStar.Point(edgeX,edgeZ);}
        public RoadAStar.Point outside(int distance){return new RoadAStar.Point(edgeX+dx*distance,edgeZ+dz*distance);}
        public boolean permits(int x,int z,int radius){int a=(x-edgeX)*dx+(z-edgeZ)*dz,b=(x-edgeX)*tx()+(z-edgeZ)*tz();return a>=0&&a<=escapeDistance+radius&&Math.abs(b)<=radius;}
    }
    private RoadPortGeometry() {}
    public static Frame frame(RoadRules.Port p,int originX,int originZ,int margin,int width,int gridStep) {
        int x=originX+p.x(),z=originZ+p.z(),ex=x,ez=z;
        switch(p.facing()){case SOUTH->ez=originZ+p.depth();case NORTH->ez=originZ-1;case EAST->ex=originX+p.width();case WEST->ex=originX-1;}
        int rows=Math.abs(ex-x)+Math.abs(ez-z);
        if(rows<1||rows>Math.max(p.width(),p.depth()))throw new IllegalArgumentException("出口不在城域边缘方向");
        return new Frame(x,z,ex,ez,p.facing().dx,p.facing().dz,rows,margin+width/2+gridStep*2);
    }
}
