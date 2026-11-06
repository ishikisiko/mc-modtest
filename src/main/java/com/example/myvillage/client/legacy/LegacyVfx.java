package com.example.myvillage.client.legacy;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.legacy.LegacyProjectionEntity;
import com.example.myvillage.legacy.LegacyRules;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Continuous geometry from tracked server views. No client hit, cost, or skill scheduler. */
@EventBusSubscriber(modid=MyVillageMod.MOD_ID,value=Dist.CLIENT)
public final class LegacyVfx {
    private static final RenderType LAYER=RenderType.create("myvillage_legacy_effects",DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,8192,false,true,RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL).setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false));
    private record Sample(Vec3 position,long tick) {}
    private static final Map<Integer,ArrayDeque<Sample>> TRAILS=new LinkedHashMap<>();
    private static final Map<Long,Double> GROUND=new HashMap<>();
    private static List<LegacyProjectionEntity> views=List.of();
    private static ClientLevel world;
    private static long groundEpoch=Long.MIN_VALUE;
    private static LegacyRules rules(){return LegacyRules.get();}
    private static double v(String name){return rules().visual(name);}
    private static int color(String name){return rules().color(name);}
    private LegacyVfx(){}

    static void tick(Minecraft mc) {
        if(world!=mc.level){world=mc.level;views=List.of();TRAILS.clear();GROUND.clear();}
        if(world==null||mc.player==null)return;
        long now=world.getGameTime();
        if(groundEpoch!=now/4){GROUND.clear();groundEpoch=now/4;}
        if(mc.player.tickCount%4==0||views.isEmpty())
            views=world.getEntitiesOfClass(LegacyProjectionEntity.class,mc.player.getBoundingBox().inflate(v("effect_range")),e->!e.isRemoved())
                    .stream().sorted(Comparator.comparingDouble(e->e.distanceToSqr(mc.player))).limit((long)v("max_visible_effects")).toList();
        for(var e:views)if(!e.isRemoved()&&e.kind()==LegacyProjectionEntity.SWORD) {
            var samples=TRAILS.computeIfAbsent(e.getId(),id->new ArrayDeque<>());
            if(samples.isEmpty()||samples.getLast().position().distanceToSqr(e.position())>.0001)samples.addLast(new Sample(e.position(),now));
            while(samples.size()>v("trail_samples")||!samples.isEmpty()&&now-samples.getFirst().tick()>v("trail_ticks"))samples.removeFirst();
        }
        TRAILS.values().removeIf(s->s.isEmpty()||now-s.getLast().tick()>v("trail_ticks"));
        while(TRAILS.size()>v("max_visible_effects"))TRAILS.remove(TRAILS.keySet().iterator().next());
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||world==null)return;
        var mc=Minecraft.getInstance();if(mc.level!=world||mc.player==null)return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(LAYER);
        Draw draw=new Draw(out,event.getCamera().getPosition());
        double now=world.getGameTime()+partial;
        for(var entry:TRAILS.entrySet()) {
            var e=world.getEntity(entry.getKey());boolean returning=e instanceof LegacyProjectionEntity p&&p.phase()==1;
            var samples=new ArrayList<>(entry.getValue());
            for(int i=1;i<samples.size();i++) {
                var a=samples.get(i-1);var b=samples.get(i);double fade=Math.max(0,1-(now-b.tick())/v("trail_ticks"));
                double width=v("sword_trail_width")*fade*(returning?v("sword_return_width_factor"):1);
                draw.ribbon(a.position(),b.position(),width,color("metal"),.62*fade);
                draw.ribbon(a.position(),b.position(),width*.18,color("edge"),.9*fade);
            }
        }
        for(var e:views) {
            if(e.isRemoved()||e.distanceToSqr(mc.player)>v("effect_range")*v("effect_range"))continue;
            Vec3 at=new Vec3(Mth.lerp(partial,e.xOld,e.getX()),Mth.lerp(partial,e.yOld,e.getY()),Mth.lerp(partial,e.zOld,e.getZ()));
            double age=e.age(partial),t=Math.clamp(age/e.duration(),0,1),fade=Math.max(0,1-t);
            switch(e.kind()) {
                case LegacyProjectionEntity.SWORD -> sword(draw,e,at,age);
                case LegacyProjectionEntity.SHADOW -> {
                    draw.groundRing(at,.55,.1,color("water"),.35);
                    draw.crown(at,.58,.14,color("water"),.14);
                }
                case LegacyProjectionEntity.FIRE,LegacyProjectionEntity.EMBER -> {
                    double r=v("fire_seed_radius"),pulse=.75+.25*Math.sin(age*.28);
                    draw.cracks(at,r*1.6,color("fire"),.6*pulse);
                    draw.groundRing(at,r,.11,color("fire"),.65*pulse);
                    draw.flames(at,r,v("fire_seed_height")*pulse,color("fire"),.68);
                }
                case LegacyProjectionEntity.RULER -> {
                    double r=e.radius(),pulse=1-(now%rules().ticks("ruler_interval_ticks"))/rules().ticks("ruler_interval_ticks");
                    draw.groundRing(at,r,v("ruler_band_width"),color("earth"),.65);
                    draw.groundRing(at,r*.83,v("ruler_band_width")*.5,color("earth"),.32);
                    draw.glyphs(at,r,color("earth"),.55);
                    draw.groundRing(at,r*(.2+.8*(1-pulse)),v("ruler_band_width")*2,color("edge"),pulse*.55);
                    draw.crown(at,r,v("ruler_field_height"),color("earth"),.07+.12*pulse);
                }
                case LegacyProjectionEntity.FIRE_BURST -> {
                    double swell=Math.min(1,(age+1)/4),r=e.radius();
                    draw.disc(at,r,color("fire"),.2*fade);
                    draw.groundRing(at,r,.18,color("fire"),.85*fade);
                    draw.cracks(at,r,color("edge"),fade);
                    draw.flames(at,r*swell,v("fire_burst_height")*Math.sin(Math.PI*t),color("fire"),.8*fade);
                    draw.crown(at,r*swell,v("fire_burst_height")*.45*fade,color("fire"),.35*fade);
                }
                case LegacyProjectionEntity.QUAKE -> {
                    double r=Math.min(e.radius(),(age+1)*rules().number("quake_speed"));
                    draw.groundRing(at,r,rules().number("quake_width"),color("earth"),.72);
                    draw.crown(at,r,v("quake_height")*(.45+.55*fade),color("ash"),.33*fade+.12);
                    draw.groundRing(at,Math.max(0,r-.18),.07,color("edge"),.75*fade);
                }
                case LegacyProjectionEntity.FIRE_JET -> {
                    Vec3 to=at.add(e.vector());double w=e.phase()==1?e.radius():e.radius()*v("ray_envelope_share");
                    draw.jet(at,to,w,e.phase()==1,color("fire"),Math.min(1,fade*2));
                }
                case LegacyProjectionEntity.WATER_RAY -> {
                    Vec3 to=at.add(e.vector());double w=e.radius()*v("ray_envelope_share");
                    draw.ribbon(at,to,w,color("water"),.48*fade);draw.ribbon(at,to,w*.14,color("edge"),.92*fade);
                    draw.axisRing(to,e.vector(),w,.1,color("water"),.8*fade);
                }
                case LegacyProjectionEntity.MIRROR,LegacyProjectionEntity.MIRROR_HIT -> {
                    boolean hit=e.kind()==LegacyProjectionEntity.MIRROR_HIT;double a=hit?fade:Math.min(1,(age+1)/4),r=e.radius()*(hit?1+.3*t:1);
                    draw.axisRing(at,e.vector(),r,.11,color("water"),.75*a);
                    draw.axisRing(at,e.vector(),r*.78,.04,color("edge"),.6*a);
                    draw.lens(at,e.vector(),r,color("water"),(hit?.28:.08)*a);
                }
                case LegacyProjectionEntity.EMBER_COLLECT -> {
                    Vec3 to=at.add(e.vector());draw.ribbon(at,to,.12,color("fire"),.5*fade);
                    Vec3 ember=at.lerp(to,t);draw.axisRing(ember,e.vector(),e.radius()*.5,.09,color("edge"),fade);
                }
                case LegacyProjectionEntity.EARTH_IMPACT -> {
                    double r=e.radius()*Math.min(1,(age+1)/5);
                    draw.groundRing(at,r,.22,color("earth"),fade*.9);draw.crown(at,r,.55*fade,color("ash"),.42*fade);
                }
                default -> {}
            }
        }
        buffers.endBatch(LAYER);
    }

    private static void sword(Draw draw,LegacyProjectionEntity e,Vec3 at,double age) {
        double yaw=Math.toRadians(e.getYRot()),pitch=Math.toRadians(e.getXRot());
        Vec3 direction=new Vec3(-Math.sin(yaw)*Math.cos(pitch),-Math.sin(pitch),Math.cos(yaw)*Math.cos(pitch));
        double length=rules().shape("guiyan_sword","tip_y")/16*v("sword_body_scale")*Math.min(1,(age+1)/v("sword_summon_ticks"));
        Vec3 tail=at.subtract(direction.scale(length));
        draw.ribbon(tail,at,v("sword_aura_width"),color("metal"),e.phase()==1?.45:.27);
        draw.ribbon(tail,at,v("sword_aura_width")*.12,color("edge"),.7);
    }

    private static double ground(Vec3 center,double x,double z) {
        int bx=Mth.floor(x),bz=Mth.floor(z),by=Mth.floor(center.y);long key=BlockPos.asLong(bx,by,bz);
        var cached=GROUND.get(key);if(cached!=null)return cached;
        if(GROUND.size()>=v("ground_samples"))return center.y;
        double y=center.y;
        for(int dy=(int)v("ground_scan_up");dy>=-v("ground_scan_down");dy--) {
            BlockPos p=new BlockPos(bx,by+dy,bz);if(!world.hasChunkAt(p))break;
            var shape=world.getBlockState(p).getCollisionShape(world,p);
            if(!shape.isEmpty()) {double top=p.getY()+shape.max(Direction.Axis.Y);if(Math.abs(top-center.y)<=v("ground_height_limit")){y=top;break;}}
        }
        GROUND.put(key,y);return y;
    }

    private static final class Draw {
        private final VertexConsumer out;private final Vec3 camera;private final int segments=(int)v("segments");
        Draw(VertexConsumer out,Vec3 camera){this.out=out;this.camera=camera;}
        private void vertex(Vec3 p,int rgb,double alpha) {
            Vec3 q=p.subtract(camera);out.addVertex((float)q.x,(float)q.y,(float)q.z).setColor(rgb>>16&255,rgb>>8&255,rgb&255,(int)Math.clamp(Math.round(alpha*255),0,255));
        }
        private void quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int rgb,double aa,double ba,double ca,double da) {vertex(a,rgb,aa);vertex(b,rgb,ba);vertex(c,rgb,ca);vertex(d,rgb,da);}
        private Vec3 point(Vec3 center,double angle,double radius,double height) {
            double x=center.x+Math.cos(angle)*radius,z=center.z+Math.sin(angle)*radius;
            return new Vec3(x,ground(center,x,z)+v("ground_offset")+height,z);
        }
        void groundRing(Vec3 at,double radius,double band,int rgb,double alpha) {
            for(int i=0;i<segments;i++) {
                double a=i*Math.PI*2/segments,b=(i+1)*Math.PI*2/segments;
                quad(point(at,a,Math.max(0,radius-band),0),point(at,a,radius,0),point(at,b,radius,0),point(at,b,Math.max(0,radius-band),0),rgb,alpha*.35,alpha,alpha,alpha*.35);
            }
        }
        void crown(Vec3 at,double radius,double height,int rgb,double alpha) {
            for(int i=0;i<segments;i++) {
                double a=i*Math.PI*2/segments,b=(i+1)*Math.PI*2/segments;
                quad(point(at,a,radius,0),point(at,b,radius,0),point(at,b,radius,height),point(at,a,radius,height),rgb,alpha,alpha,0,0);
            }
        }
        void disc(Vec3 at,double radius,int rgb,double alpha) {
            Vec3 center=point(at,0,0,0);
            for(int i=0;i<segments;i++)quad(center,point(at,i*Math.PI*2/segments,radius,0),point(at,(i+1)*Math.PI*2/segments,radius,0),center,rgb,alpha,0,0,alpha);
        }
        void glyphs(Vec3 at,double radius,int rgb,double alpha) {
            int count=(int)v("ruler_glyph_count");
            for(int i=0;i<count;i++) {
                double a=i*Math.PI*2/count;
                groundStroke(point(at,a,radius*.5,0),point(at,a,radius*.74,0),.04,rgb,alpha);
                groundStroke(point(at,a-.04,radius*.62,0),point(at,a+.04,radius*.62,0),.03,rgb,alpha);
            }
        }
        void cracks(Vec3 at,double radius,int rgb,double alpha) {
            for(int i=0;i<v("crack_count");i++) {
                double a=i*Math.PI*2/v("crack_count");Vec3 p=point(at,a,radius*.12,0);
                for(int j=1;j<=v("crack_steps");j++){Vec3 q=point(at,a+(j%2==0?-.14:.14),radius*j/v("crack_steps"),0);groundStroke(p,q,.025+radius*.012,rgb,alpha*(1-j*.18));p=q;}
            }
        }
        void groundStroke(Vec3 a,Vec3 b,double width,int rgb,double alpha) {
            Vec3 d=b.subtract(a).multiply(1,0,1).normalize(),side=new Vec3(-d.z,0,d.x).scale(width);
            quad(a.add(side),b.add(side),b.subtract(side),a.subtract(side),rgb,alpha,alpha,alpha,alpha);
        }
        void flames(Vec3 at,double radius,double height,int rgb,double alpha) {
            for(int i=0;i<v("flame_count");i++) {
                double a=i*Math.PI*2/v("flame_count");Vec3 base=point(at,a,radius*(i%2==0?.7:.35),0),top=base.add(Math.sin(a)*.12,height*(.6+(i%3)*.2),Math.cos(a)*.12);
                ribbon(base,top,radius*.18,rgb,alpha);ribbon(base,base.lerp(top,.6),radius*.055,color("edge"),alpha*.8);
            }
        }
        void ribbon(Vec3 from,Vec3 to,double halfWidth,int rgb,double alpha) {
            Vec3 axis=to.subtract(from);if(axis.lengthSqr()<.00001||halfWidth<=0||alpha<=0)return;
            Vec3 side=axis.cross(camera.subtract(from.lerp(to,.5))).normalize().scale(halfWidth);
            if(side.lengthSqr()<.000001)side=new Vec3(halfWidth,0,0);
            quad(from.subtract(side),to.subtract(side),to.add(side),from.add(side),rgb,alpha*.15,alpha*.75,alpha*.75,alpha*.15);
        }
        private Vec3[] basis(Vec3 axis) {
            Vec3 normal=axis.lengthSqr()>.0001?axis.normalize():new Vec3(0,0,1);
            Vec3 x=normal.cross(new Vec3(0,1,0));if(x.lengthSqr()<.001)x=new Vec3(1,0,0);else x=x.normalize();
            return new Vec3[]{x,normal.cross(x).normalize()};
        }
        void axisRing(Vec3 at,Vec3 axis,double radius,double band,int rgb,double alpha) {
            Vec3[] b=basis(axis);
            for(int i=0;i<segments;i++) {
                double a=i*Math.PI*2/segments,c=(i+1)*Math.PI*2/segments;
                Vec3 p=b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a))),q=b[0].scale(Math.cos(c)).add(b[1].scale(Math.sin(c)));
                quad(at.add(p.scale(Math.max(0,radius-band))),at.add(p.scale(radius)),at.add(q.scale(radius)),at.add(q.scale(Math.max(0,radius-band))),rgb,alpha*.2,alpha,alpha,alpha*.2);
            }
        }
        void lens(Vec3 at,Vec3 axis,double radius,int rgb,double alpha) {
            Vec3[] b=basis(axis);
            for(int i=0;i<segments;i++) {
                double a=i*Math.PI*2/segments,c=(i+1)*Math.PI*2/segments;
                quad(at,at.add(b[0].scale(Math.cos(a)*radius)).add(b[1].scale(Math.sin(a)*radius)),at.add(b[0].scale(Math.cos(c)*radius)).add(b[1].scale(Math.sin(c)*radius)),at,rgb,alpha,alpha*.35,alpha*.35,alpha);
            }
        }
        void jet(Vec3 from,Vec3 to,double radius,boolean cone,int rgb,double alpha) {
            Vec3 axis=to.subtract(from);Vec3[] b=basis(axis);
            double offset=cone?rules().beast("ember_toad").data().getAsJsonObject("sockets").get("mouth_forward").getAsDouble():0;
            for(int j=0;j<v("jet_ribbons");j++) {
                double angle=j*Math.PI*2/v("jet_ribbons");
                Vec3 previous=from;
                for(int i=1;i<=v("jet_steps");i++) {
                    double t=i/v("jet_steps"),distance=axis.length()*t;
                    double extent=cone?Math.min(distance*Math.tan(Math.acos(rules().number("toad_cone_dot"))),Math.sqrt(Math.max(0,radius*radius-(distance+offset)*(distance+offset))))*v("ray_envelope_share"):radius;
                    double r=extent*(.55+.2*Math.sin(t*14+j));
                    Vec3 point=from.lerp(to,t).add(b[0].scale(Math.cos(angle)*r)).add(b[1].scale(Math.sin(angle)*(cone?Math.min(r,v("toad_jet_height")):r)));
                    ribbon(previous,point,extent*.14,rgb,alpha*(1-t*.5));previous=point;
                }
            }
            ribbon(from,to,Math.min(.22,radius*.25),color("edge"),alpha*.9);
        }
    }
}
