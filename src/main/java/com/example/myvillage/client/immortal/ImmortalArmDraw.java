package com.example.myvillage.client.immortal;

import java.util.Map;

/** One synchronous draw, shared by both arms so opaque/transparent/effect ordering stays global. */
final class ImmortalArmDraw {
    private final Map<String,ImmortalArmDeformation> arms;
    private ImmortalArmDeformation current;
    ImmortalArmDraw(Map<String,ImmortalArmDeformation> arms) {this.arms=Map.copyOf(arms);}
    boolean includes(String bone) {return arms.containsKey(bone);}
    void bind(String bone) {
        current=arms.get(bone);
        if(current==null)throw new IllegalArgumentException("未选中的 IK 袖臂 "+bone);
    }
    ImmortalArmDeformation current() {
        if(current==null)throw new IllegalStateException("IK 袖臂尚未绑定");
        return current;
    }
}
