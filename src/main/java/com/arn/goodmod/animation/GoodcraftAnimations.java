package com.arn.goodmod.animation;

import com.arn.goodmod.GoodcraftMod;
import com.arn.goodmod.entity.GoodcraftNpc;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.google.gson.JsonObject;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.function.BiConsumer;

/** Original action/movement/eyes controller separation and recovered clip selection. */
public final class GoodcraftAnimations {
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    /** The resolved action clip with its loop mode, speed, length and sound markers, as the server replays them. */
    public record ActionClip(String name, boolean loop, double speed, double length, java.util.List<Marker> markers) {}
    public record Marker(double time, String name) {}
    private static final java.util.Map<String, java.util.List<Marker>> MARKERS = new ConcurrentHashMap<>();
    private static BiConsumer<GoodcraftNpc, String> clientKeyframes = (npc, marker) -> {};
    private static java.util.function.Predicate<GoodcraftNpc> firstPerson = npc -> false;
    private static final java.util.Map<GoodcraftNpc, FlightMemory> FLIGHTS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final class FlightMemory { boolean airborne, alternate; }
    private static final Pattern CALL_END = Pattern.compile(", (true|false|[0-9]+(?:, [0-9.]+f)?), \\w+$");

    private GoodcraftAnimations() {}

    public static void registerControllers(GoodcraftNpc npc, AnimatableManager.ControllerRegistrar registrar) {
        for (String name : new String[]{"action", "movement", "eyes"}) {
            var controller = new AnimationController<GoodcraftNpc>(name, name.equals("action") ? 0 : name.equals("movement") ? 5 : 10,
                    test -> animate(test, name));
            controller.setSoundKeyframeHandler(event -> event.animatable().onAnimationKeyframe(name, event.keyframeData().getSound()));
            controller.setCustomInstructionKeyframeHandler(event -> event.animatable().onAnimationKeyframe(name, event.keyframeData().getInstructions()));
            controller.setParticleKeyframeHandler(event -> event.animatable().onAnimationKeyframe(name, event.keyframeData().getEffect()));
            registrar.add(controller);
        }
    }

    private static PlayState animate(AnimationTest<GoodcraftNpc> test, String controller) {
        GoodcraftNpc npc = test.animatable();
        String clip = switch (controller) {
            case "movement" -> movement(npc);
            case "eyes" -> eyes(npc);
            default -> action(npc);
        };
        if (clip.isEmpty()) return PlayState.STOP;
        for (var input : AnimationCatalog.ANIMATIONS.getAsJsonObject("clips").getAsJsonObject(clip).getAsJsonArray("inputs")) {
            if (!npc.getAnimationInputs().containsKey(input.getAsString())) {
                unresolved(npc.getNpcKind().id(), npc.getAnimationState(), "Missing input " + input.getAsString());
                return PlayState.STOP;
            }
        }
        boolean loop = true;
        JsonObject state = AnimationCatalog.state(npc.getNpcKind().id(), npc.isPlayerForm(), npc.getAnimationState());
        if (controller.equals("action") && npc.clientActionEpoch != npc.getActionEpoch()) {
            // a1/N(): the server restarted this clip (a held "faster" loop); replay it from the first frame.
            npc.clientActionEpoch = npc.getActionEpoch();
            test.controller().reset();
        }
        if (controller.equals("action") && state != null) {
            loop = loops(npc, state);
            test.controller().setAnimationSpeed(state.get("speed").getAsDouble());
        } else {
            test.controller().setAnimationSpeed(controller.equals("movement") ? movementSpeed(npc, clip) : 1);
        }
        // Original em.a(...,boolean,...) selects LOOP or HOLD_ON_LAST_FRAME.
        test.controller().setTransitionTicks(0);
        return test.setAndContinue(raw(clip, loop));
    }

    private static RawAnimation raw(String clip, boolean loop) {
        return loop ? RawAnimation.begin().thenLoop(clip) : RawAnimation.begin().thenPlayAndHold(clip);
    }

    private static String checked(String clip) { return AnimationCatalog.contains(clip) ? clip : ""; }
    private static String prefix(GoodcraftNpc npc) { return "animation." + npc.getNpcKind().animationPrefix() + "."; }
    private static boolean resting(GoodcraftNpc npc) { return "NULL".equals(npc.getAnimationState()); }
    private static boolean flag(GoodcraftNpc npc, String name) { return npc.getAnimationInputs().getOrDefault(name, 0d) != 0; }

    public static String movement(GoodcraftNpc npc) {
        String kind = npc.getNpcKind().id(), p = prefix(npc);
        boolean player = npc.isPlayerForm();
        if (kind.equals("allie")) return checked(p + "tail");
        if (kind.equals("slime") && !player) return ""; // Its jump state machine drives action.
        if (!resting(npc)) return checked(p + "null");
        double distance = Math.abs(npc.getX() - npc.xo) + Math.abs(npc.getZ() - npc.zo);
        if (kind.equals("bee")) return checked(p + (!player && npc instanceof com.arn.goodmod.entity.BeeNpc bee && bee.hasChest() ? "idle_has_chest" : "idle"));
        if (player) return playerMovement(npc);
        if (kind.equals("galath") && !npc.onGround()) return checked(p + "controlled_flight");
        if (npc.isPassenger()) return checked(p + "sit");
        if (kind.equals("kobold")) {
            if (distance > 0) {
                if (!npc.onGround() || Math.abs(Math.abs(npc.yo) - Math.abs(npc.getY())) >= .1) return checked(p + "fly");
                if (npc.isCrouching()) return checked(p + "crouch_walk");
                if (flag(npc, "armed")) return checked(p + "run_armed");
                return checked(p + (distance > .2 ? "run" : "walk"));
            }
            return checked(p + (npc.isCrouching() ? "crouch_idle" : flag(npc, "armed") ? "idle_armed" : "idle"));
        }
        if (kind.equals("goblin")) {
            if (distance > 0 && (!npc.onGround() || Math.abs(Math.abs(npc.yo) - Math.abs(npc.getY())) >= .1)) return checked(p + "fly");
            return checked(p + (distance > 0 ? "walk" : "idle"));
        }
        if (kind.equals("luna") && distance > 0 && (!npc.onGround() || Math.abs(Math.abs(npc.yo) - Math.abs(npc.getY())) >= .1)) return checked(p + "fly");
        if (kind.equals("manglelie")) return checked(p + (distance > 0 ? flag(npc, "legacy.ar") ? "scared_run" : "walk" : "idle"));
        if (kind.equals("ellie") && npc.isCrouching()) return checked(p + (distance > 0 ? "crouchwalk" : "crouchidle"));
        if (distance > 0) {
            if (npc.isSprinting()) return checked(p + "run");
            if (flag(npc, "fast_walk")) return checked(p + "fastwalk");
            return checked(p + "walk");
        }
        return checked(p + "idle" + (kind.equals("luna") && flag(npc, "idle_variant") ? "2" : ""));
    }

    private static net.minecraft.world.entity.LivingEntity movementActor(GoodcraftNpc npc) {
        if (npc.isPlayerForm()) {
            var player = npc.getAssociatedPlayerUUID().map(npc.level()::getPlayerByUUID);
            if (player.isPresent()) return player.get();
        }
        return npc;
    }

    private static double forwardMovement(GoodcraftNpc npc) {
        var actor = movementActor(npc);
        double yaw = Math.toRadians(actor.getYRot());
        return (actor.xo - actor.getX()) * Math.sin(yaw) + (actor.getZ() - actor.zo) * Math.cos(yaw);
    }

    private static String playerMovement(GoodcraftNpc npc) {
        var actor = movementActor(npc);
        String kind = npc.getNpcKind().id(), p = prefix(npc);
        if (actor.isPassenger()) return checked(p + (kind.equals("ellie") ? "ride" : "sit"));
        var flight = FLIGHTS.computeIfAbsent(npc, key -> new FlightMemory());
        if (flight.airborne && actor.onGround()) flight.alternate = !flight.alternate;
        flight.airborne = !actor.onGround();
        if (flight.airborne) return checked(p + (kind.equals("galath") ? "controlled_flight" : "fly" + (flight.alternate ? "2" : "")));
        boolean moving = Math.abs(actor.getX() - actor.xo) + Math.abs(actor.getZ() - actor.zo) > 0;
        boolean crouching = actor.isCrouching() && (kind.equals("ellie") || kind.equals("galath"));
        if (!moving) return checked(p + (crouching ? "crouchidle" : "idle"));
        if (crouching) return checked(p + "crouchwalk");
        if (actor.isSprinting()) return checked(p + (kind.equals("goblin") ? "running" : "run"));
        if (forwardMovement(npc) < -.1) return checked(p + "backwards_walk");
        return checked(p + (Set.of("jenny", "ellie", "bia", "luna").contains(kind) ? "fastwalk" : "walk"));
    }

    private static double movementSpeed(GoodcraftNpc npc, String clip) {
        String kind = npc.getNpcKind().id();
        var actor = movementActor(npc);
        if (kind.equals("allie") && npc.isPlayerForm()) return Math.min(1 + 4 * (Math.abs(actor.getX() - actor.xo) + Math.abs(actor.getY() - actor.yo) + Math.abs(actor.getZ() - actor.zo)), 4);
        if (!npc.isPlayerForm()) {
            if (kind.equals("kobold") && (clip.endsWith("walk") || clip.endsWith("run") || clip.endsWith("run_armed"))) return 1 + npc.getAnimationInputs().getOrDefault("size", 0d) * 2;
            return 1;
        }
        if (clip.contains("fly") || clip.contains("flight") || clip.endsWith("idle") || clip.endsWith("sit") || clip.endsWith("ride")) return 1;
        boolean running = actor.isSprinting(), backward = forwardMovement(npc) < -.1;
        return switch (kind) {
            case "jenny" -> running || backward ? 1.2f : 1.5;
            case "bia" -> 1.2;
            case "ellie", "galath" -> running || backward ? 1.5 : 2;
            case "luna" -> running ? 1.5 : 2;
            case "kobold" -> running ? 1.2f : backward ? 1.75 : 2;
            case "goblin" -> running ? 1.2f : backward ? 1.5 : 2;
            default -> 1;
        };
    }

    public static String eyes(GoodcraftNpc npc) {
        String kind = npc.getNpcKind().id(), p = prefix(npc);
        if (kind.equals("manglelie")) return npc instanceof com.arn.goodmod.entity.ManglelieNpc child && child.getBowTicks() >= 0 ? checked(p + "angry_face") : "";
        if (kind.equals("allie") && !npc.isPlayerForm()) return resting(npc) ? "" : checked(p + "null");
        if (kind.equals("slime")) {
            return checked(p + (resting(npc) || !AnimationCatalog.autoBlink(npc.getAnimationState()) ? "null" : "fhappy"));
        }
        if ((!resting(npc) && !(kind.equals("galath") && !npc.isPlayerForm()))
                || !AnimationCatalog.autoBlink(npc.getAnimationState()) || npc.getAnimationState().equals("GALATH_DE_SUMMON")) return checked(p + "null");
        String animation = switch (kind) {
            case "ellie" -> "eyes";
            // e5 named a nonexistent bia.blink; the original Allie file supplies its own blink.
            case "luna", "kobold", "goblin", "galath", "allie" -> "blink";
            default -> "fhappy";
        };
        return checked(p + animation);
    }

    private static boolean loops(GoodcraftNpc npc, JsonObject state) {
        var calls = state.getAsJsonArray("calls");
        int branch = actionBranch(npc, state);
        if (branch < 0 || branch >= calls.size()) return true;
        var match = CALL_END.matcher(calls.get(branch).getAsJsonObject().get("source_expression").getAsString());
        return !match.find() || !match.group(1).equals("false");
    }

    /** The current action clip and its timing, or null when the state has no playable clip. */
    public static ActionClip actionClip(GoodcraftNpc npc) {
        String clip = action(npc);
        if (clip.isEmpty()) return null;
        JsonObject state = AnimationCatalog.state(npc.getNpcKind().id(), npc.isPlayerForm(), npc.getAnimationState());
        var data = AnimationCatalog.ANIMATIONS.getAsJsonObject("clips").getAsJsonObject(clip);
        boolean loop = state == null || loops(npc, state);
        double speed = state == null ? 1 : state.get("speed").getAsDouble();
        var markers = MARKERS.computeIfAbsent(clip, name -> {
            var list = new java.util.ArrayList<Marker>();
            var sounds = data.getAsJsonObject("sound_markers");
            if (sounds != null) for (var entry : sounds.entrySet()) {
                var effect = entry.getValue().getAsJsonObject().get("effect");
                if (effect != null) list.add(new Marker(Double.parseDouble(entry.getKey()), effect.getAsString()));
            }
            list.sort(java.util.Comparator.comparingDouble(Marker::time));
            return java.util.List.copyOf(list);
        });
        return new ActionClip(clip, loop, speed, data.get("length").getAsDouble(), markers);
    }

    public static String action(GoodcraftNpc npc) {
        String kind = npc.getNpcKind().id(), stateName = npc.getAnimationState();
        if (kind.equals("slime") && !npc.isPlayerForm() && resting(npc)) {
            int phase = npc.getAnimationInputs().getOrDefault("slime_jump_phase", 0d).intValue();
            return checked(prefix(npc) + switch (phase) { case 1 -> "jumpstart"; case 2 -> "jumpair"; case 3 -> "jumpend"; default -> "idle"; });
        }
        JsonObject state = AnimationCatalog.state(kind, npc.isPlayerForm(), stateName);
        if (state == null) return "";
        var calls = state.getAsJsonArray("calls");
        int selected = actionBranch(npc, state);
        if (selected < 0 || selected >= calls.size()) return "";
        String expression = calls.get(selected).getAsJsonObject().get("source_expression").getAsString();
        var ending = CALL_END.matcher(expression);
        if (!ending.find()) return unresolved(kind, stateName, expression);
        try {
            String clip = evaluate(npc, expression.substring(0, ending.start()).trim());
            return checked(clip);
        } catch (IllegalArgumentException error) {
            return unresolved(kind, stateName, expression);
        }
    }

    /** Registered only on the client; markers never perform server inventory/world actions. */
    public static void setClientKeyframeHandler(BiConsumer<GoodcraftNpc, String> handler) { clientKeyframes = java.util.Objects.requireNonNull(handler); }
    public static void setClientPerspective(java.util.function.Predicate<GoodcraftNpc> predicate) { firstPerson = java.util.Objects.requireNonNull(predicate); }

    private static int actionBranch(GoodcraftNpc npc, JsonObject state) {
        if (npc.getNpcKind().id().equals("goblin") && npc.getAnimationState().equals("RUN")) return npc.onGround() ? 0 : 1;
        return npc.getAnimationInputs().getOrDefault("branch." + npc.getAnimationState(), 0d).intValue();
    }

    public static void onKeyframe(GoodcraftNpc npc, String controller, String marker) {
        if (npc.level().isClientSide()) clientKeyframes.accept(npc, marker);
    }

    private static String unresolved(String kind, String state, String expression) {
        if (REPORTED.add(kind + ":" + state + ":" + expression)) {
            GoodcraftMod.LOGGER.warn("Animation selector requires migrated state input: {} / {}", kind, state);
        }
        return "";
    }

    private static String evaluate(GoodcraftNpc npc, String expression) {
        String text = expression.trim();
        if (text.startsWith("String.format(\"animation.goblin.") && text.endsWith(", string)")) {
            String template = text.substring("String.format(\"".length(), text.length() - "\", string)".length());
            return template.replace("%s", firstPerson.test(npc) ? "1" : "3");
        }
        while (text.startsWith("(") && matchingClose(text, 0) == text.length() - 1) text = text.substring(1, text.length() - 1).trim();
        int question = topLevel(text, '?');
        if (question >= 0) {
            int colon = topLevel(text, ':');
            if (colon < question) throw new IllegalArgumentException(text);
            return evaluate(npc, condition(npc, text.substring(0, question)) ? text.substring(question + 1, colon) : text.substring(colon + 1));
        }
        int plus = topLevel(text, '+');
        if (plus >= 0) return evaluate(npc, text.substring(0, plus)) + evaluate(npc, text.substring(plus + 1));
        if (text.startsWith("\"") && text.endsWith("\"")) return text.substring(1, text.length() - 1);
        if (text.equals("this.S")) return Integer.toString(npc.getAnimationVariant());
        if (text.equals("string")) return flag(npc, "legacy.a4") ? "R" : "L";
        if (text.equals("string2")) return flag(npc, "legacy.aT") ? "Switch" : "";
        if (text.matches("this\\.\\w+")) return Integer.toString((int) legacyValue(npc, text.substring(5)));
        throw new IllegalArgumentException(text);
    }

    private static boolean condition(GoodcraftNpc npc, String expression) {
        String value = expression.trim();
        if (value.contains("field_70122_E")) return npc.onGround();
        var data = Pattern.compile("func_187225_a\\((\\w+)\\)").matcher(value);
        if (data.find()) return legacyValue(npc, data.group(1)) != 0;
        var field = Pattern.compile("this\\.(\\w+)").matcher(value);
        if (!field.find()) throw new IllegalArgumentException(value);
        boolean result = legacyValue(npc, field.group(1)) != 0;
        return value.startsWith("!") ? !result : result;
    }

    private static double legacyValue(GoodcraftNpc npc, String field) {
        Double explicit = npc.getAnimationInputs().get("legacy." + field);
        if (explicit != null) return explicit;
        var defaults = AnimationCatalog.profile(npc.getNpcKind().id(), npc.isPlayerForm()).getAsJsonObject("legacy_field_defaults");
        if (defaults.has(field)) return defaults.get(field).getAsDouble();
        throw new IllegalArgumentException("Unbound legacy field: " + field);
    }

    private static int topLevel(String text, char wanted) {
        int depth = 0; boolean quote = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) quote = !quote;
            if (quote) continue;
            if (c == '(') depth++; else if (c == ')') depth--;
            else if (depth == 0 && c == wanted) return i;
        }
        return -1;
    }

    private static int matchingClose(String text, int start) {
        int depth = 0; boolean quote = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') quote = !quote;
            if (quote) continue;
            if (c == '(') depth++; else if (c == ')' && --depth == 0) return i;
        }
        return -1;
    }
}
