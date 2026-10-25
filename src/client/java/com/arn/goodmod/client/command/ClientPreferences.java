package com.arn.goodmod.client.command;

import com.arn.goodmod.GoodcraftMod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

/** Retains the original a_/br client preference files. */
public final class ClientPreferences {
    private static final Path ROOT = FabricLoader.getInstance().getGameDir().resolve("sexmod");
    private static boolean alternateBody = true;
    private ClientPreferences() {}

    public static void initialize() {
        Path preference = ROOT.resolve("futa");
        if (!Files.isRegularFile(preference)) return;
        try {
            String value = Files.readString(preference, StandardCharsets.UTF_8).trim();
            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) alternateBody = Boolean.parseBoolean(value);
        } catch (IOException exception) {
            GoodcraftMod.LOGGER.warn("Cannot read client appearance preference", exception);
        }
    }

    public static boolean showAlternateBody() { return alternateBody; }

    public static void setAlternateBody(boolean value) throws IOException {
        write(ROOT.resolve("futa"), Boolean.toString(value));
        alternateBody = value;
    }

    public static Set<String> trustedServers() throws IOException {
        Path file = ROOT.resolve("custom_models/whitelisted_servers.txt");
        if (!Files.isRegularFile(file)) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) result.add(line.trim());
        }
        return result;
    }

    public static void trustServer(String address) throws IOException {
        if (address.isBlank() || address.indexOf('\n') >= 0 || address.indexOf('\r') >= 0) throw new IllegalArgumentException("Invalid server address");
        Set<String> servers = new LinkedHashSet<>(trustedServers());
        servers.add(address);
        write(ROOT.resolve("custom_models/whitelisted_servers.txt"), String.join("\n", servers) + "\n");
    }

    private static void write(Path path, String value) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, value, StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
