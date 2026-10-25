package com.arn.goodmod.client.render;

import com.arn.goodmod.entity.GoodcraftNpc;
import com.arn.goodmod.entity.LunaNpc;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.layer.builtin.ItemInHandGeoLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Original d_/dp weapon selection and pivots, with actual current item models. */
final class GoodcraftHeldItems extends ItemInHandGeoLayer<GoodcraftNpc, Void, GoodcraftNpcRenderState> {
    GoodcraftHeldItems(EntityRendererProvider.Context context, GoodcraftNpcRenderer renderer) { super(context, renderer, "weapon", "offhand"); }
    private static final String PAYMENT_BONE = "itemRenderer";

    @Override protected List<RenderData> getRelevantBones(GoodcraftNpc npc, Void related, GoodcraftNpcRenderState state, float partialTick) {
        List<RenderData> result = new ArrayList<>();
        if (npc.isPlayerForm()) {
            npc.getAssociatedPlayerUUID().map(npc.level()::getPlayerByUUID).ifPresent(player -> {
                boolean rightMain = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
                ItemStack right = rightMain ? player.getMainHandItem() : player.getOffhandItem();
                ItemStack left = rightMain ? player.getOffhandItem() : player.getMainHandItem();
                if (!right.isEmpty()) add(result, "weapon", right, player, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
                if (!left.isEmpty()) add(result, "offhand", left, player, ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
            });
            return result;
        }
        // The equipment container is server-only; the server mirrors the wielded sword or bow into the synced main hand.
        ItemStack main = npc.getHeldWeaponMode() != 0 ? npc.getMainHandItem() : ItemStack.EMPTY;
        if (npc instanceof LunaNpc luna) {
            if (state.action.equals("FISHING_START") || state.action.equals("FISHING_IDLE")) {
                main = npc.getMainHandItem();
                if (main.isEmpty()) main = new ItemStack(Items.FISHING_ROD);
            }
            if (!luna.getCaughtItem().isEmpty()) add(result, "offhand", luna.getCaughtItem(), npc);
        }
        if (!main.isEmpty()) add(result, "weapon", main, npc);
        if (state.action.equals("PAYMENT") && !npc.getPaymentItem().isEmpty()) {
            state.paymentCount = Math.min(3, npc.getPaymentItem().getCount());
            add(result, PAYMENT_BONE, npc.getPaymentItem().copyWithCount(1), npc);
        }
        return result;
    }

    private void add(List<RenderData> result, String bone, ItemStack stack, GoodcraftNpc npc) {
        add(result, bone, stack, npc, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
    }

    private void add(List<RenderData> result, String bone, ItemStack stack, net.minecraft.world.entity.LivingEntity entity, ItemDisplayContext context) {
        var model = new ItemStackRenderState();
        itemModelResolver.updateForLiving(model, stack, context, entity);
        result.add(RenderData.item(bone, context, model));
    }

    @Override public void addRenderData(GoodcraftNpc npc, Void related, GoodcraftNpcRenderState state, float partialTick) {
        state.attackVariant = npc.getAnimationVariant();
        state.caughtItemScale = npc.getAnimationInputs().getOrDefault("legacy.aa", 1d).floatValue();
        super.addRenderData(npc, related, state, partialTick);
        if (npc.isPlayerForm()) npc.getAssociatedPlayerUUID().map(npc.level()::getPlayerByUUID).ifPresent(player -> {
            state.addGeckolibData(MAINHAND_SHIELD, player.getMainHandItem().is(Items.SHIELD));
            state.addGeckolibData(OFFHAND_SHIELD, player.getOffhandItem().is(Items.SHIELD));
        });
    }

    @Override protected void submitItemStackRender(PoseStack stack, GeoBone bone, ItemStackRenderState item, ItemDisplayContext context,
            GoodcraftNpcRenderState state, SubmitNodeCollector collector, int light) {
        if (state.playerForm) { super.submitItemStackRender(stack, bone, item, context, state, collector, light); return; }
        if (bone.name().equals(PAYMENT_BONE)) {
            // d_.b(): one item per coin paid, the second and third fanned 15 degrees either side.
            for (int copy = 0; copy < state.paymentCount; copy++) {
                stack.pushPose();
                if (copy == 1) { stack.rotate(new org.joml.Quaternionf().rotationZ((float)Math.toRadians(-15))); stack.translate(0, 0, -.025); }
                if (copy == 2) { stack.rotate(new org.joml.Quaternionf().rotationZ((float)Math.toRadians(15))); stack.translate(0, 0, .025); }
                item.submit(stack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
                stack.popPose();
            }
            return;
        }
        stack.pushPose();
        if (bone.name().equals("offhand") && state.kind.equals("luna")) {
            stack.rotate(new org.joml.Quaternionf().rotationX((float)Math.PI/2));
            stack.scale(state.caughtItemScale, state.caughtItemScale, state.caughtItemScale);
        }
        else {
            boolean firstAttack = state.action.equals("ATTACK") && state.attackVariant == 0;
            float angle = switch (state.kind) {
                case "jenny", "bia" -> firstAttack ? 50 : 140;
                case "ellie" -> firstAttack ? -175 : -85;
                case "luna" -> state.action.equals("BOW") ? 320 : firstAttack ? 150 : 230;
                default -> 0;
            };
            if (firstAttack) switch (state.kind) {
                case "jenny", "bia" -> stack.translate(0,-.03,-.2);
                case "ellie" -> stack.translate(-.1,.05,0);
                case "luna" -> stack.translate(0,-.06,.1);
            }
            stack.rotate(new org.joml.Quaternionf().rotationX((float)Math.toRadians(angle)));
        }
        item.submit(stack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        stack.popPose();
    }
}
