package com.example.myvillage.client.qianji;

import com.example.myvillage.qianji.*;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.*;

/** 每座总舵单个锚点的纯客户端表现，不创建实体、不修改方块、不累计粒子任务。 */
public final class QianjiAtmosphereRenderer implements BlockEntityRenderer<QianjiAtmosphereEntity> {
    private static final int FULL_BRIGHT=0xF000F0;
    private static final Map<String,RenderType> TEXTURES=new HashMap<>();
    private static final RenderType COLOR=RenderType.create("myvillage_qianji_light",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,32768,false,true,
            RenderType.CompositeState.builder().setShaderState(RenderStateShard.POSITION_COLOR_SHADER).setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL).setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false));
    private static final RenderType TERRAIN=RenderType.create("myvillage_qianji_mirage",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,262144,false,true,
            RenderType.CompositeState.builder().setShaderState(RenderStateShard.POSITION_COLOR_SHADER).setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL).setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE).createCompositeState(false));
    private record Spark(Vec3 p,double size,int color,double alpha) { }
    private record Mote(double angle,double radius,double y,double z) { }
    private final List<Mote> motes;
    public QianjiAtmosphereRenderer(BlockEntityRendererProvider.Context context){
        var r=rules();long seed=(long)r.n("motes","seed");List<Mote> list=new ArrayList<>();
        for(int i=0;i<(int)r.n("motes","count");i++){
            seed=next(seed);double a=unit(seed)*Math.PI*2;seed=next(seed);double radius=r.n("motes","minimum_radius")+unit(seed)*r.n("motes","radius_span");
            seed=next(seed);double y=r.n("motes","minimum_y")+unit(seed)*r.n("motes","y_span");seed=next(seed);double z=r.n("motes","z_center")+(unit(seed)-.5)*r.n("motes","z_span");seed=next(seed);
            list.add(new Mote(a,radius,y,z));
        }motes=List.copyOf(list);
    }
    private static long next(long n){return(n*1664525+1013904223)&0xffffffffL;}
    private static double unit(long n){return n/4294967296.0;}
    private static QianjiAtmosphereRules rules(){return QianjiAtmosphereRules.get();}
    private static double n(JsonObject o,String key){return o.get(key).getAsDouble();}
    private static Vec3 v(JsonArray a){return QianjiAtmosphereMesh.point(a);}
    private static RenderType texture(String name){return TEXTURES.computeIfAbsent(name,id->RenderType.create("myvillage_qianji_"+id,DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,16384,false,true,
            RenderType.CompositeState.builder().setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(ResourceLocation.fromNamespaceAndPath("myvillage","textures/effect/qianji/"+id+".png"),true,false))
                    .setTransparencyState(id.startsWith("cloud_")?RenderStateShard.TRANSLUCENT_TRANSPARENCY:RenderStateShard.LIGHTNING_TRANSPARENCY).setCullState(RenderStateShard.NO_CULL).setWriteMaskState(RenderStateShard.COLOR_WRITE).setOverlayState(RenderStateShard.OVERLAY).createCompositeState(false)));}
    private static int alpha(double v){return(int)Math.round(Math.clamp(v,0,1)*255);}
    private static void vertex(PoseStack pose,VertexConsumer out,Vec3 p,int color,double a){out.addVertex(pose.last(),(float)p.x,(float)p.y,(float)p.z).setColor(color>>16&255,color>>8&255,color&255,alpha(a));}
    private static void tv(PoseStack pose,VertexConsumer out,Vec3 p,int color,double a,float u,float v){out.addVertex(pose.last(),(float)p.x,(float)p.y,(float)p.z).setColor(color>>16&255,color>>8&255,color&255,alpha(a)).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose.last(),0,1,0);}
    private static void quad(PoseStack pose,VertexConsumer out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,double opacity){if(opacity<=.001)return;vertex(pose,out,a,color,opacity);vertex(pose,out,b,color,opacity);vertex(pose,out,c,color,opacity);vertex(pose,out,d,color,opacity);}
    private static void sprite(PoseStack pose,VertexConsumer out,Vec3 center,double width,double height,Vec3 camera,int color,double opacity){
        if(opacity<=.001)return;Vec3 view=camera.subtract(center).normalize(),right=new Vec3(view.z,0,-view.x).normalize(),up=view.cross(right).normalize();if(right.lengthSqr()<.001)right=new Vec3(1,0,0);right=right.scale(width/2);up=up.scale(height/2);
        tv(pose,out,center.subtract(right).add(up),color,opacity,0,0);tv(pose,out,center.add(right).add(up),color,opacity,1,0);tv(pose,out,center.add(right).subtract(up),color,opacity,1,1);tv(pose,out,center.subtract(right).subtract(up),color,opacity,0,1);
    }
    private static Vec3 rotate(Vec3 p,double a){double c=Math.cos(a),s=Math.sin(a);return new Vec3(p.x*c+p.z*s,p.y,-p.x*s+p.z*c);}
    private static Vec3 sourceCamera(QianjiAtmosphereEntity e,Vec3 camera){var a=v(rules().array("anchor"));var off=v(rules().array("source_offset"));double angle=Math.toRadians(180-e.getBlockState().getValue(QianjiAtmosphereBlock.FACING).toYRot());Vec3 rel=camera.subtract(e.getBlockPos().getX()+.5,e.getBlockPos().getY(),e.getBlockPos().getZ()+.5);return rotate(rel,-angle).add(a.x+.5,a.y-off.y,a.z+.5);}
    private static void ribbon(PoseStack pose,VertexConsumer out,Vec3[] path,Vec3 camera,double u,double length,double width,int color,double opacity,int segments){
        if(opacity<=.001)return;
        for(int i=0;i<segments;i++){
            double t=Math.max(0,u-length*i/segments),tt=Math.max(0,u-length*(i+1)/segments);Vec3 a=QianjiAtmosphereMesh.sample(path,t),b=QianjiAtmosphereMesh.sample(path,tt);Vec3 direction=a.subtract(b).normalize();if(direction.lengthSqr()<.001)continue;
            Vec3 side=direction.cross(camera.subtract(a)).normalize().scale(width*(1-i/(double)segments));double fade=Math.pow(1-i/(double)segments,1.4)*opacity;quad(pose,out,a.add(side),a.subtract(side),b.subtract(side),b.add(side),color,fade);
        }
    }
    private static void line(PoseStack pose,VertexConsumer out,Vec3 a,Vec3 b,Vec3 camera,double width,int color,double opacity){Vec3 side=b.subtract(a).cross(camera.subtract(a)).normalize().scale(width);quad(pose,out,a.add(side),a.subtract(side),b.subtract(side),b.add(side),color,opacity);}
    private static Vec3 diskPoint(Vec3 center,double x,double y,double rotation,boolean horizontal){double c=Math.cos(rotation),s=Math.sin(rotation),xx=x*c-y*s,yy=x*s+y*c;return horizontal?center.add(xx,0,yy):center.add(xx,yy,0);}
    private static void disk(PoseStack pose,VertexConsumer out,JsonObject spec,double progress,double opacity,double t,Vec3 camera){
        if(opacity<=.001||progress<=0)return;Vec3 center=v(spec.getAsJsonArray("position"));double radius=n(spec,"size")/2,rotation=n(spec,"angle")+n(spec,"spin")*t;boolean horizontal=spec.get("plane").getAsString().equals("horizontal");int rgb=rules().color(spec.get("color").getAsString());int segments=(int)rules().n("render","seal_segments");boolean flip=!horizontal&&camera.z<center.z;
        for(int i=0;i<segments&&i/(double)segments<progress;i++){
            double a=i*Math.PI*2/segments,b=Math.min((i+1)/(double)segments,progress)*Math.PI*2;Vec3 p=diskPoint(center,Math.cos(a)*radius,Math.sin(a)*radius,rotation,horizontal),q=diskPoint(center,Math.cos(b)*radius,Math.sin(b)*radius,rotation,horizontal);
            float ua=(float)(.5+Math.cos(a)*.5),va=(float)(.5-Math.sin(a)*.5),ub=(float)(.5+Math.cos(b)*.5),vb=(float)(.5-Math.sin(b)*.5);if(flip){ua=1-ua;ub=1-ub;}
            tv(pose,out,center,rgb,opacity,.5f,.5f);tv(pose,out,p,rgb,opacity,ua,va);tv(pose,out,q,rgb,opacity,ub,vb);tv(pose,out,q,rgb,opacity,ub,vb);
        }
    }
    @Override public void render(QianjiAtmosphereEntity e,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(e.getLevel()==null||e.isRemoved())return;var r=rules();Vec3 worldCamera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();double distance=worldCamera.distanceTo(Vec3.atCenterOf(e.getBlockPos()));double fade=1-QianjiAtmosphereRules.smooth((distance-r.n("render","fade_start"))/(r.n("render","distance")-r.n("render","fade_start")));if(fade<=0)return;
        var phase=r.phase(e.getLevel().getGameTime(),e.cycleOffset(),partial);double t=phase.seconds(),array=phase.array(),mirage=phase.mirage();var mesh=QianjiAtmosphereMesh.get();Vec3 camera=sourceCamera(e,worldCamera),anchor=v(r.array("anchor")),offset=v(r.array("source_offset"));List<Spark> sparks=new ArrayList<>();
        pose.pushPose();pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(180-e.getBlockState().getValue(QianjiAtmosphereBlock.FACING).toYRot()));pose.translate(-anchor.x-.5,offset.y-anchor.y,-anchor.z-.5);
        for(int texture=0;texture<3;texture++){
            var out=buffers.getBuffer(texture("cloud_"+texture));var cm=r.object("cloud_motion");var clouds=r.array("clouds");
            for(int i=texture;i<clouds.size();i+=3){var c=clouds.get(i).getAsJsonArray();boolean upper=i>=n(cm,"upper_start")&&i<=n(cm,"upper_end");double p=i*n(cm,"phase_step");Vec3 at=v(c).add(Math.sin(t*n(cm,"x_rate")+p)*n(cm,upper?"upper_x_amplitude":"x_amplitude"),Math.sin(t*n(cm,"y_rate")+p)*n(cm,"y_amplitude"),Math.cos(t*n(cm,"z_rate")+p)*n(cm,"z_amplitude"));sprite(pose,out,at,c.get(3).getAsDouble(),c.get(4).getAsDouble(),camera,r.color(upper?"cloud_upper":"cloud_lower"),n(cm,upper?"upper_opacity":"opacity")*(1+array*n(cm,"array_boost"))*fade);}
        }
        for(var entry:r.array("seals")){var spec=entry.getAsJsonObject();double opacity=(array*n(spec,"array_alpha")+mirage*n(spec,"mirage_alpha"))*fade;double progress=mirage>0?1:QianjiAtmosphereRules.smooth((t-n(spec,"progress_start"))/n(spec,"progress_duration"));disk(pose,buffers.getBuffer(texture(spec.get("texture").getAsString())),spec,progress,opacity,t,camera);}
        var out=buffers.getBuffer(COLOR);var flow=r.object("flow");int segments=(int)r.n("render","ribbon_segments");
        for(int i=0;i<mesh.routes.size();i++){double u=(t/flow.getAsJsonArray("periods").get(i).getAsDouble()+flow.getAsJsonArray("offsets").get(i).getAsDouble())%1;double a=QianjiAtmosphereRules.smooth(u/n(flow,"fade_in"))*QianjiAtmosphereRules.smooth((1-u)/n(flow,"fade_out"))*(n(flow,"opacity")+array*n(flow,"array_boost"))*fade;int color=r.color(i==1?"gold":"pale");ribbon(pose,out,mesh.routes.get(i),camera,u,n(flow,"length"),flow.getAsJsonArray("widths").get(i).getAsDouble(),color,a,segments);sparks.add(new Spark(QianjiAtmosphereMesh.sample(mesh.routes.get(i),u),n(flow,"head_size"),color,a));}
        for(int i=0;i<mesh.links.size();i++)ribbon(pose,out,mesh.links.get(i),camera,Math.max(0,((t-r.n("array_motion","start"))/r.n("array_motion","period")+i*r.n("array_motion","phase_step"))%1),r.n("array_motion","trail_length"),r.n("array_motion","width"),r.color(i%2==0?"pale":"gold"),array*r.n("array_motion","opacity")*fade,segments);
        for(var m:motes){double angle=m.angle+t*r.n("motes","spin_rate");Vec3 at=new Vec3(r.n("motes","origin_x")+Math.cos(angle)*m.radius,(m.y+t*r.n("motes","rise_rate"))%r.n("motes","height")+r.n("motes","bottom"),m.z+Math.sin(angle)*m.radius*r.n("motes","depth_share"));sparks.add(new Spark(at,r.n("motes","size"),r.color("mote"),r.n("motes","opacity")*fade));}
        if(mirage>.001){
            Vec3 at=v(r.object("mirage").getAsJsonArray("origin")).add(0,Math.sin(t*r.n("mirage","bob_rate"))*r.n("mirage","bob"),0);pose.pushPose();pose.translate(at.x,at.y,at.z);Vec3 localCamera=camera.subtract(at);var terrain=buffers.getBuffer(TERRAIN);
            for(var f:mesh.faces){double opacity=mirage*fade*(f.layer()==0?r.n("mirage","terrain_alpha"):r.object("mirage").getAsJsonArray("building_alpha").get(f.layer()-1).getAsDouble());double reveal=f.layer()==0?QianjiAtmosphereRules.smooth((mirage-f.points()[0].y/r.n("mirage","reveal_height")+r.n("mirage","reveal_padding"))/r.n("mirage","reveal_width")):QianjiAtmosphereRules.smooth(mirage*r.n("mirage","building_reveal_rate"));quad(pose,terrain,f.points()[0],f.points()[1],f.points()[2],f.points()[3],f.color(),opacity*reveal);}
            var edges=buffers.getBuffer(COLOR);for(var pair:mesh.edges)line(pose,edges,pair[0].add(0,r.n("mirage","edge_lift"),0),pair[1].add(0,r.n("mirage","edge_lift"),0),localCamera,r.n("mirage","edge_width"),r.color("edge"),mirage*r.n("mirage","edge_alpha")*fade);ribbon(pose,edges,mesh.river,localCamera,(t*r.n("mirage","river_rate"))%1,r.n("mirage","river_trail"),r.n("mirage","river_width"),r.color("pale"),mirage*r.n("mirage","river_opacity")*fade,segments);
            pose.popPose();
        }
        var dots=buffers.getBuffer(texture("spot"));for(var s:sparks)sprite(pose,dots,s.p,s.size,s.size,camera,s.color,s.alpha);
        pose.popPose();
    }
    @Override public AABB getRenderBoundingBox(QianjiAtmosphereEntity e){var r=rules();var b=r.object("render").getAsJsonArray("bounds");Vec3 a=v(r.array("anchor")),off=v(r.array("source_offset"));double angle=Math.toRadians(180-e.getBlockState().getValue(QianjiAtmosphereBlock.FACING).toYRot());Vec3 min=new Vec3(Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY),max=new Vec3(Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY);for(int x:new int[]{0,3})for(int y:new int[]{1,4})for(int z:new int[]{2,5}){Vec3 p=rotate(new Vec3(b.get(x).getAsDouble()-a.x-.5,b.get(y).getAsDouble()+off.y-a.y,b.get(z).getAsDouble()-a.z-.5),angle).add(e.getBlockPos().getX()+.5,e.getBlockPos().getY(),e.getBlockPos().getZ()+.5);min=new Vec3(Math.min(min.x,p.x),Math.min(min.y,p.y),Math.min(min.z,p.z));max=new Vec3(Math.max(max.x,p.x),Math.max(max.y,p.y),Math.max(max.z,p.z));}return new AABB(min,max);}
    @Override public int getViewDistance(){return(int)rules().n("render","distance");}
    @Override public boolean shouldRenderOffScreen(QianjiAtmosphereEntity e){return true;}
}
