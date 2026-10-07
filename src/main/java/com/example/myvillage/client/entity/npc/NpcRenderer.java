package com.example.myvillage.client.entity.npc;

import com.example.myvillage.client.entity.beast.BeastAnimationFile;
import com.example.myvillage.client.entity.beast.BeastGait;
import com.example.myvillage.client.entity.beast.BeastModelFile;
import com.example.myvillage.entity.beast.BeastDataException;
import com.example.myvillage.entity.npc.NpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
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
 * {@code ns:name} and the look): the {@code default} look reads {@code assets/ns/npc/name_model.json},
 * {@code name_animations.json} and {@code textures/entity/name/name.png}; any other look reads
 * {@code npc/name_<look>_model.json}, {@code npc/name_<look>_animations.json} and
 * {@code textures/entity/name/name_<look>.png}. All are written by {@code tools/npcgen}. The files
 * use the beast model and animation schemas ({@link BeastModelFile}, {@link BeastAnimationFile});
 * the model's {@code scale} is applied here, so a half-scale file draws at twice the texel density.
 *
 * <p><b>Looks.</b> The renderer loads one model, layer, texture, scale and shadow per look it was
 * registered with and, per frame, draws the entity's synced {@link NpcEntity#look()} by swapping
 * {@code LivingEntityRenderer.model} before vanilla renders. A look the renderer was not given is
 * drawn as {@link NpcEntity#LOOK_DEFAULT} and logged once per name.
 *
 * <p>The texture is drawn with the model's default cut-out render type: a texel with zero alpha is
 * a hole, which is how a shell layer (hair, an open vest) shows the layer under it. Like the beast
 * renderer, the layer definitions read the model files on every resource reload, and a missing or
 * invalid file of any registered look is a startup or reload error naming the file and field.
 *
 * <p><b>Colours.</b> An NPC with {@link NpcEntity#colours()} is drawn with its look's texture
 * recoloured to those hair and eye colours ({@link NpcSkins}, from the look's {@link #roleMap}); a
 * look drawn as the default reads the default's role map.
 */
public class NpcRenderer<T extends NpcEntity> extends MobRenderer<T, NpcModel<T>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(NpcRenderer.class);
    private final ResourceLocation npcId;
    private final Map<String, Loaded<T>> looks;
    private final Loaded<T> fallback;
    private final Set<String> unknownLooks = ConcurrentHashMap.newKeySet();

    /** A renderer with only the default look. */
    public NpcRenderer(EntityRendererProvider.Context context, ResourceLocation npcId) {
        this(context, npcId, List.of(NpcEntity.LOOK_DEFAULT));
    }

    /** {@code looks}: the look names the type wears ({@link NpcEntity#LOOK_DEFAULT} is always loaded). */
    public NpcRenderer(EntityRendererProvider.Context context, ResourceLocation npcId, List<String> looks) {
        this(context, npcId, NpcRenderer.<T>loadAll(context, npcId, looks));
    }

    private NpcRenderer(EntityRendererProvider.Context context, ResourceLocation npcId, Map<String, Loaded<T>> looks) {
        super(context, looks.get(NpcEntity.LOOK_DEFAULT).model(), looks.get(NpcEntity.LOOK_DEFAULT).shadowRadius());
        this.npcId = npcId;
        this.looks = looks;
        this.fallback = looks.get(NpcEntity.LOOK_DEFAULT);
    }

    @Override
    public void render(T npc, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight) {
        this.model = loaded(npc).model();
        super.render(npc, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(T npc) {
        Loaded<T> loaded = loaded(npc);
        return NpcSkins.texture(npcId, loaded.look(), npc.packedColours(), loaded.texture());
    }

    @Override
    protected float getShadowRadius(T npc) {
        this.shadowRadius = loaded(npc).shadowRadius();
        return super.getShadowRadius(npc);
    }

    @Override
    protected void scale(T npc, PoseStack poseStack, float partialTick) {
        float modelScale = loaded(npc).scale();
        poseStack.scale(modelScale, modelScale, modelScale);
    }

    /** The files of {@code npc}'s look; the default look's for a name this renderer was not given. */
    private Loaded<T> loaded(T npc) {
        String look = npc.look();
        Loaded<T> loaded = looks.get(look);
        if (loaded != null) {
            return loaded;
        }
        if (unknownLooks.add(look)) {
            LOGGER.warn("NPC {}: unknown look {}; drawing {} (known: {})", npcId, look, NpcEntity.LOOK_DEFAULT,
                    looks.keySet());
        }
        return fallback;
    }

    /** Registers an NPC entity type's renderer with only the default look. */
    public static <T extends NpcEntity> void register(
            EntityRenderersEvent.RegisterRenderers event, DeferredHolder<EntityType<?>, EntityType<T>> type) {
        register(event, type, List.of(NpcEntity.LOOK_DEFAULT));
    }

    /**
     * Registers an NPC entity type's renderer for {@code looks} (the type's own list, such as
     * {@code CultivatorEntity.LOOKS}: the renderer is built before any entity exists to ask).
     */
    public static <T extends NpcEntity> void register(
            EntityRenderersEvent.RegisterRenderers event, DeferredHolder<EntityType<?>, EntityType<T>> type,
            List<String> looks) {
        ResourceLocation id = type.getId();
        List<String> names = List.copyOf(looks);
        event.registerEntityRenderer(type.get(), context -> new NpcRenderer<>(context, id, names));
    }

    /** Registers an NPC's default-look layer definition, read from its model file on every resource reload. */
    public static void registerLayer(EntityRenderersEvent.RegisterLayerDefinitions event, ResourceLocation npcId) {
        registerLayer(event, npcId, List.of(NpcEntity.LOOK_DEFAULT));
    }

    /** Registers one layer definition per look ({@link #layer(ResourceLocation, String)}), each read on every reload. */
    public static void registerLayer(EntityRenderersEvent.RegisterLayerDefinitions event, ResourceLocation npcId,
            List<String> looks) {
        for (String look : withDefault(looks)) {
            event.registerLayerDefinition(layer(npcId, look),
                    () -> readModel(Minecraft.getInstance().getResourceManager(), npcId, look).toLayerDefinition());
        }
    }

    public static ModelLayerLocation layer(ResourceLocation npcId) {
        return layer(npcId, NpcEntity.LOOK_DEFAULT);
    }

    /** {@code ns:name#main} for the default look (the contract's {@code model_layer}), {@code ns:name#<look>} otherwise. */
    public static ModelLayerLocation layer(ResourceLocation npcId, String look) {
        return new ModelLayerLocation(npcId, NpcEntity.LOOK_DEFAULT.equals(look) ? "main" : look);
    }

    public static ResourceLocation modelFile(ResourceLocation npcId) {
        return modelFile(npcId, NpcEntity.LOOK_DEFAULT);
    }

    public static ResourceLocation modelFile(ResourceLocation npcId, String look) {
        return npcId.withPath("npc/" + fileName(npcId, look) + "_model.json");
    }

    public static ResourceLocation animationFile(ResourceLocation npcId) {
        return animationFile(npcId, NpcEntity.LOOK_DEFAULT);
    }

    public static ResourceLocation animationFile(ResourceLocation npcId, String look) {
        return npcId.withPath("npc/" + fileName(npcId, look) + "_animations.json");
    }

    public static ResourceLocation texture(ResourceLocation npcId) {
        return texture(npcId, NpcEntity.LOOK_DEFAULT);
    }

    /** Every look's texture sits in the entity's own directory, {@code textures/entity/<name>/}. */
    public static ResourceLocation texture(ResourceLocation npcId, String look) {
        return npcId.withPath("textures/entity/" + npcId.getPath() + "/" + fileName(npcId, look) + ".png");
    }

    /**
     * A look's role map, {@code npc/<prefix>_roles.png}: which texels take the portrait's hair and eye
     * colours ({@link NpcSkins}). Written by {@code tools/npcgen}; a look without one keeps its baked colours.
     */
    public static ResourceLocation roleMap(ResourceLocation npcId, String look) {
        return npcId.withPath("npc/" + fileName(npcId, look) + "_roles.png");
    }

    /** The file name prefix of a look: {@code name} for the default, {@code name_<look>} otherwise. */
    static String fileName(ResourceLocation npcId, String look) {
        return NpcEntity.LOOK_DEFAULT.equals(look) ? npcId.getPath() : npcId.getPath() + "_" + look;
    }

    /** {@code looks} with {@link NpcEntity#LOOK_DEFAULT} first and no repeats. */
    static List<String> withDefault(List<String> looks) {
        Set<String> names = new LinkedHashSet<>();
        names.add(NpcEntity.LOOK_DEFAULT);
        names.addAll(looks);
        return List.copyOf(names);
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

    private record Loaded<T extends NpcEntity>(String look, NpcModel<T> model, float shadowRadius, float scale,
            ResourceLocation texture) {
    }

    private static <T extends NpcEntity> Map<String, Loaded<T>> loadAll(
            EntityRendererProvider.Context context, ResourceLocation npcId, List<String> looks) {
        Map<String, Loaded<T>> loaded = new LinkedHashMap<>();
        for (String look : withDefault(looks)) {
            loaded.put(look, load(context, npcId, look));
        }
        return Map.copyOf(loaded);
    }

    private static <T extends NpcEntity> Loaded<T> load(EntityRendererProvider.Context context, ResourceLocation npcId,
            String look) {
        ResourceManager resources = context.getResourceManager();
        BeastModelFile modelFile = readModel(resources, npcId, look);
        ResourceLocation animationLocation = animationFile(npcId, look);
        String animationPath = assetPath(animationLocation);
        BeastAnimationFile animations = read(resources, animationLocation,
                reader -> BeastAnimationFile.parse(animationPath, npcId, reader));
        check(animationPath, modelFile, animations);
        Map<String, AnimationDefinition> clips = animations.toAnimationDefinitions();
        double footSpeed = BeastGait.plantedFootSpeed(modelFile, animations.clip(NpcEntity.WALK_CLIP).orElseThrow());
        float walkRate = walkRate(footSpeed);
        LOGGER.info("NPC {} look {}: scale {}, walk rate {} (planted foot {} blocks per clip second)",
                npcId, look, modelFile.scale(), walkRate, Math.round(footSpeed * 1000.0) / 1000.0);
        NpcModel<T> model = new NpcModel<>(context.bakeLayer(layer(npcId, look)), modelFile,
                clips.get(NpcEntity.IDLE_CLIP), clips.get(NpcEntity.WALK_CLIP), walkRate);
        return new Loaded<>(look, model, modelFile.shadowRadius(), modelFile.scale(), texture(npcId, look));
    }

    /** A look's model file; every look's file declares the entity id. */
    static BeastModelFile readModel(ResourceManager resources, ResourceLocation npcId, String look) {
        ResourceLocation location = modelFile(npcId, look);
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
