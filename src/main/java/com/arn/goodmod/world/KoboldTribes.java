package com.arn.goodmod.world;

import com.arn.goodmod.entity.KoboldNpc;
import com.arn.goodmod.registry.GoodcraftEntities;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.phys.Vec3;

/** Tribe identity survives unloads. Members are UUIDs, never references to obsolete entity objects. */
public final class KoboldTribes extends SavedData {
    public static final List<String> COLORS = List.of("GREEN", "YELLOW", "RED", "PURPLE", "LIGHT_GREEN",
            "OLD_BLUE", "DARK_GREY", "BROWN", "DARK_BLUE", "LIGHT_BLUE", "SILVER");
    public enum JobKind { TREE, MINE }
    public record WorkJob(UUID id, JobKind kind, BlockPos origin, Direction direction, List<BlockPos> blocks) {
        public static final Codec<WorkJob> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(WorkJob::id),
                Codec.STRING.xmap(JobKind::valueOf, JobKind::name).fieldOf("kind").forGetter(WorkJob::kind),
                BlockPos.CODEC.fieldOf("origin").forGetter(WorkJob::origin),
                Direction.CODEC.fieldOf("direction").forGetter(WorkJob::direction),
                BlockPos.CODEC.listOf().fieldOf("blocks").forGetter(WorkJob::blocks)
        ).apply(i, WorkJob::new));
        public WorkJob {
            origin = origin.immutable();
            blocks = blocks.stream().map(BlockPos::immutable).distinct().toList();
            if (blocks.size() > 512) throw new IllegalArgumentException("Job exceeds block limit");
        }
    }
    public record WorkTarget(UUID jobId, JobKind kind, BlockPos block) {}
    public record Tribe(UUID id, String color, UUID leader, List<UUID> members, Optional<UUID> owner, BlockPos home,
                        String name, boolean following, List<BlockPos> beds, List<BlockPos> chests, List<WorkJob> jobs) {
        public static final Codec<Tribe> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(Tribe::id),
                Codec.STRING.fieldOf("color").forGetter(Tribe::color),
                UUIDUtil.STRING_CODEC.fieldOf("leader").forGetter(Tribe::leader),
                UUIDUtil.STRING_CODEC.listOf().fieldOf("members").forGetter(Tribe::members),
                UUIDUtil.STRING_CODEC.optionalFieldOf("owner").forGetter(Tribe::owner),
                BlockPos.CODEC.fieldOf("home").forGetter(Tribe::home),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Tribe::name),
                Codec.BOOL.optionalFieldOf("following", false).forGetter(Tribe::following),
                BlockPos.CODEC.listOf().optionalFieldOf("beds", List.of()).forGetter(Tribe::beds),
                BlockPos.CODEC.listOf().optionalFieldOf("chests", List.of()).forGetter(Tribe::chests),
                WorkJob.CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(Tribe::jobs)
        ).apply(i, Tribe::new));
        public Tribe {
            members = List.copyOf(members); home = home.immutable();
            beds = List.copyOf(beds); chests = List.copyOf(chests); jobs = List.copyOf(jobs);
            if (name.length() > 48 || beds.size() > 256 || chests.size() > 256 || jobs.size() > 32) throw new IllegalArgumentException("Tribe data limit exceeded");
        }
        Tribe withMembers(List<UUID> next) { return new Tribe(id,color,leader,next,owner,home,name,following,beds,chests,jobs); }
        Tribe withOwner(UUID next, String newName) { return new Tribe(id,color,leader,members,Optional.of(next),home,newName,following,beds,chests,jobs); }
        Tribe withFollowing(boolean next) { return new Tribe(id,color,leader,members,owner,home,name,next,beds,chests,jobs); }
        Tribe withBlocks(List<BlockPos> nextBeds,List<BlockPos> nextChests) { return new Tribe(id,color,leader,members,owner,home,name,following,nextBeds,nextChests,jobs); }
        Tribe withJobs(List<WorkJob> next) { return new Tribe(id,color,leader,members,owner,home,name,following,beds,chests,next); }
    }
    public static final Codec<KoboldTribes> CODEC = Tribe.CODEC.listOf().xmap(KoboldTribes::new, KoboldTribes::snapshot);
    public static final SavedDataType<KoboldTribes> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("sexmod", "tribes"), KoboldTribes::new, CODEC, null);
    private final Map<UUID, Tribe> tribes = new LinkedHashMap<>();
    private final Map<BlockPos, UUID> workReservations = new LinkedHashMap<>();
    public KoboldTribes() {}
    private KoboldTribes(List<Tribe> loaded) { loaded.forEach(tribe -> tribes.put(tribe.id(), tribe)); }
    public static KoboldTribes get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    public synchronized List<Tribe> snapshot() { return List.copyOf(tribes.values()); }
    public synchronized Optional<Tribe> find(UUID id) { return Optional.ofNullable(tribes.get(id)); }
    public synchronized Optional<Tribe> ownedBy(UUID owner) { return tribes.values().stream().filter(t -> t.owner().filter(owner::equals).isPresent()).findFirst(); }
    public synchronized Tribe registerSingle(UUID id, String color, UUID member, Optional<UUID> owner, BlockPos home) {
        if (tribes.containsKey(id)) { addMember(id,member); return tribes.get(id); }
        Optional<UUID> uniqueOwner=owner.filter(player->ownedBy(player).isEmpty());
        Tribe tribe=new Tribe(id,COLORS.contains(color)?color:"PURPLE",member,List.of(member),uniqueOwner,home,"",false,List.of(),List.of(),List.of());
        tribes.put(id,tribe);setDirty();return tribe;
    }
    public synchronized void addMember(UUID tribeId, UUID member) {
        Tribe tribe = tribes.get(tribeId);
        if (tribe == null || tribe.members().contains(member)) return;
        var members = new ArrayList<>(tribe.members()); members.add(member);
        tribes.put(tribeId, tribe.withMembers(members)); setDirty();
    }
    public synchronized void removeMember(UUID tribeId, UUID member) {
        Tribe tribe=tribes.get(tribeId);if(tribe==null)return;
        releaseWork(member);
        List<UUID> members=tribe.members().stream().filter(id->!id.equals(member)).toList();
        if(members.isEmpty()){tribe.jobs().stream().flatMap(job->job.blocks().stream()).forEach(workReservations::remove);tribes.remove(tribeId);}
        else {
            UUID leader=tribe.leader().equals(member)?members.getFirst():tribe.leader();
            tribes.put(tribeId,new Tribe(tribe.id(),tribe.color(),leader,members,tribe.owner(),tribe.home(),tribe.name(),tribe.following(),tribe.beds(),tribe.chests(),tribe.jobs()));
        }
        setDirty();
    }
    public synchronized boolean setOwner(UUID tribeId, UUID owner) {
        Tribe tribe = tribes.get(tribeId);
        if (tribe == null || tribe.owner().isPresent() && !tribe.owner().get().equals(owner)) return false;
        if (ownedBy(owner).filter(existing -> !existing.id().equals(tribeId)).isPresent()) return false;
        tribes.put(tribeId, tribe.withOwner(owner, tribe.name()));
        setDirty(); return true;
    }
    public synchronized boolean claim(UUID tribeId, UUID owner, String name) {
        if (name.isBlank() || name.length() > 48 || name.chars().anyMatch(Character::isISOControl)) return false;
        if (!setOwner(tribeId, owner)) return false;
        tribes.put(tribeId, tribes.get(tribeId).withOwner(owner, name.trim())); setDirty(); return true;
    }
    public synchronized void setFollowing(UUID id, boolean following) {
        Tribe tribe = tribes.get(id); if (tribe == null) return;
        tribes.put(id, tribe.withFollowing(following)); setDirty();
        if (following) tribe.members().forEach(this::releaseWork);
    }
    public synchronized boolean setAssignedBlock(UUID id, BlockPos pos, boolean bed, boolean enabled) {
        Tribe tribe = tribes.get(id); if (tribe == null) return false;
        var blocks = new ArrayList<>(bed ? tribe.beds() : tribe.chests());
        if (enabled && !blocks.contains(pos)) {
            if (blocks.size() >= 256 || tribes.values().stream().filter(t -> !t.id().equals(id))
                    .anyMatch(t -> t.beds().contains(pos) || t.chests().contains(pos))) return false;
            blocks.add(pos.immutable());
        } else if (!enabled) blocks.remove(pos);
        tribes.put(id, tribe.withBlocks(bed ? blocks : tribe.beds(), bed ? tribe.chests() : blocks)); setDirty(); return true;
    }
    public synchronized boolean addJob(UUID id, WorkJob job) {
        Tribe tribe = tribes.get(id);
        if (tribe == null || tribe.jobs().size() >= 32 || job.blocks().isEmpty()) return false;
        if (tribes.values().stream().flatMap(t -> t.jobs().stream()).flatMap(j -> j.blocks().stream()).anyMatch(job.blocks()::contains)) return false;
        var jobs = new ArrayList<>(tribe.jobs()); jobs.add(job);
        tribes.put(id, tribe.withJobs(jobs)); setDirty(); return true;
    }
    public synchronized void cancelJobAt(UUID id, BlockPos pos) {
        Tribe tribe = tribes.get(id); if (tribe == null) return;
        tribe.jobs().stream().filter(j -> j.blocks().contains(pos) || j.origin().equals(pos))
                .flatMap(j -> j.blocks().stream()).forEach(workReservations::remove);
        tribes.put(id, tribe.withJobs(tribe.jobs().stream().filter(j -> !j.blocks().contains(pos) && !j.origin().equals(pos)).toList())); setDirty();
    }
    public synchronized Optional<WorkTarget> reserveWork(UUID id, UUID worker, BlockPos from) {
        Tribe tribe = tribes.get(id);
        if (tribe == null || tribe.following() || tribe.owner().isEmpty() || !tribe.members().contains(worker)
                || tribe.beds().size() < tribe.members().size()) return Optional.empty();
        WorkTarget nearest = null; double distance = Double.POSITIVE_INFINITY;
        for (WorkJob job : tribe.jobs()) {
            long assigned=job.blocks().stream().map(workReservations::get).filter(java.util.Objects::nonNull)
                    .filter(idAssigned->!idAssigned.equals(worker)).distinct().count();
            if(assigned>=(job.kind()==JobKind.TREE?1:3))continue;
            for (BlockPos block : job.blocks()) {
            UUID reserved = workReservations.get(block);
            if (reserved != null && !reserved.equals(worker)) continue;
            double candidate = block.distSqr(from);
            if (candidate < distance) { nearest = new WorkTarget(job.id(), job.kind(), block); distance = candidate; }
            }
        }
        releaseWork(worker);
        if (nearest != null) workReservations.put(nearest.block(), worker);
        return Optional.ofNullable(nearest);
    }
    public synchronized void releaseWork(UUID worker) { workReservations.entrySet().removeIf(entry -> entry.getValue().equals(worker)); }
    public synchronized void completeWork(UUID id, UUID worker, BlockPos pos) {
        if (!worker.equals(workReservations.get(pos))) return;
        Tribe tribe = tribes.get(id); if (tribe == null) return;
        var jobs = tribe.jobs().stream().map(job -> new WorkJob(job.id(), job.kind(), job.origin(), job.direction(),
                job.blocks().stream().filter(block -> !block.equals(pos)).toList())).filter(job -> !job.blocks().isEmpty()).toList();
        workReservations.remove(pos); tribes.put(id, tribe.withJobs(jobs)); setDirty();
    }
    /** ax.a: four members, the first is leader, original half-block offsets and shared color. */
    public synchronized List<KoboldNpc> spawn(WorldGenLevel level, Vec3 position) {
        UUID id = UUID.randomUUID();
        String color = COLORS.get(level.getRandom().nextInt(COLORS.size()));
        double[][] offsets = {{0,0}, {0.5,0}, {-0.5,0}, {0,0.5}};
        List<KoboldNpc> spawned = new ArrayList<>();
        for (int n = 0; n < 4; n++) {
            KoboldNpc member = GoodcraftEntities.KOBOLD.create(level.getLevel(), EntitySpawnReason.MOB_SUMMONED);
            if (member == null) { spawned.forEach(KoboldNpc::discard); return List.of(); }
            member.setTribeId(id); member.setTribeColor(color);
            member.setAnimationInput("size", n == 0 ? 0.25 : level.getRandom().nextDouble() * 0.25);
            member.setPos(position.x + offsets[n][0], position.y, position.z + offsets[n][1]);
            member.setHomePosition(BlockPos.containing(position)); member.setPersistenceRequired();
            if (!level.addFreshEntity(member)) { spawned.forEach(KoboldNpc::discard); return List.of(); }
            spawned.add(member);
        }
        tribes.put(id, new Tribe(id, color, spawned.getFirst().getUUID(), spawned.stream().map(KoboldNpc::getUUID).toList(),
                Optional.empty(), BlockPos.containing(position), "", false, List.of(), List.of(), List.of()));
        setDirty(); return List.copyOf(spawned);
    }
}
