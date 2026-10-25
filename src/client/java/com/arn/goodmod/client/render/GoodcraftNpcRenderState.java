package com.arn.goodmod.client.render;

import com.geckolib.renderer.base.GeoRenderState;
import java.util.Map;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * A fresh snapshot for each entity; baked geometry is never mutated globally.
 *
 * <p>Do NOT override {@code getDataMap()} here. GeckoLib 5 mixes {@code GeoRenderState} into every
 * {@code EntityRenderState} and supplies its own data map (and the matching addGeckolibData /
 * hasGeckolibData / getPackedLight / getAnimatableAge). Overriding getDataMap with a second map makes
 * writes go to GeckoLib's map while reads hit ours, so ANIMATABLE_MANAGER reads back null and the
 * renderer crashes in AnimationProcessor.extractControllerStates. The interface is declared only so
 * this module compiles; at runtime GeckoLib's mixin methods take precedence over the interface defaults.
 */
public final class GoodcraftNpcRenderState extends LivingEntityRenderState implements GeoRenderState {
    public String kind;
    public String action;
    public int modelVariant;
    public int attackVariant;
    public boolean playerForm;
    public boolean combinedModel;
    public boolean slimArms;
    public boolean showPlayer;
    /** The scene partner's skin for the steve bones, whether he is the local player, and the bones it draws. */
    public net.minecraft.resources.Identifier partnerSkin;
    public boolean partnerLocal;
    public int paymentCount = 1;
    public final java.util.Set<String> skinBones = new java.util.HashSet<>(), partBones = new java.util.HashSet<>();
    public boolean showChest;
    public boolean headLook;
    public boolean anchored;
    public float caughtItemScale = 1;
    public java.util.UUID entityUUID;
    public Object levelIdentity;
    public long worldTick;
    public net.minecraft.world.phys.Vec3 attachmentOffset = net.minecraft.world.phys.Vec3.ZERO;
    public float parentBodyYaw;
    public float parentBodyScale = 1;
    public final boolean[] armor = new boolean[4];
    public Map<String, Boolean> boneVisibility = Map.of();
    public Map<String, Float> boneScales = Map.of();
    public java.util.List<Integer> selectors = java.util.List.of();
    public Map<String, Integer> boneColors = Map.of();
    public Map<String, Integer> armorColors = Map.of();
    public Map<String, Float> armorUvOffsets = Map.of();
    public final java.util.Set<String> tintedBones = new java.util.HashSet<>();
    public java.util.List<GoodcraftAddonLayer.Attachment> addons = java.util.List.of();
}
