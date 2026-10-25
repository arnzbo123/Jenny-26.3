package com.arn.goodmod.animation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;

/** Allocation bounds applied before GeckoLib baking or native texture decoding. */
public final class AddonGeometryLimits {
    private AddonGeometryLimits() {}

    public static JsonObject parse(String json) throws IOException {
        int depth = 0, values = 0;
        try (var reader = new JsonReader(new StringReader(json))) {
            while (reader.peek() != JsonToken.END_DOCUMENT) {
                if (++values > 500000) throw new IOException("Custom geometry has too many JSON values");
                switch (reader.peek()) {
                    case BEGIN_OBJECT -> { reader.beginObject(); depth++; }
                    case BEGIN_ARRAY -> { reader.beginArray(); depth++; }
                    case END_OBJECT -> { reader.endObject(); depth--; }
                    case END_ARRAY -> { reader.endArray(); depth--; }
                    case NAME -> reader.nextName();
                    case STRING -> reader.nextString();
                    case NUMBER -> { if (!Double.isFinite(reader.nextDouble())) throw new IOException("Non-finite geometry coordinate"); }
                    case BOOLEAN -> reader.nextBoolean();
                    case NULL -> reader.nextNull();
                    default -> throw new IOException("Unexpected geometry token");
                }
                if (depth > 64) throw new IOException("Custom geometry JSON nesting exceeds 64");
            }
        }
        var root = JsonParser.parseString(json);
        if (!root.isJsonObject()) throw new IOException("Geometry must be an object");
        JsonObject object = root.getAsJsonObject();
        var geometries = new java.util.ArrayList<JsonObject>();
        if (object.has("minecraft:geometry")) for (var geometry : object.getAsJsonArray("minecraft:geometry")) geometries.add(geometry.getAsJsonObject());
        else for (var entry : object.entrySet()) if (entry.getKey().startsWith("geometry.") && entry.getValue().isJsonObject()) geometries.add(entry.getValue().getAsJsonObject());
        if (geometries.isEmpty() || geometries.size() > 16) throw new IOException("Custom model must contain 1 to 16 geometries");
        int bones = 0, cubes = 0;
        for (var geometry : geometries) {
            var parents = new HashMap<String, String>();
            if (!geometry.has("bones")) throw new IOException("Geometry has no bones");
            for (var element : geometry.getAsJsonArray("bones")) {
                var bone = element.getAsJsonObject();
                String name = bone.get("name").getAsString();
                if (parents.containsKey(name)) throw new IOException("Duplicate bone name: " + name);
                parents.put(name, bone.has("parent") ? bone.get("parent").getAsString() : null);
                bones++; if (bone.has("cubes")) cubes += bone.getAsJsonArray("cubes").size();
                if (bones > 2048 || cubes > 8192) throw new IOException("Custom geometry exceeds 2048 bones or 8192 cubes");
            }
            for (String name : parents.keySet()) {
                var seen = new HashSet<String>(); String parent = name;
                while (parent != null) {
                    if (!seen.add(parent)) throw new IOException("Bone parent cycle: " + name);
                    if (!parents.containsKey(parent)) throw new IOException("Missing parent bone: " + parent);
                    if (seen.size() > 128) throw new IOException("Bone hierarchy exceeds 128 levels");
                    parent = parents.get(parent);
                }
            }
        }
        return object;
    }

    public static int pngPixels(byte[] header) throws IOException {
        byte[] signature = {(byte)137,80,78,71,13,10,26,10};
        if (header.length < 24 || !java.util.Arrays.equals(java.util.Arrays.copyOf(header,8), signature)
                || java.nio.ByteBuffer.wrap(header).getInt(8) != 13 || header[12]!='I' || header[13]!='H' || header[14]!='D' || header[15]!='R') throw new IOException("Invalid PNG header");
        var data = java.nio.ByteBuffer.wrap(header); int width = data.getInt(16), height = data.getInt(20);
        if (width < 1 || height < 1 || width > 4096 || height > 4096) throw new IOException("Custom texture dimensions must be 1 to 4096");
        return width * height;
    }
}
