package com.arn.goodmod;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;

/** 26.3 binds item defaults after dynamic registries exist, beyond static Bootstrap. */
public final class MinecraftTestBootstrap {
    private static boolean initialized;
    private MinecraftTestBootstrap(){}
    public static synchronized void initialize(){
        if(initialized)return;
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        // Fabric JUnit uses Knot for game classes, while Gradle's thread context
        // loader is its parent. GeckoLib ServiceLoader providers must use Knot too.
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(MinecraftTestBootstrap.class.getClassLoader());
        try {
        // Register everything any test class may touch before the registries freeze below.
        com.arn.goodmod.registry.GoodcraftBlocks.initialize();
        com.arn.goodmod.registry.GoodcraftSounds.initialize();
        com.arn.goodmod.registry.GoodcraftItems.items();
        com.arn.goodmod.registry.GoodcraftEntities.npcTypes();
        com.arn.goodmod.registry.GoodcraftEffects.POTION.value();
        // Fabric API defers the built-in registry freeze until after mod initializers,
        // which never run under JUnit. Tag reloads refuse unfrozen registries, so
        // repeat vanilla's BuiltInRegistries.freeze() here.
        BuiltInRegistries.REGISTRY.freeze();
        for (net.minecraft.core.Registry<?> registry : BuiltInRegistries.REGISTRY) {
            if (registry instanceof net.minecraft.core.MappedRegistry<?> mapped) mapped.bindAllTagsToEmpty();
            registry.freeze();
        }
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
                .forEach(DataComponentInitializers.PendingComponents::apply);
        try (var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(
                net.minecraft.server.packs.PackType.SERVER_DATA,
                java.util.List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource().fullResources()))) {
            net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources,
                    net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY))
                    .forEach(net.minecraft.core.Registry.PendingTags::apply);
        }
        initialized=true;
        } finally { Thread.currentThread().setContextClassLoader(previous); }
    }
}
