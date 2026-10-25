package com.arn.goodmod.scene;

import com.arn.goodmod.entity.AllieNpc;
import com.arn.goodmod.entity.GalathNpc;
import com.arn.goodmod.entity.GoodcraftNpc;
import com.arn.goodmod.entity.SlimeNpc;
import com.arn.goodmod.registry.GoodcraftEffects;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The action lists and prices each original girl passed to the m overlay from her b(EntityPlayer). */
public final class SceneMenus {
    /** {@code key} is the original action string; {@code label} a translation key or the original literal. */
    public record Action(String key, String label, ItemStack cost) {
        public Action { cost = cost == null ? ItemStack.EMPTY : cost.copy(); }
        static Action free(String key) { return new Action(key, key, ItemStack.EMPTY); }
        static Action paid(String key, ItemStack cost) { return new Action(key, key, cost); }
    }

    private SceneMenus() {}

    public static boolean horny(GoodcraftNpc npc) { return npc.hasEffect(GoodcraftEffects.TRANSFORMATION); }
    private static boolean dressed(GoodcraftNpc npc) { return npc.getModelVariant() != 0; }

    /** Whether the overlay shows the follow/home/equipment column (the m constructor's boolean). */
    public static boolean showsCompanionIcons(GoodcraftNpc npc) {
        return npc.supportsEquipment() && !npc.isPlayerForm() && switch (npc.getNpcKind()) {
            case JENNY, ELLIE, BIA, LUNA -> true;
            default -> false;
        };
    }

    public static List<Action> actions(GoodcraftNpc npc, Player player) {
        List<Action> actions = new ArrayList<>();
        SceneDirector scene = npc.scene();
        // A girl busy with someone else, or mid-animation, offered nothing ("x.dialogue.busy").
        if (scene.partner().filter(id -> !id.equals(player.getUUID())).isPresent()) return actions;
        String state = npc.getAnimationState();
        switch (npc.getNpcKind()) {
            case JENNY -> {
                if (!state.equals("NULL") || scene.isActive()) break;
                boolean free = horny(npc);
                actions.add(Action.paid("action.names.blowjob", free ? null : new ItemStack(Items.EMERALD, 3)));
                actions.add(Action.paid("action.names.boobjob", free ? null : new ItemStack(Items.ENDER_PEARL, 2)));
                actions.add(Action.paid("action.names.doggy", free ? null : new ItemStack(Items.DIAMOND, 2)));
                actions.add(dressed(npc) ? Action.paid("action.names.strip", free ? null : new ItemStack(Items.GOLD_INGOT, 1))
                        : Action.free("action.names.dressup"));
            }
            case ELLIE -> {
                if (!state.equals("NULL") || scene.isActive()) break;
                // The original needed her horny potion dash for these; the port offers them directly too.
                actions.add(dressed(npc) ? Action.free("Face fuck") : Action.free("action.names.dressup"));
                actions.add(Action.free("action.names.cowgirl")); actions.add(Action.free("action.names.missionary"));
            }
            case BIA -> {
                if (state.equals("TALK_IDLE") && scene.partner().filter(player.getUUID()::equals).isPresent()) {
                    actions.add(Action.free("action.names.anal")); actions.add(Action.free("doggy"));
                    break;
                }
                if (!state.equals("NULL") || scene.isActive()) break;
                actions.add(Action.free(dressed(npc) ? "action.names.strip" : "action.names.dressup"));
                actions.add(Action.free("action.names.talk"));
                actions.add(Action.free("action.names.headpat"));
                actions.add(Action.free("action.names.anal")); actions.add(Action.free("doggy"));
            }
            case LUNA -> {
                if (!state.equals("NULL") || scene.isActive()) break;
                actions.add(Action.paid("action.names.sex", new ItemStack(Items.COD, 3)));
                actions.add(Action.paid("action.names.touchboobs", new ItemStack(Items.SALMON, 2)));
                actions.add(Action.free("action.names.headpat"));
            }
            case ALLIE -> {
                if (npc instanceof AllieNpc allie && player instanceof net.minecraft.server.level.ServerPlayer server && allie.canRequestWish(server)) {
                    actions.add(Action.free("action.names.makemerichallie"));
                    actions.add(Action.free("action.names.deepthroat"));
                    actions.add(Action.free("Reverse cowgirl"));
                }
            }
            case KOBOLD -> {
                if (!state.equals("NULL") || scene.isActive()) break;
                boolean master = npc.getOwnerUUID().filter(player.getUUID()::equals).isPresent();
                boolean free = master || horny(npc);
                actions.add(Action.paid("anal", free ? null : new ItemStack(Items.GOLD_INGOT, 3)));
                actions.add(Action.paid("oral", free ? null : new ItemStack(Items.IRON_PICKAXE)));
                if (master) actions.add(Action.free("mating"));
            }
            case GOBLIN -> {
                if (!(npc instanceof com.arn.goodmod.entity.GoblinNpc goblin) || goblin.isLairResident()) break;
                // e3 catchBjDone: a caught thief offers herself or the stolen stack back.
                if (state.equals("CATCH_BJ_IDLE") && scene.partner().filter(player.getUUID()::equals).isPresent()) {
                    actions.add(Action.free("use her"));
                    if (goblin.isThief()) actions.add(Action.free("take ur stuff back"));
                    break;
                }
                if (!state.equals("NULL") || scene.isActive()) break;
                actions.add(Action.free("use her"));
            }
            case GALATH -> {
                if (!(npc instanceof GalathNpc galath) || galath.getOwnerUUID().filter(player.getUUID()::equals).isEmpty()) break;
                if (!state.equals("NULL") || scene.isActive() || galath.getCombatPhase() != GalathNpc.Phase.NONE) break;
                if (player.onGround()) { actions.add(Action.free("cowgirl")); actions.add(Action.free("anal")); }
                actions.add(Action.free("ride"));
            }
            case SLIME -> {
                // fn grabbed whoever touched her once horny; the port asks first.
                if (!(npc instanceof SlimeNpc) || !state.equals("NULL") || scene.isActive()) break;
                actions.add(Action.free("action.names.blowjob"));
                actions.add(Action.free("action.names.doggy"));
            }
            case BEE -> {
                // fz.c(): a wild bee girl is ready again 4800 ticks after her last time, or at once on the potion.
                if (!(npc instanceof com.arn.goodmod.entity.BeeNpc bee) || !state.equals("NULL") || scene.isActive()) break;
                if (bee.isReady() || horny(npc)) actions.add(Action.free("action.names.sex"));
            }
            default -> {}
        }
        return actions;
    }
}
