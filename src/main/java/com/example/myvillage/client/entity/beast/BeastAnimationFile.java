package com.example.myvillage.client.entity.beast;

import com.example.myvillage.entity.beast.BeastDataException;
import com.example.myvillage.entity.beast.BeastDefinition;
import com.example.myvillage.entity.beast.BeastEntity;
import com.example.myvillage.entity.beast.BeastJson;
import com.example.myvillage.entity.beast.BeastMoveDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/**
 * A beast's keyframe clips (schema 1, {@code assets/<ns>/beast/<name>_animations.json}, written by
 * {@code tools/beastgen}). A keyframe is exactly {@code new Keyframe(time, V, interpolation)} with V
 * from {@code KeyframeAnimations.degreeVec} (rotation), {@code posVec} (position; vanilla negates y,
 * so +y in the file is up) or {@code scaleVec} (scale), all offsets from the bone's rest pose.
 *
 * <p>Parsing is pure and strict. {@link #check} adds the cross-file rules: every channel names a
 * model bone, and the clips the entity drives exist with the right looping and, for moves, a
 * length of {@code total_ticks / 20}.
 */
public record BeastAnimationFile(ResourceLocation id, Map<String, Clip> clips) {
    private static final Pattern CLIP_NAME = Pattern.compile("[a-z0-9_]+");
    private static final Set<String> ROOT_FIELDS = Set.of("schema", "id", "clips");
    private static final Set<String> CLIP_FIELDS = Set.of("length", "loop", "channels");
    private static final Set<String> CHANNEL_FIELDS = Set.of("bone", "target", "keyframes");
    private static final Set<String> KEYFRAME_FIELDS = Set.of("time", "value", "interp");
    /** Move clip length must match the server move to within this many seconds. */
    static final double LENGTH_TOLERANCE = 1.0E-4;

    public BeastAnimationFile {
        Objects.requireNonNull(id, "id");
        clips = Map.copyOf(clips);
    }

    public enum Target {
        ROTATION, POSITION, SCALE
    }

    public enum Interpolation {
        LINEAR, CATMULLROM
    }

    public record Clip(String name, float length, boolean loop, List<Channel> channels) {
        public Clip {
            channels = List.copyOf(channels);
        }
    }

    public record Channel(String bone, Target target, List<Key> keyframes) {
        public Channel {
            keyframes = List.copyOf(keyframes);
        }
    }

    public record Key(float time, BeastModelFile.Vec value, Interpolation interpolation) {
    }

    public Optional<Clip> clip(String name) {
        return Optional.ofNullable(clips.get(name));
    }

    public static BeastAnimationFile parse(String file, ResourceLocation expectedId, Reader source) {
        return parse(file, expectedId, BeastJson.parseStrict(file, source));
    }

    public static BeastAnimationFile parse(String file, ResourceLocation expectedId, JsonObject json) {
        BeastJson.Fields root = new BeastJson.Fields(file, "", json, ROOT_FIELDS);
        root.schema();
        ResourceLocation id = root.id("id");
        if (!id.equals(expectedId)) {
            throw root.error("id", "is " + id + " but this file belongs to " + expectedId);
        }
        BeastJson.Fields clipsFields = root.object("clips", null);
        List<String> names = clipsFields.keys();
        if (names.isEmpty()) {
            throw root.error("clips", "needs at least one clip");
        }
        Map<String, Clip> clips = new LinkedHashMap<>();
        for (String name : names) {
            if (!CLIP_NAME.matcher(name).matches()) {
                throw clipsFields.error(name, "clip names must match [a-z0-9_]+");
            }
            clips.put(name, parseClip(clipsFields.object(name, CLIP_FIELDS), name));
        }
        return new BeastAnimationFile(id, clips);
    }

    private static Clip parseClip(BeastJson.Fields clip, String name) {
        double length = clip.positiveNumber("length");
        boolean loop = clip.bool("loop");
        JsonArray channelsJson = clip.array("channels");
        List<Channel> channels = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < channelsJson.size(); index++) {
            BeastJson.Fields channel = new BeastJson.Fields(
                    clip.file(), clip.field("channels[" + index + "]"), channelsJson.get(index), CHANNEL_FIELDS);
            String bone = channel.string("bone");
            String targetName = channel.string("target");
            Target target = switch (targetName) {
                case "rotation" -> Target.ROTATION;
                case "position" -> Target.POSITION;
                case "scale" -> Target.SCALE;
                default -> throw channel.error("target", "must be rotation, position or scale, not " + targetName);
            };
            if (!seen.add(bone + "/" + target)) {
                throw channel.error("bone", "has a second " + targetName + " channel for bone " + bone);
            }
            JsonArray keysJson = channel.array("keyframes");
            if (keysJson.isEmpty()) {
                throw channel.error("keyframes", "needs at least one keyframe");
            }
            List<Key> keys = new ArrayList<>();
            for (int keyIndex = 0; keyIndex < keysJson.size(); keyIndex++) {
                BeastJson.Fields key = new BeastJson.Fields(
                        clip.file(), channel.field("keyframes[" + keyIndex + "]"), keysJson.get(keyIndex), KEYFRAME_FIELDS);
                double time = key.nonNegativeNumber("time");
                if (time > length + LENGTH_TOLERANCE) {
                    throw key.error("time", "is past the clip length " + length + ": " + time);
                }
                if (!keys.isEmpty() && time < keys.getLast().time()) {
                    throw key.error("time", "keyframes must be in time order; " + time + " comes after "
                            + keys.getLast().time());
                }
                String interpName = key.string("interp");
                Interpolation interpolation = switch (interpName) {
                    case "linear" -> Interpolation.LINEAR;
                    case "catmullrom" -> Interpolation.CATMULLROM;
                    default -> throw key.error("interp", "must be linear or catmullrom, not " + interpName);
                };
                keys.add(new Key((float) time, BeastModelFile.Vec.of(key.numbers("value", 3)), interpolation));
            }
            channels.add(new Channel(bone, target, keys));
        }
        return new Clip(name, (float) length, loop, channels);
    }

    /**
     * Cross-file rules for a beast: channels name bones of {@code model}; {@code idle}, {@code walk}
     * and {@code run} exist and loop; every move clip and the stagger clip exist and do not loop;
     * a move clip lasts {@code total_ticks / 20} seconds.
     */
    public void check(String file, BeastModelFile model, BeastDefinition definition) {
        for (Clip clip : clips.values()) {
            for (int index = 0; index < clip.channels().size(); index++) {
                String bone = clip.channels().get(index).bone();
                if (!model.hasBone(bone)) {
                    throw new BeastDataException(file, "clips." + clip.name() + ".channels[" + index + "].bone",
                            "names " + bone + ", which is not a bone of the model");
                }
            }
        }
        for (String looping : List.of(BeastEntity.IDLE_CLIP, BeastEntity.WALK_CLIP, BeastEntity.RUN_CLIP)) {
            requireClip(file, looping, true);
        }
        requireClip(file, definition.staggerAnimation(), false);
        for (BeastMoveDefinition move : definition.moves()) {
            Clip clip = requireClip(file, move.animation(), false);
            double expected = move.totalTicks() / 20.0;
            if (Math.abs(clip.length() - expected) > LENGTH_TOLERANCE) {
                throw new BeastDataException(file, "clips." + clip.name() + ".length",
                        "must be total_ticks / 20 = " + expected + " s for move " + move.id() + ", got " + clip.length());
            }
        }
    }

    private Clip requireClip(String file, String name, boolean loop) {
        Clip clip = clips.get(name);
        if (clip == null) {
            throw new BeastDataException(file, "clips." + name, "is required");
        }
        if (clip.loop() != loop) {
            throw new BeastDataException(file, "clips." + name + ".loop", "must be " + loop);
        }
        return clip;
    }

    /** Every clip as a vanilla animation definition, by clip name. */
    public Map<String, AnimationDefinition> toAnimationDefinitions() {
        Map<String, AnimationDefinition> definitions = new LinkedHashMap<>();
        for (Clip clip : clips.values()) {
            definitions.put(clip.name(), toAnimationDefinition(clip));
        }
        return Map.copyOf(definitions);
    }

    static AnimationDefinition toAnimationDefinition(Clip clip) {
        AnimationDefinition.Builder builder = AnimationDefinition.Builder.withLength(clip.length());
        if (clip.loop()) {
            builder.looping();
        }
        for (Channel channel : clip.channels()) {
            Keyframe[] keyframes = new Keyframe[channel.keyframes().size()];
            for (int index = 0; index < keyframes.length; index++) {
                Key key = channel.keyframes().get(index);
                keyframes[index] = new Keyframe(key.time(), vector(channel.target(), key.value()), switch (key.interpolation()) {
                    case LINEAR -> AnimationChannel.Interpolations.LINEAR;
                    case CATMULLROM -> AnimationChannel.Interpolations.CATMULLROM;
                });
            }
            AnimationChannel.Target target = switch (channel.target()) {
                case ROTATION -> AnimationChannel.Targets.ROTATION;
                case POSITION -> AnimationChannel.Targets.POSITION;
                case SCALE -> AnimationChannel.Targets.SCALE;
            };
            builder.addAnimation(channel.bone(), new AnimationChannel(target, keyframes));
        }
        return builder.build();
    }

    static Vector3f vector(Target target, BeastModelFile.Vec value) {
        return switch (target) {
            case ROTATION -> KeyframeAnimations.degreeVec(value.x(), value.y(), value.z());
            case POSITION -> KeyframeAnimations.posVec(value.x(), value.y(), value.z());
            case SCALE -> KeyframeAnimations.scaleVec(value.x(), value.y(), value.z());
        };
    }
}
