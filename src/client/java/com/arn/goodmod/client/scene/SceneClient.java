package com.arn.goodmod.client.scene;

import com.arn.goodmod.animation.AnimationCatalog;
import com.arn.goodmod.entity.GoodcraftNpc;
import com.arn.goodmod.network.SceneEffectPayload;
import com.arn.goodmod.network.SceneInputPayload;
import com.arn.goodmod.scene.SceneScripts;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The partner's side of a scene: d3 (frozen movement, sneak = faster, jump = finish), ds (the horny meter and
 * key prompts), fh (the black transition screen) and l (hiding the partner whose body the girl's model draws).
 */
public final class SceneClient {
    private static final Identifier BUTTONS = Identifier.fromNamespaceAndPath("sexmod", "textures/gui/buttons.png");
    private static final Identifier METER = Identifier.fromNamespaceAndPath("sexmod", "textures/gui/hornymeter.png");
    private static final Map<UUID, GoodcraftNpc> PARTNERS = new HashMap<>();
    private static GoodcraftNpc own;
    private static boolean locked, lastFaster, lastEscape, lastJump, cumming;
    private static float slide, meter, flyAway;
    private static long blackscreenStart = -1, lastFrame;
    /** Offsets of each partner's boyCam bone from her position, captured by the renderer every frame. */
    private static final Map<UUID, Vec3> BOY_CAMS = new HashMap<>();

    private SceneClient() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(SceneClient::tick);
        ClientPlayNetworking.registerGlobalReceiver(SceneEffectPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            if (payload.effect() == SceneEffectPayload.BLACKSCREEN) blackscreenStart = net.minecraft.util.Util.getMillis();
        }));
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("sexmod", "scene_meter"), SceneClient::renderHud);
    }

    /** The girl whose scene this player is bound to, if any. */
    public static GoodcraftNpc sceneOf(UUID player) { return PARTNERS.get(player); }
    public static boolean inputLocked() { return locked; }

    /** l.a(RenderPlayerEvent.Pre): the girl's steve bones draw this player while her clip includes him. */
    public static boolean hidesPlayer(UUID player) {
        GoodcraftNpc npc = PARTNERS.get(player);
        return npc != null && AnimationCatalog.hasPlayer(npc.getAnimationState());
    }

    public static void recordBoyCam(UUID npc, Vec3 offset) { BOY_CAMS.put(npc, offset); }

    /** l.a(RenderTickEvent): with a boy-cam clip in first person, the camera rides the model's boyCam bone. */
    public static Vec3 boyCamPosition(float partialTick) {
        if (own == null || !SceneScripts.BOY_CAM.contains(own.getAnimationState())) return null;
        Vec3 offset = BOY_CAMS.get(own.getUUID());
        return offset == null ? null : own.getPosition(partialTick).add(offset);
    }

    private static void tick(Minecraft client) {
        PARTNERS.clear();
        own = null; locked = false;
        var player = client.player;
        if (player == null || client.level == null) return;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof GoodcraftNpc npc && npc.isAlive()) npc.getRenderPartnerUUID().ifPresent(id -> PARTNERS.put(id, npc));
        }
        if (BOY_CAMS.size() > 64) BOY_CAMS.clear();
        own = PARTNERS.get(player.getUUID());
        if (own == null) { lastFaster = lastEscape = lastJump = false; cumming = false; return; }
        locked = own.isSceneLocked();
        if (locked) player.setDeltaMovement(Vec3.ZERO);
        boolean faster = client.options.keyShift.isDown(), jump = client.options.keyJump.isDown();
        boolean full = own.getArousal() >= 1;
        boolean finish = jump && !lastJump && full;
        boolean escape = jump && !full;
        if (finish) cumming = true;
        if (faster != lastFaster || escape != lastEscape || finish) {
            ClientPlayNetworking.send(new SceneInputPayload(own.getId(), own.getUUID(), faster, finish, escape));
        }
        if (escape && !lastEscape && locked) player.sendOverlayMessage(Component.literal("Hold jump to get out of the animation"));
        lastFaster = faster; lastEscape = escape; lastJump = jump;
    }

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        long now = net.minecraft.util.Util.getMillis();
        float frameTicks = Math.min(5, (now - lastFrame) / 50f);
        lastFrame = now;
        boolean shown = own != null && own.isSceneUiShown();
        if (!shown) { slide = 0; meter = 0; flyAway = 0; cumming = false; }
        else {
            if (slide == 0) {
                var client = Minecraft.getInstance();
                if (client.player != null) client.player.sendOverlayMessage(Component.literal("Hold ").append(client.options.keyShift.getTranslatedKeyMessage())
                        .append(": faster  |  ").append(client.options.keyJump.getTranslatedKeyMessage()).append(" with a full meter: finish  |  hold ")
                        .append(client.options.keyJump.getTranslatedKeyMessage()).append(" 3s: leave"));
            }
            // ds: everything slides in over 25 ticks; the meter eases toward the partner's synced value.
            slide = Math.min(1, slide + frameTicks / 25f);
            float target = own.getArousal();
            meter = target < meter ? target : Mth.lerp(Math.min(1, frameTicks * 0.15f), meter, target);
            if (cumming) flyAway += frameTicks / 15f;
            drawMeter(graphics);
        }
        drawBlackscreen(graphics, now);
    }

    private static void drawMeter(GuiGraphicsExtractor graphics) {
        boolean full = own.getArousal() >= 1, faster = Minecraft.getInstance().options.keyShift.isDown();
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(0.35f, 0.35f);
        if (full) graphics.blit(RenderPipelines.GUI_TEXTURED, BUTTONS, 240, 160, 0, 108 + (cumming ? 54 : 0), 256, 52, 256, 256);
        if (!cumming) graphics.blit(RenderPipelines.GUI_TEXTURED, BUTTONS, (int)Mth.lerp(slide, -200, 98), 405, 0, faster ? 54 : 0, 158, 54, 256, 256);
        pose.popMatrix();
        pose.pushMatrix();
        pose.scale(0.75f, 0.75f);
        graphics.blit(RenderPipelines.GUI_TEXTURED, METER, 10, (int)Mth.lerp(slide, -200, 10), 0, 0, 146, 175, 256, 256);
        if (!cumming) {
            int height = (int)Mth.lerp(meter, 0, 160), v = (int)Mth.lerp(meter, 167, 8);
            float y = Mth.lerp(meter, 178, 18);
            if (height > 0) graphics.blit(RenderPipelines.GUI_TEXTURED, METER, 67, (int)Mth.lerp(slide, -45, y), 159, v, 32, height, 256, 256);
            float empty = 1 - meter;
            int dropY = (int)Mth.lerp(slide, -58, Mth.lerp(empty, 178, 149)), dropV = (int)Mth.lerp(empty, 169, 141), dropH = (int)Mth.lerp(empty, 1, 29);
            graphics.blit(RenderPipelines.GUI_TEXTURED, METER, 120, dropY, 212, dropV, 28, dropH, 256, 256);
            graphics.blit(RenderPipelines.GUI_TEXTURED, METER, 18, dropY, 212, dropV, 28, dropH, 256, 256);
        } else graphics.blit(RenderPipelines.GUI_TEXTURED, METER, 67, (int)Mth.lerp(Math.min(1, flyAway), 18, -300), 159, 8, 32, 160, 256, 256);
        pose.popMatrix();
    }

    /** fh: fade to black, hold while the scene is rearranged behind it, then fade back (about 2.4 s). */
    private static void drawBlackscreen(GuiGraphicsExtractor graphics, long now) {
        if (blackscreenStart < 0) return;
        float seconds = (now - blackscreenStart) / 1000f;
        if (seconds > 2.4f) { blackscreenStart = -1; return; }
        float alpha = seconds < 0.6f ? seconds / 0.6f : seconds < 1.6f ? 1 : 1 - (seconds - 1.6f) / 0.8f;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (int)(Mth.clamp(alpha, 0, 1) * 255) << 24);
    }
}
