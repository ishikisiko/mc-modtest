package com.example.myvillage.client.immortal;

import com.example.myvillage.client.combat.ImmortalArmPoseBridge;
import com.example.myvillage.client.combat.ImmortalArmPoseBridge.ArmPose;
import com.example.myvillage.equipment.immortal.ImmortalMeshData.Vec3;
import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Continuous sleeve warp in bone-local pixels -> the existing IK's hand-render blocks.
 * Cross sections rotate without shrinking at the elbow; a C1 Hermite centerline passes through
 * the actual three joints. No cuts, replacement boxes, rest-mesh mutation or animation history. */
final class ImmortalArmDeformation {
    private static final float SHOULDER = ImmortalArmPoseBridge.SHOULDER_Y;
    private static final float ELBOW = ImmortalArmPoseBridge.ELBOW_Y;
    private static final float WRIST = ImmortalArmPoseBridge.WRIST_Y;
    private final Vector3f shoulder, elbow, wrist, upperTangent, elbowTangent, wristTangent;
    private final Quaternionf upper, forearm, fist;
    private final float centerX, pixel;
    private final Map<Vec3, Sample> samples = new HashMap<>();
    private record Sample(Vector3f point, Matrix3f normal) { }

    ImmortalArmDeformation(ArmPose pose, boolean slim, boolean right) {
        shoulder=pose.shoulder(); elbow=pose.elbow(); wrist=pose.wrist();
        upper=pose.upper(); forearm=pose.forearm(); fist=pose.fist();
        centerX=(right?-1:1)*(slim?.5f:1);
        pixel=pose.thickness()/16;
        upperTangent=new Vector3f(elbow).sub(shoulder).div(ELBOW-SHOULDER);
        Vector3f lower=new Vector3f(wrist).sub(elbow).div(WRIST-ELBOW);
        elbowTangent=new Vector3f(upperTangent).add(lower).mul(.5f);
        wristTangent=fist.transform(new Vector3f(0,pixel,0));
    }

    Vector3f position(float x,float y,float z) { return new Vector3f(sample(x,y,z).point); }

    Vector3f normal(float x,float y,float z,float nx,float ny,float nz) {
        return sample(x,y,z).normal.transform(new Vector3f(nx,ny,nz)).normalize();
    }

    private Sample sample(float x,float y,float z) {
        return samples.computeIfAbsent(new Vec3(x,y,z), ignored -> {
            Vector3f point=warp(x,y,z);
            Quaternionf frame=rotation(y);
            Vector3f dx=frame.transform(new Vector3f(pixel,0,0));
            Vector3f dz=frame.transform(new Vector3f(0,0,pixel));
            // Includes both changing cross-section orientation and nonuniform longitudinal fit.
            float h=.002f;
            Vector3f dy=warp(x,y+h,z).sub(warp(x,y-h,z)).div(2*h);
            Matrix3f jacobian=new Matrix3f(dx,dy,dz);
            Matrix3f normal=Math.abs(jacobian.determinant())>1e-12f
                    ?jacobian.invert().transpose():new Matrix3f().rotation(frame);
            return new Sample(point,normal);
        });
    }

    private Quaternionf rotation(float y) {
        // Smooth, bounded two-pixel joint influence bands. Quaternion interpolation avoids
        // linear matrix skinning's collapsing elbow cross section. Left uses the solver's
        // already-mirrored proper rotations and the left mesh; do not reflect it a second time.
        if(y<ELBOW+1) return new Quaternionf(upper).slerp(forearm,smooth(y,ELBOW-1,ELBOW+1));
        return new Quaternionf(forearm).slerp(fist,smooth(y,WRIST-1,WRIST+1));
    }

    private Vector3f warp(float x,float y,float z) {
        Vector3f center;
        if(y<SHOULDER) center=new Vector3f(shoulder).fma(y-SHOULDER,upperTangent);
        else if(y<ELBOW) center=hermite(shoulder,elbow,upperTangent,elbowTangent,SHOULDER,ELBOW,y);
        else if(y<WRIST) center=hermite(elbow,wrist,elbowTangent,wristTangent,ELBOW,WRIST,y);
        else center=new Vector3f(wrist).fma(y-WRIST,wristTangent);
        return center.add(rotation(y).transform(new Vector3f((x-centerX)*pixel,0,z*pixel)));
    }

    private static Vector3f hermite(Vector3f a,Vector3f b,Vector3f da,Vector3f db,float low,float high,float y) {
        float length=high-low,t=(y-low)/length,t2=t*t,t3=t2*t;
        return new Vector3f(a).mul(2*t3-3*t2+1).fma(t3-2*t2+t,new Vector3f(da).mul(length))
                .fma(-2*t3+3*t2,b).fma(t3-t2,new Vector3f(db).mul(length));
    }

    private static float smooth(float y,float low,float high) {
        float t=Math.max(0,Math.min(1,(y-low)/(high-low))); return t*t*(3-2*t);
    }
}
