package com.arn.goodmod.client.render;

import com.arn.goodmod.animation.AnimationCatalog;
import com.arn.goodmod.entity.GoodcraftNpc;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Client views are derived from server-tracked form entities, never client-selected identities. */
public final class GoodcraftPlayerForms {
    private GoodcraftPlayerForms() {}
    public static Optional<GoodcraftNpc> find(UUID player) {
        var level = Minecraft.getInstance().level;
        if (level == null) return Optional.empty();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof GoodcraftNpc npc && npc.isAlive() && npc.isPlayerForm()
                    && npc.getAssociatedPlayerUUID().filter(player::equals).isPresent()) return Optional.of(npc);
        }
        return Optional.empty();
    }
    public static boolean hideWorldEntity(Entity entity) {
        var client = Minecraft.getInstance();
        if (entity instanceof com.arn.goodmod.entity.ManglelieNpc child && child.getMotherUUID().isPresent() && client.level != null) {
            for (Entity other : client.level.entitiesForRendering()) {
                if (other instanceof GoodcraftNpc parent && parent.getUUID().equals(child.getMotherUUID().get()) && parent.isUsingCombinedModel()) return true;
            }
        }
        if (entity instanceof Player player) return find(player.getUUID()).isPresent() || com.arn.goodmod.client.scene.SceneClient.hidesPlayer(player.getUUID());
        return entity instanceof GoodcraftNpc npc && npc.isPlayerForm() && client.player != null
                && npc.getAssociatedPlayerUUID().filter(client.player.getUUID()::equals).isPresent()
                && client.options.getCameraType().isFirstPerson() && client.getCameraEntity() == client.player;
    }
    public static float eyeHeight(GoodcraftNpc npc) {
        var heights = AnimationCatalog.MODELS.getAsJsonObject(npc.getNpcKind().id()).getAsJsonArray("camera_heights");
        int variant = Math.clamp(npc.getModelVariant(), 0, heights.size() - 1);
        float height = heights.get(variant).getAsFloat();
        var selectors = RenderModelCode.read(npc).selectors();
        if (npc.getNpcKind().id().equals("kobold") && selectors.size() == 11) height *= .75f + selectors.get(0) / 400f;
        return height;
    }
    static void copyRenderMotion(GoodcraftNpc npc) {
        if (!npc.isPlayerForm()) return;
        npc.getAssociatedPlayerUUID().map(npc.level()::getPlayerByUUID).ifPresent(player -> {
            npc.setPos(player.position()); npc.xo = player.xo; npc.yo = player.yo; npc.zo = player.zo;
            npc.setYRot(player.getYRot()); npc.yRotO = player.yRotO;
            npc.setXRot(player.getXRot()); npc.xRotO = player.xRotO;
            npc.setYHeadRot(player.getYHeadRot()); npc.yHeadRotO = player.yHeadRotO;
            npc.yBodyRot = player.yBodyRot; npc.yBodyRotO = player.yBodyRotO;
            npc.setOnGround(player.onGround()); npc.setSprinting(player.isSprinting()); npc.setShiftKeyDown(player.isShiftKeyDown());
            npc.setDeltaMovement(player.getDeltaMovement()); npc.setPose(player.getPose());
        });
    }
}
