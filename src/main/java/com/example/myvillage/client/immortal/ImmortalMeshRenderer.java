package com.example.myvillage.client.immortal;

import com.example.myvillage.equipment.immortal.ImmortalMeshData.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.example.myvillage.client.cultivation.motion.MeditationPart;
import com.example.myvillage.client.cultivation.motion.MeditationSurface;
import com.example.myvillage.client.cultivation.motion.MeditationWarp;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import org.joml.Vector3f;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Stateless model-local drawing. No world particles or per-entity animation history survive a draw. */
final class ImmortalMeshRenderer {
    record Wearing(ImmortalMeshResources.Loaded mesh, Profile profile, String slot, boolean foil) { }
    private record Transparent(Wearing wearing,Part part,float distance) { }
    static void draw(List<Wearing> pieces, HumanoidModel<?> model, PoseStack poses, MultiBufferSource buffers,
            int light, double seconds, boolean effects, int budget, String onlyBone) {
        draw(pieces,model,poses,buffers,light,seconds,effects,budget,onlyBone,null);
    }
    static void draw(List<Wearing> pieces, HumanoidModel<?> model, PoseStack viewPoses, MultiBufferSource viewBuffers,
            int light, double seconds, boolean effects, int budget, String onlyBone, ImmortalArmDraw warp) {
        // IK passes run all original mesh/motion/effect code in bone-local space, then deform
        // the emitted vertices once. Both transparency centers and every pass use the same warp.
        PoseStack poses=warp==null?viewPoses:new PoseStack();
        MultiBufferSource buffers=warp==null?viewBuffers:
                type->ImmortalFirstPersonArm.consumer(viewBuffers.getBuffer(type),warp,viewPoses.last());
        // Emit opaque batches first. Consumers never survive another getBuffer call.
        for(Wearing w:pieces) for(var entry:w.mesh.data().materials().entrySet()) {
            Material material=entry.getValue();if(material.opacity()!=1)continue;
            var surface=w.mesh.surfaces().get(entry.getKey());
            for(int sided=0;sided<2;sided++) {
                boolean doubleSided=sided==1;
                VertexConsumer consumer=buffers.getBuffer(doubleSided?surface.solidDouble():surface.solid());
                for(Part part:w.profile.parts()) if(selected(part,w.slot,onlyBone,warp)&&part.material().equals(entry.getKey())
                        &&part.side().equals("double")==doubleSided) {
                    poses.pushPose();bind(poses,model,part.bone(),warp);if(effects)motion(poses,w,part,seconds);
                    triangles(poses,consumer,warp==null?part:w.mesh.armMeshes().get(part),light,0xffffff,1,false,meditation(model,part,warp));poses.popPose();
                }
            }
        }
        // Sort transparent components across materials/pieces, then let each material's QUADS
        // buffer sort its individual faces. Glass never writes depth or duplicates its back faces.
        List<Transparent> transparent=new ArrayList<>();
        for(Wearing w:pieces)for(Part part:w.profile.parts()) {
            float alpha=w.mesh.data().materials().get(part.material()).opacity();
            if(!selected(part,w.slot,onlyBone,warp)||alpha<=0||alpha==1)continue;
            poses.pushPose();bind(poses,model,part.bone(),warp);if(effects)motion(poses,w,part,seconds);
            Vec3 center=w.mesh.centers().get(part);Vector3f eye=new Vector3f(center.x()/16,center.y()/16,center.z()/16);
            poses.last().pose().transformPosition(eye);
            if(warp!=null) {
                eye=warp.current().position(eye.x*16,eye.y*16,eye.z*16);
                viewPoses.last().pose().transformPosition(eye);
            }
            transparent.add(new Transparent(w,part,eye.lengthSquared()));poses.popPose();
        }
        transparent.sort(Comparator.comparingDouble(Transparent::distance).reversed());
        for(Transparent draw:transparent) {
            Wearing w=draw.wearing;Part part=draw.part;
            VertexConsumer consumer=buffers.getBuffer(w.mesh.surfaces().get(part.material()).translucent());
            poses.pushPose();bind(poses,model,part.bone(),warp);if(effects)motion(poses,w,part,seconds);
            triangles(poses,consumer,warp==null?part:w.mesh.armMeshes().get(part),light,0xffffff,w.mesh.data().materials().get(part.material()).opacity(),false,meditation(model,part,warp));poses.popPose();
        }
        // Vanilla armor glint shader/texture/scroll. Translucent surfaces use LEQUAL because their
        // color-only base pass deliberately left the scene's depth buffer untouched.
        for(int pass=0;pass<2;pass++) {
            VertexConsumer consumer=buffers.getBuffer(pass==0?ImmortalRenderTypes.GLINT:ImmortalRenderTypes.GLASS_GLINT);
            for(Wearing w:pieces)if(w.foil)for(Part part:w.profile.parts()) {
                float alpha=w.mesh.data().materials().get(part.material()).opacity();
                if(!selected(part,w.slot,onlyBone,warp)||alpha<=0||(alpha<1)!=(pass==1))continue;
                poses.pushPose();bind(poses,model,part.bone(),warp);if(effects)motion(poses,w,part,seconds);
                triangles(poses,consumer,warp==null?part:w.mesh.armMeshes().get(part),light,0xffffff,1,true,meditation(model,part,warp));poses.popPose();
            }
        }
        if(!effects) return;
        for(Wearing w:pieces) for(int glowIndex=0;glowIndex<w.mesh.data().effects().glows().size();glowIndex++) {
            Glow glow=w.mesh.data().effects().glows().get(glowIndex);
            float alpha=(float)(glow.min()+(glow.max()-glow.min())*(.5-.5*Math.cos(seconds*Math.PI*2/glow.period()+glowIndex*.7)));
            for(Part part:w.profile.parts()) if(selected(part,w.slot,onlyBone,warp)&&glow.targets().contains(part.id())) {
                float opacity=w.mesh.data().materials().get(part.material()).opacity();
                if(opacity<=0)continue;
                VertexConsumer consumer=buffers.getBuffer(w.mesh.surfaces().get(part.material()).glow());
                poses.pushPose();bind(poses,model,part.bone(),warp);motion(poses,w,part,seconds);
                triangles(poses,consumer,warp==null?part:w.mesh.armMeshes().get(part),0xf000f0,glow.color(),alpha*opacity,false,meditation(model,part,warp));poses.popPose();
            }
        }
        int[] remaining={budget};
        for(Wearing w:pieces) ImmortalModelEffects.draw(w,model,poses,buffers,seconds,remaining,onlyBone,warp);
    }
    private static boolean selected(Part p,String slot,String bone,ImmortalArmDraw warp) {
        return p.slot().equals(slot)&&(bone==null||bone.equals(p.bone()))&&(warp==null||warp.includes(p.bone()));
    }
    static ModelPart bone(HumanoidModel<?> m,String bone) {
        return switch(bone) {case "head"->m.head;case "body"->m.body;case "right_arm"->m.rightArm;
            case "left_arm"->m.leftArm;case "right_leg"->m.rightLeg;case "left_leg"->m.leftLeg;
            default->throw new IllegalArgumentException("未知仙衣骨骼 "+bone);};
    }
    static void bind(PoseStack poses,HumanoidModel<?> model,String bone) {
        bind(poses,model,bone,null);
    }
    static void bind(PoseStack poses,HumanoidModel<?> model,String bone,ImmortalArmDraw warp) {
        if(warp!=null){warp.bind(bone);return;}

        // HumanoidModel's AgeableListModel transform lives outside ModelPart. Match it before
        // applying the actual bone, including small armor stands and vanilla baby humanoids.
        if(model.young) {
            if(bone.equals("head")) {poses.scale(.75f,.75f,.75f);poses.translate(0,1,0);}
            else {poses.scale(.5f,.5f,.5f);poses.translate(0,1.5f,0);}
        }
        bone(model,bone).translateAndRotate(poses);
    }
    static void motion(PoseStack poses,Wearing w,Part part,double seconds) {
        for(int index=0;index<w.mesh.data().effects().motions().size();index++) {
            Motion m=w.mesh.data().effects().motions().get(index);
            if(!m.targets().contains(part.id()))continue;
            double wave=Math.sin(seconds*Math.PI*2/m.period()+index*.83);
            Vec3 p=w.mesh.local(w.profile,part.bone(),m.pivot()),lift=w.mesh.vector(w.profile,part.bone(),m.lift());
            float x=p.x()/16,y=p.y()/16,z=p.z()/16;
            poses.translate(lift.x()*wave/16,lift.y()*wave/16,lift.z()*wave/16);
            poses.translate(x,y,z);
            // Author to Minecraft is a 180-degree X rotation; axial Y and Z signs invert.
            float angle=(float)(m.angle()*wave*(m.axis().equals("x")?1:-1));
            poses.mulPose((m.axis().equals("x")?Axis.XP:m.axis().equals("y")?Axis.YP:Axis.ZP).rotationDegrees(angle));
            poses.translate(-x,-y,-z);
        }
    }
    private static final java.util.Map<Part,List<MeditationSurface.Vertex>> MEDITATION_MESHES=new java.util.WeakHashMap<>();
    private static MeditationWarp meditation(HumanoidModel<?> model,Part part,ImmortalArmDraw firstPerson) {
        return firstPerson==null && model!=null ? ((MeditationPart)(Object)bone(model,part.bone())).myvillage$meditationWarp() : null;
    }
    private static void triangles(PoseStack poses,VertexConsumer out,Part part,int light,int rgb,float alpha,boolean glint,MeditationWarp warp) {
        if(warp==null){triangles(poses,out,part,light,rgb,alpha,glint);return;}
        var mesh=MEDITATION_MESHES.computeIfAbsent(part,p->{
            List<MeditationSurface.Vertex> vertices=new ArrayList<>();
            for(int i:p.indices()) {Vertex v=p.vertices().get(i);vertices.add(new MeditationSurface.Vertex(v.x(),v.y(),v.z(),v.u(),v.v(),v.nx(),v.ny(),v.nz()));}
            return MeditationSurface.tessellate(vertices);
        });
        MeditationSurface.draw(mesh,poses.last(),out,warp,light,OverlayTexture.NO_OVERLAY,
                Math.round(alpha*255)<<24 | rgb,glint);
    }
    static void triangles(PoseStack poses,VertexConsumer out,Part part,int light,int rgb,float alpha,boolean glint) {
        var indices=part.indices(); var vertices=part.vertices();
        for(int i=0;i<indices.size();i+=3) {
            vertex(poses,out,vertices.get(indices.get(i)),light,rgb,alpha,glint);
            vertex(poses,out,vertices.get(indices.get(i+1)),light,rgb,alpha,glint);
            Vertex c=vertices.get(indices.get(i+2));
            vertex(poses,out,c,light,rgb,alpha,glint);
            // QUADS triangulates ABC + CDA. D=C makes the second triangle degenerate.
            vertex(poses,out,c,light,rgb,alpha,glint);
        }
    }
    private static void vertex(PoseStack poses,VertexConsumer out,Vertex v,int light,int rgb,float alpha,boolean glint) {
        out.addVertex(poses.last(),v.x()/16,v.y()/16,v.z()/16);
        if(glint) {out.setUv(v.u(),v.v());return;}
        out.setColor((rgb>>16)&255,(rgb>>8)&255,rgb&255,Math.round(alpha*255)).setUv(v.u(),v.v())
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(poses.last(),v.nx(),v.ny(),v.nz());
    }
    private ImmortalMeshRenderer() { }
}
