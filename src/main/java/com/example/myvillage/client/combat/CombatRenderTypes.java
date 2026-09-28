package com.example.myvillage.client.combat;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector3f;

final class CombatRenderTypes {
    /** Additive, unlit, double-sided ribbon that does not write depth. */
    static final RenderType SWORD_TRAIL = RenderType.create(
            "myvillage_sword_trail",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    static final int TIP_RED = 196;
    static final int TIP_GREEN = 242;
    static final int TIP_BLUE = 255;
    static final int BASE_RED = 96;
    static final int BASE_GREEN = 196;
    static final int BASE_BLUE = 255;

    private CombatRenderTypes() {
    }

    /** One ribbon quad between two blade samples; alpha is per sample, the base is dimmer than the tip. */
    static void ribbonQuad(
            VertexConsumer consumer,
            Vector3f newerBase,
            Vector3f newerTip,
            float newerAlpha,
            Vector3f olderBase,
            Vector3f olderTip,
            float olderAlpha) {
        vertex(consumer, newerBase, false, newerAlpha);
        vertex(consumer, newerTip, true, newerAlpha);
        vertex(consumer, olderTip, true, olderAlpha);
        vertex(consumer, olderBase, false, olderAlpha);
    }

    private static void vertex(VertexConsumer consumer, Vector3f point, boolean tip, float alpha) {
        float bounded = Math.max(0.0F, Math.min(1.0F, alpha));
        consumer.addVertex(point.x, point.y, point.z).setColor(
                tip ? TIP_RED : BASE_RED,
                tip ? TIP_GREEN : BASE_GREEN,
                tip ? TIP_BLUE : BASE_BLUE,
                Math.round((tip ? bounded : bounded * 0.2F) * 255.0F));
    }
}
