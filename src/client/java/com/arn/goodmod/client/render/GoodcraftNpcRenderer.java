package com.arn.goodmod.client.render;

import com.arn.goodmod.animation.AnimationCatalog;
import com.arn.goodmod.client.GoodcraftMolang;
import com.arn.goodmod.client.command.ClientPreferences;
import com.arn.goodmod.entity.GoodcraftNpc;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.google.gson.JsonObject;
import java.util.Map;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;

/** Shared original model rules, applied to GeckoLib 5's per-instance bone snapshots. */
public final class GoodcraftNpcRenderer extends GeoEntityRenderer<GoodcraftNpc, GoodcraftNpcRenderState> {
    /** cr.f: the slime girl forms her own bed for these states. */
    private static final java.util.Set<String> SLIME_BED = java.util.Set.of("STARTDOGGY", "WAITDOGGY", "DOGGYSTART", "DOGGYSLOW", "DOGGYFAST", "DOGGYCUM");
    public GoodcraftNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new GoodcraftNpcModel());
        this.shadowRadius = .2f;
        withRenderLayer(new GoodcraftAttachmentPositions(this));
        withRenderLayer(new GoodcraftAddonLayer(this));
        withRenderLayer(new GoodcraftBoneColors<>(this));
        withRenderLayer(new GoodcraftHeldItems(context, this));
        withRenderLayer(new SceneSkinLayer(this));
    }

    @Override public GoodcraftNpcRenderState createRenderState(GoodcraftNpc npc, Void related) {
        return new GoodcraftNpcRenderState();
    }

    @Override public void extractRenderState(GoodcraftNpc npc, GoodcraftNpcRenderState state, float partialTick) {
        GoodcraftPlayerForms.copyRenderMotion(npc);
        super.extractRenderState(npc, state, partialTick);
    }

    @Override public void addRenderData(GoodcraftNpc npc, Void related, GoodcraftNpcRenderState state, float partialTick) {
        state.kind = npc.getNpcKind().id();
        state.action = npc.getAnimationState();
        state.modelVariant = npc.getModelVariant();
        state.playerForm = npc.isPlayerForm();
        state.combinedModel = npc.isUsingCombinedModel();
        state.anchored = npc.isAnchored();
        state.boneVisibility = Map.copyOf(npc.getBoneVisibility());
        state.boneScales = Map.copyOf(npc.getBoneScales());
        state.showPlayer = AnimationCatalog.hasPlayer(state.action);
        state.showChest = npc instanceof com.arn.goodmod.entity.BeeNpc bee && bee.hasChest() && state.action.equals("NULL");
        state.headLook = state.kind.equals("kobold")
                ? state.action.equals("NULL") && npc.isCrouching() && !(npc.onGround() && Math.abs(Math.abs(npc.yo) - Math.abs(npc.getY())) > .1)
                : state.action.equals("NULL") || state.action.equals("ATTACK") || state.action.equals("BOW");
        state.slimArms = npc.getAnimationInputs().getOrDefault("slim_arms", 0d) != 0;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        // The NPC's equipment container is server-only; the server mirrors it into the synced vanilla slots.
        for (int i = 0; i < slots.length; i++) state.armor[i] = npc.getModelVariant() != 0 && !npc.getItemBySlot(slots[i]).isEmpty();
        if (npc.isPlayerForm()) npc.getAssociatedPlayerUUID().map(npc.level()::getPlayerByUUID).ifPresent(player -> {
            for (int i = 0; i < slots.length; i++) state.armor[i] = npc.getModelVariant() != 0 && !player.getItemBySlot(slots[i]).isEmpty();
        });
        state.selectors = RenderModelCode.read(npc).selectors();
        for (GoodcraftMolang.Input input : GoodcraftMolang.Input.values()) {
            Double value = npc.getAnimationInputs().get(input.name().toLowerCase(java.util.Locale.ROOT));
            if (value != null) GoodcraftMolang.bind(state, input, value);
        }
    }

    @Override public void scaleModelForRender(RenderPassInfo<GoodcraftNpcRenderState> pass, float width, float height) {
        var state = pass.renderState();
        float size = state.kind.equals("kobold") && state.selectors.size() == 11 ? .75f + state.selectors.get(0) / 400f : 1;
        super.scaleModelForRender(pass, width * size, height * size);
    }

    @Override public net.minecraft.world.phys.Vec3 getRenderOffset(GoodcraftNpcRenderState state) {
        return super.getRenderOffset(state).add(state.attachmentOffset);
    }

    @Override public RenderType getRenderType(GoodcraftNpcRenderState state, Identifier texture) {
        return RenderTypes.entityTranslucent(texture);
    }

    @Override public int getRenderColor(GoodcraftNpc npc, Void related, float partialTick) {
        int alpha = Math.round(255 * Math.clamp(npc.getAnimationInputs().getOrDefault("allie.alpha", 1d).floatValue(), 0, 1));
        return alpha << 24 | 0xffffff;
    }

    @Override public void adjustModelBonesForRender(RenderPassInfo<GoodcraftNpcRenderState> pass, BoneSnapshots bones) {
        GoodcraftNpcRenderState state = pass.renderState();
        GoodcraftAppearance.apply(state, pass.model(), bones);
        visible(bones, "boyCam", false);
        visible(bones, "girlCam", false);
        visible(bones, "steve", state.showPlayer);
        if (state.partnerLocal && net.minecraft.client.Minecraft.getInstance().options.getCameraType().isFirstPerson()) visible(bones, "Head2", false);
        for (String side : new String[]{"right", "left"}) {
            for (String segment : new String[]{"Arm", "LowerArm"}) {
                visible(bones, side + segment + "Alex", state.slimArms);
                visible(bones, side + segment + "Steve", !state.slimArms);
            }
        }
        JsonObject groups = AnimationCatalog.MODELS.getAsJsonObject(state.kind).getAsJsonObject("armor_groups");
        String[] armor = {"c", "f", "h", "b"}, flesh = {"g", "a", "e", "d"};
        for (int i = 0; i < 4; i++) {
            group(bones, groups, armor[i], state.armor[i]);
            group(bones, groups, flesh[i], !state.armor[i]);
        }
        if (state.kind.equals("bee")) visible(bones, "chest", state.showChest);
        if (state.kind.equals("kobold")) for (String bone : new String[]{"crown", "egg", "tounge"}) visible(bones, bone, false);
        if (state.kind.equals("goblin")) visible(bones, "preggy", false);
        if (state.kind.equals("slime")) for (String bone : new String[]{"bedSlime", "bedSlimeLayer"}) visible(bones, bone, SLIME_BED.contains(state.action));
        if (state.kind.equals("manglelie") && state.action.equals("RIDE_MOMMY_HEAD")) {
            bones.ifPresent("body", bone -> bone.setRotY(bone.getRotY() + state.parentBodyYaw).setScale(state.parentBodyScale, state.parentBodyScale, state.parentBodyScale));
        }
        if (state.headLook) {
            float yaw = (float)Math.toRadians(state.yRot), pitch = (float)Math.toRadians(state.xRot);
            bones.ifPresent("neck", bone -> bone.setRotY(yaw * .5f));
            bones.ifPresent("head", bone -> bone.setRotY(yaw).setRotX(pitch + (state.kind.equals("bee") ? 1 : 0)));
            bones.ifPresent("body", bone -> bone.setRotY(0));
            bones.ifPresent("dd", bone -> bone.setRotY(0));
        }
        if (state.kind.equals("galath")) {
            for (String bone : new String[]{"futaCock", "futaBallLL", "futaBallLR"}) visible(bones, bone, ClientPreferences.showAlternateBody());
        }
        if (state.kind.equals("luna") && !state.anchored) {
            bones.ifPresent("head", head -> {
                float pitch = head.getRotX(), fraction = pitch / (float)Math.toRadians(45);
                if (!state.playerForm || pitch > 0) bones.ifPresent("backHair", bone -> bone.setRotX(-pitch).setTranslateY(.75f * fraction).setTranslateZ(.75f * fraction));
                if (!state.playerForm) for (String name : new String[]{"sideHairR", "sideHairL"}) bones.ifPresent(name, bone -> bone.setRotX(-pitch).setTranslateY(1.3f * fraction).setTranslateZ(-1.3f * fraction));
                for (String name : new String[]{"frontHairL", "frontHairR"}) bones.ifPresent(name, bone -> bone.setRotX(-pitch));
            });
        }
        state.boneVisibility.forEach((name, shown) -> visible(bones, name, shown));
        state.boneScales.forEach((name, scale) -> bones.ifPresent(name, bone -> bone.setScale(scale, scale, scale)));
    }

    private static void group(BoneSnapshots bones, JsonObject groups, String key, boolean shown) {
        if (groups.has(key)) groups.getAsJsonArray(key).forEach(name -> visible(bones, name.getAsString(), shown));
    }

    /** GeckoLib 3's setHidden, used by the original, hides a bone's whole subtree, not just its own cubes. */
    private static void visible(BoneSnapshots bones, String name, boolean shown) {
        bones.ifPresent(name, bone -> bone.skipRender(!shown).skipChildrenRender(!shown));
    }
}
