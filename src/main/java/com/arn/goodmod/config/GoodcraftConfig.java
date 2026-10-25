package com.arn.goodmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;

/** Retains the three legacy settings without rewriting existing configuration files. */
public record GoodcraftConfig(boolean shouldGenBuildings, boolean shouldLoadOtherSkins, boolean allowFlying) {
    public static final GoodcraftConfig DEFAULT = new GoodcraftConfig(true, false, true);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static GoodcraftConfig load(Path path, Logger logger) {
        if (Files.notExists(path)) {
            try {
                Path parent = path.toAbsolutePath().getParent();
                Files.createDirectories(parent);
                Files.writeString(path, GSON.toJson(DEFAULT) + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (IOException exception) {
                logger.warn("Could not create {}. Using defaults if it cannot be read.", path, exception);
            }
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new JsonParseException("Configuration must be a JSON object");
            }
            JsonObject object = parsed.getAsJsonObject();
            return new GoodcraftConfig(
                    readBoolean(object, "shouldGenBuildings", DEFAULT.shouldGenBuildings(), logger),
                    readBoolean(object, "shouldLoadOtherSkins", DEFAULT.shouldLoadOtherSkins(), logger),
                    readBoolean(object, "allowFlying", DEFAULT.allowFlying(), logger));
        } catch (IOException | JsonParseException exception) {
            logger.warn("Could not read {}. Using defaults; the existing file has not been changed.", path, exception);
            return DEFAULT;
        }
    }

    private static boolean readBoolean(JsonObject object, String key, boolean fallback, Logger logger) {
        JsonElement value = object.get(key);
        if (value == null) {
            return fallback;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            logger.warn("Ignoring non-boolean setting {}; using {}.", key, fallback);
            return fallback;
        }
        return value.getAsBoolean();
    }
}
