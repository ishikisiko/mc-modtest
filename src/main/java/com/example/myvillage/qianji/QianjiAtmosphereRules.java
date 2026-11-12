package com.example.myvillage.qianji;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** 常态与间歇相位共用配置和世界时间；此类没有客户端依赖。 */
public final class QianjiAtmosphereRules {
    public record Phase(double seconds,double array,double mirage) { }
    private final JsonObject root;
    private QianjiAtmosphereRules(JsonObject root){
        this.root=root;if(root.get("schema").getAsInt()!=1||number("cycle_seconds")<=0||number("cycle_seconds")>3600)throw new IllegalArgumentException("千机阁动效周期无效");
        for(String key:new String[]{"array","mirage"}){var a=object("phases").getAsJsonArray(key);if(a.size()!=3||a.get(0).getAsDouble()<0||a.get(1).getAsDouble()>number("cycle_seconds")||a.get(0).getAsDouble()>=a.get(1).getAsDouble()||a.get(2).getAsDouble()<=0)throw new IllegalArgumentException("千机阁动效相位无效");}
    }
    public JsonObject object(String key){return root.getAsJsonObject(key);}
    public JsonArray array(String key){return root.getAsJsonArray(key);}
    public double number(String key){return root.get(key).getAsDouble();}
    public double n(String object,String key){return root.getAsJsonObject(object).get(key).getAsDouble();}
    public long cycleTicks(){return Math.round(number("cycle_seconds")*20);}
    public int color(String key){return Integer.parseInt(object("colors").get(key).getAsString(),16);}
    public Phase phase(long ticks,long offset,float partial){double seconds=(Math.floorMod(ticks,cycleTicks())+Math.floorMod(offset,cycleTicks())+partial)/20.0%number("cycle_seconds");return at(seconds);}
    public Phase at(double seconds){double t=(seconds%number("cycle_seconds")+number("cycle_seconds"))%number("cycle_seconds");return new Phase(t,band(t,"array"),band(t,"mirage"));}
    private double band(double t,String name){var p=object("phases").getAsJsonArray(name);return smooth((t-p.get(0).getAsDouble())/p.get(2).getAsDouble())*smooth((p.get(1).getAsDouble()-t)/p.get(2).getAsDouble());}
    public long offsetFor(String phase,long gameTime){double start=phase.equals("quiet")?0:object("phases").getAsJsonArray(phase).get(0).getAsDouble();return Math.floorMod(Math.round(start*20)-Math.floorMod(gameTime,cycleTicks()),cycleTicks());}
    public static double smooth(double v){v=Math.clamp(v,0,1);return v*v*(3-2*v);}
    private static final class Holder {static final QianjiAtmosphereRules INSTANCE=read();}
    public static QianjiAtmosphereRules get(){return Holder.INSTANCE;}
    private static QianjiAtmosphereRules read(){try(var in=QianjiAtmosphereRules.class.getResourceAsStream("/data/myvillage/qianji/atmosphere.json")){if(in==null)throw new IllegalStateException("缺少千机阁动效配置");return new QianjiAtmosphereRules(JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject());}catch(java.io.IOException e){throw new IllegalStateException(e);}}
}
