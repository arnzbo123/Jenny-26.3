package com.arn.goodmod.entity;

import com.arn.goodmod.registry.GoodcraftEntities;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * e3: the lair queen sits on her throne (aX) and every 32000 ticks sends a thief to a player within 100 blocks who
 * carries gold. The thief takes one gold stack and runs (J()); caught within 3.5 blocks she offers herself or the
 * stolen stack back (CATCH, CATCH_BJ_IDLE), and is thrown away when he takes his things back (START_THROWING, THROWN).
 */
public final class GoblinNpc extends HumanoidNpc {
    /** e3.ag: what a thief goes for. */
    private static final Set<Item> GOLD = Set.of(Items.GOLDEN_HOE, Items.GOLDEN_HORSE_ARMOR, Items.GOLD_INGOT, Items.GOLDEN_APPLE, Items.GOLDEN_AXE,
            Items.GOLDEN_SHOVEL, Items.GOLDEN_PICKAXE, Items.GOLDEN_SWORD, Items.GOLDEN_CARROT, Items.GOLDEN_HELMET, Items.GOLDEN_BOOTS,
            Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLD_NUGGET, Items.GOLD_BLOCK, Items.GOLD_ORE);
    private static final int ROB_INTERVAL = 32000;
    private boolean lairResident, used;
    private ItemStack stolen = ItemStack.EMPTY;
    private int robTicks, thrownTicks;
    public GoblinNpc(EntityType<? extends GoodcraftNpc> type, Level level) { super(type, level, NpcKind.GOBLIN, false); if (!level.isClientSide()) setModelCode(NpcAppearance.randomCode(NpcKind.GOBLIN, random)); }
    public boolean isLairResident() { return lairResident; }
    public void setLairResident(boolean resident) { lairResident = resident; }
    public boolean isThief() { return !stolen.isEmpty(); }
    public void setStolen(ItemStack stack) { stolen = stack.copy(); }
    /** e3.L(): the stolen stack goes back to whoever caught her. */
    public ItemStack takeStolen() { ItemStack stack = stolen; stolen = ItemStack.EMPTY; return stack; }

    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (lairResident) return InteractionResult.PASS;
        if (getAnimationState().equals("RUN")) {
            if (player instanceof ServerPlayer server) {
                if (distanceTo(player) > 3.5) server.sendOverlayMessage(Component.literal("get a bit closer..."));
                else if (canPlayerUse(server)) scene().catchThief(server);
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        String state = getAnimationState();
        if (lairResident) tickQueen(server, state);
        if (state.equals("RUN") && onGround() && tickCount % 20 == 0) flee(server);
        if (state.equals("PAIZURI_CUM")) used = true;
        if (state.equals("START_THROWING")) {
            var clip = com.arn.goodmod.animation.GoodcraftAnimations.actionClip(this);
            if (clip == null || getActionTicks() >= clip.length() * 20) {
                Player thrower = scene().partner().map(server::getPlayerByUUID).orElse(null);
                scene().end();
                setAnimationState("THROWN"); thrownTicks = 0;
                Vec3 away = thrower == null ? Vec3.ZERO : Vec3.directionFromRotation(0, thrower.getYRot());
                setDeltaMovement(away.x * 0.8, 0.5, away.z * 0.8);
            }
        }
        // e3.H(): once the thrown thief lands she is gone; e3.D(): so is one who was used instead.
        if (state.equals("THROWN") && (onGround() || isInWater()) && ++thrownTicks > 20 || used && state.equals("NULL") && !scene().isActive()) {
            server.sendParticles(ParticleTypes.POOF, getX(), getY() + .5, getZ(), 12, .3, .4, .3, .02);
            discard();
        }
    }

    /** aX: sit on the throne; B(): rob a nearby player carrying gold. */
    private void tickQueen(ServerLevel server, String state) {
        if (state.equals("NULL") && !scene().isActive()) {
            setAnchored(true); teleportTo(getHomePosition().getX() + .5, getHomePosition().getY(), getHomePosition().getZ() + .5);
            setAnimationState("SIT");
        }
        if (!getAnimationState().equals("SIT") || ++robTicks < ROB_INTERVAL) return;
        Player player = server.getNearestPlayer(this, 3000);
        if (!(player instanceof ServerPlayer target) || !target.onGround() || target.getAbilities().flying || target.isCreative()) return;
        if (Math.hypot(target.getX() - getX(), target.getZ() - getZ()) > 100) return;
        var inventory = target.getInventory();
        int slot = -1;
        for (int i = 0; i < inventory.getContainerSize() && slot < 0; i++) if (GOLD.contains(inventory.getItem(i).getItem())) slot = i;
        if (slot < 0) return;
        robTicks = 0;
        GoblinNpc thief = (GoblinNpc)GoodcraftEntities.GOBLIN.create(server, EntitySpawnReason.MOB_SUMMONED);
        if (thief == null) return;
        Vec3 behind = target.position().add(com.arn.goodmod.scene.SceneDirector.rotate(0, 0, -0.2, target.getYHeadRot()));
        thief.setPos(behind.x, behind.y, behind.z);
        thief.setModelCode(getModelCode());
        thief.stolen = inventory.removeItemNoUpdate(slot);
        inventory.setChanged();
        thief.setAnimationState("RUN");
        if (server.addFreshEntity(thief)) target.sendSystemMessage(Component.literal(String.format("<%s> I got your %s hehe~", thief.getName().getString(), thief.stolen.getHoverName().getString())));
        else inventory.add(thief.stolen);
    }

    /** e3.J(): run away from the nearest player, trying shorter dashes until a path exists. */
    private void flee(ServerLevel server) {
        Player player = server.getNearestPlayer(this, 100);
        if (player == null) { getNavigation().stop(); return; }
        Vec3 offset = position().subtract(player.position());
        double total = Math.abs(offset.x) + Math.abs(offset.z);
        if (total < 1.0E-4) offset = new Vec3(random.nextDouble() - .5, 0, random.nextDouble() - .5);
        for (double distance = 20; distance > 0; distance--) {
            Vec3 target = position().add(Math.signum(offset.x) * Math.abs(offset.x) / Math.max(total, 1.0E-4) * distance, 0,
                    Math.signum(offset.z) * Math.abs(offset.z) / Math.max(total, 1.0E-4) * distance);
            getNavigation().stop();
            if (getNavigation().moveTo(target.x, target.y, target.z, 0.825)) return;
        }
    }

    @Override public boolean removeWhenFarAway(double distanceSqr) { return false; }
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out); out.putBoolean("lairResident", lairResident); out.putInt("robTicks", robTicks);
        out.store("stolen", ItemStack.OPTIONAL_CODEC, stolen);
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in); lairResident = in.getBooleanOr("lairResident", false); robTicks = in.getIntOr("robTicks", 0);
        stolen = in.read("stolen", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }
}
