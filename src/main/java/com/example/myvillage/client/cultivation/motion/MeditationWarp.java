package com.example.myvillage.client.cultivation.motion;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Continuous two-joint skin warp; original UVs, silhouette widths and material passes survive. */
public final class MeditationWarp {
    private final Quaternionf upper,lower;
    private final Vector3f a,b,c,da,db,dc;
    private final float centerX,start,joint,end,band;
    MeditationWarp(Quaternionf upper,Quaternionf lower,float centerX,float start,float band) {
        this.upper=new Quaternionf(upper);this.lower=new Quaternionf(lower);
        this.centerX=centerX;this.start=start;this.joint=start+6;this.end=start+12;this.band=band;
        a=new Vector3f(centerX,start,0);
        da=upper.transform(new Vector3f(0,1,0));dc=lower.transform(new Vector3f(0,1,0));
        b=new Vector3f(a).fma(6,da);c=new Vector3f(b).fma(6,dc);db=new Vector3f(da).add(dc).mul(.5f);
    }
    private Quaternionf rotation(float y) {
        float t=Math.clamp((y-joint+band)/(2*band),0,1);t=t*t*(3-2*t);
        return new Quaternionf(upper).slerp(lower,t);
    }
    public Vector3f position(float x,float y,float z) {
        Vector3f p;
        if(y<start)p=new Vector3f(a).fma(y-start,da);
        else if(y<joint)p=hermite(a,b,da,db,(y-start)/6);
        else if(y<end)p=hermite(b,c,db,dc,(y-joint)/6);
        else p=new Vector3f(c).fma(y-end,dc);
        return p.add(rotation(y).transform(new Vector3f(x-centerX,0,z)));
    }
    public Vector3f normal(float x,float y,float z,float nx,float ny,float nz) {
        Quaternionf frame=rotation(y);
        Vector3f dx=frame.transform(new Vector3f(1,0,0)),dz=frame.transform(new Vector3f(0,0,1));
        float h=.002f;
        Vector3f dy=position(x,y+h,z).sub(position(x,y-h,z)).div(2*h);
        Matrix3f jacobian=new Matrix3f(dx,dy,dz);
        Matrix3f normal=Math.abs(jacobian.determinant())>1e-6f?jacobian.invert().transpose():new Matrix3f().rotation(frame);
        return normal.transform(new Vector3f(nx,ny,nz)).normalize();
    }
    private static Vector3f hermite(Vector3f a,Vector3f b,Vector3f da,Vector3f db,float t) {
        float t2=t*t,t3=t2*t;
        return new Vector3f(a).mul(2*t3-3*t2+1).fma(6*(t3-2*t2+t),da)
                .fma(-2*t3+3*t2,b).fma(6*(t3-t2),db);
    }
}
