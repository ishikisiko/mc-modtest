package com.example.myvillage.client.entity.beast;

import com.example.myvillage.entity.beast.BeastDefinition;
import com.example.myvillage.entity.beast.BeastDefinitions;
import com.example.myvillage.entity.beast.BeastDataException;
import com.example.myvillage.entity.beast.BeastEntity;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renderer for any {@link BeastEntity}, built from its client assets (derived from the entity id
 * {@code ns:name}): {@code assets/ns/beast/name_model.json}, {@code name_animations.json},
 * {@code textures/entity/name/name.png} and the emissive {@code name_eyes.png}.
 *
 * <p>The layer definition is a supplier that reads the model file through the client resource
 * manager, and renderers are rebuilt on every resource reload (after the model set), so F3+T picks
 * up changed model, animation and texture files. A missing or invalid file is a startup or reload
 * error naming the file and field.
 */
public class BeastRenderer<T extends BeastEntity> extends MobRenderer<T, BeastModel<T>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BeastRenderer.class);
    private final ResourceLocation texture;

    public BeastRenderer(EntityRendererProvider.Context context, ResourceLocation beastId) {
        this(context, beastId, BeastRenderer.<T>load(context, beastId));
    }

    private BeastRenderer(EntityRendererProvider.Context context, ResourceLocation beastId, Loaded<T> loaded) {
        super(context, loaded.model(), loaded.shadowRadius());
        this.texture = texture(beastId);
        RenderType eyes = RenderType.eyes(eyesTexture(beastId));
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return eyes;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(T beast) {
        return texture;
    }

    /** Registers a beast entity type's renderer. */
    public static <T extends BeastEntity> void register(
            EntityRenderersEvent.RegisterRenderers event, DeferredHolder<EntityType<?>, EntityType<T>> type) {
        ResourceLocation id = type.getId();
        event.registerEntityRenderer(type.get(), context -> new BeastRenderer<>(context, id));
    }

    /** Registers a beast's layer definition, read from its model file on every resource reload. */
    public static void registerLayer(EntityRenderersEvent.RegisterLayerDefinitions event, ResourceLocation beastId) {
        event.registerLayerDefinition(layer(beastId),
                () -> readModel(Minecraft.getInstance().getResourceManager(), beastId).toLayerDefinition());
    }

    public static ModelLayerLocation layer(ResourceLocation beastId) {
        return new ModelLayerLocation(beastId, "main");
    }

    public static ResourceLocation modelFile(ResourceLocation beastId) {
        return beastId.withPath("beast/" + beastId.getPath() + "_model.json");
    }

    public static ResourceLocation animationFile(ResourceLocation beastId) {
        return beastId.withPath("beast/" + beastId.getPath() + "_animations.json");
    }

    public static ResourceLocation texture(ResourceLocation beastId) {
        return beastId.withPath("textures/entity/" + beastId.getPath() + "/" + beastId.getPath() + ".png");
    }

    public static ResourceLocation eyesTexture(ResourceLocation beastId) {
        return beastId.withPath("textures/entity/" + beastId.getPath() + "/" + beastId.getPath() + "_eyes.png");
    }

    private record Loaded<T extends BeastEntity>(BeastModel<T> model, float shadowRadius) {
    }

    private static <T extends BeastEntity> Loaded<T> load(EntityRendererProvider.Context context, ResourceLocation beastId) {
        ResourceManager resources = context.getResourceManager();
        BeastModelFile modelFile = readModel(resources, beastId);
        ResourceLocation animationLocation = animationFile(beastId);
        String animationPath = assetPath(animationLocation);
        BeastAnimationFile animations = read(resources, animationLocation,
                reader -> BeastAnimationFile.parse(animationPath, beastId, reader));
        BeastDefinition definition = BeastDefinitions.bundled().require(beastId);
        animations.check(animationPath, modelFile, definition);
        Map<String, AnimationDefinition> clips = animations.toAnimationDefinitions();
        BeastGait.Rates gait = BeastGait.rates(modelFile, animations, definition, BeastEntity.WALK_CLIP, BeastEntity.RUN_CLIP);
        LOGGER.info("Beast {}: walk rate {} (planted feet {} blocks/s, stroll {} blocks/tick), run rate {} ({} blocks/s, "
                        + "chase {} blocks/tick)", beastId, gait.walk(), round(gait.walkFootSpeed()), round(gait.walkBlocksPerTick()),
                gait.run(), round(gait.runFootSpeed()), round(gait.runBlocksPerTick()));
        BeastModel<T> model = new BeastModel<>(context.bakeLayer(layer(beastId)), modelFile, clips, gait);
        return new Loaded<>(model, modelFile.shadowRadius());
    }

    static BeastModelFile readModel(ResourceManager resources, ResourceLocation beastId) {
        ResourceLocation location = modelFile(beastId);
        String path = assetPath(location);
        return read(resources, location, reader -> BeastModelFile.parse(path, beastId, reader));
    }

    private interface Parser<R> {
        R parse(Reader reader);
    }

    private static <R> R read(ResourceManager resources, ResourceLocation location, Parser<R> parser) {
        String path = assetPath(location);
        Resource resource = resources.getResource(location)
                .orElseThrow(() -> new BeastDataException(path, "<file>", "is missing"));
        try (Reader reader = resource.openAsReader()) {
            return parser.parse(reader);
        } catch (IOException exception) {
            throw new BeastDataException(path, "<file>", "cannot read: " + exception.getMessage(), exception);
        }
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    static String assetPath(ResourceLocation location) {
        return "assets/" + location.getNamespace() + "/" + location.getPath();
    }
}
