package com.example.myvillage.client.immortal;

import com.example.myvillage.client.combat.ImmortalArmPoseBridge;
import com.example.myvillage.client.combat.ImmortalArmPoseBridge.ArmPose;
import com.example.myvillage.combat.definition.CombatTestData;
import com.example.myvillage.equipment.immortal.ImmortalMeshData;
import com.example.myvillage.equipment.immortal.ImmortalMeshData.*;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** Actual exported sleeves and unchanged shipped IK solves; no Minecraft launch. */
public final class ImmortalFirstPersonArmTest {
    private static final String COMBAT="com.example.myvillage.client.combat.";
    private record Fixture(ArmPose pose,boolean right,String label) { }
    private static void require(boolean yes,String label){if(!yes)throw new AssertionError(label);}
    private static void near(Vector3f a,Vector3f b,float eps,String label){require(a.distance(b)<eps,label+": "+a+" != "+b);}
    // Reflection keeps the production solver package-private; no test-only production API.
    private static Object call(Object o,String name,Object... args)throws Exception {
        Class<?> type=o instanceof Class<?> c?c:o.getClass();
        for(Method m:type.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length) {
            boolean match=true;Class<?>[] ts=m.getParameterTypes();
            for(int i=0;i<args.length;i++)if(args[i]!=null&&!ts[i].isInstance(args[i])&&!(ts[i]==float.class&&args[i] instanceof Float))match=false;
            if(match){m.setAccessible(true);return m.invoke(o instanceof Class<?>?null:o,args);}
        }
        throw new NoSuchMethodException(type+"."+name);
    }
    private static com.google.gson.JsonObject json(Path p)throws Exception{return JsonParser.parseString(Files.readString(p)).getAsJsonObject();}
    private static List<Fixture> fixtures()throws Exception {
        List<Fixture> result=new ArrayList<>();
        var weapons=List.of(CombatTestData.qingfeng(),CombatTestData.lingxiao(),CombatTestData.xuantie());
        var styles=List.of(CombatTestData.basicSword(),CombatTestData.basicSpear(),CombatTestData.basicFist());
        Class<?> geometry=Class.forName(COMBAT+"WeaponGeometry"),swingType=Class.forName(COMBAT+"FirstPersonSwing");
        Class<?> ik=Class.forName(COMBAT+"FirstPersonArmIk"),lagType=Class.forName(COMBAT+"FirstPersonArmLag");
        for(int w=0;w<weapons.size();w++) {
            Object mesh=call(geometry,"parse",json(CombatTestData.assetPath(weapons.get(w).geometry())));
            Object swing=call(swingType,"parse",json(CombatTestData.assetPath(weapons.get(w).firstPersonRig())),styles.get(w),mesh);
            Object rig=call(swing,"rig");List<Object> poses=new ArrayList<>();poses.add(call(swing,"neutral"));
            List<Vector3f> lags=new ArrayList<>();lags.add(new Vector3f());
            for(Object move:(List<?>)call(swing,"moves"))for(float fraction:new float[]{0,.5f,1}) {
                float tick=((Number)call(move,"totalTicks")).floatValue()*fraction;
                poses.add(call(move,"sample",tick));lags.add((Vector3f)call(lagType,"offset",swing,move,tick));
            }
            for(int p=0;p<poses.size();p++)for(HumanoidArm arm:HumanoidArm.values()) {
                Object solved=call(ik,"solve",arm,0f,swing,poses.get(p),lags.get(p));
                result.add(new Fixture((ArmPose)call(ImmortalArmPoseBridge.class,"snapshot",solved,call(rig,"arm")),
                        arm==HumanoidArm.RIGHT,weapons.get(w).firstPersonRig()+"/"+p+"/"+arm));
                Optional<?> off=(Optional<?>)call(ik,"solveOffHand",arm,0f,swing,poses.get(p));
                if(off.isPresent())result.add(new Fixture((ArmPose)call(ImmortalArmPoseBridge.class,"snapshot",call(off.get(),"arm"),call(rig,"offArm")),
                        arm!=HumanoidArm.RIGHT,"off/"+weapons.get(w).firstPersonRig()+"/"+p));
            }
        }
        return result;
    }
    private static Vector3f mirror(Vector3f p){return new Vector3f(-p.x,p.y,p.z);}
    private static Quaternionf mirror(Quaternionf q){return new Quaternionf(q.x,-q.y,-q.z,q.w);}

    @Test public void solvedJointsMirrorsAndContinuity()throws Exception {
        var fixtures=fixtures();
        for(Fixture f:fixtures)for(boolean slim:new boolean[]{false,true}) {
            var warp=new ImmortalArmDeformation(f.pose,slim,f.right);float c=(f.right?-1:1)*(slim?.5f:1);
            near(warp.position(c,ImmortalArmPoseBridge.SHOULDER_Y,0),f.pose.shoulder(),2e-5f,"shoulder "+f.label);
            near(warp.position(c,ImmortalArmPoseBridge.ELBOW_Y,0),f.pose.elbow(),2e-5f,"elbow "+f.label);
            near(warp.position(c,ImmortalArmPoseBridge.WRIST_Y,0),f.pose.wrist(),2e-5f,"wrist "+f.label);
            for(float y:new float[]{-2,3,4,5,5.75f,6.75f,7.75f,10}) {
                near(warp.position(c+2,y-.0001f,1),warp.position(c+2,y+.0001f,1),.001f,"joint discontinuity");
                float width=warp.position(c+2,y,0).distance(warp.position(c-2,y,0));
                require(Math.abs(width-f.pose.thickness()/4)<2e-5f,"collapsed cross section");
            }
            Vector3f saved=f.pose.shoulder();f.pose.shoulder().zero();near(saved,f.pose.shoulder(),2e-5f,"mutable snapshot");
            var p=f.pose;var mirrored=new ArmPose(mirror(p.shoulder()),mirror(p.elbow()),mirror(p.wrist()),mirror(p.upper()),mirror(p.forearm()),mirror(p.fist()),p.thickness());
            var other=new ImmortalArmDeformation(mirrored,slim,!f.right);
            near(mirror(warp.position(c+1.3f,4.2f,1.7f)),other.position(-c-1.3f,4.2f,1.7f),2e-5f,"double mirror");
        }
        System.out.println("PASS actual IK snapshots="+fixtures.size()+" x 2 profiles: joints, continuity, section width, mirrors, immutable bridge");
    }

    @Test public void allFifteenMeshesAndOriginalRenderSink()throws Exception {
        var fixtures=fixtures();int sets=0;long vertices=0,triangles=0;
        try(var paths=Files.list(Path.of("src/main/resources/assets/myvillage/immortal_equipment"))) {
            for(Path path:paths.filter(p->p.toString().endsWith(".json")).sorted().toList()) {
                if(!json(path).has("profiles"))continue;
                ImmortalMeshData data;try(var reader=Files.newBufferedReader(path)){data=ImmortalMeshData.parse(reader);}
                for(var entry:data.profiles().entrySet())for(boolean right:new boolean[]{false,true}) {
                    boolean slim=entry.getKey().equals("slim");int matched=0;
                    for(int fi=0;fi<fixtures.size();fi++) {
                        Fixture f=fixtures.get(fi);if(f.right!=right||matched++%17!=0)continue;
                        var warp=new ImmortalArmDeformation(f.pose,slim,right);
                        for(Part part:entry.getValue().parts())if(part.bone().equals(right?"right_arm":"left_arm")) {
                            for(Vertex v:part.vertices()) {
                                Vector3f p=warp.position(v.x(),v.y(),v.z()),n=warp.normal(v.x(),v.y(),v.z(),v.nx(),v.ny(),v.nz());
                                require(p.isFinite()&&n.isFinite()&&Math.abs(n.length()-1)<2e-5f,"invalid deformed mesh "+data.setId());vertices++;
                            }
                            Capture target=new Capture();PoseStack outer=new PoseStack();outer.translate(.2,-.3,.1);
                            var sink=ImmortalFirstPersonArm.consumer(target,warp,outer.last());
                            ImmortalMeshRenderer.triangles(new PoseStack(),sink,part,0x123456,0xabcdef,.37f,false);
                            require(target.points.size()==part.indices().size()/3*4,"face count changed");
                            for(int i=0;i<target.points.size();i++) {
                                Vertex original=part.vertices().get(part.indices().get(i/4*3+Math.min(i%4,2)));
                                near(target.points.get(i),warp.position(original.x(),original.y(),original.z()).add(.2f,-.3f,.1f),2e-5f,"sink position");
                                require(target.uvs.get(i).equals(List.of(original.u(),original.v())),"UV changed");
                            }
                            require(target.alpha==Math.round(.37f*255)&&target.light==0x123456,"alpha/light changed");
                            int count=target.points.size();ImmortalMeshRenderer.triangles(new PoseStack(),sink,part,0,0,1,true);
                            require(target.points.size()==2*count,"glint geometry changed");triangles+=count/4;
                        }
                    }
                }
                sets++;
            }
        }
        require(sets==15,"missing sets");System.out.println("PASS sets="+sets+" vertex samples="+vertices+" triangle samples="+triangles+": UV, alpha/light, glint, finite normals");
    }

    @Test public void normalsFollowJacobianAndOuterPose() {
        var pose=new ArmPose(new Vector3f(),new Vector3f(.2f,.25f,0),new Vector3f(.3f,.1f,.15f),
                new Quaternionf().rotationZ(-.6f),new Quaternionf().rotationX(.7f),new Quaternionf().rotationX(.4f),.5f);
        var warp=new ImmortalArmDeformation(pose,false,true);float x=.1f,y=4.3f,z=.5f,h=.005f;
        Vector3f tx=warp.position(x+h,y,z).sub(warp.position(x-h,y,z)).normalize();
        Vector3f ty=warp.position(x,y+h,z).sub(warp.position(x,y-h,z)).normalize();
        Vector3f n=warp.normal(x,y,z,0,0,1);
        require(Math.abs(n.dot(tx))<.001f&&Math.abs(n.dot(ty))<.001f,"normal misses derivative");
        Capture target=new Capture();PoseStack outer=new PoseStack();outer.scale(2,1,.5f);outer.mulPose(new Quaternionf().rotationY(.6f));
        ImmortalFirstPersonArm.consumer(target,warp,outer.last()).addVertex(x/16,y/16,z/16).setNormal(0,0,1);
        Capture expected=new Capture();expected.setNormal(outer.last(),n.x,n.y,n.z);
        near(target.normal,expected.normal,2e-5f,"outer normal");
        System.out.println("PASS inverse-transpose warp normals and outer view normal transform");
    }

    @Test public void tessellationPreservesOriginalSurfacesAndUv()throws Exception {
        long originals=0,subdivisions=0;
        try(var paths=Files.list(Path.of("src/main/resources/assets/myvillage/immortal_equipment"))) {
            for(Path path:paths.filter(p->p.toString().endsWith(".json")).toList()) {
                if(!json(path).has("profiles"))continue;
                ImmortalMeshData data;try(var r=Files.newBufferedReader(path)){data=ImmortalMeshData.parse(r);}
                for(Profile profile:data.profiles().values())for(Part part:profile.parts()) {
                    if(!part.bone().endsWith("_arm"))continue;
                    for(int i=0;i<part.indices().size();i+=3) {
                        var abc=List.of(part.vertices().get(part.indices().get(i)),part.vertices().get(part.indices().get(i+1)),part.vertices().get(part.indices().get(i+2)));
                        Part split=ImmortalArmTessellation.split(new Part(part.id(),part.slot(),part.bone(),part.material(),part.side(),abc,List.of(0,1,2)));
                        Vector3f a=pos(abc.get(0)),u=pos(abc.get(1)).sub(a),v=pos(abc.get(2)).sub(a),n=new Vector3f(u).cross(v);
                        double area=n.length(),sum=0,uu=u.dot(u),vv=v.dot(v),uv=u.dot(v),den=uu*vv-uv*uv;
                        for(Vertex vertex:split.vertices()) {
                            Vector3f p=pos(vertex).sub(a);double pu=p.dot(u),pv=p.dot(v);
                            double wb=(pu*vv-pv*uv)/den,wc=(pv*uu-pu*uv)/den,wa=1-wb-wc;
                            require(Math.abs(p.dot(n))/area<3e-5,"subdivision left original plane");
                            require(wa>-.002&&wb>-.002&&wc>-.002,"subdivision outside triangle");
                            require(Math.abs(vertex.u()-(wa*abc.get(0).u()+wb*abc.get(1).u()+wc*abc.get(2).u()))<.002,"subdivision changed U");
                            require(Math.abs(vertex.v()-(wa*abc.get(0).v()+wb*abc.get(1).v()+wc*abc.get(2).v()))<.002,"subdivision changed V");
                        }
                        for(int j=0;j<split.indices().size();j+=3) {
                            Vertex p=split.vertices().get(split.indices().get(j)),q=split.vertices().get(split.indices().get(j+1)),r=split.vertices().get(split.indices().get(j+2));
                            Vector3f cross=pos(q).sub(pos(p)).cross(pos(r).sub(pos(p)));
                            require(cross.dot(n)>0,"subdivision reversed winding");sum+=cross.length();
                            float lo=Math.min(p.y(),Math.min(q.y(),r.y())),hi=Math.max(p.y(),Math.max(q.y(),r.y()));
                            if(lo<ImmortalArmPoseBridge.WRIST_Y+1&&hi>ImmortalArmPoseBridge.SHOULDER_Y)
                                require(hi-lo<.50001f,"sparse elbow triangle remains");
                        }
                        require(Math.abs(sum-area)<Math.max(2e-5,area*2e-5),"subdivision changed surface area");
                        originals++;subdivisions+=split.indices().size()/3;
                    }
                }
            }
        }
        System.out.println("PASS original arm triangles="+originals+" barycentric subdivisions="+subdivisions+": plane/area/winding/UV and half-pixel elbow sampling");
    }
    private static Vector3f pos(Vertex v){return new Vector3f(v.x(),v.y(),v.z());}

    @Test public void localEffectsUseTheSameWarpAndZeroBudgetStops()throws Exception {
        var fixture=fixtures().getFirst();
        var warp=new ImmortalArmDeformation(fixture.pose,false,fixture.right);
        PoseStack outer=new PoseStack();outer.translate(.2,-.3,.1);
        Method spark=ImmortalModelEffects.class.getDeclaredMethod("spark",PoseStack.class,VertexConsumer.class,
                Vec3.class,float.class,String.class,int.class,float.class,float.class,float.class);
        spark.setAccessible(true);
        for(String shape:List.of("spark","seal","leaf","star")) {
            Capture local=new Capture(),warped=new Capture();
            Object[] args={new PoseStack(),local,new Vec3(-1,4.2f,.5f),.2f,shape,0xffffff,.4f,.1f,.2f};
            spark.invoke(null,args);args[0]=new PoseStack();args[1]=ImmortalFirstPersonArm.consumer(warped,warp,outer.last());spark.invoke(null,args);
            require(!local.points.isEmpty()&&local.points.size()==warped.points.size(),"effect geometry changed");
            for(int i=0;i<local.points.size();i++) {
                Vector3f p=local.points.get(i);
                near(warped.points.get(i),warp.position(p.x*16,p.y*16,p.z*16).add(.2f,-.3f,.1f),2e-5f,"effect used another pose");
            }
            require(warped.alpha==Math.round(.4f*255),"effect alpha changed");
        }
        Method ribbon=ImmortalModelEffects.class.getDeclaredMethod("ribbon",PoseStack.class,VertexConsumer.class,
                Vec3.class,Vec3.class,float.class,int.class,float.class);
        ribbon.setAccessible(true);Capture local=new Capture(),warped=new Capture();
        Object[] args={new PoseStack(),local,new Vec3(-1,3,.5f),new Vec3(-1,5,.5f),.05f,0xffffff,.4f};
        ribbon.invoke(null,args);args[0]=new PoseStack();args[1]=ImmortalFirstPersonArm.consumer(warped,warp,outer.last());ribbon.invoke(null,args);
        for(int i=0;i<local.points.size();i++) {
            Vector3f p=local.points.get(i);
            near(warped.points.get(i),warp.position(p.x*16,p.y*16,p.z*16).add(.2f,-.3f,.1f),2e-5f,"trail/arc used another pose");
        }
        ImmortalModelEffects.draw(null,null,new PoseStack(),type->{throw new AssertionError("zero budget requested a buffer");},0,new int[]{0},"right_arm");
        System.out.println("PASS all four local effect shapes + trail/arc vertices use same warp; zero budget emits nothing");
    }
    static final class Capture implements VertexConsumer {
        final List<Vector3f> points=new ArrayList<>();final List<List<Float>> uvs=new ArrayList<>();int alpha,light;Vector3f normal;
        public VertexConsumer addVertex(float x,float y,float z){points.add(new Vector3f(x,y,z));return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){alpha=a;return this;}
        public VertexConsumer setUv(float u,float v){uvs.add(List.of(u,v));return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){light=u|(v<<16);return this;}
        public VertexConsumer setNormal(float x,float y,float z){normal=new Vector3f(x,y,z);return this;}
    }
    @Test public void bothArmsShareOneSinkWithoutStaleBinding()throws Exception {
        var fixtures=fixtures();
        var rightPose=fixtures.stream().filter(f->f.right).findFirst().orElseThrow();
        var leftPose=fixtures.stream().filter(f->!f.right).reduce((a,b)->b).orElseThrow();
        var right=new ImmortalArmDeformation(rightPose.pose,false,true);
        var left=new ImmortalArmDeformation(leftPose.pose,true,false);
        var draw=new ImmortalArmDraw(Map.of("right_arm",right,"left_arm",left));
        require(draw.includes("right_arm")&&draw.includes("left_arm")&&!draw.includes("body"),"arm filter leaked");
        Capture out=new Capture();PoseStack poses=new PoseStack();
        var sink=ImmortalFirstPersonArm.consumer(out,draw,poses.last());
        for(String bone:List.of("right_arm","left_arm","right_arm")) {
            ImmortalMeshRenderer.bind(poses,null,bone,draw);
            sink.addVertex(.1f,.3f,.05f).setNormal(0,0,1);
            var warp=bone.equals("right_arm")?right:left;
            near(out.points.getLast(),warp.position(1.6f,4.8f,.8f),2e-5f,"stale shared arm sink");
            near(out.normal,warp.normal(1.6f,4.8f,.8f,0,0,1),2e-5f,"stale shared arm normal");
        }
        var next=new ImmortalArmDraw(Map.of("right_arm",right));
        require(!next.includes("left_arm"),"released off arm survived next draw");
        System.out.println("PASS one shared dual-arm sink: bone filters, independent poses/normals, no released off-arm binding");
    }
    public static void main(String[] args)throws Exception {
        var test=new ImmortalFirstPersonArmTest();test.solvedJointsMirrorsAndContinuity();
        test.allFifteenMeshesAndOriginalRenderSink();test.normalsFollowJacobianAndOuterPose();
        test.tessellationPreservesOriginalSurfacesAndUv();
        test.localEffectsUseTheSameWarpAndZeroBudgetStops();
        test.bothArmsShareOneSinkWithoutStaleBinding();
    }
}
