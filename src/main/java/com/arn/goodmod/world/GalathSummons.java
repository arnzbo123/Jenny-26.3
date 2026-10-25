package com.arn.goodmod.world;

import com.arn.goodmod.entity.GoodcraftNpc;
import com.arn.goodmod.entity.NpcKind;
import com.arn.goodmod.registry.GoodcraftEntities;
import com.arn.goodmod.registry.GoodcraftItems;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

/** Persistent owner/summon association from v.java. Delays run on authoritative server ticks. */
public final class GalathSummons extends SavedData {
    public record Bond(UUID owner, UUID entity, boolean manglelieUnlocked) {
        public static final Codec<Bond> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(Bond::owner),
                UUIDUtil.STRING_CODEC.fieldOf("entity").forGetter(Bond::entity),
                Codec.BOOL.optionalFieldOf("manglelieUnlocked", false).forGetter(Bond::manglelieUnlocked)
        ).apply(i, Bond::new));
    }
    public static final Codec<GalathSummons> CODEC = Bond.CODEC.listOf().xmap(GalathSummons::new,
            data -> new ArrayList<>(data.bonds.values()));
    public static final SavedDataType<GalathSummons> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("sexmod", "galath_summons"), GalathSummons::new, CODEC, null);
    private final Map<UUID, Bond> bonds = new HashMap<>();
    private final Map<UUID, Transition> transitions = new HashMap<>();
    private record Transition(boolean dismiss, long started, ItemStack coin) {}
    public GalathSummons() {}
    private GalathSummons(List<Bond> loaded) { loaded.forEach(bond -> bonds.put(bond.owner(), bond)); }
    public static GalathSummons get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(TYPE); }
    public Optional<Bond> bond(UUID owner) { return Optional.ofNullable(bonds.get(owner)); }
    public boolean isBusy(UUID owner) { return transitions.containsKey(owner); }
    public boolean beginSummon(ServerPlayer player, ItemStack coin) {
        UUID owner = player.getUUID();
        if (bonds.containsKey(owner) || transitions.containsKey(owner)) return false;
        transitions.put(owner, new Transition(false, player.level().getGameTime(), coin.copy()));
        return true;
    }
    public boolean beginDismiss(ServerPlayer player, GoodcraftNpc npc, ItemStack coin) {
        Bond bond = bonds.get(player.getUUID());
        if (bond == null || !bond.entity().equals(npc.getUUID()) || transitions.containsKey(player.getUUID())) return false;
        transitions.put(player.getUUID(), new Transition(true, player.level().getGameTime(), coin.copy()));
        return true;
    }
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> get(server).tick(server));
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) ->
                get(destination.getServer()).remove(destination.getServer(), player.getUUID()));
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof GoodcraftNpc npc && npc.getNpcKind() == NpcKind.GALATH
                    && npc.getSummoningStack().is(GoodcraftItems.GALATH_COIN) && npc.getOwnerUUID().isPresent()) {
                Bond bond = get(level.getServer()).bonds.get(npc.getOwnerUUID().get());
                if (bond == null || !bond.entity().equals(npc.getUUID())) npc.discard();
            }
        });
    }
    private static Entity find(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) { Entity entity = level.getEntity(uuid); if (entity != null) return entity; }
        return null;
    }
    public void remove(MinecraftServer server, UUID owner) {
        transitions.remove(owner);
        Bond bond = bonds.remove(owner);
        if (bond != null) {
            Entity entity = find(server, bond.entity());
            if (entity != null) entity.discard();
            setDirty();
        }
    }
    private void tick(MinecraftServer server) {
        for (var entry : List.copyOf(transitions.entrySet())) {
            UUID owner = entry.getKey();
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            if (player == null || !player.isAlive()) { transitions.remove(owner); continue; }
            ServerLevel level = player.level();
            Transition transition = entry.getValue();
            long elapsed = level.getGameTime() - transition.started();
            if (elapsed < 0) { transitions.remove(owner); continue; }
            Bond bond = bonds.get(owner);
            Entity entity = bond == null ? null : find(server, bond.entity());
            if (transition.dismiss()) {
                if (elapsed >= 17 && entity instanceof GoodcraftNpc npc) {
                    npc.setAnimationState("GALATH_DE_SUMMON"); npc.setAnchored(true);
                    npc.setTargetYaw(npc.getYRot()); npc.setDeltaMovement(Vec3.ZERO);
                }
                if (elapsed >= 60 && bond != null) {
                    if (entity != null) entity.discard();
                    bonds.remove(owner); setDirty();
                }
                if (elapsed >= 80) transitions.remove(owner);
            } else if (elapsed >= 80) {
                GoodcraftNpc npc = GoodcraftEntities.create(NpcKind.GALATH, level);
                Vec3 position = player.getEyePosition().add(player.getLookAngle().normalize().scale(2));
                npc.setPos(position); npc.setYRot(player.getYRot() + 180); npc.setOwnerUUID(owner);
                npc.setHomePosition(npc.blockPosition()); npc.setPersistenceRequired();
                npc.setAnimationState("GALATH_SUMMON");
                // Register before addFreshEntity: entity-load validation also runs for fresh entities.
                bonds.put(owner, new Bond(owner, npc.getUUID(), false));
                npc.setSummoningStack(transition.coin());
                if (!level.addFreshEntity(npc)) bonds.remove(owner);
                setDirty(); transitions.remove(owner);
            }
            if (elapsed > 20 && elapsed < 60) {
                Vec3 start = player.getEyePosition();
                Vec3 end = transition.dismiss() && entity != null ? entity.getEyePosition() : start.add(player.getLookAngle().scale(2));
                double progress = (elapsed - 20) / 40.0;
                Vec3 point = transition.dismiss() ? end.lerp(start, progress) : start.lerp(end, progress);
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, point.x, point.y, point.z, 1, 0, 0, 0, 0);
            }
        }
        for (Bond bond : List.copyOf(bonds.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(bond.owner());
            Entity npc = find(server, bond.entity());
            if (npc != null && (!npc.isAlive() || player != null &&
                    (player.level() != npc.level() || player.distanceToSqr(npc) > 3600))) remove(server, bond.owner());
        }
    }
}
