package com.example.myvillage.client.cultivation.motion;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Immutable generated pose definition, in ordinary Minecraft skin pixels. */
public record MeditationMotion(int exitTicks, int breathTicks, float breathPixels, float headPitch,
        float jointBand, Map<String, Directions> limbs) {
    public record Directions(Vector3f upper, Vector3f lower) {}
    public static MeditationMotion parse(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        if(root.get("schema").getAsInt()!=1) throw new IllegalArgumentException("Unknown meditation motion schema");
        Map<String,Directions> limbs = new HashMap<>();
        for(String bone: new String[]{"right_arm","left_arm","right_leg","left_leg"}) {
            JsonObject o=root.getAsJsonObject("limbs").getAsJsonObject(bone);
            limbs.put(bone,new Directions(vector(o,"upper"),vector(o,"lower")));
        }
        return new MeditationMotion(root.get("exit_ticks").getAsInt(),root.get("breath_ticks").getAsInt(),
                root.get("breath_pixels").getAsFloat(),(float)Math.toRadians(root.get("head_pitch_degrees").getAsFloat()),
                root.get("joint_band_pixels").getAsFloat(),Map.copyOf(limbs));
    }
    public MeditationMotion {
        if(exitTicks<1||exitTicks>200||breathTicks<1||breathTicks>200||!Float.isFinite(breathPixels)
                ||breathPixels<0||breathPixels>.5f||!Float.isFinite(headPitch)||Math.abs(headPitch)>.5f
                ||!Float.isFinite(jointBand)||jointBand<.5f||jointBand>2)
            throw new IllegalArgumentException("Invalid meditation motion limits");
        limbs=Map.copyOf(limbs);
    }
    private static Vector3f vector(JsonObject o,String name) {
        var a=o.getAsJsonArray(name);
        if(a.size()!=3) throw new IllegalArgumentException("Expected direction vector");
        Vector3f v=new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());
        if(!v.isFinite()||v.lengthSquared()<.01f)throw new IllegalArgumentException("Invalid direction");
        return v.normalize();
    }
    public static MeditationMotion bundled() {
        try(var input=MeditationMotion.class.getResourceAsStream("/assets/myvillage/cultivation/meditation_motion.json")) {
            if(input==null)throw new IllegalStateException("Missing meditation motion resource");
            return parse(new InputStreamReader(input,StandardCharsets.UTF_8));
        } catch(java.io.IOException e) {throw new IllegalStateException(e);}
    }
    public MeditationWarp warp(String bone,float amount,boolean slim) {
        Directions d=limbs.get(bone);
        Quaternionf upper=new Quaternionf().slerp(new Quaternionf().rotationTo(new Vector3f(0,1,0),d.upper),amount);
        Quaternionf lower=new Quaternionf().slerp(new Quaternionf().rotationTo(new Vector3f(0,1,0),d.lower),amount);
        boolean arm=bone.endsWith("arm");
        float center=arm?(bone.startsWith("right")?-1:1)*(slim?.5f:1):0;
        return new MeditationWarp(upper,lower,center,arm?-2:0,jointBand);
    }
}
