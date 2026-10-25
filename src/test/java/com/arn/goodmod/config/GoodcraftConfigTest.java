package com.arn.goodmod.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import static org.junit.jupiter.api.Assertions.*;

final class GoodcraftConfigTest {
    @TempDir Path directory;

    @Test
    void createsDefaultsOnFirstRun() throws Exception {
        Path path = directory.resolve("config/sexmod.json");
        assertEquals(GoodcraftConfig.DEFAULT, GoodcraftConfig.load(path, NOPLogger.NOP_LOGGER));
        assertTrue(Files.isRegularFile(path));
        assertEquals(GoodcraftConfig.DEFAULT, GoodcraftConfig.load(path, NOPLogger.NOP_LOGGER));
    }

    @Test
    void readsLegacySettingsWithoutRewritingTheFile() throws Exception {
        Path path = directory.resolve("sexmod.json");
        String original = "{\"shouldGenBuildings\": false, \"shouldLoadOtherSkins\": true,\n"
                + "\"allowFlying\": false, \"unknownOption\": 42}";
        Files.writeString(path, original);
        assertEquals(new GoodcraftConfig(false, true, false), GoodcraftConfig.load(path, NOPLogger.NOP_LOGGER));
        assertEquals(original, Files.readString(path));
    }

    @Test
    void retainsCorruptConfigurationForUserRecovery() throws Exception {
        Path path = directory.resolve("sexmod.json");
        String original = "{\"allowFlying\":";
        Files.writeString(path, original);
        assertEquals(GoodcraftConfig.DEFAULT, GoodcraftConfig.load(path, NOPLogger.NOP_LOGGER));
        assertEquals(original, Files.readString(path));
    }

    @Test
    void missingAndWrongTypeSettingsUseDefaultsIndividually() throws Exception {
        Path path = directory.resolve("sexmod.json");
        Files.writeString(path, "{\"allowFlying\":false,\"shouldLoadOtherSkins\":\"true\",\"shouldGenBuildings\":null}");
        assertEquals(new GoodcraftConfig(true, false, false), GoodcraftConfig.load(path, NOPLogger.NOP_LOGGER));
    }

    @Test
    void nonObjectRootUsesDefaultsWithoutOverwriting() throws Exception {
        Path path = directory.resolve("sexmod.json");
        Files.writeString(path, "[]");
        assertEquals(GoodcraftConfig.DEFAULT, GoodcraftConfig.load(path, NOPLogger.NOP_LOGGER));
        assertEquals("[]", Files.readString(path));
    }
}
