package com.arn.goodmod.entity;

import com.arn.goodmod.registry.GoodcraftEntities;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Registry-backed checks against bi/ei/ff/f_/fz identities and physical contracts. */
class GoodcraftEntitiesTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); GoodcraftEntities.initialize(); }
    @Test void allOriginalRegistryIdsArePresentWithoutSubstitutingVanillaTypes() {
        Set<String> actual = BuiltInRegistries.ENTITY_TYPE.keySet().stream().filter(id -> id.getNamespace().equals("sexmod")).map(id -> id.getPath()).collect(Collectors.toSet());
        assertEquals(Set.of("jenny","ellie","slime","bia","bee","luna","allie","kobold","goblin","galath","manglelie",
                "kobold_egg","custom_model","friendly_slime","luna_hook","energy_ball","pyrocinical",
                "player_jenny","player_ellie","player_slime","player_bia","player_bee","player_allie","player_kobold","player_goblin","player_luna","player_galath"), actual);
    }
    @Test void playerProxiesKeepTinyCollisionAndNonDeltaTracking() {
        assertEquals(10, GoodcraftEntities.playerTypes().size());
        GoodcraftEntities.playerTypes().forEach((kind, type) -> {
            assertEquals(0.01F, type.getWidth(), 0.00001F); assertEquals(0.01F, type.getHeight(), 0.00001F);
            assertFalse(type.trackDeltas()); assertEquals(1, type.updateInterval()); assertEquals(7, type.clientTrackingRange());
            assertEquals(20, DefaultAttributes.getSupplier(type).getValue(Attributes.MAX_HEALTH));
        });
    }
    @Test void speciesAttributesAndAmbientSpawnCategoriesSurviveMigration() {
        assertEquals(110, DefaultAttributes.getSupplier(GoodcraftEntities.GALATH).getValue(Attributes.MAX_HEALTH));
        assertEquals(69, DefaultAttributes.getSupplier(GoodcraftEntities.KOBOLD).getValue(Attributes.MAX_HEALTH));
        assertEquals(12, DefaultAttributes.getSupplier(GoodcraftEntities.BEE).getValue(Attributes.MAX_HEALTH));
        assertEquals(0.4, DefaultAttributes.getSupplier(GoodcraftEntities.BEE).getValue(Attributes.FLYING_SPEED));
        assertEquals(MobCategory.AMBIENT, GoodcraftEntities.PYROCINICAL.getCategory());
        assertEquals(MobCategory.AMBIENT, GoodcraftEntities.MANGLELIE.getCategory());
        assertFalse(GoodcraftEntities.ENERGY_BALL.canSerialize()); assertFalse(GoodcraftEntities.LUNA_HOOK.canSerialize());
    }
}
