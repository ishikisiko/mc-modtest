package com.example.myvillage.client.entity.npc;

import com.example.myvillage.client.entity.beast.BeastAnimationFile;
import com.example.myvillage.client.entity.beast.BeastGait;
import com.example.myvillage.client.entity.beast.BeastModelFile;
import com.example.myvillage.entity.beast.BeastDataException;
import com.example.myvillage.entity.npc.NpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renderer for any {@link NpcEntity}, built from its client assets (derived from the entity id
 * {@code ns:name}): {@code assets/ns/npc/name_model.json}, {@code name_animations.json} and
 * {@code textures/entity/name/name.png}, all written by {@code tools/npcgen}. The files use the
 * beast model and animation schemas ({@link BeastModelFile}, {@link BeastAnimationFile}); the
 * model's {@code scale} is applied here, so a half-scale file draws at twice the texel density.
 *
 * <p>The texture is drawn with the model's default cut-out render type: a texel with zero alpha is
 * a hole, which is how a shell layer (hair, an open vest) shows the layer under it. Like the beast
 * renderer, the layer definition reads the model file on every resource reload, and a missing or
 * invalid file is a startup or reload error naming the file and field.
 */
public class NpcRenderer<T extends NpcEntity> extends MobRenderer<T, NpcModel<T>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(NpcRenderer.class);
    private final ResourceLocation texture;
    private final float modelScale;

    public NpcRenderer(EntityRendererProvider.Context context, ResourceLocation npcId) {
        this(context, npcId, NpcRenderer.<T>load(context, npcId));
    }

    private NpcRenderer(EntityRendererProvider.Context context, ResourceLocation npcId, Loaded<T> loaded) {
        super(context, loaded.model(), loaded.shadowRadius());
        this.texture = texture(npcId);
        this.modelScale = loaded.scale();
    }

    @Override
    public ResourceLocation getTextureLocation(T npc) {
        return texture;
    }

    @Override
    protected void scale(T npc, PoseStack poseStack, float partialTick) {
        poseStack.scale(modelScale, modelScale, modelScale);
    }

    /** Registers an NPC entity type's renderer. */
    public static <T extends NpcEntity> void register(
            EntityRenderersEvent.RegisterRenderers event, DeferredHolder<EntityType<?>, EntityType<T>> type) {
        ResourceLocation id = type.getId();
        event.registerEntityRenderer(type.get(), context -> new NpcRenderer<>(context, id));
    }

    /** Registers an NPC's layer definition, read from its model file on every resource reload. */
    public static void registerLayer(EntityRenderersEvent.RegisterLayerDefinitions event, ResourceLocation npcId) {
        event.registerLayerDefinition(layer(npcId),
                () -> readModel(Minecraft.getInstance().getResourceManager(), npcId).toLayerDefinition());
    }

    public static ModelLayerLocation layer(ResourceLocation npcId) {
        return new ModelLayerLocation(npcId, "main");
    }

    public static ResourceLocation modelFile(ResourceLocation npcId) {
        return npcId.withPath("npc/" + npcId.getPath() + "_model.json");
    }

    public static ResourceLocation animationFile(ResourceLocation npcId) {
        return npcId.withPath("npc/" + npcId.getPath() + "_animations.json");
    }

    public static ResourceLocation texture(ResourceLocation npcId) {
        return npcId.withPath("textures/entity/" + npcId.getPath() + "/" + npcId.getPath() + ".png");
    }

    /**
     * Cross-file rules for an NPC: every channel names a bone of {@code model}, and the
     * {@code idle} and {@code walk} clips exist and loop.
     */
    public static void check(String file, BeastModelFile model, BeastAnimationFile animations) {
        for (BeastAnimationFile.Clip clip : animations.clips().values()) {
            for (int index = 0; index < clip.channels().size(); index++) {
                String bone = clip.channels().get(index).bone();
                if (!model.hasBone(bone)) {
                    throw new BeastDataException(file, "clips." + clip.name() + ".channels[" + index + "].bone",
                            "names " + bone + ", which is not a bone of the model");
                }
            }
        }
        for (String name : new String[] {NpcEntity.IDLE_CLIP, NpcEntity.WALK_CLIP}) {
            BeastAnimationFile.Clip clip = animations.clip(name)
                    .orElseThrow(() -> new BeastDataException(file, "clips." + name, "is required"));
            if (!clip.loop()) {
                throw new BeastDataException(file, "clips." + name + ".loop", "must be true");
            }
        }
    }

    /**
     * The {@code animateWalk} rate at which the walk clip's planted foot stays where it is: limb
     * swing advances four per block walked and the clip 50 ms times the rate per unit of it, so a
     * foot moving back at {@code footSpeed} blocks per clip second needs {@code 5 / footSpeed}.
     */
    public static float walkRate(double footSpeed) {
        return footSpeed > 1.0E-6 ? (float) (5.0 / footSpeed) : 1.0F;
    }

    private record Loaded<T extends NpcEntity>(NpcModel<T> model, float shadowRadius, float scale) {
    }

    private static <T extends NpcEntity> Loaded<T> load(EntityRendererProvider.Context context, ResourceLocation npcId) {
        ResourceManager resources = context.getResourceManager();
        BeastModelFile modelFile = readModel(resources, npcId);
        ResourceLocation animationLocation = animationFile(npcId);
        String animationPath = assetPath(animationLocation);
        BeastAnimationFile animations = read(resources, animationLocation,
                reader -> BeastAnimationFile.parse(animationPath, npcId, reader));
        check(animationPath, modelFile, animations);
        Map<String, AnimationDefinition> clips = animations.toAnimationDefinitions();
        double footSpeed = BeastGait.plantedFootSpeed(modelFile, animations.clip(NpcEntity.WALK_CLIP).orElseThrow());
        float walkRate = walkRate(footSpeed);
        LOGGER.info("NPC {}: scale {}, walk rate {} (planted foot {} blocks per clip second)",
                npcId, modelFile.scale(), walkRate, Math.round(footSpeed * 1000.0) / 1000.0);
        NpcModel<T> model = new NpcModel<>(context.bakeLayer(layer(npcId)), modelFile,
                clips.get(NpcEntity.IDLE_CLIP), clips.get(NpcEntity.WALK_CLIP), walkRate);
        return new Loaded<>(model, modelFile.shadowRadius(), modelFile.scale());
    }

    static BeastModelFile readModel(ResourceManager resources, ResourceLocation npcId) {
        ResourceLocation location = modelFile(npcId);
        String path = assetPath(location);
        return read(resources, location, reader -> BeastModelFile.parse(path, npcId, reader));
    }

    private static <R> R read(ResourceManager resources, ResourceLocation location, Function<Reader, R> parser) {
        String path = assetPath(location);
        Resource resource = resources.getResource(location)
                .orElseThrow(() -> new BeastDataException(path, "<file>", "is missing"));
        try (Reader reader = resource.openAsReader()) {
            return parser.apply(reader);
        } catch (IOException exception) {
            throw new BeastDataException(path, "<file>", "cannot read: " + exception.getMessage(), exception);
        }
    }

    static String assetPath(ResourceLocation location) {
        return "assets/" + location.getNamespace() + "/" + location.getPath();
    }
}
