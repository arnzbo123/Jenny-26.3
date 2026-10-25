package com.arn.goodmod.mixin;

import com.arn.goodmod.entity.EncounterSpawnHistory;
import com.arn.goodmod.entity.ManglelieNpc;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** f_.a spawn replacement happens only after vanilla natural spawn checks have succeeded. */
@Mixin(NaturalSpawner.class)
abstract class NaturalSpawnerMixin {
    @Redirect(method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"))
    private static void goodcraft$replaceNaturalSpawn(ServerLevel level, Entity entity) {
        if (EncounterSpawnHistory.replaceNaturalSpawn(level, entity)) return;
        level.addFreshEntityWithPassengers(entity);
        if (entity instanceof ManglelieNpc && !entity.isRemoved()) EncounterSpawnHistory.get(level).recordManglelie(entity.blockPosition());
    }
}
