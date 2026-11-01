package com.example.myvillage.worldplan.roads;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 护栏尺寸、危险判定和模型几何的唯一运行时输入。 */
public final class RoadGuardrailRules {
    public final String id,name,mode;
    public final int minDrop2,probeDistance,runPadding,endGap,maxJoinStep2;
    public final List<double[]> post,arms,risers,collisionPost,collisionArms;
    public static RoadGuardrailRules load() {
        return RoadRules.load().guard;
    }

    public RoadGuardrailRules(JsonObject r) {
        if(r.get("schema").getAsInt()!=1)throw new IllegalArgumentException("护栏配置格式");
        id=r.get("id").getAsString();name=r.get("name").getAsString();mode=r.get("mode").getAsString();
        minDrop2=r.get("min_drop2").getAsInt();probeDistance=r.get("probe_distance").getAsInt();runPadding=r.get("run_padding").getAsInt();
        endGap=r.get("end_gap").getAsInt();maxJoinStep2=r.get("max_join_step2").getAsInt();
        if(!mode.equals("hazards")||minDrop2<2||probeDistance<1||probeDistance>8||runPadding<0||runPadding>4||endGap<1||endGap>32||maxJoinStep2!=2)throw new IllegalArgumentException("护栏判定越界");
        post=boxes(r,"post");arms=boxes(r,"arms");risers=boxes(r,"risers");collisionPost=boxes(r,"collision_post");collisionArms=boxes(r,"collision_arms");
        if(arms.size()!=4||risers.size()!=4||collisionArms.size()!=4)throw new IllegalArgumentException("护栏方向不完整");
    }
    private static List<double[]> boxes(JsonObject r,String key) {
        List<double[]> out=new ArrayList<>();for(var v:r.getAsJsonArray(key)) {
            var a=v.getAsJsonArray();if(a.size()!=6)throw new IllegalArgumentException("护栏几何尺寸");double[] b=new double[6];
            for(int i=0;i<6;i++){b[i]=a.get(i).getAsDouble();if(!Double.isFinite(b[i])||b[i]<0||b[i]>32)throw new IllegalArgumentException("护栏几何范围");}
            if(b[0]>=b[3]||b[1]>=b[4]||b[2]>=b[5]||b[3]>16||b[5]>16)throw new IllegalArgumentException("护栏几何越过通行列");out.add(b);
        }return List.copyOf(out);
    }
    public List<double[]> boxes(int mask,boolean lowered,boolean collision) {
        if(mask<0||mask>255)throw new IllegalArgumentException("护栏连接标记");
        List<double[]> source=new ArrayList<>(collision?collisionPost:post);
        for(int i=0;i<4;i++)if((mask&(1<<i))!=0){source.add((collision?collisionArms:arms).get(i));if(!collision&&(mask&(16<<i))!=0)source.add(risers.get(i));}
        List<double[]> result=new ArrayList<>();for(double[] b:source){double[] n=b.clone();if(lowered){n[1]-=8;n[4]-=8;}result.add(n);}return result;
    }
}
