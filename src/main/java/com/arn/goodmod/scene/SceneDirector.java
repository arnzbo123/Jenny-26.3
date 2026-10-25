package com.arn.goodmod.scene;

import com.arn.goodmod.animation.GoodcraftAnimations;
import com.arn.goodmod.entity.AllieNpc;
import com.arn.goodmod.entity.GalathNpc;
import com.arn.goodmod.entity.GoodcraftNpc;
import com.arn.goodmod.entity.KoboldNpc;
import com.arn.goodmod.entity.NpcKind;
import com.arn.goodmod.entity.SlimeNpc;
import com.arn.goodmod.network.SceneEffectPayload;
import com.arn.goodmod.registry.GoodcraftItems;
import com.arn.goodmod.registry.GoodcraftSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

/**
 * One girl's scene, owned by the server: em/ei's dc (approach), j (place partner), a8 (camera), b0/fg (bed),
 * a1 (restart), s (reset) and t (payment) packets folded into one state machine. Animation keyframe markers are
 * replayed from the original clips' timings instead of trusting client keyframe packets.
 */
public final class SceneDirector {
    private static final double[][][] BED_SIDES = {
            {{0.5, 0, -0.5}, {0, 0, -1}}, {{0.5, 0, 1.5}, {0, 0, 1}}, {{-0.5, 0, 0.5}, {-1, 0, 0}}, {{1.5, 0, 0.5}, {1, 0, 0}}};
    /** el.g(): sitting spots on the bed's edge, with the floor beside and the bed behind. */
    private static final double[][][] ELLIE_SIDES = {
            {{0.5, 0, -0.18}, {0, 0, -1}, {0, 0, 1}}, {{0.5, 0, 1.18}, {0, 0, 1}, {0, 0, -1}},
            {{-0.18, 0, 0.5}, {-1, 0, 0}, {1, 0, 0}}, {{1.18, 0, 0.5}, {1, 0, 0}, {-1, 0, 0}}};
    private static final int[] SIDE_YAW = {0, 180, -90, 90};
    private static final int MAX_STATE_TICKS = 6000, ESCAPE_TICKS = 60;

    /** d3: players frozen in a scene; they can neither hit nor use anything until released. */
    private static final java.util.Set<UUID> LOCKED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static boolean isLockedPlayer(UUID player) { return LOCKED.contains(player); }
    private final GoodcraftNpc npc;
    private final SceneScripts.Script script;
    private UUID partner;
    private String followUp = "";
    private boolean payFirst, fasterHeld, finishPressed, escapeHeld, locked;
    private int escapeTicks;
    private Vec3 lockPos, cameraAnchor, approachTarget, bedSpot;
    private float sceneYaw, bedYaw;
    private int approachTicks = -1, approachLength, bedTicks = -1;
    private double bedSpeed;
    private String bedArrival = "";
    private double awaitRadius;
    private int awaitHold, awaitCounter = -1, pendingTicks = -1;
    private String awaitPlacement = "", pending = "";
    private String lastState = "NULL";
    private double cursor = -1;
    private int lastEpoch, stateTicks;

    public SceneDirector(GoodcraftNpc npc) {
        this.npc = npc;
        this.script = SceneScripts.of(npc.getNpcKind());
    }

    public Optional<UUID> partner() { return Optional.ofNullable(partner); }
    public boolean isActive() { return partner != null || approachTicks >= 0 || bedTicks >= 0 || pendingTicks >= 0 || !awaitPlacement.isEmpty(); }
    public boolean isLocked() { return locked; }
    /** fg.a(): she is walking to a bed on her own path; companion and wander goals keep out of the way. */
    public boolean walking() { return bedTicks >= 0; }

    // ---- requests ----------------------------------------------------------------------------------------------

    /** m.func_146284_a + t: validate against the offered menu, charge its price, then start the action. */
    public boolean request(ServerPlayer player, String key) {
        var offered = SceneMenus.actions(npc, player).stream().filter(action -> action.key().equals(key)).findFirst();
        if (offered.isEmpty()) return false;
        ItemStack cost = offered.get().cost();
        boolean charged = !cost.isEmpty() && !player.getAbilities().instabuild;
        if (charged && !pay(player, cost)) {
            player.sendSystemMessage(Component.literal("<" + npc.getName().getString() + "> you cannot afford that..."));
            play("girls.jenny.sadoh.sadoh1");
            return false;
        }
        npc.setPaymentItem(cost);
        begin(player, key, !cost.isEmpty());
        return true;
    }

    private static boolean pay(ServerPlayer player, ItemStack cost) {
        var inventory = player.getInventory();
        int owned = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(cost.getItem())) owned += stack.getCount();
        }
        if (owned < cost.getCount()) return false;
        int remaining = cost.getCount();
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(cost.getItem())) continue;
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken); remaining -= taken;
        }
        inventory.setChanged();
        return true;
    }

    private void begin(ServerPlayer player, String key, boolean paid) {
        boolean dressed = npc.getModelVariant() != 0;
        switch (npc.getNpcKind()) {
            case JENNY -> {
                if (key.equals("action.names.dressup")) { setState("STRIP"); return; }
                followUp = shortName(key); payFirst = !SceneMenus.horny(npc);
                faceApproach(player, 40, true);
            }
            case ELLIE -> {
                switch (key) {
                    case "action.names.dressup" -> setState("STRIP");
                    case "Face fuck" -> {
                        // el.b(): she lifts the player at once, facing him.
                        faceApproach(player, 0, false);
                        npc.setTargetYaw(player.getYRot() - 180);
                        setState("CARRY_INTRO");
                    }
                    default -> {
                        // el: after her hug she says "follow me darling", frees him and heads to a bed (q()).
                        bind(player); followUp = shortName(key);
                        npc.removeEffect(com.arn.goodmod.registry.GoodcraftEffects.TRANSFORMATION);
                        player.sendSystemMessage(Component.literal("<" + npc.getName().getString() + "> ").append(Component.translatable("ellie.dialogue.followmedarling")));
                        bedForKind();
                    }
                }
            }
            case BIA -> {
                switch (key) {
                    case "action.names.strip", "action.names.dressup" -> setState("STRIP");
                    case "action.names.talk" -> { followUp = "talkHorny"; faceApproach(player, 40, true); }
                    case "action.names.headpat" -> { followUp = "Headpat"; faceApproach(player, 40, true); }
                    case "action.names.anal", "doggy" -> {
                        followUp = key.equals("doggy") ? "doggy" : "anal";
                        if (npc.getAnimationState().equals("TALK_IDLE")) setState("TALK_RESPONSE");
                        else faceApproach(player, 40, true); // Chosen straight from the menu: walk up, then to the bed.
                    }
                    default -> {}
                }
            }
            case LUNA -> {
                followUp = switch (key) { case "action.names.touchboobs" -> "touch_boobs"; case "action.names.sex" -> "sex"; default -> "headpat"; };
                if (followUp.equals("touch_boobs")) npc.setModelVariant(0);
                faceApproach(player, 40, true);
            }
            case ALLIE -> {
                if (!(npc instanceof AllieNpc allie)) return;
                if (key.equals("action.names.makemerichallie")) { allie.grantWealthWish(player); return; }
                bind(player);
                followUp = key.equals("action.names.deepthroat") ? "deepthroat" : "reverse_cowgirl";
                setState(allie.isFirstWish() ? "ALLIE_PREPARE_FIRST_TIME" : "ALLIE_PREPARE_NORMAL");
            }
            case KOBOLD -> {
                followUp = switch (key) { case "oral" -> "STARTBLOWJOB"; case "anal" -> "KOBOLD_ANAL_START"; default -> "MATING_PRESS_START"; };
                payFirst = paid && !key.equals("mating");
                faceApproach(player, 40, true);
            }
            case GOBLIN -> {
                if (key.equals("take ur stuff back")) {
                    // e3.L(): he gets his stack back, then throws her away (START_THROWING still draws him).
                    if (npc instanceof com.arn.goodmod.entity.GoblinNpc goblin) player.getInventory().placeItemBackInInventory(goblin.takeStolen(), net.minecraft.util.Prediction.SERVER_ONLY);
                    setState("START_THROWING");
                    return;
                }
                // e3.c(): fade out, then z() seats her in front of the player 15 ticks later.
                bind(player); lockAt(player, player.position(), null, null);
                blackscreen(); pending = "goblin_paizuri"; pendingTicks = 15;
            }
            case GALATH -> {
                if (!(npc instanceof GalathNpc galath)) return;
                if (key.equals("ride")) { galath.startRidingFlight(player); return; }
                bind(player); lockAt(player, player.position(), null, null);
                blackscreen(); pending = key.equals("anal") ? "galath_anal" : "galath_cowgirl"; pendingTicks = 24;
            }
            case BEE -> {
                // fz.c(): she floats a block in front of the player, who is drawn 0.2 forward, and CITIZEN_START plays.
                npc.removeEffect(com.arn.goodmod.registry.GoodcraftEffects.TRANSFORMATION);
                faceApproach(player, 0, false);
                lockAt(player, player.position().add(forward(player.getYRot()).scale(0.2 - 0.35)), null, null);
                setState("CITIZEN_START");
            }
            case SLIME -> {
                bind(player);
                npc.setModelVariant(0);
                npc.setTargetYaw(npc.getYRot()); anchorNpc(npc.position());
                if (key.equals("action.names.doggy")) setState("STARTDOGGY");
                else { placeRelative(player, 0, 0, 0.65f, npc.getYRot(), npc.getYRot(), null); setState("SUCKBLOWJOB"); }
            }
            default -> {}
        }
    }

    /** ac/bk/dc input from the scene partner: sneak speeds up, jump finishes once the meter is full. */
    public void input(ServerPlayer player, boolean faster, boolean finish, boolean escape) {
        if (partner == null || !partner.equals(player.getUUID())) return;
        fasterHeld = faster; escapeHeld = escape;
        if (finish) finishPressed = true;
    }

    // ---- ticking -----------------------------------------------------------------------------------------------

    public void tick(ServerLevel level) {
        String state = npc.getAnimationState();
        if (!state.equals(lastState)) changed(lastState, state);
        if (npc.getActionEpoch() != lastEpoch) { lastEpoch = npc.getActionEpoch(); cursor = -1; }
        stateTicks++;
        if (!isActive()) {
            if (isSceneState(state) && stateTicks > 40 && !npc.isPlayerForm()) setState("NULL"); // A scene left over after a reload.
            else { advanceMarkers(); runTimed(); }
            return;
        }
        ServerPlayer player = partnerPlayer();
        if (partner != null && (player == null || !player.isAlive() || player.isSpectator() || player.level() != level || player.distanceToSqr(npc) > 576)) { end(); return; }
        if (player != null && locked) holdPartner(player);
        if (pendingTicks >= 0 && --pendingTicks < 0) place(pending);
        tickApproach(player);
        tickBed(level);
        tickAwait(level);
        if (player != null) {
            String fast = script.faster(state);
            if (fasterHeld && fast != null) { run(script.fasterOps.getOrDefault(state, List.of())); setState(fast); }
            if (finishPressed) {
                finishPressed = false;
                String finish = script.finish(npc.getAnimationState());
                if (finish != null && npc.getArousal() >= 1) { run(script.finishOps.getOrDefault(npc.getAnimationState(), List.of())); setState(finish); }
            }
            escapeTicks = escapeHeld ? escapeTicks + 1 : 0;
            if (escapeTicks > ESCAPE_TICKS) { end(); return; }
        }
        Object[] auto = SceneScripts.AUTO.get(npc.getAnimationState());
        if (auto != null && npc.getActionTicks() >= (int)auto[0]) setState((String)auto[1]);
        advanceMarkers();
        runTimed();
        if (partner != null) stallGuard();
    }

    private void changed(String from, String to) {
        run(script.leave.getOrDefault(from, List.of()));
        lastState = to; cursor = -1; stateTicks = 0;
        run(script.enter.getOrDefault(to, List.of()));
    }

    private void runTimed() {
        var timed = script.timed.get(npc.getAnimationState());
        if (timed != null) { var ops = timed.get(npc.getActionTicks()); if (ops != null) run(ops); }
    }

    /** Replays every sound marker the current clip crossed since the last tick, in clip order. */
    private void advanceMarkers() {
        var clip = GoodcraftAnimations.actionClip(npc);
        if (clip == null || clip.markers().isEmpty()) return;
        String state = npc.getAnimationState();
        int epoch = npc.getActionEpoch();
        double time = npc.getActionTicks() / 20.0 * clip.speed();
        double from = cursor;
        cursor = time;
        for (var marker : clip.markers()) {
            if (!crossed(marker.time(), from, time, clip)) continue;
            say(marker.name());
            sound(marker.name());
            run(script.marker(marker.name()));
            // A transition or restart abandons the rest of the old clip, as the original controller did.
            if (!npc.getAnimationState().equals(state) || npc.getActionEpoch() != epoch) return;
        }
    }

    private static boolean crossed(double at, double from, double to, GoodcraftAnimations.ActionClip clip) {
        if (!clip.loop() || clip.length() <= 0) return from < at && at <= to;
        if (to < at) return false;
        return Math.floor((to - at) / clip.length()) > Math.floor((from - at) / clip.length()) || from < at;
    }

    /** The listener's this.a(sound[, volume]) calls for this marker, played from the girl for everyone near. */
    private void sound(String marker) {
        if (npc.isPlayerForm()) return;
        var profile = com.arn.goodmod.animation.AnimationCatalog.SOUNDS.getAsJsonObject(npc.getNpcKind().id());
        if (profile == null || !profile.has(marker)) return;
        for (var element : profile.getAsJsonArray(marker)) {
            var entry = element.getAsJsonObject();
            if (npc.getRandom().nextFloat() >= entry.get("chance").getAsFloat()) continue;
            var event = entry.has("vanilla")
                    ? net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(entry.get("vanilla").getAsString()))
                    : GoodcraftSounds.pick(entry.get("array").getAsString(), entry.get("index").getAsInt(), npc.getRandom());
            if (event == null) continue;
            npc.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(), event, SoundSource.NEUTRAL, entry.get("volume").getAsFloat(), 1);
            SOUNDS_PLAYED.incrementAndGet();
        }
    }

    /** Marker sounds played so far (checked by the world tests). */
    public static final java.util.concurrent.atomic.AtomicInteger SOUNDS_PLAYED = new java.util.concurrent.atomic.AtomicInteger();

    /** em.h()/a(String)/b(String, true): the girl's (or her partner's) chat line for this marker, to everyone near. */
    private void say(String marker) {
        if (npc.isPlayerForm()) return;
        var profile = com.arn.goodmod.animation.AnimationCatalog.DIALOGUE.getAsJsonObject(npc.getNpcKind().id());
        if (profile == null || !profile.has(marker)) return;
        var lines = profile.getAsJsonArray(marker);
        List<com.google.gson.JsonElement> chosen = new ArrayList<>();
        lines.forEach(chosen::add);
        // ex paymentMSG2 asks for what was bought; ev summon_normalMSG3 counts the wishes left.
        if (marker.equals("paymentMSG2") && lines.size() == 5) {
            int index = switch (followUp) { case "strip" -> 0; case "blowjob" -> 1; case "doggy" -> 2; case "boobjob" -> 3; default -> 4; };
            chosen = List.of(lines.get(index));
        } else if (marker.equals("summon_normalMSG3") && lines.size() == 2) {
            chosen = List.of(lines.get(com.arn.goodmod.item.LampItem.remainingWishes(npc.getSummoningStack()) >= 2 ? 0 : 1));
        }
        ServerPlayer player = partnerPlayer();
        for (var element : chosen) {
            var line = element.getAsJsonObject();
            boolean girl = line.get("speaker").getAsString().equals("girl");
            if (!girl && player == null) continue;
            String speaker = girl ? npc.getName().getString() : player.getName().getString();
            Component text = line.has("key") ? Component.translatable(line.get("key").getAsString()) : Component.literal(line.get("text").getAsString());
            Component message = Component.literal("<" + speaker + "> ").append(text);
            for (ServerPlayer listener : ((ServerLevel)npc.level()).getPlayers(near -> near.distanceToSqr(npc) < 1600)) listener.sendSystemMessage(message);
        }
    }

    /** Hold-on-last-frame clips without a scripted exit, or a scene idling far too long, are wrapped up. */
    private void stallGuard() {
        String state = npc.getAnimationState();
        if (stateTicks > MAX_STATE_TICKS) { end(); return; }
        if (approachTicks >= 0 || bedTicks >= 0 || pendingTicks >= 0 || !awaitPlacement.isEmpty()) return;
        if (state.equals("NULL")) { if (stateTicks > 100) end(); return; }
        var clip = GoodcraftAnimations.actionClip(npc);
        if (clip == null) { if (stateTicks > 200) end(); return; }
        if (!clip.loop() && stateTicks > clip.length() * 20 / Math.max(.1, clip.speed()) + 60) end();
    }

    // ---- ops ---------------------------------------------------------------------------------------------------

    private void run(List<String> ops) {
        for (String op : ops) {
            String[] p = op.split(" ");
            switch (p[0]) {
                case "?held" -> { if (!fasterHeld) return; }
                case "?released" -> { if (fasterHeld) return; }
                case "?field" -> { if (field(p[1]) == 0) return; }
                case "?nofield" -> { if (field(p[1]) != 0) return; }
                case "?state" -> { if (!npc.getAnimationState().equals(p[1])) return; }
                case "state" -> setState(p[1]);
                case "end" -> end();
                case "followup" -> followup();
                case "restart" -> restart();
                case "meter" -> npc.setArousal(npc.getArousal() + Float.parseFloat(p[1]));
                case "meterreset" -> npc.setArousal(0);
                case "ui" -> npc.setSceneUi(true);
                case "uioff" -> npc.setSceneUi(false);
                case "blackscreen" -> blackscreen();
                case "nude" -> npc.setModelVariant(npc.getModelVariant() == 0 ? 1 : 0);
                case "model" -> npc.setModelVariant(Integer.parseInt(p[1]));
                case "camera" -> camera(Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), Float.parseFloat(p[4]), Float.parseFloat(p[5]));
                case "yaw" -> {
                    ServerPlayer player = partnerPlayer();
                    float base = p[1].equals("npc") ? npc.getYRot() : player != null ? player.getYRot() : npc.getYRot();
                    sceneYaw = base + Float.parseFloat(p[2]);
                }
                case "field" -> npc.setAnimationInput("legacy." + p[1], Double.parseDouble(p[2]));
                case "fieldrand" -> npc.setAnimationInput("legacy." + p[1], Integer.parseInt(p[2]) + npc.getRandom().nextInt(Integer.parseInt(p[3]) - Integer.parseInt(p[2]) + 1));
                case "togglefield" -> npc.setAnimationInput("legacy." + p[1], field(p[1]) == 0 ? 1 : 0);
                case "menu" -> {
                    ServerPlayer player = partnerPlayer();
                    if (player == null && npc.getOwnerPlayer().orElse(null) instanceof ServerPlayer owner) player = owner;
                    if (player != null) com.arn.goodmod.network.NpcNetworking.openInteraction(player, npc);
                }
                case "unlock" -> unlock();
                case "awaitpartner" -> awaitPartner();
                case "nudge" -> nudge(Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
                case "koboldegg" -> giveKoboldEgg();
                case "slimebirth" -> { if (npc instanceof SlimeNpc slime) slime.startBirth(Integer.parseInt(p[1])); }
                case "bed" -> bedForKind();
                case "place" -> place(p[1]);
                default -> com.arn.goodmod.GoodcraftMod.LOGGER.warn("Unknown scene op {}", op);
            }
        }
    }

    /**
     * em.N(): GeckoLib 3 zeroed the controller's tick offset. A looping clip wrapped back to its first frame;
     * a held clip jumped past its end, firing its remaining markers (holding sneak skips Jenny's long intro).
     */
    private void restart() {
        var clip = GoodcraftAnimations.actionClip(npc);
        if (clip != null && !clip.loop()) npc.skipActionTicks((int)Math.ceil(clip.length() * 20 / Math.max(.1, clip.speed())));
        else npc.restartAction();
    }

    private double field(String name) { return npc.getAnimationInputs().getOrDefault("legacy." + name, 0d); }

    public void setState(String next) {
        String current = npc.getAnimationState();
        if (current.equals(next)) return;
        npc.setAnimationState(next);
        changed(current, npc.getAnimationState());
    }

    /** U(): the queued follow-up of each girl, run after payment, stripping or her talk. */
    private void followup() {
        boolean dressed = npc.getModelVariant() != 0;
        String next = followUp;
        switch (npc.getNpcKind()) {
            case JENNY -> {
                switch (next) {
                    case "strip" -> { followUp = ""; setState("STRIP"); }
                    case "blowjob" -> { followUp = ""; setState("STARTBLOWJOB"); }
                    case "boobjob" -> { if (dressed) setState("STRIP"); else { followUp = ""; setState("PAIZURI_START"); } }
                    case "doggy" -> {
                        if (dressed) { setState("STRIP"); return; }
                        followUp = ""; setState("NULL"); unlock();
                        startBed("STARTDOGGY", 0.35, BED_SIDES, Component.translatable("jenny.dialogue.nobedinsight"), "jenny_doggy");
                    }
                    default -> end();
                }
            }
            case BIA -> {
                switch (next) {
                    case "talkHorny" -> setState("TALK_HORNY");
                    case "Headpat" -> setState("HEAD_PAT");
                    case "anal", "doggy" -> {
                        setState("NULL"); unlock();
                        startBed(next.equals("anal") ? "ANAL_PREPARE" : "SITDOWN", 0.35, BED_SIDES, Component.literal("no bed in sight..."), "bia_" + next);
                    }
                    default -> {}
                }
                if (!next.equals("anal") && !next.equals("doggy")) followUp = "";
            }
            case LUNA -> {
                switch (next) {
                    case "touch_boobs" -> { if (!npc.getAnimationState().equals("PAYMENT")) setState("PAYMENT"); else { followUp = ""; setState("TOUCH_BOOBS_INTRO"); } }
                    case "sex" -> {
                        if (!npc.getAnimationState().equals("PAYMENT")) { setState("PAYMENT"); return; }
                        followUp = ""; setState("NULL"); unlock();
                        startBed("WAIT_CAT", 0.2, BED_SIDES, Component.literal("Heh.. there is no bed nearby.. but I already ate the fish so nya~ hehe"), "luna_sit");
                    }
                    case "headpat" -> { followUp = ""; setState("HEAD_PAT"); }
                    default -> end();
                }
            }
            case ALLIE -> {
                ServerPlayer player = partnerPlayer();
                if (player == null || next.isEmpty()) { end(); return; }
                // ev deepthroat_prepareDone: dc places the partner, then r = yaw + 180 and the camera moves in.
                followUp = "";
                faceApproach(player, 0, false);
                setState(next.equals("reverse_cowgirl") ? "REVERSE_COWGIRL_START" : "DEEPTHROAT_START");
                sceneYaw = npc.getTargetYaw() + 180; cameraAnchor = null;
                camera(0, 0, 1.35, 0, 30);
            }
            case KOBOLD -> {
                if (next.isEmpty()) { end(); return; }
                if (payFirst && !npc.getAnimationState().equals("PAYMENT")) { setState("PAYMENT"); return; }
                followUp = ""; setState(next);
            }
            default -> { if (next.isEmpty()) end(); }
        }
    }

    private void bedForKind() {
        if (npc.getNpcKind() == NpcKind.ELLIE) {
            setState("NULL"); unlock();
            startBed("SITDOWN", 0.35, ELLIE_SIDES, Component.literal("no bed in sight..."), "ellie_" + followUp);
        }
    }

    private void awaitPartner() {
        switch (npc.getNpcKind()) {
            case JENNY -> await(1.0, 1, "jenny_doggy");
            case SLIME -> await(1.0, 1, "slime_doggy");
            case BIA -> await(1.0, 1, npc.getAnimationState().equals("ANAL_WAIT") ? "bia_anal" : "bia_doggy");
            case LUNA -> await(1.25, 25, "luna_sit");
            case ELLIE -> await(1.5, 20, "ellie_" + followUp);
            default -> {}
        }
    }

    private void await(double radius, int hold, String placement) {
        awaitRadius = radius; awaitHold = hold; awaitCounter = 0; awaitPlacement = placement;
        ServerPlayer player = partnerPlayer();
        if (player != null) player.sendOverlayMessage(Component.literal("Walk up to " + npc.getName().getString()));
    }

    private void tickAwait(ServerLevel level) {
        if (awaitPlacement.isEmpty()) return;
        ServerPlayer player = partnerPlayer();
        if (player == null) {
            // b0: whoever walks up first becomes her partner when the original one is gone.
            Player nearest = level.getNearestPlayer(npc, awaitRadius);
            if (!(nearest instanceof ServerPlayer server) || !npc.canPlayerUse(server)) return;
            bind(server); player = server;
        }
        if (player.distanceTo(npc) > awaitRadius) { awaitCounter = 0; return; }
        if (++awaitCounter < awaitHold) return;
        String placement = awaitPlacement; awaitPlacement = "";
        if (placement.startsWith("bia_")) {
            // e0.d(): a black screen, then the partner is posed while it is dark.
            lockAt(player, player.position(), null, null); blackscreen(); pending = placement; pendingTicks = 20;
        } else place(placement);
    }

    /** The original's per-scene partner poses (positions relative to the girl's anchored spot). */
    private void place(String placement) {
        ServerPlayer player = partnerPlayer();
        if (player == null) { end(); return; }
        float yaw = npc.getTargetYaw();
        Vec3 spot = npc.position();
        switch (placement) {
            case "jenny_doggy" -> {
                lockAt(player, spot, null, null); cameraAnchor = null;
                setState("DOGGYSTART");
                camera(0, 0, 0.4, 0, 60);
            }
            case "ellie_carry" -> lockAt(player, spot.add(rotate(0, 2.5625 - player.getEyeHeight(), -0.3125, 180 + yaw)), null, null);
            case "slime_doggy" -> { placeRelative(player, 0, 0, 0.65f, yaw, yaw, null); setState("DOGGYSTART"); }
            case "bia_anal" -> { placeRelative(player, -0.3, -1.0, -0.5f, yaw, null, null); setState("ANAL_START"); }
            case "bia_doggy" -> {
                anchorNpc(spot.add(rotate(0, 0, 1, yaw)));
                lockAt(player, spot.add(rotate(0, 1.1875 - player.getEyeHeight(), 0.5, yaw)), yaw, 60f);
                setState("PRONE_DOGGY_INTRO");
            }
            case "luna_sit" -> {
                lockAt(player, spot, yaw + 180, 0f);
                setState("COWGIRL_SITTING_INTRO");
                npc.setModelVariant(0);
                sceneYaw = yaw + 180; cameraAnchor = null;
                camera(0, -0.075, -0.7109375, 0, 0);
            }
            case "ellie_missionary" -> {
                npc.setModelVariant(0); setState("MISSIONARY_START");
                lockAt(player, spot.add(rotate(0, 0, 0.1, yaw)), yaw, null);
            }
            case "ellie_cowgirl" -> {
                npc.setModelVariant(0); setState("COWGIRLSTART");
                lockAt(player, spot.add(rotate(0, 1 - player.getEyeHeight(), -1.8125, yaw + 180)), yaw + 180, null);
            }
            case "goblin_paizuri" -> {
                anchorNpc(player.position()); npc.setTargetYaw(player.getYRot() + 180);
                lockAt(player, player.position().add(0, -0.5, 0), null, 70f);
                setState("PAIZURI_START");
            }
            case "galath_cowgirl", "galath_anal" -> {
                anchorNpc(player.position());
                npc.setTargetYaw(placement.equals("galath_anal") ? 0 : player.getYRot() + 180);
                lockAt(player, player.position(), null, null);
                setState(placement.equals("galath_anal") ? "CORRUPT_SLOW" : "RAPE_INTRO");
            }
            default -> end();
        }
    }

    // ---- movement helpers --------------------------------------------------------------------------------------

    private void bind(ServerPlayer player) {
        partner = player.getUUID();
        npc.setScenePartnerUUID(partner);
        npc.getNavigation().stop(); npc.setTarget(null);
    }

    /** e3.func_184645_a: a caught thief stands at the player, facing his way, and CATCH plays. */
    public void catchThief(ServerPlayer player) {
        bind(player); npc.setTargetYaw(player.getYRot()); anchorNpc(player.position());
        lockAt(player, player.position(), null, null); setState("CATCH");
    }

    /** A partner posed for a clip driven by the girl's own code (Galath's GIVE_COIN). */
    public void holdForAnimation(ServerPlayer player, Vec3 spot, float yaw) {
        bind(player); anchorNpc(spot); lockAt(player, spot, yaw, 19f);
    }

    /** dc + j: the partner steps 0.35 forward and freezes; she comes to stand a block in front of his old spot. */
    private void faceApproach(ServerPlayer player, int lerpTicks, boolean lerp) {
        bind(player);
        float yaw = player.getYRot();
        Vec3 origin = player.position();
        approachTarget = origin.add(forward(yaw));
        lockAt(player, origin.add(forward(yaw).scale(0.35)), null, null);
        npc.setTargetYaw(yaw + 180);
        npc.setAnchored(true);
        if (lerp && lerpTicks > 0) { approachTicks = 0; approachLength = lerpTicks; }
        else anchorNpc(approachTarget);
    }

    private void tickApproach(ServerPlayer player) {
        if (approachTicks < 0) return;
        Vec3 position = npc.position();
        if (position.distanceToSqr(approachTarget) < 1.0E-4 || approachTicks >= approachLength) {
            approachTicks = -1; anchorNpc(approachTarget);
            if (player != null) npc.setTargetYaw(player.getYRot() + 180);
            if (payFirst) setState("PAYMENT"); else followup();
            return;
        }
        // b6.a(pos, target, 40 - ticks): close a growing share of the remaining distance each tick.
        Vec3 next = position.lerp(approachTarget, 1.0 / Math.max(1, approachLength - approachTicks++));
        npc.setPos(next.x, next.y, next.z);
    }

    private void startBed(String arrival, double speed, double[][][] sides, Component noBed, String placement) {
        BlockPos origin = npc.blockPosition();
        List<BlockPos> beds = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-22, -3, -22), origin.offset(22, 3, 22))) {
            if (npc.level().getBlockState(pos).is(BlockTags.BEDS)) beds.add(pos.immutable());
        }
        beds.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        for (BlockPos bed : beds) {
            int chosen = -1;
            for (int i = 0; i < sides.length; i++) {
                if (!npc.level().isEmptyBlock(BlockPos.containing(Vec3.atLowerCornerOf(bed).add(sides[i][1][0], sides[i][1][1], sides[i][1][2])))) continue;
                if (sides[i].length > 2 && !npc.level().getBlockState(BlockPos.containing(Vec3.atLowerCornerOf(bed).add(sides[i][2][0], sides[i][2][1], sides[i][2][2]))).is(BlockTags.BEDS)) continue;
                if (chosen == -1 || origin.distToCenterSqr(Vec3.atLowerCornerOf(bed).add(sides[i][0][0], 0, sides[i][0][2]))
                        < origin.distToCenterSqr(Vec3.atLowerCornerOf(bed).add(sides[chosen][0][0], 0, sides[chosen][0][2]))) chosen = i;
            }
            if (chosen == -1) continue;
            bedSpot = Vec3.atLowerCornerOf(bed).add(sides[chosen][0][0], sides[chosen][0][1], sides[chosen][0][2]);
            bedYaw = SIDE_YAW[chosen]; bedSpeed = speed; bedArrival = arrival; bedTicks = 0;
            awaitPlacement = "";
            npc.setAnchored(false); npc.setNoGravity(false); npc.noPhysics = false;
            npc.getNavigation().moveTo(bedSpot.x, bedSpot.y, bedSpot.z, speed);
            return;
        }
        ServerPlayer player = partnerPlayer();
        if (player != null) player.sendSystemMessage(Component.literal("<" + npc.getName().getString() + "> ").append(noBed));
        // ex/el/e0/eb: each girl's own disappointed sound when no bed is near.
        switch (npc.getNpcKind()) {
            case ELLIE -> npc.playLegacy("GIRLS_ELLIE_SIGH", 0, 6);
            case BIA -> npc.playLegacy("GIRLS_BIA_BREATH", 2, 1);
            case LUNA -> npc.playLegacy("GIRLS_LUNA_GIGGLE", -1, 1);
            default -> npc.playLegacy("GIRLS_JENNY_HMPH", 2, 1);
        }
        end();
    }

    private void tickBed(ServerLevel level) {
        if (bedTicks < 0) return;
        if (npc.position().distanceTo(bedSpot) < 0.6 || bedTicks > 200) {
            bedTicks = -1;
            npc.setTargetYaw(bedYaw); anchorNpc(bedSpot);
            setState(bedArrival);
            return;
        }
        if (++bedTicks == 60 || bedTicks == 120) { npc.getNavigation().stop(); npc.getNavigation().moveTo(bedSpot.x, bedSpot.y, bedSpot.z, bedSpeed); }
    }

    /** G flag: she is pinned to her scene spot, no gravity or collision, facing her scene yaw. */
    private void anchorNpc(Vec3 spot) {
        npc.getNavigation().stop();
        npc.setAnchored(true); npc.setNoGravity(true); npc.noPhysics = true;
        npc.teleportTo(spot.x, spot.y, spot.z); npc.setDeltaMovement(Vec3.ZERO);
        npc.setYRot(npc.getTargetYaw()); npc.setYBodyRot(npc.getTargetYaw()); npc.setYHeadRot(npc.getTargetYaw());
    }

    private void placeRelative(ServerPlayer player, double x, double y, float z, float yaw, Float playerYaw, Float pitch) {
        lockAt(player, npc.position().add(rotate(x, y, z, yaw)), playerYaw, pitch);
    }

    private void lockAt(ServerPlayer player, Vec3 pos, Float yaw, Float pitch) {
        locked = true; lockPos = pos; LOCKED.add(player.getUUID());
        npc.setSceneLocked(true);
        player.stopRiding(); player.setNoGravity(true); player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance();
        if (yaw == null && pitch == null) player.teleportTo(pos.x, pos.y, pos.z);
        else player.teleportTo((ServerLevel)player.level(), pos.x, pos.y, pos.z, java.util.Set.of(),
                yaw == null ? player.getYRot() : yaw, pitch == null ? player.getXRot() : pitch, false);
    }

    private void holdPartner(ServerPlayer player) {
        player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance();
        if (!player.isNoGravity()) player.setNoGravity(true);
        // Only real movement is undone. Re-sending tiny collision corrections every tick made the client and
        // server fight over the position, bouncing the camera back and forth.
        if (player.position().distanceToSqr(lockPos) > 0.25) player.teleportTo(lockPos.x, lockPos.y, lockPos.z);
    }

    /** em.a(x, y, z, yaw, pitch): move the partner's view relative to where his scene began. */
    private void camera(double x, double y, double z, float yawOffset, float pitch) {
        ServerPlayer player = partnerPlayer();
        if (player == null) return;
        if (cameraAnchor == null) cameraAnchor = player.position();
        double r = Math.toRadians(sceneYaw);
        Vec3 pos = cameraAnchor.add(-Math.sin(r + Math.PI / 2) * x, y, Math.cos(r + Math.PI / 2) * x)
                .add(-Math.sin(r) * z, 0, Math.cos(r) * z);
        lockAt(player, pos, sceneYaw + yawOffset, pitch);
    }

    private void nudge(double x, double y, double z) {
        ServerPlayer player = partnerPlayer();
        if (player != null && locked) lockAt(player, lockPos.add(rotate(x, y, z, npc.getTargetYaw() + 180)), null, null);
    }

    private void unlock() {
        ServerPlayer player = partnerPlayer();
        if (player != null) release(player);
        if (partner != null) LOCKED.remove(partner);
        locked = false; npc.setSceneLocked(false); npc.setSceneUi(false);
    }

    private static void release(ServerPlayer player) {
        player.setNoGravity(false);
        Vec3 pos = player.position();
        for (int i = 0; i < 4 && !player.level().noCollision(player, player.getDimensions(player.getPose()).makeBoundingBox(pos)); i++) pos = pos.add(0, 1, 0);
        if (!pos.equals(player.position())) player.teleportTo(pos.x, pos.y, pos.z);
    }

    // ---- endings -----------------------------------------------------------------------------------------------

    /** s.a (ResetGirl): free both of them, lift them out of any block, and return her to idle. */
    public void end() {
        ServerPlayer player = partnerPlayer();
        if (player != null) release(player);
        if (partner != null) LOCKED.remove(partner);
        partner = null; npc.setScenePartnerUUID(null);
        locked = false; npc.setSceneLocked(false); npc.setSceneUi(false); npc.setArousal(0);
        followUp = ""; payFirst = false; fasterHeld = finishPressed = escapeHeld = false;
        approachTicks = -1; bedTicks = -1; pendingTicks = -1; pending = ""; awaitPlacement = ""; cameraAnchor = null; escapeTicks = 0;
        boolean floating = npc.getNpcKind() == NpcKind.ALLIE;
        npc.setAnchored(false); npc.setNoGravity(floating); npc.noPhysics = false;
        Vec3 pos = npc.position();
        for (int i = 0; i < 4 && !npc.level().noCollision(npc, npc.getBoundingBox().move(pos.subtract(npc.position()))); i++) pos = pos.add(0, 1, 0);
        if (!pos.equals(npc.position())) npc.teleportTo(pos.x, pos.y, pos.z);
        if (npc instanceof SlimeNpc slime) slime.calmDown();
        setState("NULL");
    }

    /** States that only make sense while a scene is running (everything but her own idle/combat/home life). */
    private static boolean isSceneState(String state) {
        return !java.util.Set.of("NULL", "ATTACK", "BOW", "RIDE", "SIT", "THROW_PEARL", "DOWNED", "FLY", "SUMMON_SKELETON", "ATTACK_SWORD",
                "KNOCK_OUT_FLY", "KNOCK_OUT_GROUND", "KNOCK_OUT_STAND_UP", "CONTROLLED_FLIGHT", "BOOST", "GALATH_SUMMON", "GALATH_DE_SUMMON",
                "GIVE_COIN", "HUG_MANG", "RIDE_MOMMY_HEAD", "RUN", "WAVE", "WAVE_IDLE", "SUMMON", "SUMMON_WAIT", "SUMMON_NORMAL",
                "SUMMON_SAND", "SUMMON_NORMAL_WAIT", "RICH_FIRST_TIME", "RICH_NORMAL", "FISHING_START", "FISHING_IDLE", "FISHING_EAT",
                "FISHING_THROW_AWAY", "MINE", "SLEEP", "SHOULDER_IDLE", "PICK_UP", "CATCH", "START_THROWING", "THROWN", "JUMP_0", "JUMP_1",
                "JUMP_2", "AWAIT_PICK_UP", "VANISH", "STAND_UP", "UNDRESS", "DRESS", "STRIP", "DASH").contains(state);
    }

    private void giveKoboldEgg() {
        ServerPlayer player = partnerPlayer();
        if (player == null || !(npc instanceof KoboldNpc kobold)) return;
        ItemStack egg = new ItemStack(GoodcraftItems.KOBOLD_EGG_ITEM);
        CompoundTag tag = new CompoundTag();
        kobold.getTribeId().ifPresent(id -> tag.putString("tribeID", id.toString()));
        tag.putString("egg_color", kobold.getTribeColor());
        egg.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        player.getInventory().placeItemBackInInventory(egg, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    private void blackscreen() {
        ServerPlayer player = partnerPlayer();
        if (player != null) ServerPlayNetworking.send(player, new SceneEffectPayload(SceneEffectPayload.BLACKSCREEN));
    }

    private void play(String id) {
        var sound = GoodcraftSounds.events().get(id);
        if (sound != null) npc.level().playSound(null, npc.blockPosition(), sound, SoundSource.NEUTRAL, 1, 1);
    }

    private ServerPlayer partnerPlayer() {
        return partner != null && npc.level().getPlayerByUUID(partner) instanceof ServerPlayer player ? player : null;
    }

    private static String shortName(String key) { return key.startsWith("action.names.") ? key.substring("action.names.".length()) : key; }
    private static Vec3 forward(float yaw) { double r = Math.toRadians(yaw); return new Vec3(-Math.sin(r), 0, Math.cos(r)); }

    /** ck.a(x, y, z, yaw): rotate a girl-relative offset into world space. */
    public static Vec3 rotate(double x, double y, double z, float yaw) {
        double r = Math.toRadians(yaw);
        return new Vec3(-Math.sin(r + Math.PI / 2) * x - Math.sin(r) * z, y, Math.cos(r + Math.PI / 2) * x + Math.cos(r) * z);
    }
}
