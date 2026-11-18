package com.example.myvillage.client.item;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.item.QualityPickerArrays;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

@EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
public final class QuickArrayPreview {
    private static final RenderType MATERIAL = RenderType.create("yuanxian_quick_array_preview",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 131072, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private static final RenderType CLEARANCE = RenderType.create("yuanxian_quick_array_clearance",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private record Piece(BlockPos pos, List<BakedQuad> faces) {}
    private record Mesh(QualityPickerArrays.Preview ticket, BoundingBox bounds, List<Piece> pieces) {}
    private static Mesh mesh;

    private QuickArrayPreview() {}

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !QualityPickerArrays.builderHand(mc.player)) {
            mesh = null;
            return;
        }
        var preview = QualityPickerArrays.selectedPreview(mc.player.getMainHandItem()).orElse(null);
        if (preview == null || !preview.dimension().equals(mc.level.dimension().location())) {
            mesh = null;
            return;
        }
        var camera = event.getCamera().getPosition();
        if (camera.distanceToSqr(preview.ground().getCenter()) > 64 * 64) return;
        if (mesh == null || !preview.equals(mesh.ticket())) mesh = bake(mc, preview);
        var pose = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        var out = buffers.getBuffer(MATERIAL);
        for (var piece : mesh.pieces()) {
            var pos = piece.pos();
            for (var face : piece.faces()) {
                int[] data = face.getVertices();
                int stride = data.length / 4;
                for (int i = 0; i < 4; i++) {
                    int at = i * stride;
                    out.addVertex(pose.last(), pos.getX() + Float.intBitsToFloat(data[at]),
                                    pos.getY() + Float.intBitsToFloat(data[at + 1]), pos.getZ() + Float.intBitsToFloat(data[at + 2]))
                            .setUv(Float.intBitsToFloat(data[at + 4]), Float.intBitsToFloat(data[at + 5]))
                            .setColor(195, 246, 240, 115);
                }
            }
        }
        buffers.endBatch(MATERIAL);
        var bounds = mesh.bounds();
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(CLEARANCE), bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1, .98F, .64F, .26F, .95F);
        buffers.endBatch(CLEARANCE);
        pose.popPose();
    }

    private static Mesh bake(Minecraft mc, QualityPickerArrays.Preview preview) {
        var build = QualityPickerArrays.blueprint(preview.layout(), preview.ground(), preview.front());
        var pieces = new ArrayList<Piece>();
        for (var cell : build.cells()) {
            var state = cell.blockState();
            if (state.isAir()) continue;
            var model = mc.getBlockRenderer().getBlockModel(state);
            var seed = state.getSeed(cell.pos());
            var random = RandomSource.create(seed);
            var faces = new ArrayList<>(model.getQuads(state, null, random, ModelData.EMPTY, null));
            for (var side : Direction.values()) {
                random.setSeed(seed);
                faces.addAll(model.getQuads(state, side, random, ModelData.EMPTY, null));
            }
            pieces.add(new Piece(cell.pos(), faces));
        }
        return new Mesh(preview, build.bounds(), List.copyOf(pieces));
    }
}
