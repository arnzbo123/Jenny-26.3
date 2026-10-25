package com.arn.goodmod.client.render;

import com.arn.goodmod.GoodcraftMod;
import com.arn.goodmod.client.command.ClientPreferences;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.loading.definition.geometry.Geometry;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Reads the original name/name.geo.json, name.png, name.cfg local addon format. */
public final class GoodcraftCustomModels {
    public record Model(Identifier id, Identifier texture, BakedGeoModel geometry, Properties configuration) {}
    private static final Map<String, Model> MODELS = new LinkedHashMap<>();
    private GoodcraftCustomModels() {}
    public static Model get(String name) { return MODELS.get(name); }
    static Model byId(Identifier id) { return MODELS.values().stream().filter(m -> m.id().equals(id)).findFirst().orElse(null); }

    /** Primary cache destination. Legacy folders are read-only fallbacks, never download targets. */
    public static Optional<Path> currentRoot() {
        Path base = FabricLoader.getInstance().getGameDir().resolve("sexmod/custom_models").toAbsolutePath().normalize();
        var server = Minecraft.getInstance().getCurrentServer();
        if (server == null) return Optional.of(base.resolve("singleplayer"));
        String endpoint = server.ip.trim().toLowerCase(Locale.ROOT);
        try {
            boolean trusted = ClientPreferences.trustedServers().stream().anyMatch(value -> value.trim().toLowerCase(Locale.ROOT).equals(endpoint));
            if (!trusted) return Optional.empty();
        } catch (java.io.IOException error) {
            GoodcraftMod.LOGGER.warn("Cannot read custom model server preferences", error);
            return Optional.empty();
        }
        String digest;
        try { digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new AssertionError(error); }
        String label = endpoint.replace(':', '_').replaceAll("[^A-Za-z0-9_.-]", "_");
        if (label.length() > 48) label = label.substring(0, 48);
        Path destination = base.resolve(label + "-" + digest).normalize();
        return destination.startsWith(base) && !destination.equals(base) ? Optional.of(destination) : Optional.empty();
    }

    private static List<Path> roots() {
        Optional<Path> primary = currentRoot();
        if (primary.isEmpty()) return List.of();
        var server = Minecraft.getInstance().getCurrentServer();
        if (server == null) return List.of(primary.get());
        // Parse bracketed IPv6 correctly; the old split-at-colon implementation did not.
        String host = net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(server.ip).getHost().toLowerCase(Locale.ROOT);
        Path legacy = primary.get().getParent().resolve(host.replaceAll("[^A-Za-z0-9_.-]", "_")).normalize();
        Path previous = primary.get().getParent().resolve(server.ip.trim().toLowerCase(Locale.ROOT).replace(':', '_').replaceAll("[^A-Za-z0-9_.-]", "_")).normalize();
        if (!legacy.startsWith(primary.get().getParent()) || legacy.equals(primary.get().getParent())) return List.of(primary.get());
        if (!previous.startsWith(primary.get().getParent()) || previous.equals(primary.get().getParent())) return List.of(primary.get());
        return legacy.equals(previous) ? List.of(legacy, primary.get()) : List.of(legacy, previous, primary.get());
    }

    public static int reload() {
        Minecraft client = Minecraft.getInstance();
        for (Model model : MODELS.values()) client.getTextureManager().release(model.texture());
        MODELS.clear();
        try {
          for (Path root : roots()) {
            if (!Files.isDirectory(root)) continue;
            try (var directories = Files.list(root)) {
                for (Path directory : directories.filter(Files::isDirectory).sorted().limit(256).toList()) {
                    String name = directory.getFileName().toString();
                    if (!name.matches("[A-Za-z0-9_ .-]+") || name.equals("cross")) continue;
                    Path resolved = directory.toRealPath();
                    if (!resolved.startsWith(root.toRealPath())) continue;
                    try {
                        Path geometryPath = resolved.resolve(name + ".geo.json"), png = resolved.resolve(name + ".png"), config = resolved.resolve(name + ".cfg");
                        if (!geometryPath.toRealPath().startsWith(resolved) || !png.toRealPath().startsWith(resolved) || !config.toRealPath().startsWith(resolved)) continue;
                        if (Files.size(geometryPath) > 16L*1024*1024 || Files.size(png) > 32L*1024*1024 || Files.size(config) > 65536) throw new java.io.IOException("Custom model file exceeds size limit");
                        try (var stream = Files.newInputStream(png)) { com.arn.goodmod.animation.AddonGeometryLimits.pngPixels(stream.readNBytes(24)); }
                        Properties properties = new Properties();
                        try (var stream = Files.newInputStream(config)) { properties.load(stream); }
                        String wearType = properties.getProperty("wear_type", "").trim();
                        if (!java.util.Set.of("HEAD", "FOOT_L", "FOOT_R", "HAND_L", "HAND_R", "CUSTOM_BONE").contains(wearType)) throw new IllegalArgumentException("Unknown wear_type");
                        if (wearType.equals("CUSTOM_BONE") && properties.getProperty("custom_bone", "").isBlank()) throw new IllegalArgumentException("Missing custom_bone");
                        Identifier id = GoodcraftNpcModel.id("custom/" + java.util.HexFormat.of().formatHex(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                        var definition = com.arn.goodmod.animation.AddonGeometryLimits.parse(Files.readString(geometryPath));
                        BakedGeoModel geometry = Geometry.GSON.fromJson(definition, Geometry.class).bake(id);
                        Identifier texture = GoodcraftNpcModel.id(id.getPath() + ".png");
                        NativeImage pixels;
                        try (var stream = Files.newInputStream(png)) { pixels = NativeImage.read(stream); }
                        client.getTextureManager().register(texture, new DynamicTexture(() -> "Goodcraft addon " + name, pixels));
                        MODELS.put(name, new Model(id, texture, geometry, properties));
                    } catch (Exception error) { GoodcraftMod.LOGGER.warn("Cannot load original custom model {}: {}", name, error.toString()); }
                }
            }
          }
        } catch (Exception error) { GoodcraftMod.LOGGER.warn("Cannot read custom model directory", error); }
        return MODELS.size();
    }
}
