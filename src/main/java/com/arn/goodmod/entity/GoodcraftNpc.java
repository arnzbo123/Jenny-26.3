package com.arn.goodmod.entity;

import com.arn.goodmod.animation.GoodcraftAnimations;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Shared state reconstructed from em/e2/ei. This class uses tracked values, vanilla
 * equipment synchronization and slot-preserving persistence instead of global entity lists.
 */
public abstract class GoodcraftNpc extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(GoodcraftNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ASSOCIATED_PLAYER = SynchedEntityData.defineId(GoodcraftNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ACTION = SynchedEntityData.defineId(GoodcraftNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> MODEL = SynchedEntityData.defineId(GoodcraftNpc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(GoodcraftNpc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HELD_WEAPON = SynchedEntityData.defineId(GoodcraftNpc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MOVEMENT = SynchedEntityData.defineId(GoodcraftNpc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ANCHORED = SynchedEntityData.defineId(GoodcraftNpc.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> COMBINED = SynchedEntityData.defineId(GoodcraftNpc.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> TARGET_YAW = SynchedEntityData.defineId(GoodcraftNpc.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> MODEL_CODE = SynchedEntityData.defineId(GoodcraftNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<ItemStack> SUMMONING_STACK = SynchedEntityData.defineId(GoodcraftNpc.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<String> VISUAL_DATA = SynchedEntityData.defineId(GoodcraftNpc.class, EntityDataSerializers.STRING);
    // em.y (playerSheHasSexWith), ds.c (the partner's meter), ds.d (meter shown), d3.c (input locked) and a1 restarts.
    private static final EntityDataAccessor<String> SCENE_PARTNER = SynchedEntityData.defineId(GoodcraftNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> AROUSAL = SynchedEntityData.defineId(GoodcraftNpc.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> SCENE_UI = SynchedEntityData.defineId(GoodcraftNpc.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SCENE_LOCK = SynchedEntityData.defineId(GoodcraftNpc.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ACTION_EPOCH = SynchedEntityData.defineId(GoodcraftNpc.class, EntityDataSerializers.INT);
    /** d_.a(): the stack the girl holds up at her itemRenderer bone while PAYMENT plays. */
    private static final EntityDataAccessor<ItemStack> PAYMENT_ITEM = SynchedEntityData.defineId(GoodcraftNpc.class, EntityDataSerializers.ITEM_STACK);
    private static final EquipmentSlot[] EQUIPMENT = { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
    private final NpcKind kind;
    private final boolean playerForm;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final SimpleContainer equipmentInventory = new SimpleContainer(7);
    private final SimpleContainer storageInventory = new SimpleContainer(27);
    private final Map<String, Double> animationInputs = new LinkedHashMap<>();
    private final Map<String, Boolean> boneVisibility = new LinkedHashMap<>();
    private final Map<String, Float> boneScales = new LinkedHashMap<>();
    private BlockPos homePosition = BlockPos.ZERO;
    private boolean homeInitialized;
    private int actionTicks;
    private String previousAction = "NULL";
    private HomePearl homePearl;
    private com.arn.goodmod.scene.SceneDirector scene;
    /** Client only: the last action epoch the action controller restarted for. */
    public int clientActionEpoch;

    protected GoodcraftNpc(EntityType<? extends GoodcraftNpc> type, Level level, NpcKind kind, boolean playerForm) {
        super(type, level);
        this.kind = kind;
        this.playerForm = playerForm;
        if (supportsEquipment() && !playerForm) {
            equipmentInventory.setItem(0, new ItemStack(Items.IRON_SWORD));
            equipmentInventory.setItem(1, new ItemStack(Items.BOW));
        }
        if (getNavigation() instanceof GroundPathNavigation ground) ground.setCanOpenDoors(true);
        configureNpcGoals();
        if (playerForm) { setNoAi(true); setNoGravity(true); noPhysics = true; }
    }

    public static AttributeSupplier.Builder createAttributes(NpcKind kind) {
        return createMobAttributes().add(Attributes.MAX_HEALTH, kind.maxHealth())
                .add(Attributes.MOVEMENT_SPEED, kind.movementSpeed()).add(Attributes.FOLLOW_RANGE, kind.followRange())
                .add(Attributes.ATTACK_DAMAGE, 1).add(Attributes.ATTACK_SPEED, 4)
                .add(Attributes.TEMPT_RANGE, 10)
                .add(Attributes.FLYING_SPEED, kind == NpcKind.BEE ? 0.4 : 0.6);
    }

    // Mob calls this before the kind is assigned; install goals after construction instead.
    @Override protected void registerGoals() {}

    protected void configureNpcGoals() {
        if (playerForm || kind == NpcKind.SLIME) return;
        goalSelector.addGoal(0, new FloatGoal(this));
        if (supportsEquipment()) goalSelector.addGoal(0, new CompanionCombatGoal(this));
        goalSelector.addGoal(1, new NpcMovementGoal(this));
        goalSelector.addGoal(2, new TemptGoal(this, 0.4, stack -> stack.is(Items.EMERALD)
                || stack.is(Items.DIAMOND) || stack.is(Items.GOLD_INGOT) || stack.is(Items.ENDER_PEARL), false) {
            @Override public boolean canUse() { return !isAnchored() && getAnimationState().equals("NULL") && super.canUse(); }
            @Override public boolean canContinueToUse() { return !isAnchored() && getAnimationState().equals("NULL") && super.canContinueToUse(); }
        });
        if (getNavigation() instanceof GroundPathNavigation) goalSelector.addGoal(3, new OpenDoorGoal(this, true));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.35) {
            @Override public boolean canUse() { return getMovementMode() == MovementMode.WANDER && !isAnchored() && !scene().isActive() && super.canUse(); }
            @Override public boolean canContinueToUse() { return getMovementMode() == MovementMode.WANDER && !isAnchored() && super.canContinueToUse(); }
        });
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, ""); builder.define(ASSOCIATED_PLAYER, ""); builder.define(ACTION, "NULL");
        // em defaults currentModel to 1 (dressed); index 0 is the nude model.
        builder.define(MODEL, 1); builder.define(VARIANT, 0); builder.define(MOVEMENT, MovementMode.WANDER.ordinal());
        builder.define(HELD_WEAPON, 0);
        builder.define(ANCHORED, false); builder.define(COMBINED, false); builder.define(TARGET_YAW, 0F);
        builder.define(MODEL_CODE, ""); builder.define(SUMMONING_STACK, ItemStack.EMPTY);
        builder.define(VISUAL_DATA, "{}");
        builder.define(SCENE_PARTNER, ""); builder.define(AROUSAL, 0F); builder.define(SCENE_UI, false);
        builder.define(SCENE_LOCK, false); builder.define(ACTION_EPOCH, 0); builder.define(PAYMENT_ITEM, ItemStack.EMPTY);
    }

    public NpcKind getNpcKind() { return kind; }
    public boolean isPlayerForm() { return playerForm; }
    public boolean supportsEquipment() { return playerForm || kind.supportsEquipment(); }
    public boolean supportsStorage() { return !playerForm && kind.supportsStorage(); }
    public boolean supportsFishingRod() { return kind == NpcKind.LUNA; }
    public SimpleContainer getEquipmentInventory() { return equipmentInventory; }
    public SimpleContainer getStorageInventory() { return storageInventory; }
    public Optional<UUID> getOwnerUUID() { return parseUuid(entityData.get(OWNER)); }
    public void setOwnerUUID(UUID uuid) { entityData.set(OWNER, uuid == null ? "" : uuid.toString()); }
    public Optional<UUID> getAssociatedPlayerUUID() { return parseUuid(entityData.get(ASSOCIATED_PLAYER)); }
    public void setAssociatedPlayerUUID(UUID uuid) { entityData.set(ASSOCIATED_PLAYER, uuid == null ? "" : uuid.toString()); }
    public Optional<Player> getOwnerPlayer() { return getOwnerUUID().map(level()::getPlayerByUUID); }
    public String getAnimationState() { return entityData.get(ACTION); }
    public void setAnimationState(String state) {
        String next = state == null ? "NULL" : state;
        if (next.length() > 128 || !next.matches("[A-Z0-9_]+")) throw new IllegalArgumentException("Invalid action identifier");
        if (next.equals("ATTACK") && !getAnimationState().equals("NULL")) return;
        if (!next.equals(getAnimationState())) { entityData.set(ACTION, next); actionTicks = 0; previousAction = next; }
    }
    /** a1/N(): replay the current clip from its start on every client. */
    public void restartAction() { actionTicks = 0; entityData.set(ACTION_EPOCH, entityData.get(ACTION_EPOCH) + 1); }
    public int getActionEpoch() { return entityData.get(ACTION_EPOCH); }
    public void skipActionTicks(int ticks) { actionTicks = Math.max(actionTicks, ticks); }
    public com.arn.goodmod.scene.SceneDirector scene() { if (scene == null) scene = new com.arn.goodmod.scene.SceneDirector(this); return scene; }
    public Optional<UUID> getScenePartnerUUID() { return parseUuid(entityData.get(SCENE_PARTNER)); }
    public void setScenePartnerUUID(UUID uuid) { entityData.set(SCENE_PARTNER, uuid == null ? "" : uuid.toString()); }
    public float getArousal() { return entityData.get(AROUSAL); }
    public void setArousal(float value) { entityData.set(AROUSAL, Math.clamp(Float.isFinite(value) ? value : 0, 0, 1)); }
    public boolean isSceneUiShown() { return entityData.get(SCENE_UI); }
    public void setSceneUi(boolean shown) { entityData.set(SCENE_UI, shown); }
    public boolean isSceneLocked() { return entityData.get(SCENE_LOCK); }
    public ItemStack getPaymentItem() { return entityData.get(PAYMENT_ITEM); }
    public void setPaymentItem(ItemStack stack) { entityData.set(PAYMENT_ITEM, stack.copy()); }
    /** Whoever her model draws on its steve bones: the scene partner, or the master riding Galath. */
    public Optional<UUID> getRenderPartnerUUID() {
        var partner = getScenePartnerUUID();
        if (partner.isPresent()) return partner;
        return getFirstPassenger() instanceof Player rider ? Optional.of(rider.getUUID()) : Optional.empty();
    }
    public void setSceneLocked(boolean locked) { entityData.set(SCENE_LOCK, locked); }
    public int getActionTicks() { return actionTicks; }
    public int getAnimationVariant() { return entityData.get(VARIANT); }
    public int getHeldWeaponMode() { return entityData.get(HELD_WEAPON); }
    public void setHeldWeaponMode(int mode) { entityData.set(HELD_WEAPON, Math.clamp(mode, 0, 2)); }
    public void setAnimationVariant(int variant) { entityData.set(VARIANT, Math.max(0, variant)); }
    public int getModelVariant() { return entityData.get(MODEL); }
    public void setModelVariant(int model) { entityData.set(MODEL, Math.max(0, model)); }
    public String getModelCode() { return entityData.get(MODEL_CODE); }
    public void setModelCode(String code) {
        if (code.length() > 4096) throw new IllegalArgumentException("Model code too long");
        entityData.set(MODEL_CODE, code);
        updateAppearanceInputs();
    }
    private void updateAppearanceInputs() {
        if (kind != NpcKind.KOBOLD) return;
        try {
            var selectors = com.arn.goodmod.menu.NpcModelCode.parse(getModelCode(), kind).selectors();
            if (!selectors.isEmpty()) setAnimationInput("size", NpcAppearance.koboldAnimationSize(selectors.getFirst()));
        } catch (IllegalArgumentException ignored) { /* Legacy addon-only codes have no numeric customization. */ }
    }
    public MovementMode getMovementMode() { return MovementMode.values()[entityData.get(MOVEMENT)]; }
    public void setMovementMode(MovementMode mode) {
        entityData.set(MOVEMENT, mode.ordinal()); getNavigation().stop();
        if (mode == MovementMode.HOME && !getAnimationState().equals("THROW_PEARL")) {
            setAnimationState("THROW_PEARL");
            setTargetYaw((float)(Math.atan2(getZ() - homePosition.getZ(), getX() - homePosition.getX()) * 180 / Math.PI + 90));
            setAnchored(true);
        }
    }
    public BlockPos getHomePosition() { return homePosition; }
    public void setHomePosition(BlockPos pos) { homePosition = pos.immutable(); homeInitialized = true; }
    public float getTargetYaw() { return entityData.get(TARGET_YAW); }
    public void setTargetYaw(float yaw) { if (Float.isFinite(yaw)) entityData.set(TARGET_YAW, net.minecraft.util.Mth.wrapDegrees(yaw)); }
    public boolean isAnchored() { return entityData.get(ANCHORED); }
    public void setAnchored(boolean anchored) { entityData.set(ANCHORED, anchored); if (anchored) getNavigation().stop(); }
    public boolean isUsingCombinedModel() { return entityData.get(COMBINED); }
    public void setUsingCombinedModel(boolean combined) { entityData.set(COMBINED, combined); }
    public ItemStack getSummoningStack() { return entityData.get(SUMMONING_STACK); }
    public void setSummoningStack(ItemStack stack) { entityData.set(SUMMONING_STACK, stack.copy()); }
    public Map<String, Double> getAnimationInputs() { return Collections.unmodifiableMap(animationInputs); }
    public Map<String, Boolean> getBoneVisibility() { return Collections.unmodifiableMap(boneVisibility); }
    public Map<String, Float> getBoneScales() { return Collections.unmodifiableMap(boneScales); }
    public void setAnimationInput(String key, double value) { if (Double.isFinite(value)) { animationInputs.put(key, value); synchronizeVisualData(); } }
    public void setBoneVisible(String bone, boolean visible) { boneVisibility.put(bone, visible); synchronizeVisualData(); }
    public void setBoneScale(String bone, float scale) { if (Float.isFinite(scale) && scale > 0) { boneScales.put(bone, scale); synchronizeVisualData(); } }
    private void synchronizeVisualData() {
        if (level().isClientSide()) return;
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        com.google.gson.JsonObject inputs = new com.google.gson.JsonObject(), visible = new com.google.gson.JsonObject(), scales = new com.google.gson.JsonObject();
        animationInputs.forEach(inputs::addProperty); boneVisibility.forEach(visible::addProperty); boneScales.forEach(scales::addProperty);
        json.add("inputs", inputs); json.add("visible", visible); json.add("scales", scales);
        entityData.set(VISUAL_DATA, json.toString());
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor.equals(VISUAL_DATA)) applyVisualData(entityData.get(VISUAL_DATA));
    }
    private void applyVisualData(String encoded) {
        if (encoded.length() > 65536) return;
        try {
            var json = com.google.gson.JsonParser.parseString(encoded).getAsJsonObject();
            if (json.has("inputs")) json.getAsJsonObject("inputs").entrySet().forEach(e -> { double value = e.getValue().getAsDouble(); if (Double.isFinite(value)) animationInputs.put(e.getKey(), value); });
            if (json.has("visible")) json.getAsJsonObject("visible").entrySet().forEach(e -> boneVisibility.put(e.getKey(), e.getValue().getAsBoolean()));
            if (json.has("scales")) json.getAsJsonObject("scales").entrySet().forEach(e -> { float value = e.getValue().getAsFloat(); if (Float.isFinite(value) && value > 0) boneScales.put(e.getKey(), value); });
        } catch (RuntimeException malformed) { com.arn.goodmod.GoodcraftMod.LOGGER.warn("Ignoring invalid saved NPC visual state for {}", getUUID()); }
    }
    public String getMovementAnimation() { return GoodcraftAnimations.movement(this); }
    public String getActionAnimation() { return GoodcraftAnimations.action(this); }
    public String getEyeAnimation() { return GoodcraftAnimations.eyes(this); }

    /** Client animation notifications never grant authority to mutate world/inventory state. */
    public void onAnimationKeyframe(String controller, String marker) {
        GoodcraftAnimations.onKeyframe(this, controller, marker);
    }

    public boolean canPlayerUse(Player player) {
        boolean present = isAlive() && player.isAlive() && !player.isSpectator() && player.level() == level() && distanceToSqr(player) <= 64;
        boolean operator = player instanceof ServerPlayer serverPlayer && serverPlayer.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        return com.arn.goodmod.inventory.NpcAccess.permits(player.getUUID(), getOwnerUUID(), getAssociatedPlayerUUID(), operator, present);
    }

    @Override public void tick() {
        super.tick();
        String current = getAnimationState();
        if (!current.equals(previousAction)) { actionTicks = 0; previousAction = current; } else actionTicks++;
        animationInputs.put("pitch", (double)getXRot());
        animationInputs.put("yaw", (double)(getYHeadRot() - yBodyRot));
        if (level().isClientSide()) return;
        if (!homeInitialized) setHomePosition(blockPosition());
        // The non-interactive automatic follow-ups encoded directly by fp constructors.
        switch (current) {
            case "ATTACK" -> {
                String clip = getActionAnimation();
                var clips = com.arn.goodmod.animation.AnimationCatalog.ANIMATIONS.getAsJsonObject("clips");
                if (clips.has(clip)) {
                    var markers = clips.getAsJsonObject(clip).getAsJsonObject("sound_markers");
                    if (markers != null) for (var marker : markers.entrySet()) {
                        if (marker.getValue().getAsJsonObject().get("effect").getAsString().equals("attackDone") && actionTicks >= Math.ceil(Double.parseDouble(marker.getKey()) * 20)) {
                            setAnimationState("NULL"); setAnimationVariant((getAnimationVariant() + 1) % 3); break;
                        }
                    }
                }
            }
            case "WAVE" -> { if (actionTicks >= 71) setAnimationState("WAVE_IDLE"); }
            case "BOOST" -> { if (actionTicks >= 43) setAnimationState("CONTROLLED_FLIGHT"); }
            case "GALATH_SUMMON" -> { if (actionTicks >= 15) setAnimationState("NULL"); }
            case "GIVE_COIN" -> { if (actionTicks >= 140) setAnimationState("NULL"); }
            case "HUG_MANG" -> { if (actionTicks >= 239) { setAnimationState("NULL"); setAnchored(false); setUsingCombinedModel(false); } }
        }
        if (getMovementMode() == MovementMode.HOME && current.equals("THROW_PEARL") && level() instanceof ServerLevel serverLevel) {
            // All four original clips have pearl markers at 0.9583 s and 2.875 s.
            if (actionTicks == 20) {
                homePearl = new HomePearl(serverLevel, this);
                Vec3 target = Vec3.atBottomCenterOf(homePosition).subtract(position());
                homePearl.shoot(target.x, target.y, target.z, (float)Math.min(4, target.length() * 0.1), 0);
                serverLevel.addFreshEntity(homePearl);
            }
            if (actionTicks >= 58) finishHomeTeleport(Vec3.atBottomCenterOf(homePosition));
        }
        if (isAnchored()) {
            getNavigation().stop(); setDeltaMovement(Vec3.ZERO);
            float yaw = getTargetYaw();
            setYRot(yaw); yRotO = yaw; yBodyRot = yaw; yBodyRotO = yaw; setYHeadRot(yaw); yHeadRotO = yaw;
        }
        if (playerForm) {
            getAssociatedPlayerUUID().map(level()::getPlayerByUUID).ifPresent(player -> {
                setPos(player.position()); setYRot(player.getYRot()); setXRot(player.getXRot());
                setYHeadRot(player.getYHeadRot()); yBodyRot = player.yBodyRot;
            });
        }
        if (supportsEquipment()) synchronizeEquipment();
        if (level() instanceof ServerLevel sceneLevel) scene().tick(sceneLevel);
        if (supportsEquipment() && tickCount % 80 == 0 && getHealth() < getMaxHealth()) {
            // e2: passive regeneration; four points while incapacitated with no nearby hostiles.
            boolean downed = current.equals("DOWNED");
            boolean nearby = !level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                    getBoundingBox().inflate(7, 1, 7), enemy -> enemy.isAlive()).isEmpty();
            heal(downed && !nearby ? 4 : 1);
            if (downed && getHealth() >= getMaxHealth()) setAnimationState("NULL");
        }
    }

    /** c.X[index] (index -1: c.a(c.X), a random one) from this girl, for everyone near. */
    public void playLegacy(String array, int index, float volume) {
        var event = com.arn.goodmod.registry.GoodcraftSounds.pick(array, index, random);
        if (event != null) level().playSound(null, getX(), getY(), getZ(), event, net.minecraft.sounds.SoundSource.NEUTRAL, volume, 1);
    }

    /** em.c(): what she says when her home is set here (ex/el/e0/eb). */
    public void announceHome(net.minecraft.world.entity.player.Player player) {
        String line = switch (kind) {
            case JENNY -> "Alright, this is my new Home~";
            case ELLIE -> "Okay, I will be residing here then..";
            case BIA -> "I am living here now nya~";
            case LUNA -> "Love it here owo";
            default -> null;
        };
        if (line == null) return;
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("<" + getName().getString() + "> " + line));
        switch (kind) {
            case JENNY -> playLegacy("GIRLS_JENNY_HAPPYOH", 1, 1);
            case ELLIE -> playLegacy("GIRLS_ELLIE_HUH", 0, 6);
            case BIA -> playLegacy("GIRLS_BIA_BREATH", -1, 1);
            case LUNA -> playLegacy("GIRLS_LUNA_OWO", -1, 1);
            default -> {}
        }
    }

    /** ex: Jenny makes no hurt sound; eb.func_184581_c: Luna's "ouu". */
    @Override protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        if (kind == NpcKind.LUNA && !playerForm) return com.arn.goodmod.registry.GoodcraftSounds.pick("GIRLS_LUNA_OUU", -1, random);
        return kind == NpcKind.JENNY ? null : super.getHurtSound(source);
    }
    /** ex/eb death sounds: Jenny sighs; Luna's "ouu", or one time in twenty Allie's scream. */
    @Override protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return switch (kind) {
            case JENNY -> com.arn.goodmod.registry.GoodcraftSounds.pick("GIRLS_JENNY_SIGH", -1, random);
            case LUNA -> random.nextFloat() * 100 > 95 ? com.arn.goodmod.registry.GoodcraftSounds.pick("GIRLS_ALLIE_SCAWY", 2, random)
                    : com.arn.goodmod.registry.GoodcraftSounds.pick("GIRLS_LUNA_OUU", 12, random);
            default -> super.getDeathSound();
        };
    }
    /** em.func_184639_G: once in ten thousand ambient rolls. */
    @Override protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return !playerForm && random.nextInt(10000) == 0 ? com.arn.goodmod.registry.GoodcraftSounds.pick("MISC_FART", -1, random) : null;
    }

    // em.func_70692_ba: the girls never despawn.
    @Override public boolean removeWhenFarAway(double distanceSqr) { return false; }

    @Override public void remove(RemovalReason reason) {
        // Never leave a partner frozen in place when she unloads, dies or is discarded mid-scene.
        if (!level().isClientSide() && scene != null && scene.isActive()) scene.end();
        super.remove(reason);
    }

    void finishHomeTeleport(Vec3 destination) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, getX(), getY(), getZ(), 32, 0.5, 1, 0.5, 0.1);
        stopRiding(); teleportTo(destination.x, destination.y, destination.z); resetFallDistance();
        if (homePearl != null) homePearl.discard(); homePearl = null;
        setAnchored(false); setAnimationState("NULL"); setMovementMode(MovementMode.WAIT);
    }

    @Override protected void dropCustomDeathLoot(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, boolean killedByPlayer) {
        if (playerForm) return; // Proxy inventories are owned and saved by PlayerForms, never entity drops.
        // Drop each backing slot once; vanilla mirrored equipment must not duplicate these stacks.
        for (var slot : EquipmentSlot.VALUES) setItemSlot(slot, ItemStack.EMPTY);
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        for (ItemStack stack : equipmentInventory.getItems()) if (!stack.isEmpty()) spawnAtLocation(level, stack.copy());
        for (ItemStack stack : storageInventory.getItems()) if (!stack.isEmpty()) spawnAtLocation(level, stack.copy());
        equipmentInventory.clearContent(); storageInventory.clearContent();
    }

    @Override public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (playerForm) return false;
        // g.a(LivingHurtEvent): an owned equipment companion is incapacitated, not killed.
        if (supportsEquipment() && getOwnerUUID().isPresent() && !source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) {
            if (getAnimationState().equals("DOWNED")) return false;
            if (amount >= getHealth()) {
                setHealth(1); setAnimationState("DOWNED"); getNavigation().stop(); return true;
            }
        }
        return super.hurtServer(level, source, amount);
    }

    private void synchronizeEquipment() {
        for (int slot = 0; slot < EQUIPMENT.length; slot++) {
            if (slot == 1) continue; // The bow is stored separately, not permanently wielded in the offhand.
            boolean bow = getAnimationState().equals("BOW") || getHeldWeaponMode() == 2;
            ItemStack desired = equipmentInventory.getItem(slot == 0 && bow ? 1 : slot);
            if (!ItemStack.matches(getItemBySlot(EQUIPMENT[slot]), desired)) setItemSlot(EQUIPMENT[slot], desired.copy());
        }
    }

    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putString("master", entityData.get(OWNER)); out.putString("associatedPlayer", entityData.get(ASSOCIATED_PLAYER));
        out.putString("currentAction", getAnimationState()); out.putInt("currentModel", getModelVariant());
        out.putInt("animationVariant", getAnimationVariant()); out.putInt("actionTicks", actionTicks);
        out.putString("movementMode", getMovementMode().name()); out.putBoolean("anchored", isAnchored());
        out.putBoolean("combinedModel", isUsingCombinedModel()); out.putFloat("targetYaw", getTargetYaw());
        out.putDouble("homeX", homePosition.getX()); out.putDouble("homeY", homePosition.getY()); out.putDouble("homeZ", homePosition.getZ());
        out.putBoolean("homeInitialized", homeInitialized); out.putString("girlID", getUUID().toString());
        out.putString("sexmod:customModel", getModelCode());
        ContainerHelper.saveAllItems(out.child("npcEquipment"), equipmentInventory.getItems());
        ContainerHelper.saveAllItems(out.child("storage"), storageInventory.getItems());
        out.store("summoningStack", ItemStack.OPTIONAL_CODEC, getSummoningStack());
        out.putString("visualData", entityData.get(VISUAL_DATA));
    }

    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        setOwnerUUID(parseUuid(in.getStringOr("master", "")).orElse(null));
        setAssociatedPlayerUUID(parseUuid(in.getStringOr("associatedPlayer", "")).orElse(null));
        String state = in.getStringOr("currentAction", "NULL");
        if (state.matches("[A-Z0-9_]{1,128}")) entityData.set(ACTION, state);
        previousAction = getAnimationState(); actionTicks = Math.max(0, in.getIntOr("actionTicks", 0));
        setModelVariant(in.getIntOr("currentModel", 1)); setAnimationVariant(in.getIntOr("animationVariant", 0));
        try { setMovementMode(MovementMode.valueOf(in.getStringOr("movementMode", "WANDER"))); }
        catch (IllegalArgumentException ignored) { setMovementMode(MovementMode.WANDER); }
        setAnchored(in.getBooleanOr("anchored", false)); setUsingCombinedModel(in.getBooleanOr("combinedModel", false));
        setTargetYaw(in.getFloatOr("targetYaw", 0));
        homePosition = BlockPos.containing(in.getDoubleOr("homeX", getX()), in.getDoubleOr("homeY", getY()), in.getDoubleOr("homeZ", getZ()));
        homeInitialized = in.getBooleanOr("homeInitialized", true);
        String modelCode = in.getStringOr("sexmod:customModel", "");
        if (modelCode.isBlank() && (kind == NpcKind.KOBOLD || kind == NpcKind.GOBLIN)) modelCode = NpcAppearance.randomCode(kind, random);
        if (modelCode.length() <= 4096) setModelCode(modelCode);
        in.child("npcEquipment").or(() -> in.child("equipment").filter(child -> child.childrenList("Items").isPresent()))
                .ifPresent(child -> { equipmentInventory.clearContent(); ContainerHelper.loadAllItems(child, equipmentInventory.getItems()); });
        in.child("storage").ifPresent(child -> { storageInventory.clearContent(); ContainerHelper.loadAllItems(child, storageInventory.getItems()); });
        setSummoningStack(in.read("summoningStack", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        String visual = in.getStringOr("visualData", "{}"); if (visual.length() <= 65536) entityData.set(VISUAL_DATA, visual);
        updateAppearanceInputs();
        synchronizeEquipment();
    }

    private static Optional<UUID> parseUuid(String value) {
        try { return value.isBlank() || value.equals("null") ? Optional.empty() : Optional.of(UUID.fromString(value)); }
        catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar registrar) { GoodcraftAnimations.registerControllers(this, registrar); }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }
}
