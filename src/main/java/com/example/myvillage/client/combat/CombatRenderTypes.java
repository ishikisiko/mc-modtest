package com.example.myvillage.client.combat;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector3f;

final class CombatRenderTypes {
    /**
     * Alpha-blended (SRC_ALPHA, ONE_MINUS_SRC_ALPHA), unlit, double-sided ribbon that does not
     * write depth. Normal blending keeps the trail's hue and edge in daylight, where additive
     * blending clipped to a white slab over the sky.
     */
    static final RenderType WEAPON_TRAIL_TRANSLUCENT = RenderType.create(
            "myvillage_weapon_trail_translucent",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            4096,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    /** Near-white edge along the tip's path. */
    static final int EDGE_RED = 235;
    static final int EDGE_GREEN = 248;
    static final int EDGE_BLUE = 255;
    /** Soft pale-blue body inside the edge. */
    static final int BODY_RED = 160;
    static final int BODY_GREEN = 215;
    static final int BODY_BLUE = 255;

    private CombatRenderTypes() {
    }

    /**
     * One trail segment between a newer and an older blade sample, drawn as two bands across the
     * blade: a soft body from the age-tapered inner edge out to the edge line (alpha 0 inside,
     * half the sample alpha at the edge line), and a near-white edge band from there to the tip.
     */
    static void trailSegment(
            VertexConsumer consumer,
            Vector3f newerBase,
            Vector3f newerTip,
            float newerAge,
            float newerAlpha,
            Vector3f olderBase,
            Vector3f olderTip,
            float olderAge,
            float olderAlpha) {
        Vector3f newerInner = along(newerBase, newerTip, WeaponTrailShape.innerFraction(newerAge));
        Vector3f newerEdge = along(newerBase, newerTip, WeaponTrailShape.edgeFraction(newerAge));
        Vector3f olderInner = along(olderBase, olderTip, WeaponTrailShape.innerFraction(olderAge));
        Vector3f olderEdge = along(olderBase, olderTip, WeaponTrailShape.edgeFraction(olderAge));
        float body = WeaponTrailShape.BODY_ALPHA_SHARE;
        float tip = WeaponTrailShape.TIP_ALPHA_SHARE;

        body(consumer, newerInner, 0.0F);
        body(consumer, newerEdge, newerAlpha * body);
        body(consumer, olderEdge, olderAlpha * body);
        body(consumer, olderInner, 0.0F);

        edge(consumer, newerEdge, newerAlpha);
        edge(consumer, newerTip, newerAlpha * tip);
        edge(consumer, olderTip, olderAlpha * tip);
        edge(consumer, olderEdge, olderAlpha);
    }

    /**
     * A thin streak along a thrust's axis, turned to face the camera (which sits at the origin
     * of the vertices' space). The base end is narrow and transparent, the tip end carries
     * {@code alpha}.
     */
    static void streak(VertexConsumer consumer, Vector3f base, Vector3f tip, float halfWidth, float alpha) {
        Vector3f axis = new Vector3f(tip).sub(base);
        Vector3f middle = new Vector3f(base).add(tip).mul(0.5F);
        Vector3f toCamera = middle.negate(new Vector3f());
        Vector3f side = axis.cross(toCamera, new Vector3f());
        if (side.lengthSquared() < 1.0E-10F) {
            return;
        }
        side.normalize();
        Vector3f baseSide = new Vector3f(side).mul(halfWidth * 0.35F);
        Vector3f tipSide = new Vector3f(side).mul(halfWidth);
        edge(consumer, new Vector3f(base).add(baseSide), 0.0F);
        edge(consumer, new Vector3f(tip).add(tipSide), alpha);
        edge(consumer, new Vector3f(tip).sub(tipSide), alpha);
        edge(consumer, new Vector3f(base).sub(baseSide), 0.0F);
    }

    private static Vector3f along(Vector3f base, Vector3f tip, float fraction) {
        return new Vector3f(base).lerp(tip, fraction);
    }

    private static void body(VertexConsumer consumer, Vector3f point, float alpha) {
        consumer.addVertex(point.x, point.y, point.z)
                .setColor(BODY_RED, BODY_GREEN, BODY_BLUE, alphaByte(alpha));
    }

    private static void edge(VertexConsumer consumer, Vector3f point, float alpha) {
        consumer.addVertex(point.x, point.y, point.z)
                .setColor(EDGE_RED, EDGE_GREEN, EDGE_BLUE, alphaByte(alpha));
    }

    private static int alphaByte(float alpha) {
        return Math.round(WeaponTrailShape.clamp01(alpha) * 255.0F);
    }
}
