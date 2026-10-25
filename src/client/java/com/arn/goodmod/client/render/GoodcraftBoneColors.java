package com.arn.goodmod.client.render;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.CustomBoneTextureGeoLayer;
import java.util.function.BiConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.Entity;
import com.geckolib.renderer.base.GeoRenderer;

/** Uses the supplied model's cubes and texture with original per-bone palette colors. */
final class GoodcraftBoneColors<T extends Entity & GeoAnimatable> extends CustomBoneTextureGeoLayer<T, Void, GoodcraftNpcRenderState> {
    GoodcraftBoneColors(GeoRenderer<T, Void, GoodcraftNpcRenderState> renderer) { super(renderer, "", null); }

    @Override public void addRenderData(T entity, Void related, GoodcraftNpcRenderState state, float partialTick) {
        if (!(entity instanceof com.arn.goodmod.entity.GoodcraftNpc npc) || npc.getModelVariant() == 0) return;
        var colors = new java.util.HashMap<String, Integer>();
        var offsets = new java.util.HashMap<String, Float>();
        var groups = com.arn.goodmod.animation.AnimationCatalog.MODELS.getAsJsonObject(npc.getNpcKind().id()).getAsJsonObject("armor_groups");
        var owner = npc.isPlayerForm() ? npc.getAssociatedPlayerUUID().map(npc.level()::getPlayerByUUID).orElse(null) : null;
        var slots = new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
        String[] keys = {"c", "f", "h", "b"};
        for (int i=0; i<slots.length; i++) {
            var item = owner != null ? owner.getItemBySlot(slots[i]) : npc.getItemBySlot(slots[i]);
            var equipment = item.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
            if (equipment == null || !groups.has(keys[i])) continue;
            String material = equipment.assetId().map(key -> key.identifier().getPath()).orElse("");
            int column = switch (material) { case "gold" -> 1; case "iron", "chainmail" -> 2; case "leather" -> 4; default -> 0; };
            int color = material.equals("leather") ? 0xff000000 | net.minecraft.world.item.component.DyedItemColor.getOrDefault(item, net.minecraft.world.item.component.DyedItemColor.LEATHER_COLOR) : -1;
            for (var name : groups.getAsJsonArray(keys[i])) { colors.put(name.getAsString(), color); offsets.put(name.getAsString(), 72f * column / 4096f); }
        }
        state.armorColors = java.util.Map.copyOf(colors); state.armorUvOffsets = java.util.Map.copyOf(offsets);
    }

    @Override public void preRender(RenderPassInfo<GoodcraftNpcRenderState> pass, SubmitNodeCollector collector) {
        if (!pass.renderState().kind.equals("egg")) {
            var colors = new java.util.HashMap<>(GoodcraftAppearance.colors(pass.renderState(), pass.model()));
            // d_ passes an armor bone's material offset and tint down to every child bone.
            var armorColors = new java.util.HashMap<>(pass.renderState().armorColors);
            var offsets = new java.util.HashMap<>(pass.renderState().armorUvOffsets);
            for (String name : pass.renderState().armorColors.keySet()) pass.model().getBone(name).ifPresent(bone ->
                    inherit(bone, armorColors.get(name), offsets.get(name), armorColors, offsets));
            pass.renderState().armorUvOffsets = java.util.Map.copyOf(offsets);
            colors.putAll(armorColors); pass.renderState().boneColors = java.util.Map.copyOf(colors);
        }
        pass.addBoneUpdater((renderPass, bones) -> pass.renderState().boneColors.keySet().forEach(name ->
                bones.ifPresent(name, bone -> {
                    // Visibility is captured before hiding only this bone's cubes for the tint pass.
                    if (!bone.isHidden()) {
                        pass.renderState().tintedBones.add(name);
                        boolean childrenHidden = bone.areChildrenHidden();
                        bone.skipRender(true).skipChildrenRender(childrenHidden);
                    }
                })));
    }

    private static void inherit(GeoBone parent, int color, float offset, java.util.Map<String, Integer> colors, java.util.Map<String, Float> offsets) {
        for (GeoBone child : parent.children()) {
            colors.putIfAbsent(child.name(), color); offsets.putIfAbsent(child.name(), offset);
            inherit(child, color, offset, colors, offsets);
        }
    }

    @Override public void addPerBoneRender(RenderPassInfo<GoodcraftNpcRenderState> pass,
            BiConsumer<GeoBone, PerBoneRender<GoodcraftNpcRenderState>> consumer) {
        for (String name : pass.renderState().boneColors.keySet()) {
            pass.model().getBone(name).filter(CuboidGeoBone.class::isInstance).ifPresent(bone -> consumer.accept(bone, this::renderBone));
        }
    }

    @Override protected void renderBone(RenderPassInfo<GoodcraftNpcRenderState> pass, GeoBone bone, SubmitNodeCollector collector) {
        if (!pass.renderState().tintedBones.contains(bone.name())) return;
        int color = pass.renderState().boneColors.get(bone.name()) & 0xffffff | pass.renderColor() & 0xff000000;
        var type = renderer.getRenderType(pass.renderState(), renderer.getTextureLocation(pass.renderState()));
        if (type == null) return;
        collector.submitCustomGeometry(pass.poseStack(), type, (pose, vertices) -> {
            var stack = pass.poseStack();
            stack.pushPose();
            stack.last().set(pose);
            bone.translateAwayFromPivotPoint(stack);
            var consumer = new ArmorUvConsumer(vertices, pass.renderState().armorUvOffsets.getOrDefault(bone.name(), 0f));
            for (var cube : ((CuboidGeoBone)bone).cubes) {
                stack.pushPose();
                renderCube(cube, stack, consumer, pass.packedLight(), pass.packedOverlay(), color, 1, 1);
                stack.popPose();
            }
            stack.popPose();
        });
    }
}
