package com.example.myvillage.client.manor;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;

/** 沿相邻格边界裁去玻璃接面；顶点次序、法线和原贴图坐标一起保留。 */
final class ManorQuadClipper {
    private static final double EPS=1.0e-6;
    record Rect(double u0,double v0,double u1,double v1) { }
    static int axis(Direction face){return face.getAxis().ordinal();}
    static int u(int axis){return axis==0?2:0;}
    static int v(int axis){return axis==1?2:1;}
    private static double value(int[] vertices,int stride,int vertex,int field){return Float.intBitsToFloat(vertices[vertex*stride+field]);}

    static List<BakedQuad> clip(BakedQuad quad,List<Rect> covers) {
        if(covers.isEmpty())return List.of(quad);
        int[] source=quad.getVertices();int stride=source.length/4;
        int axis=axis(quad.getDirection()),u=u(axis),v=v(axis);
        double plane=quad.getDirection().getAxisDirection()==Direction.AxisDirection.POSITIVE?1:0;
        double u0=1,u1=0,v0=1,v1=0;
        for(int i=0;i<4;i++){
            if(Math.abs(value(source,stride,i,axis)-plane)>EPS)return List.of(quad);
            double a=value(source,stride,i,u),b=value(source,stride,i,v);
            u0=Math.min(u0,a);u1=Math.max(u1,a);v0=Math.min(v0,b);v1=Math.max(v1,b);
        }
        Rect whole=new Rect(u0,v0,u1,v1);List<Rect> remaining=List.of(whole);
        for(Rect cover:covers){
            List<Rect> next=new ArrayList<>();
            for(Rect r:remaining){
                double a=Math.max(r.u0,cover.u0),b=Math.max(r.v0,cover.v0),c=Math.min(r.u1,cover.u1),d=Math.min(r.v1,cover.v1);
                if(c-a<=EPS||d-b<=EPS){next.add(r);continue;}
                for(Rect part:List.of(new Rect(r.u0,r.v0,a,r.v1),new Rect(c,r.v0,r.u1,r.v1),new Rect(a,r.v0,c,b),new Rect(a,d,c,r.v1)))
                    if(part.u1-part.u0>EPS&&part.v1-part.v0>EPS)next.add(part);
            }
            remaining=next;if(remaining.isEmpty())return List.of();
        }
        if(remaining.size()==1&&remaining.getFirst().equals(whole))return List.of(quad);
        List<BakedQuad> result=new ArrayList<>(remaining.size());
        for(Rect r:remaining){
            int[] out=source.clone();
            for(int i=0;i<4;i++){
                double a=value(source,stride,i,u)<(u0+u1)/2?r.u0:r.u1;
                double b=value(source,stride,i,v)<(v0+v1)/2?r.v0:r.v1;
                out[i*stride+u]=Float.floatToRawIntBits((float)a);out[i*stride+v]=Float.floatToRawIntBits((float)b);
                double s=(a-u0)/(u1-u0),t=(b-v0)/(v1-v0);
                for(int field=4;field<=5;field++){
                    double uv=0;
                    for(int j=0;j<4;j++){
                        double weight=(value(source,stride,j,u)<(u0+u1)/2?1-s:s)*(value(source,stride,j,v)<(v0+v1)/2?1-t:t);
                        uv+=weight*value(source,stride,j,field);
                    }
                    out[i*stride+field]=Float.floatToRawIntBits((float)uv);
                }
            }
            result.add(new BakedQuad(out,quad.getTintIndex(),quad.getDirection(),quad.getSprite(),quad.isShade(),quad.hasAmbientOcclusion()));
        }
        return result;
    }
    private ManorQuadClipper(){ }
}
