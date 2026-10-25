package com.arn.goodmod.entity;

import com.arn.goodmod.registry.GoodcraftEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Non-sexual combat phases from f_/h8, advanced exclusively by the server. */
public final class GalathNpc extends HumanoidNpc {
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> MANGLELIE = net.minecraft.network.syncher.SynchedEntityData.defineId(GalathNpc.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> BOOST_CHARGES = net.minecraft.network.syncher.SynchedEntityData.defineId(GalathNpc.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> BOOST_COOLDOWN = net.minecraft.network.syncher.SynchedEntityData.defineId(GalathNpc.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> BOOST_RECHARGE = net.minecraft.network.syncher.SynchedEntityData.defineId(GalathNpc.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    public enum Phase { NONE, CHANGE_POSITION, SUMMON_SKELETON, ATTACK_SWORD, KNOCK_OUT_FLY, KNOCK_OUT_GROUND, KNOCK_OUT_STAND_UP }
    private final ServerBossEvent bossEvent = new ServerBossEvent(UUID.randomUUID(), getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    private final List<UUID> summonedSkeletons = new ArrayList<>();
    private Phase phase = Phase.NONE;
    private int phaseTicks;
    private Vec3 phaseOrigin = Vec3.ZERO, destination = Vec3.ZERO;
    private boolean mirrorHands;
    private EnergyBallEntity leftCharge, rightCharge;
    private BlockPos idleDestination;
    private int idleWait;
    private UUID savedTarget;
    private final GalathFlightEnergy flightEnergy = new GalathFlightEnergy();
    private boolean jumpHeld;
    /** f_.bP + L: a wild Galath brought to zero health is paralyzed on the ground instead of dying. */
    private boolean defeated;
    private UUID victor;
    public GalathNpc(EntityType<? extends GoodcraftNpc> type, Level level) { super(type, level, NpcKind.GALATH, false); }
    @Override protected void configureNpcGoals() {}
    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(MANGLELIE, ""); data.define(BOOST_CHARGES, 3); data.define(BOOST_COOLDOWN, 0); data.define(BOOST_RECHARGE, 0); }
    public int getBoostCharges() { return entityData.get(BOOST_CHARGES); }
    public int getBoostCooldown() { return entityData.get(BOOST_COOLDOWN); }
    public int getBoostRechargeTicks() { return entityData.get(BOOST_RECHARGE); }
    public boolean isFlightActive() { return getFirstPassenger() instanceof Player && (getAnimationState().equals("CONTROLLED_FLIGHT") || getAnimationState().equals("BOOST")); }
    /** bk/f_: only the associated owner may mount the actual NPC. Movement comes from vanilla authenticated input. */
    public boolean startRidingFlight(ServerPlayer player) {
        if (!canPlayerUse(player) || getOwnerUUID().filter(player.getUUID()::equals).isEmpty() || isVehicle()
                || isAnchored() || phase != Phase.NONE || !getAnimationState().equals("NULL")) return false;
        if (!player.startRiding(this, false, true)) return false;
        getNavigation().stop(); setTarget(null); setAssociatedPlayerUUID(player.getUUID()); setAnimationState("CONTROLLED_FLIGHT");
        setNoGravity(true); setDeltaMovement(getDeltaMovement().x, 0.25, getDeltaMovement().z); return true;
    }
    @Override protected net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        if (player.getMainHandItem().is(com.arn.goodmod.registry.GoodcraftItems.GALATH_COIN) || player.getOffhandItem().is(com.arn.goodmod.registry.GoodcraftItems.GALATH_COIN)) return net.minecraft.world.InteractionResult.PASS;
        if (defeated) {
            // f_.b(EntityPlayer): walking up to the paralyzed succubus ends the fight. The port skips the original's
            // forced "corruption" scene and goes straight to her surrender (GIVE_COIN).
            if (player instanceof ServerPlayer serverPlayer && phase == Phase.KNOCK_OUT_GROUND && distanceToSqr(player) < 16) surrender(serverPlayer);
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if (getOwnerUUID().isEmpty()) return net.minecraft.world.InteractionResult.PASS;
        return super.mobInteract(player, hand);
    }
    public boolean isDefeated() { return defeated; }
    private void surrender(ServerPlayer player) {
        victor = player.getUUID(); getNavigation().stop(); setTarget(null);
        phase = Phase.NONE; phaseTicks = 0; noPhysics = false;
        // f_.b(EntityPlayer): she takes the victor's facing and he is placed at her; GIVE_COIN draws him.
        setTargetYaw(player.getYRot());
        setAnimationState("GIVE_COIN");
        scene().holdForAnimation(player, position(), player.getYRot());
    }
    @Override protected boolean canAddPassenger(net.minecraft.world.entity.Entity passenger) { return !isVehicle() && passenger instanceof Player && getOwnerUUID().filter(passenger.getUUID()::equals).isPresent(); }
    public Optional<UUID> getManglelieUUID() { try { return Optional.of(UUID.fromString(entityData.get(MANGLELIE))); } catch (IllegalArgumentException ignored) { return Optional.empty(); } }
    public void setManglelieUUID(UUID uuid) { entityData.set(MANGLELIE, uuid == null ? "" : uuid.toString()); }
    public Phase getCombatPhase() { return phase; }
    public boolean isSummonedSkeleton(UUID uuid) { return summonedSkeletons.contains(uuid); }
    public void registerSummonedSkeleton(UUID uuid) { summonedSkeletons.add(uuid); }
    @Override public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) || source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)
                || source.is(net.minecraft.world.damagesource.DamageTypes.CACTUS) || source.is(net.minecraft.world.damagesource.DamageTypes.FALL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL)) return false;
        boolean voidDamage = source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD);
        if (defeated && !voidDamage) return false;
        // f_.a(LivingDeathEvent): death is cancelled. Wild, she is paralyzed; tamed, she returns to her coin.
        if (amount >= getHealth() && !voidDamage) {
            if (getOwnerUUID().isPresent()) {
                getOwnerUUID().ifPresent(owner -> com.arn.goodmod.world.GalathSummons.get(level.getServer()).remove(level.getServer(), owner));
                return true;
            }
            setHealth(1); defeated = true; setTarget(null); discardCharges();
            Vec3 impact = source.getEntity() != null ? source.getEntity().position() : position().add(random.nextDouble() - .5, 0, random.nextDouble() - .5);
            knockOut(impact);
            for (ServerPlayer viewer : level.getPlayers(viewer -> viewer.distanceToSqr(this) < 4096)) {
                viewer.sendSystemMessage(net.minecraft.network.chat.Component.literal("Galath is paralyzed! Now it's time to make her submit").withStyle(net.minecraft.ChatFormatting.YELLOW));
                viewer.sendSystemMessage(net.minecraft.network.chat.Component.literal("(Walk to her and right click her)").withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            return true;
        }
        boolean hurt = super.hurtServer(level, source, amount);
        // A boss answers whoever attacks her, including players the original's target scan skipped.
        if (hurt && getOwnerUUID().isEmpty() && source.getEntity() instanceof Player attacker && !attacker.isSpectator() && !phase.name().startsWith("KNOCK_OUT")) {
            setTarget(attacker);
            if (phase == Phase.NONE) beginPhase(Phase.CHANGE_POSITION);
        }
        return hurt;
    }
    @Override protected int calculateFallDamage(double distance, float multiplier) { return 0; }
    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); bossEvent.addPlayer(player); }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bossEvent.removePlayer(player); }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        flightEnergy.tick();
        bossEvent.setProgress(getHealth() / getMaxHealth()); bossEvent.setName(getDisplayName());
        // f_.aO: the wild succubus is a boss for everyone near her, not only while she has a target.
        bossEvent.setVisible(getOwnerUUID().isEmpty() && !defeated);
        if (getAnimationState().equals("GIVE_COIN")) { tickSurrender(server); return; }
        // An unloaded minion still exists. Removing its identity here would bypass the two-minion cap.
        summonedSkeletons.removeIf(uuid -> server.getEntity(uuid) instanceof LivingEntity living && !living.isAlive());
        if (savedTarget != null) { if (server.getEntity(savedTarget) instanceof LivingEntity living && living.isAlive()) setTarget(living); savedTarget = null; }
        if (tickControlledFlight()) return;
        if (phase.name().startsWith("KNOCK_OUT")) { tickKnockout(server); return; }
        if (isAnchored() || !getAnimationState().equals("NULL") && !getAnimationState().equals("RUN") && phase == Phase.NONE) return;
        LivingEntity target = getTarget();
        if (target instanceof Player player && (player.isSpectator() || !player.isAlive())) target = null;
        if (target == null || !target.isAlive() || distanceToSqr(target) > 2500) {
            setTarget(null);
            if (getOwnerUUID().isPresent()) {
                target = server.getEntitiesOfClass(Monster.class, getBoundingBox().inflate(7), e -> e.isAlive() && !summonedSkeletons.contains(e.getUUID()))
                        .stream().filter(getSensing()::hasLineOfSight).findFirst().orElse(null);
            } else target = server.getEntitiesOfClass(Player.class, getBoundingBox().inflate(20), p -> p.isAlive() && !p.isSpectator() && !p.isCreative()).stream().findFirst().orElse(null);
            setTarget(target);
            if (target == null) { if (phase != Phase.NONE) finishPhase(); setNoGravity(false); if (!tickAdoption(server)) tickIdle(server); return; }
            beginPhase(Phase.CHANGE_POSITION);
        }
        if (phase == Phase.NONE) beginPhase(Phase.CHANGE_POSITION);
        for (UUID uuid : List.copyOf(summonedSkeletons)) if (server.getEntity(uuid) instanceof net.minecraft.world.entity.monster.skeleton.WitherSkeleton skeleton) {
            if (skeleton.distanceToSqr(target) >= 225) { skeleton.discard(); summonedSkeletons.remove(uuid); }
            else { skeleton.setTarget(target); if (tickCount % 10 == 0) server.sendParticles(net.minecraft.core.particles.PowerParticleOption.create(net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH, 1), skeleton.getX(), skeleton.getY() + skeleton.getBbHeight() / 2, skeleton.getZ(), 5, 0.2, 0.4, 0.2, 0); }
        }
        phaseTicks++;
        setNoGravity(true);
        getLookControl().setLookAt(target, 360, 360);
        switch (phase) {
            case CHANGE_POSITION -> {
                if (phaseTicks == 1) {
                    Vec3 delta = destination.subtract(position()); Vec3 direction = delta.normalize();
                    setDeltaMovement(direction.x * 0.6, Math.max(-0.6, Math.min(0.6, delta.y * 0.6)), direction.z * 0.6);
                }
                noPhysics = phaseTicks == 1 || !level().getBlockState(blockPosition()).isAir();
                if (phaseTicks > 23) {
                    // Both source attacks have a mandatory reposition phase afterwards.
                    Phase next;
                    do { next = new Phase[]{Phase.CHANGE_POSITION, Phase.SUMMON_SKELETON, Phase.ATTACK_SWORD}[random.nextInt(3)]; }
                    while (next == Phase.SUMMON_SKELETON && (!(target instanceof Player) || summonedSkeletons.size() >= 2));
                    beginPhase(next);
                }
            }
            case SUMMON_SKELETON -> {
                setDeltaMovement(Vec3.ZERO);
                if (phaseTicks == 9) { leftCharge = createCharge(server); rightCharge = createCharge(server); }
                positionCharge(leftCharge, new Vec3(-1.049342, 2.054721355, -0.050482392));
                positionCharge(rightCharge, new Vec3(1.252226114, 1.435773253, 0.235709876));
                if (phaseTicks == 30) { launchCharge(target, leftCharge); launchCharge(target, rightCharge); leftCharge = null; rightCharge = null; }
                if (phaseTicks >= 45) beginPhase(Phase.CHANGE_POSITION);
            }
            case ATTACK_SWORD -> {
                if (phaseTicks >= 24 && phaseTicks < 32) {
                    Vec3 toward = target.position().subtract(position()).normalize();
                    Vec3 attackPos = target.getEyePosition().subtract(toward.scale(3));
                    setPos(phaseOrigin.lerp(attackPos, (phaseTicks - 24) / 8.0));
                } else if (phaseTicks >= 32 && phaseTicks < 54) {
                    Vec3 direction = target.position().subtract(position()).normalize();
                    setPos(target.position().subtract(direction.scale(1.5)));
                    if (phaseTicks == 36 || phaseTicks == 40) { target.setInvulnerableTime(0); target.hurtTime = 0; target.hurtServer(server, damageSources().mobAttack(this), 5); }
                } else if (phaseTicks == 54) {
                    setAnimationState("FLY"); setDeltaMovement(phaseOrigin.subtract(position()).normalize().scale(0.6));
                }
                if (phaseTicks > 77) beginPhase(Phase.CHANGE_POSITION);
            }
            default -> {}
        }
    }
    private void beginPhase(Phase next) {
        discardCharges();
        phase = next; phaseTicks = 0; phaseOrigin = position(); noPhysics = false; setDeltaMovement(Vec3.ZERO);
        switch (next) {
            case CHANGE_POSITION -> { destination = chooseFlightPosition(); setAnimationState("FLY"); }
            case SUMMON_SKELETON -> { mirrorHands = random.nextBoolean(); setAnimationInput("legacy.ay", mirrorHands ? 1 : 0); setAnimationState("SUMMON_SKELETON"); }
            case ATTACK_SWORD -> setAnimationState("ATTACK_SWORD");
            default -> setAnimationState(next.name());
        }
    }
    private boolean tickControlledFlight() {
        entityData.set(BOOST_CHARGES, flightEnergy.charges()); entityData.set(BOOST_COOLDOWN, flightEnergy.cooldown()); entityData.set(BOOST_RECHARGE, flightEnergy.rechargeTicks());
        if (!(getFirstPassenger() instanceof ServerPlayer rider)) {
            if (getAnimationState().equals("CONTROLLED_FLIGHT") || getAnimationState().equals("BOOST")) { setAnimationState("NULL"); setAssociatedPlayerUUID(null); setNoGravity(false); }
            jumpHeld = false; return false;
        }
        if (!rider.isAlive() || rider.isSpectator() || getOwnerUUID().filter(rider.getUUID()::equals).isEmpty()) { ejectPassengers(); return false; }
        getNavigation().stop(); setTarget(null); setNoGravity(true); setYRot(rider.getYRot()); yBodyRot = getYRot(); setYHeadRot(getYRot());
        var input = rider.getLastClientInput();
        if (input.jump() && !jumpHeld && getAnimationState().equals("CONTROLLED_FLIGHT") && flightEnergy.tryBoost()) setAnimationState("BOOST");
        jumpHeld = input.jump();
        if (getAnimationState().equals("BOOST")) {
            // f_.aw: wait13 ticks, then speed6 multiplied by0.94 on each subsequent tick.
            if (getActionTicks() >= 13) setDeltaMovement(rider.getLookAngle().normalize().scale(6 * Math.pow(0.94, getActionTicks() - 13)));
        } else if (getAnimationState().equals("CONTROLLED_FLIGHT")) {
            // ct is sent by flapControlled at0.0417s in a1.20833s clip. Crossing math keeps noninteger cadence.
            double marker = 0.0417 * 20, duration = 1.20833 * 20;
            if (Math.floor((getActionTicks() - marker) / duration) != Math.floor((getActionTicks() - 1 - marker) / duration)) {
                double forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0), left = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
                double length = Math.max(1, Math.sqrt(forward * forward + left * left)); forward /= length; left /= length;
                double yaw = Math.toRadians(rider.getYRot());
                Vec3 direction = rider.getLookAngle().scale(forward).add(left * Math.cos(yaw), 0, left * Math.sin(yaw));
                if (forward != 0 || left != 0) setDeltaMovement(getDeltaMovement().x + direction.x, direction.y / 2, getDeltaMovement().z + direction.z);
            }
        }
        resetFallDistance(); rider.resetFallDistance();
        entityData.set(BOOST_CHARGES, flightEnergy.charges()); entityData.set(BOOST_COOLDOWN, flightEnergy.cooldown()); entityData.set(BOOST_RECHARGE, flightEnergy.rechargeTicks());
        return true;
    }
    private Vec3 chooseFlightPosition() {
        if (getTarget() == null) return position();
        BlockPos target = getTarget().blockPosition(); List<BlockPos> open = new ArrayList<>(), roomy = new ArrayList<>();
        boolean grounded = !level().isEmptyBlock(blockPosition().below());
        for (int x = -10; x < 10; x++) for (int y = -10; y < 10; y++) for (int z = -10; z < 10; z++) {
            BlockPos candidate = target.offset(x, y, z);
            if (grounded && candidate.getY() <= getBlockY() || !level().isEmptyBlock(candidate) || !level().isEmptyBlock(candidate.above()) || !level().isEmptyBlock(candidate.above(2))) continue;
            Vec3 point = Vec3.atLowerCornerOf(candidate);
            if (level().clip(new ClipContext(position(), point, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this)).getType() != HitResult.Type.MISS) continue;
            BlockPos below = candidate.below(); while (below.getY() > level().getMinY() && level().isEmptyBlock(below)) below = below.below();
            if (!level().getFluidState(below).isEmpty()) continue;
            open.add(candidate);
            if (distanceToSqr(point) < 9 || getTarget().distanceToSqr(point) < 25 || !level().isEmptyBlock(candidate.below()) || !level().isEmptyBlock(candidate.below(2))) continue;
            int count = 0; for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 3; dy++) if (level().isEmptyBlock(candidate.offset(dx, dy, dz))) count++;
            if (count >= 25) roomy.add(candidate);
        }
        List<BlockPos> candidates = roomy.isEmpty() ? open : roomy;
        return candidates.isEmpty() ? getTarget().position().add(random.nextDouble() * 20 - 10, random.nextDouble() * 10, random.nextDouble() * 20 - 10) : Vec3.atLowerCornerOf(candidates.get(random.nextInt(candidates.size())));
    }
    private EnergyBallEntity createCharge(ServerLevel level) {
        var ball = GoodcraftEntities.ENERGY_BALL.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        if (ball != null) { ball.setCaster(this); ball.setCharging(true); ball.setVisualScale(0); ball.setPos(position()); level.addFreshEntity(ball); }
        return ball;
    }
    private void positionCharge(EnergyBallEntity ball, Vec3 offset) {
        if (ball == null || ball.isRemoved()) return;
        if (mirrorHands) offset = new Vec3(-offset.x, offset.y, offset.z);
        offset = offset.yRot((float)Math.toRadians(-180 - yBodyRot));
        ball.setPos(position().add(offset)); ball.setVisualScale(Math.min(1, (phaseTicks - 9) / 21F));
    }
    private void launchCharge(LivingEntity target, EnergyBallEntity ball) {
        if (ball == null || ball.isRemoved()) return;
        Vec3 direction = target.position().subtract(ball.position()).normalize().add(random.nextDouble() * 0.3, random.nextDouble() * 0.3, random.nextDouble() * 0.3).normalize().scale(0.4);
        ball.setCharging(false); ball.setFlightVelocity(direction); ball.setVisualScale(1);
    }
    private void discardCharges() { if (leftCharge != null) leftCharge.discard(); if (rightCharge != null) rightCharge.discard(); leftCharge = null; rightCharge = null; }
    private void finishPhase() { discardCharges(); phase = Phase.NONE; phaseTicks = 0; noPhysics = false; setDeltaMovement(Vec3.ZERO); setAnimationState("NULL"); }
    private void tickIdle(ServerLevel server) {
        Player owner = getOwnerPlayer().orElse(null);
        if (owner != null && distanceToSqr(owner) < 4) { getNavigation().stop(); return; }
        if (idleDestination == null || idleDestination.distSqr(blockPosition()) > 1800 || idleWait > 175) {
            int dx = random.nextInt(10) * (random.nextBoolean() ? 1 : -1), dz = random.nextInt(10) * (random.nextBoolean() ? 1 : -1);
            int x = getBlockX() + dx, z = getBlockZ() + dz;
            int y = server.dimension() == Level.NETHER ? (int)Math.ceil(getY()) : server.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            idleDestination = new BlockPos(x, y, z); idleWait = 0;
        }
        if (idleDestination.distSqr(blockPosition()) > 4) getNavigation().moveTo(idleDestination.getX(), idleDestination.getY(), idleDestination.getZ(), 0.35);
        else idleWait++;
    }
    private boolean tickAdoption(ServerLevel server) {
        if (!onGround() || getManglelieUUID().isPresent()) return false;
        ManglelieNpc child = server.getEntitiesOfClass(ManglelieNpc.class, getBoundingBox().inflate(15),
                npc -> npc.isAlive() && npc.getMotherUUID().isEmpty()).stream().findFirst().orElse(null);
        if (child == null) { if (getAnimationState().equals("RUN")) setAnimationState("NULL"); return false; }
        getLookControl().setLookAt(child, 360, 360);
        if (distanceToSqr(child) <= 3.65 * 3.65) {
            getNavigation().stop(); setDeltaMovement(Vec3.ZERO); setTargetYaw(getYRot());
            setManglelieUUID(child.getUUID()); child.setMotherUUID(getUUID());
            setUsingCombinedModel(true); setAnimationState("HUG_MANG"); setAnchored(true);
            child.setAnimationState("RIDE_MOMMY_HEAD");
        } else { getNavigation().moveTo(child, 0.65); setAnimationState("RUN"); }
        return true;
    }
    @Override public void remove(net.minecraft.world.entity.Entity.RemovalReason reason) { discardCharges(); super.remove(reason); }
    public void knockOut(Vec3 impact) {
        if (phase.name().startsWith("KNOCK_OUT")) return;
        beginPhase(Phase.KNOCK_OUT_FLY); setNoGravity(false);
        Vec3 away = position().subtract(impact).normalize(); setDeltaMovement(away.x, 1, away.z); getNavigation().stop();
    }
    private void tickKnockout(ServerLevel server) {
        phaseTicks++;
        if (phase == Phase.KNOCK_OUT_FLY && !level().getFluidState(blockPosition()).isEmpty()) {
            // f_.T(): knocked into lava or water, she lands on an obsidian raft at its surface.
            BlockPos surface = blockPosition();
            while (!level().getFluidState(surface.above()).isEmpty() && surface.getY() < level().getMaxY()) surface = surface.above();
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) server.setBlockAndUpdate(surface.offset(x, 0, z), net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState());
            teleportTo(surface.getX() + .5, surface.getY() + 1, surface.getZ() + .5); setDeltaMovement(Vec3.ZERO);
            beginPhase(Phase.KNOCK_OUT_GROUND);
            return;
        }
        if (phase == Phase.KNOCK_OUT_FLY && onGround()) beginPhase(Phase.KNOCK_OUT_GROUND);
        if (defeated) { if (phase != Phase.KNOCK_OUT_FLY) setDeltaMovement(0, getDeltaMovement().y, 0); return; }
        if (phase == Phase.KNOCK_OUT_GROUND && phaseTicks >= 50) beginPhase(Phase.KNOCK_OUT_STAND_UP);
        if (phase == Phase.KNOCK_OUT_STAND_UP && phaseTicks == 39) {
            setNoGravity(true); setDeltaMovement(0, 0.6, 0);
            for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2), e -> !(e instanceof GalathNpc))) {
                Vec3 away = living.position().subtract(position()).normalize(); living.setDeltaMovement(away.x, 1, away.z);
                living.hurtServer(server, damageSources().mobAttack(this), 0.5F);
            }
        }
        if (phase == Phase.KNOCK_OUT_STAND_UP && phaseTicks >= 58) finishPhase();
    }
    /** GIVE_COIN: at tick 95 she hands the victor her coin (f_.X/cc), at 140 she returns into it (f_.ap). */
    private void tickSurrender(ServerLevel server) {
        Player player = victor == null ? null : server.getPlayerByUUID(victor);
        if (player != null) getLookControl().setLookAt(player, 360, 360);
        if (getActionTicks() == 95 && player != null) {
            ItemStack held = player.getMainHandItem();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(com.arn.goodmod.registry.GoodcraftItems.GALATH_COIN));
            if (!held.isEmpty()) player.getInventory().placeItemBackInInventory(held, net.minecraft.util.Prediction.SERVER_ONLY);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Defeating a succubus makes her accept the victor as her master, granting him a coin to which her soul is bound. Using the coin summons her, offering services on demand. If her master uses the coin on her or goes too far, she returns to the coin")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        if (getActionTicks() >= 139) { // GoodcraftNpc resets GIVE_COIN to NULL at 140.
            server.sendParticles(net.minecraft.core.particles.PowerParticleOption.create(net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH, 1), getX(), getY() + 1, getZ(), 60, 0.4, 0.8, 0.4, 0.02);
            discard();
        }
    }

    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out); out.putString("combatPhase", phase.name()); out.putInt("phaseTicks", phaseTicks);
        out.putBoolean("defeated", defeated);
        if (victor != null) out.store("victor", net.minecraft.core.UUIDUtil.CODEC, victor);
        out.store("phaseOrigin", Vec3.CODEC, phaseOrigin); out.store("phaseDestination", Vec3.CODEC, destination);
        out.putBoolean("mirrorHands", mirrorHands); out.putString("manglelieUUID", entityData.get(MANGLELIE));
        out.putInt("flightCharges", flightEnergy.charges()); out.putInt("flightCooldown", flightEnergy.cooldown()); out.putInt("flightRecharge", flightEnergy.rechargeTicks());
        out.store("summonedSkeletons", net.minecraft.core.UUIDUtil.CODEC.listOf(), summonedSkeletons);
        if (getTarget() != null) out.store("combatTarget", net.minecraft.core.UUIDUtil.CODEC, getTarget().getUUID());
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        flightEnergy.restore(in.getIntOr("flightCharges", 3), in.getIntOr("flightCooldown", 0), in.getIntOr("flightRecharge", 0));
        try { phase = Phase.valueOf(in.getStringOr("combatPhase", "NONE")); } catch (IllegalArgumentException ignored) { phase = Phase.NONE; }
        phaseTicks = Math.clamp(in.getIntOr("phaseTicks", 0), 0, 1200);
        phaseOrigin = in.read("phaseOrigin", Vec3.CODEC).orElse(position()); destination = in.read("phaseDestination", Vec3.CODEC).orElse(position());
        mirrorHands = in.getBooleanOr("mirrorHands", false); setAnimationInput("legacy.ay", mirrorHands ? 1 : 0);
        defeated = in.getBooleanOr("defeated", false); victor = in.read("victor", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        savedTarget = in.read("combatTarget", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        summonedSkeletons.clear(); summonedSkeletons.addAll(in.read("summonedSkeletons", net.minecraft.core.UUIDUtil.CODEC.listOf()).orElse(List.of()));
        try { setManglelieUUID(UUID.fromString(in.getStringOr("manglelieUUID", ""))); } catch (IllegalArgumentException ignored) { setManglelieUUID(null); }
        // Charging balls are intentionally transient. Restart that attack after repositioning on load.
        if (phase == Phase.SUMMON_SKELETON) { phase = Phase.NONE; phaseTicks = 0; setAnimationState("NULL"); }
        if (phase == Phase.NONE && (getAnimationState().equals("FLY") || getAnimationState().equals("ATTACK_SWORD") || getAnimationState().startsWith("KNOCK_OUT"))) setAnimationState("NULL");
        setNoGravity(phase != Phase.NONE && !phase.name().startsWith("KNOCK_OUT"));
    }
}
