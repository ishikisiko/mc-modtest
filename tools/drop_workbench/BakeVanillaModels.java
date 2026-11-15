import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelPart;
import java.nio.file.*;
import java.util.*;

/** 只烘焙本地 1.21.1 模型层，无窗口、世界、渲染器或游戏循环。 */
public class BakeVanillaModels {
    static final class Capture implements VertexConsumer {
        final List<float[]> vertices = new ArrayList<>();
        float[] current;
        public VertexConsumer addVertex(float x,float y,float z){current=new float[]{x,y,z,0,0};vertices.add(current);return this;}
        public VertexConsumer setUv(float u,float v){current[3]=u;current[4]=v;return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    static void dragon(ModelPart root,Capture capture){
        // DragonModel 的平飞姿势：首尾共用 neck 模型段，不能直接把根部各段重叠绘制。
        PoseStack pose=new PoseStack();
        ModelPart neck=root.getChild("neck"),head=root.getChild("head");
        float y=20,z=-12;
        for(int i=0;i<5;i++){
            neck.xRot=(float)Math.cos(i*.45)*.15F;neck.yRot=0;neck.zRot=0;
            neck.setPos(0,y,z);neck.render(pose,capture,0,0);
            y+=(float)Math.sin(neck.xRot)*10;z-=(float)Math.cos(neck.xRot)*10;
        }
        head.setPos(0,y,z);head.getChild("jaw").xRot=.2F;head.render(pose,capture,0,0);
        root.getChild("body").render(pose,capture,0,0);
        for(String side:List.of("left","right")){
            float sign=side.equals("left")?1:-1;
            ModelPart wing=root.getChild(side+"_wing");wing.xRot=-.075F;wing.yRot=-.25F*sign;wing.zRot=-.1F*sign;
            wing.getChild(side+"_wing_tip").zRot=(float)(Math.sin(2)+.5)*.75F*sign;
            wing.render(pose,capture,0,0);
            for(String pos:List.of("front","hind")){
                String key=side+"_"+pos+"_leg";
                ModelPart leg=root.getChild(key);leg.xRot=pos.equals("front")?1.3F:1;
                ModelPart tip=leg.getChild(key+"_tip");tip.xRot=pos.equals("front")?-.5F:.5F;
                tip.getChild(side+"_"+pos+"_foot").xRot=.75F;leg.render(pose,capture,0,0);
            }
        }
        y=10;z=60;float tilt=0;
        for(int i=0;i<12;i++){
            tilt+=(float)Math.sin(i*.45)*.05F;neck.xRot=tilt;neck.yRot=(float)Math.PI;neck.zRot=0;
            neck.setPos(0,y,z);neck.render(pose,capture,0,0);
            y+=(float)Math.sin(tilt)*10;z+=(float)Math.cos(tilt)*10;
        }
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Map<String,Object> output=new TreeMap<>();
        LayerDefinitions.createRoots().forEach((key,layer)->{
            ModelPart root=layer.bakeRoot();
            Capture capture=new Capture();
            if(key.toString().equals("minecraft:ender_dragon#main"))dragon(root,capture);
            else {
                if(key.getModel().toString().equals("minecraft:armadillo"))root.getChild("cube").visible=false;
                // 模型层同时含有幼体腿、鞍具和箱子；代表图显示无装备的成年个体。
                for(String name:List.of("right_hind_baby_leg","left_hind_baby_leg","right_front_baby_leg","left_front_baby_leg"))
                    if(root.hasChild(name))root.getChild(name).visible=false;
                if(root.hasChild("body"))for(String name:List.of("saddle","left_chest","right_chest"))
                    if(root.getChild("body").hasChild(name))root.getChild("body").getChild(name).visible=false;
                if(root.hasChild("head_parts"))for(String name:List.of("left_saddle_mouth","right_saddle_mouth","left_saddle_line","right_saddle_line","head_saddle","mouth_saddle_wrap"))
                    if(root.getChild("head_parts").hasChild(name))root.getChild("head_parts").getChild(name).visible=false;
                if(Set.of("minecraft:evoker","minecraft:illusioner","minecraft:pillager","minecraft:vindicator").contains(key.getModel().toString())){
                    root.getChild("right_arm").visible=false;root.getChild("left_arm").visible=false;
                }
                root.render(new PoseStack(),capture,0,0);
            }
            output.put(key.toString(),capture.vertices);
        });
        Files.writeString(Path.of(args[0]),new Gson().toJson(output));
        System.out.println("已导出模型层："+output.size());
    }
}
