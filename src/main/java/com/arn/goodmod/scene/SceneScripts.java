package com.arn.goodmod.scene;

import com.arn.goodmod.entity.NpcKind;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The original per-girl sound-keyframe listeners (ex, el, e0, eb, ev, ff, e3, f_), their faster (c(fp)) and
 * finish (a(fp)) tables, and fp's fixed-length follow-ups, transcribed as data the server replays.
 *
 * <p>Marker ops run left to right, like the original switch fall-through. A guard op ({@code ?held},
 * {@code ?released}, {@code ?field X}, {@code ?nofield X}, {@code ?state X}, {@code ?follow X}) stops the
 * rest of the list when it fails, mirroring the original {@code if (...) break;}. Client-only effects of the
 * originals (chat lines, sounds) stay with the client sound callbacks.
 */
public final class SceneScripts {
    public static final class Script {
        final Map<String, List<String>> markers = new HashMap<>();
        final Map<String, String> faster = new HashMap<>(), finish = new HashMap<>();
        final Map<String, List<String>> fasterOps = new HashMap<>(), finishOps = new HashMap<>();
        final Map<String, List<String>> enter = new HashMap<>(), leave = new HashMap<>();
        final Map<String, Map<Integer, List<String>>> timed = new HashMap<>();
        Script on(String marker, String ops) { markers.put(marker, split(ops)); return this; }
        Script on(String[] markers, String ops) { for (String marker : markers) on(marker, ops); return this; }
        Script fast(String from, String to) { faster.put(from, to); return this; }
        Script fast(String from, String to, String ops) { fasterOps.put(from, split(ops)); return fast(from, to); }
        Script cum(String to, String... from) { for (String state : from) finish.put(state, to); return this; }
        Script cumOps(String from, String ops) { finishOps.put(from, split(ops)); return this; }
        Script enter(String state, String ops) { enter.put(state, split(ops)); return this; }
        Script leave(String state, String ops) { leave.put(state, split(ops)); return this; }
        Script at(String state, int tick, String ops) { timed.computeIfAbsent(state, key -> new HashMap<>()).put(tick, split(ops)); return this; }
        public List<String> marker(String name) { return markers.getOrDefault(name, List.of()); }
        public String faster(String state) { return faster.get(state); }
        public String finish(String state) { return finish.get(state); }
        public boolean hasFinish(String state) { return finish.containsKey(state); }
    }

    /** fp entries constructed with a fixed length and follow-up (fp.l() advances them on both sides). */
    static final Map<String, Object[]> AUTO = Map.ofEntries(
            Map.entry("CARRY_INTRO", new Object[]{191, "CARRY_SLOW"}),
            Map.entry("PRONE_DOGGY_HARD", new Object[]{34, "PRONE_DOGGY_SOFT"}),
            Map.entry("PRONE_DOGGY_INSERT", new Object[]{42, "PRONE_DOGGY_SOFT"}),
            Map.entry("REVERSE_COWGIRL_FAST_START", new Object[]{34, "REVERSE_COWGIRL_SLOW"}),
            Map.entry("REVERSE_COWGIRL_FAST_CONTINUES", new Object[]{39, "REVERSE_COWGIRL_SLOW"}),
            Map.entry("REVERSE_COWGIRL_START", new Object[]{88, "REVERSE_COWGIRL_SLOW"}),
            Map.entry("RAPE_INTRO", new Object[]{46, "RAPE_ON_GOING"}),
            Map.entry("RAPE_CUM", new Object[]{34, "RAPE_CUM_IDLE"}),
            Map.entry("CORRUPT_INTRO", new Object[]{29, "CORRUPT_SLOW"}));

    /** fp.useBoyCam: the first-person camera follows the model's boyCam bone. */
    public static final Set<String> BOY_CAM = Set.of("DEEPTHROAT_START", "DEEPTHROAT_SLOW", "DEEPTHROAT_FAST", "DEEPTHROAT_CUM",
            "CARRY_INTRO", "CARRY_SLOW", "CARRY_FAST", "CARRY_CUM", "PRONE_DOGGY_INTRO", "PRONE_DOGGY_SOFT", "PRONE_DOGGY_HARD",
            "PRONE_DOGGY_INSERT", "PRONE_DOGGY_CUM", "REVERSE_COWGIRL_START", "REVERSE_COWGIRL_SLOW", "REVERSE_COWGIRL_FAST_START",
            "REVERSE_COWGIRL_FAST_CONTINUES", "REVERSE_COWGIRL_CUM", "RAPE_INTRO", "RAPE_ON_GOING", "RAPE_CUM", "RAPE_CUM_IDLE",
            "CORRUPT_INTRO", "GIVE_COIN", "CONTROLLED_FLIGHT", "BOOST");

    private static final Script EMPTY = new Script();
    private static final EnumMap<NpcKind, Script> SCRIPTS = new EnumMap<>(NpcKind.class);

    static {
        // ex: Jenny.
        SCRIPTS.put(NpcKind.JENNY, new Script()
                .on("becomeNude", "nude")
                .on("stripDone", "followup")
                .on("sexUiOn", "ui")
                .on("paymentDone", "followup")
                .on("bjiMSG1", "yaw npc 180; meterreset")
                .on("bjiMSG5", "meterreset")
                .on("bjiMSG10", "camera -0.65 -0.8 -0.25 60 -3")
                .on("bjiMSG11", "meter 0.02; ?held; restart")
                .on("bjiMSG12", "meter 0.02")
                .on("bjtMSG1", "meter 0.04")
                .on("bjiDone", "state SUCKBLOWJOB; ui")
                .on("bjtDone", "state SUCKBLOWJOB")
                .on("doggyfastReady", "?held; restart; field aa 1")
                .on(new String[]{"bjtReady", "paizuriReady"}, "?held; restart")
                .on("bjcMSG2", "uioff")
                .on("bjcBlackScreen", "blackscreen")
                .on(new String[]{"bjcDone", "paizuri_cumDone", "doggyCumDone"}, "meterreset; end")
                .on("doggyGoOnBedMSG1", "yaw npc 0")
                .on("doggyGoOnBedDone", "state WAITDOGGY; awaitpartner")
                .on("doggystartMSG4", "meterreset")
                .on("doggystartDone", "state DOGGYSLOW; ui")
                .on("doggyslowMSG1", "field aa 0; meter 0.00666")
                .on("doggyfastMSG1", "meter 0.02")
                .on("doggyfastDone", "field aa 0; state DOGGYSLOW")
                .on("boobjob_camera", "yaw player 0; ?nofield ae; field ae 1; camera -0.7 -0.6 0.2 60 -3")
                .on("paizuri_startDone", "state PAIZURI_SLOW; meterreset; ui")
                .on("paizuriFastMSG1", "meter 0.04")
                .on(new String[]{"paizuriSlowMSG1", "paizuriStartMSG1"}, "meter 0.02")
                .on("paizuri_fastDone", "state PAIZURI_SLOW; ?nofield ae; field ae 1; camera -0.7 -0.6 0.2 60 -3")
                .on("paizuri_cumStart", "?nofield ae; camera -0.7 -0.6 0.2 60 -3")
                .fast("SUCKBLOWJOB", "THRUSTBLOWJOB").fast("DOGGYSLOW", "DOGGYFAST")
                .fast("PAIZURI_SLOW", "PAIZURI_FAST", "?field ae; field ae 0; camera 0 0 0.2 0 70")
                .cum("CUMBLOWJOB", "SUCKBLOWJOB", "THRUSTBLOWJOB").cumOps("SUCKBLOWJOB", "camera 0 0 0 0 70").cumOps("THRUSTBLOWJOB", "camera 0 0 0 0 70")
                .cum("DOGGYCUM", "DOGGYSLOW", "DOGGYFAST").cum("PAIZURI_CUM", "PAIZURI_SLOW", "PAIZURI_FAST")
                .leave("STARTBLOWJOB", "nudge 0 0 0.2").leave("PAIZURI_START", "nudge 0 0 0.2"));

        // el: Ellie.
        SCRIPTS.put(NpcKind.ELLIE, new Script()
                .on("becomeNude", "nude")
                .on("stripDone", "end; followup")
                .on("hugDone", "menu")
                .on("hugselectedMSG2", "unlock")
                .on("cowgirlStartMSG1", "meterreset")
                .on("cowgirlStartMSG2", "meter 0.02")
                .on("cowgirlStartDone", "state COWGIRLSLOW; ui")
                .on("cowgirlfastMSG1", "meter 0.04")
                .on("cowgirlfastDone", "?released; state COWGIRLSLOW")
                .on("cowgirlfastdomMSG1", "meter 0.2")
                .on("cowgirlcumMSG4", "uioff")
                .on(new String[]{"cowgirlcumMSG6", "blackScreen"}, "blackscreen")
                .on(new String[]{"missionary_cumDone", "cowgirlcumDone", "carry_cumDone"}, "meterreset; end")
                .on("openSexUi", "ui")
                .on("missionary_slowMSG1", "meter 0.02")
                .on("missionary_fastMSG1", "meter 0.05")
                .on("missionary_startDone", "state MISSIONARY_SLOW; ui")
                .on("missionary_fastDone", "?released; state MISSIONARY_SLOW")
                .on("lipsound", "meter 0.02")
                .on("pound", "meter 0.04")
                .on("carry_slowDone", "fieldrand aa 1 4")
                .on("carry_fastDone", "?released; state CARRY_SLOW")
                .enter("CARRY_SLOW", "ui")
                // el.q()/e()/j(): 79 ticks after choosing she heads for a bed, sits 109 ticks later, lifts at carry tick 110.
                .at("HUGSELECTED", 79, "bed").at("SITDOWN", 109, "state SITDOWNIDLE").at("CARRY_INTRO", 110, "place ellie_carry")
                .enter("SITDOWNIDLE", "awaitpartner")
                .fast("COWGIRLSLOW", "COWGIRLFAST").fast("MISSIONARY_SLOW", "MISSIONARY_FAST").fast("CARRY_SLOW", "CARRY_FAST")
                .cum("COWGIRLCUM", "COWGIRLSLOW", "COWGIRLFAST").cum("MISSIONARY_CUM", "MISSIONARY_SLOW", "MISSIONARY_FAST")
                .cum("CARRY_CUM", "CARRY_SLOW", "CARRY_FAST"));

        // fn: Slime girl.
        SCRIPTS.put(NpcKind.SLIME, new Script()
                .on("sexUiOn", "ui")
                .on("bjiMSG10", "camera -0.4 -0.8 -0.2 60 -3")
                .on(new String[]{"bjiMSG11", "bjiMSG12", "doggyslowMSG1"}, "meter 0.02")
                .on(new String[]{"bjtMSG1", "doggyfastMSG1"}, "meter 0.04")
                .on("bjiDone", "state SUCKBLOWJOB; ui")
                .on("bjtDone", "state SUCKBLOWJOB")
                .on(new String[]{"bjtReady", "doggyfastReady"}, "?held; restart")
                .on("bjcMSG2", "uioff")
                .on("bjcBlackScreen", "blackscreen")
                .on(new String[]{"bjcDone", "doggyCumDone"}, "meterreset; slimebirth 2400; end")
                .on("doggyGoOnBedMSG1", "yaw npc 0")
                .on("doggyGoOnBedDone", "state WAITDOGGY; awaitpartner")
                .on("doggystartDone", "state DOGGYSLOW; ui")
                .on("doggyfastDone", "state DOGGYSLOW")
                .fast("SUCKBLOWJOB", "THRUSTBLOWJOB").fast("DOGGYSLOW", "DOGGYFAST")
                .cum("CUMBLOWJOB", "SUCKBLOWJOB", "THRUSTBLOWJOB").cum("DOGGYCUM", "DOGGYSLOW", "DOGGYFAST"));

        // fz: Bee girl.
        SCRIPTS.put(NpcKind.BEE, new Script()
                .on("resetCumPercentage", "meterreset")
                .on("sex_fastMSG1", "meter 0.04")
                .on("sex_startMSG1", "meter 0.02")
                .on("sex_fastDone", "?released; state CITIZEN_SLOW; ui")
                .on("sex_startDone", "state CITIZEN_SLOW; ui")
                .on("blackscreen", "blackscreen")
                .on("sex_cumDone", "meterreset; end")
                .on("sex_fastReady", "?held; restart")
                .fast("CITIZEN_SLOW", "CITIZEN_FAST")
                .cum("CITIZEN_CUM", "CITIZEN_SLOW", "CITIZEN_FAST"));

        // e0: Bia.
        SCRIPTS.put(NpcKind.BIA, new Script()
                .on("becomeNude", "nude")
                .on("stripDone", "end; followup")
                .on("sexUiOn", "ui")
                .on("talk_hornyDone", "state TALK_IDLE; menu")
                .on("talk_responseDone", "unlock; followup")
                .on("anal_prepareDone", "state ANAL_WAIT; meterreset")
                .on("anal_fastMSG1", "meter 0.04")
                .on(new String[]{"anal_slowMSG1", "anal_startMSG2"}, "meter 0.02")
                .on("anal_fastDone", "?released; state ANAL_SLOW; ui")
                .on("anal_startDone", "state ANAL_SLOW; ui")
                .on(new String[]{"blackScreen", "anal_cumBlackScreen"}, "blackscreen")
                .on(new String[]{"doggy_cumDone", "anal_cumDone"}, "meterreset; end")
                .on("headpatDone", "end")
                .on("sitdownDone", "state SITDOWNIDLE")
                .on("slide", "meter 0.005")
                .on("doggyMoan", "meter 0.04")
                .on("doggySwitch", "?held; state PRONE_DOGGY_HARD")
                .on("doggyReset", "?held; restart")
                .enter("PRONE_DOGGY_INTRO", "ui")
                .enter("ANAL_WAIT", "awaitpartner").enter("SITDOWNIDLE", "awaitpartner")
                .enter("ANAL_PREPARE", "model 0").enter("PRONE_DOGGY_INTRO", "model 0")
                .fast("ANAL_SLOW", "ANAL_FAST").fast("PRONE_DOGGY_INTRO", "PRONE_DOGGY_INSERT")
                .cum("ANAL_CUM", "ANAL_SLOW", "ANAL_FAST").cum("PRONE_DOGGY_CUM", "PRONE_DOGGY_SOFT", "PRONE_DOGGY_HARD"));

        // eb: Luna.
        SCRIPTS.put(NpcKind.LUNA, new Script()
                .on("paymentDone", "followup")
                .on("touch_boobs_introDone", "state TOUCH_BOOBS_SLOW; meterreset; ui")
                .on("touch_boobs_slowDone", "fieldrand ae 0 1")
                .on("addCumSlow", "meter 0.02")
                .on("addCumFast", "meter 0.04")
                .on("fastDone", "?released; state TOUCH_BOOBS_SLOW")
                .on("blackScreen", "blackscreen")
                .on("touch_boobs_cumDone", "meterreset; end")
                .on("resetGirl", "end")
                .on("sitting_introDone", "state COWGIRL_SITTING_SLOW; meterreset; ui")
                .on("sitting_slowMSG1", "meter 0.02")
                .on("sitting_fastMSG1", "meter 0.04")
                .on("sitting_fastDone", "?released; state COWGIRL_SITTING_SLOW")
                .enter("WAIT_CAT", "awaitpartner")
                .fast("TOUCH_BOOBS_SLOW", "TOUCH_BOOBS_FAST").fast("COWGIRL_SITTING_SLOW", "COWGIRL_SITTING_FAST")
                .cum("TOUCH_BOOBS_CUM", "TOUCH_BOOBS_SLOW", "TOUCH_BOOBS_FAST")
                .cum("COWGIRL_SITTING_CUM", "COWGIRL_SITTING_SLOW", "COWGIRL_SITTING_FAST"));

        // ev: Allie.
        SCRIPTS.put(NpcKind.ALLIE, new Script()
                .on(new String[]{"summonMSG8", "summon_normalDone"}, "menu")
                .on("blackscreen", "blackscreen")
                .on("deepthroat_prepareDone", "followup; meterreset")
                .on("deepthroat_fastDone", "?released; state DEEPTHROAT_SLOW")
                .on("deepthroat_startDone", "state DEEPTHROAT_SLOW")
                .on("deepthroat_fastMSG1", "ui; meter 0.04")
                .on("deepthroat_slowMSG1", "ui; meter 0.02")
                .on(new String[]{"cowgirl_cumDone", "deepthroat_cumDone"}, "end")
                .on("slowMoan", "meter 0.02")
                .on("cowgirlSlowDone", "fieldrand T 1 3")
                .on("fastMoan", "meter 0.04")
                .on("fastSwitch", "?held; fieldrand L 1 3; ?state REVERSE_COWGIRL_FAST_START; state REVERSE_COWGIRL_FAST_CONTINUES")
                .on("openSexUi", "ui")
                .fast("DEEPTHROAT_SLOW", "DEEPTHROAT_FAST").fast("REVERSE_COWGIRL_SLOW", "REVERSE_COWGIRL_FAST_START")
                .cum("DEEPTHROAT_CUM", "DEEPTHROAT_SLOW", "DEEPTHROAT_FAST")
                .cum("REVERSE_COWGIRL_CUM", "REVERSE_COWGIRL_SLOW", "REVERSE_COWGIRL_FAST_START", "REVERSE_COWGIRL_FAST_CONTINUES"));

        // ff: Kobold.
        SCRIPTS.put(NpcKind.KOBOLD, new Script()
                .on("blackScreen", "blackscreen")
                .on("paymentDone", "followup")
                .on("lipsound", "meter 0.02")
                .on("blowjobStartDone", "state SUCKBLOWJOB_BLINK; field aT 0; field a4 1; ui")
                .on("switch", "fieldrand aT 0 1")
                .on("endSwitch", "field aT 0; togglefield a4")
                .on("blowjobFastDone", "?released; state SUCKBLOWJOB_BLINK")
                .on(new String[]{"analCumDone", "blowjobCumDone"}, "end")
                .on("analStartDone", "state KOBOLD_ANAL_SLOW; ui")
                .on("analFastRapid", "?held; ?state KOBOLD_ANAL_FAST; restart")
                .on("analDone", "?state KOBOLD_ANAL_FAST; ?released; state KOBOLD_ANAL_SLOW")
                .on("analHard", "meter 0.04")
                .on("analSoft", "meter 0.02")
                .on("mating_press_startDone", "ui; state MATING_PRESS_SOFT")
                .on("mating_press_hardDone", "state MATING_PRESS_SOFT")
                .on("mating_press_softReady", "meter 0.04; ?held; state MATING_PRESS_HARD")
                .on("mating_press_hardReady", "meter 0.04; ?held; restart")
                .on("mating_press_cumDone", "end")
                .at("MATING_PRESS_CUM", 132, "koboldegg")
                .fast("SUCKBLOWJOB_BLINK", "THRUSTBLOWJOB").fast("KOBOLD_ANAL_SLOW", "KOBOLD_ANAL_FAST")
                .cum("CUMBLOWJOB", "THRUSTBLOWJOB", "SUCKBLOWJOB_BLINK").cum("KOBOLD_ANAL_CUM", "KOBOLD_ANAL_SLOW", "KOBOLD_ANAL_FAST")
                .cum("MATING_PRESS_CUM", "MATING_PRESS_HARD", "MATING_PRESS_SOFT"));

        // e3: Goblin, the offer she makes once caught.
        SCRIPTS.put(NpcKind.GOBLIN, new Script()
                .on("catchDone", "state CATCH_BJ")
                .on("catchBjDone", "state CATCH_BJ_IDLE; menu")
                .on("paizuriSwitch", "fieldrand aP 0 1")
                .on("pound", "meter 0.04")
                .on("smallPound", "meter 0.02")
                .on("paizuri_startDone", "state PAIZURI_IDLE; ui")
                .on("paizuriFastDone", "state PAIZURI_SLOW")
                .on("paizuriFastReady", "?held; state PAIZURI_FAST_CONTINUES")
                .on("paizuriFastContinuesReady", "?held; restart")
                .on("blackScreen", "blackscreen")
                .on("paizuriCumDone", "end")
                .fast("PAIZURI_IDLE", "PAIZURI_FAST").fast("PAIZURI_SLOW", "PAIZURI_FAST")
                .cum("PAIZURI_CUM", "PAIZURI_SLOW", "PAIZURI_FAST", "PAIZURI_FAST_CONTINUES"));

        // f_: Galath, only the scenes her master asks for. Her wild-encounter assault and corruption are excluded.
        SCRIPTS.put(NpcKind.GALATH, new Script()
                .on("rapeIntroDone", "state RAPE_ON_GOING")
                .on("rape_switch", "fieldrand b1 0 2")
                .on("poundRape", "meter 0.03")
                .on("corruptSwitch", "?held; state CORRUPT_FAST")
                .on("corrupt_hard", "?held; field aT 1; restart")
                .on("corrupt_hard_end", "state CORRUPT_SLOW; field aT 0")
                .on("addCum", "meter 0.03")
                .on(new String[]{"setCamCorrupt", "sexui"}, "ui")
                .on(new String[]{"blackScreenTamed", "blackScreen"}, "blackscreen")
                .at("RAPE_CUM_IDLE", 60, "end").at("CORRUPT_CUM", 30, "end")
                .cum("CORRUPT_CUM", "CORRUPT_FAST", "CORRUPT_SLOW").cum("RAPE_CUM", "RAPE_ON_GOING"));
    }

    private SceneScripts() {}
    public static Script of(NpcKind kind) { return SCRIPTS.getOrDefault(kind, EMPTY); }

    private static List<String> split(String ops) {
        return java.util.Arrays.stream(ops.split(";")).map(String::trim).filter(op -> !op.isEmpty()).toList();
    }
}
