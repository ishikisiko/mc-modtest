package com.example.myvillage.story.npc;

import com.example.myvillage.sim.life.LifePlace;
import com.example.myvillage.sim.data.SimJson;
import java.io.Reader;
import com.example.myvillage.sim.life.StoryActorDefinition;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict startup input. Positions are references to QianzhenScene, never copied coordinates. */
public record QianzhenActorDefinitions(String storyId, String sceneId, String siteId, String siteName,
        List<StoryActorDefinition> actors) {
    public QianzhenActorDefinitions {
        LifePlace.requireId(storyId); LifePlace.requireId(sceneId); LifePlace.requireId(siteId);
        if (siteName == null || siteName.isBlank() || siteName.length() > 64) throw new IllegalArgumentException("invalid_actor_site_name");
        actors = List.copyOf(actors);
        var roles = new HashSet<String>();
        for (var actor : actors) if (!roles.add(actor.key())) throw new IllegalArgumentException("duplicate_actor_role");
        if (!roles.equals(Set.of("guide", "pharmacist", "zhou_he", "warden")))
            throw new IllegalArgumentException("qianzhen_requires_four_roles");
        for (var actor : actors) {
            if (!actor.initialPlaceId().equals(actor.key())
                    || !actor.returnPlaceId().equals(actor.key().equals("zhou_he") ? "zhou_he_home" : ""))
                throw new IllegalArgumentException("invalid_actor_scene_reference");
        }
    }

    public static QianzhenActorDefinitions read(Reader reader) {
        return read(SimJson.parseStrict("story/qianzhen_actors.json", reader));
    }

    public static QianzhenActorDefinitions read(JsonObject root) {
        exactKeys(root, Set.of("schema", "story_id", "scene_id", "site_id", "site_name", "actors"));
        if (integer(root, "schema") != 1) throw new IllegalArgumentException("unsupported_qianzhen_actors_schema");
        var actors = new ArrayList<StoryActorDefinition>();
        JsonElement list = root.get("actors");
        if (list == null || !list.isJsonArray()) throw new IllegalArgumentException("missing_actor_list");
        for (var element : list.getAsJsonArray()) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("invalid_actor_object");
            var o = element.getAsJsonObject();
            exactKeys(o, Set.of("key", "surname", "given", "gender", "age_years", "realm", "stage", "root", "traits",
                    "role", "home_id", "initial_place_id", "return_place_id", "policy"));
            actors.add(new StoryActorDefinition(text(o, "key"), text(o, "surname"), text(o, "given"),
                    text(o, "gender"), integer(o, "age_years"), text(o, "realm"), integer(o, "stage"),
                    integers(o, "root"), integers(o, "traits"), text(o, "role"), text(o, "home_id"),
                    text(o, "initial_place_id"), text(o, "return_place_id"), text(o, "policy")));
        }
        return new QianzhenActorDefinitions(text(root, "story_id"), text(root, "scene_id"), text(root, "site_id"),
                text(root, "site_name"), actors);
    }

    private static void exactKeys(JsonObject object, Set<String> keys) {
        if (!object.keySet().equals(keys)) throw new IllegalArgumentException("unknown_or_missing_actor_fields:" + object.keySet());
    }
    private static String text(JsonObject o, String key) {
        var e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("expected_actor_string:" + key);
        return e.getAsString();
    }
    private static int integer(JsonObject o, String key) { return integer(o.get(key), key); }
    private static int integer(JsonElement e, String key) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("expected_actor_integer:" + key);
        try { return e.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException error) { throw new IllegalArgumentException("invalid_actor_integer:" + key, error); }
    }
    private static List<Integer> integers(JsonObject o, String key) {
        var e = o.get(key);
        if (e == null || !e.isJsonArray()) throw new IllegalArgumentException("expected_actor_array:" + key);
        List<Integer> values = new ArrayList<>();
        for (var v : e.getAsJsonArray()) values.add(integer(v, key));
        return values;
    }
}
