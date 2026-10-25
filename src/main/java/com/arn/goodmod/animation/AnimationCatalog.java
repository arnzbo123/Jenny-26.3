package com.arn.goodmod.animation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Generated from the recovered original selectors and supplied animation JSON. */
public final class AnimationCatalog {
    public static final JsonObject ANIMATIONS = read("animation_catalog.json");
    public static final JsonObject MODELS = read("animation_models.json");
    public static final JsonObject APPEARANCE = read("appearance_catalog.json");
    /** Per-girl chat lines keyed by the sound marker that spoke them (tools/extract_scene_dialogue.py). */
    public static final JsonObject DIALOGUE = read("scene_dialogue.json");
    /** Every sound each girl's listener played per marker, with volume and odds (tools/extract_scene_sounds.py). */
    public static final JsonObject SOUNDS = read("scene_sounds.json");

    private AnimationCatalog() {}

    private static JsonObject read(String name) {
        try (var stream = Objects.requireNonNull(AnimationCatalog.class.getResourceAsStream("/sexmod/" + name), name);
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot load animation catalog " + name, error);
        }
    }

    public static boolean contains(String clip) {
        return clip != null && ANIMATIONS.getAsJsonObject("clips").has(clip);
    }

    public static JsonObject profile(String kind, boolean player) {
        var profiles = ANIMATIONS.getAsJsonObject("profiles");
        String id = player ? "player_" + kind : kind;
        return profiles.has(id) ? profiles.getAsJsonObject(id) : profiles.getAsJsonObject(kind);
    }

    public static JsonObject state(String kind, boolean player, String state) {
        var states = profile(kind, player).getAsJsonObject("states");
        return states.has(state) ? states.getAsJsonObject(state) : null;
    }

    public static boolean autoBlink(String state) {
        var states = ANIMATIONS.getAsJsonObject("states");
        return states.has(state) && states.getAsJsonObject(state).get("auto_blink").getAsBoolean();
    }

    public static boolean hasPlayer(String state) {
        var states = ANIMATIONS.getAsJsonObject("states");
        return states.has(state) && states.getAsJsonObject(state).get("has_player").getAsBoolean();
    }
}
