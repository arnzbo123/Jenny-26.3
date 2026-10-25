package com.arn.goodmod.network;

import com.arn.goodmod.entity.KoboldNpc;
import com.arn.goodmod.entity.MovementMode;
import com.arn.goodmod.registry.GoodcraftItems;
import com.arn.goodmod.world.KoboldTribes;
import com.arn.goodmod.world.TribeWorkPlanner;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;

public final class TribeNetworking {
    private TribeNetworking() {}
    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(TribeControlPayload.TYPE,TribeControlPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TribeScreenPayload.TYPE,TribeScreenPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TribeControlPayload.TYPE,(payload,context)->context.server().execute(()->apply(context.player(),payload)));
        UseEntityCallback.EVENT.register((player,level,hand,entity,hit)->{
            if(!(entity instanceof KoboldNpc kobold)||!player.getItemInHand(hand).is(GoodcraftItems.DRAGON_STAFF))return InteractionResult.PASS;
            if(player instanceof ServerPlayer serverPlayer && kobold.canPlayerUse(player)) {
                var tribes=KoboldTribes.get(serverPlayer.level());
                // A kobold hatched or spawned outside a tribe founds her own, so the staff can always claim her.
                if(kobold.getTribeId().isEmpty()) {
                    UUID id=UUID.randomUUID();
                    tribes.registerSingle(id,kobold.getTribeColor(),kobold.getUUID(),java.util.Optional.empty(),kobold.blockPosition());
                    kobold.setTribeId(id);
                }
                tribes.find(kobold.getTribeId().get()).filter(t->t.owner().isEmpty()||t.owner().get().equals(player.getUUID()))
                        .ifPresent(t->send(serverPlayer,t,kobold.blockPosition(),player.getDirection(),""));
            }
            return InteractionResult.SUCCESS;
        });
    }
    public static void openOwned(ServerPlayer player,BlockPos target,Direction direction) {
        if(!validPlayer(player)||!validTarget(player,target))return;
        var tribes=KoboldTribes.get(player.level());
        var owned=tribes.ownedBy(player.getUUID());
        if(owned.isPresent()){send(player,owned.get(),target,direction,"");return;}
        // hy: without a tribe of his own, the staff offers the nearest unclaimed tribe instead of a dead end.
        var nearby=player.level().getEntitiesOfClass(KoboldNpc.class,player.getBoundingBox().inflate(8),kobold->kobold.isAlive()&&kobold.getTribeId().isPresent())
                .stream().map(kobold->tribes.find(kobold.getTribeId().get())).flatMap(java.util.Optional::stream)
                .filter(tribe->tribe.owner().isEmpty()).findFirst();
        if(nearby.isPresent())send(player,nearby.get(),target,direction,"");
        else player.sendSystemMessage(Component.literal("Find a kobold tribe (or place a Tribe Egg), then right click one of its kobolds with the staff to claim it."));
    }
    private static boolean validPlayer(ServerPlayer player) {
        return player.isAlive()&&!player.isSpectator()&&(player.getMainHandItem().is(GoodcraftItems.DRAGON_STAFF)||player.getOffhandItem().is(GoodcraftItems.DRAGON_STAFF));
    }
    private static boolean validTarget(ServerPlayer player,BlockPos target) {
        return target.distToCenterSqr(player.position())<=64&&player.level().hasChunkAt(target)&&player.mayInteract(player.level(),target);
    }
    private static void apply(ServerPlayer player,TribeControlPayload request) {
        if(!validPlayer(player)||!validTarget(player,request.target()))return;
        var service=KoboldTribes.get(player.level()); var found=service.find(request.tribe());
        if(found.isEmpty())return;
        var tribe=found.get();
        if(request.action()!=TribeControlPayload.Action.CLAIM&&!tribe.owner().filter(player.getUUID()::equals).isPresent())return;
        String status="";
        try {
            switch(request.action()) {
                case CLAIM -> {
                    boolean near=tribe.members().stream().map(player.level()::getEntity).anyMatch(entity->entity instanceof KoboldNpc npc&&npc.isAlive()&&npc.distanceToSqr(player)<=64);
                    if(!near||!service.claim(tribe.id(),player.getUUID(),request.name()))throw new IllegalArgumentException("This tribe cannot be claimed.");
                    status="Tribe formed.";
                }
                case FOLLOW -> { service.setFollowing(tribe.id(),true); status="Tribe following."; }
                case WAIT -> { service.setFollowing(tribe.id(),false); status="Tribe working at home."; }
                case ASSIGN,UNASSIGN -> {
                    boolean enabled=request.action()==TribeControlPayload.Action.ASSIGN;
                    var state=player.level().getBlockState(request.target());
                    boolean bed=state.getBlock() instanceof BedBlock;
                    if(!bed&&!(state.getBlock() instanceof ChestBlock))throw new IllegalArgumentException("Select a bed or chest.");
                    BlockPos canonical=request.target();
                    if(bed&&state.getValue(BlockStateProperties.BED_PART)==BedPart.FOOT)canonical=canonical.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
                    if(!bed&&state.getValue(ChestBlock.TYPE)==ChestType.RIGHT)canonical=ChestBlock.getConnectedBlockPos(canonical,state);
                    if(!player.level().hasChunkAt(canonical)||!player.mayInteract(player.level(),canonical))throw new IllegalArgumentException("Block is unavailable.");
                    if(!service.setAssignedBlock(tribe.id(),canonical,bed,enabled))throw new IllegalArgumentException("Block belongs to another tribe or assignment limit reached.");
                    status=enabled?"Block assigned.":"Block unassigned.";
                }
                case TREE,MINE -> {
                    long usableBeds=tribe.beds().stream().filter(pos->player.level().hasChunkAt(pos)&&player.level().getBlockState(pos).getBlock() instanceof BedBlock).count();
                    if(usableBeds<tribe.members().size())throw new IllegalArgumentException("Every tribe member needs an assigned bed before working.");
                    List<BlockPos> blocks;
                    if(request.action()==TribeControlPayload.Action.TREE) {
                        if(!player.level().getBlockState(request.target()).is(BlockTags.LOGS))throw new IllegalArgumentException("Select a log.");
                        blocks=TribeWorkPlanner.tree(player.level(),request.target());
                    } else {
                        var targetState=player.level().getBlockState(request.target());
                        if(!targetState.is(BlockTags.MINEABLE_WITH_PICKAXE)&&!targetState.is(BlockTags.MINEABLE_WITH_SHOVEL))throw new IllegalArgumentException("Select a solid mining face.");
                        if(player.blockPosition().getY()>request.target().getY())throw new IllegalArgumentException("Select a mining face above your feet.");
                        blocks=TribeWorkPlanner.mine(request.target(),request.direction());
                    }
                    for(BlockPos pos:blocks) {
                        if(!player.level().hasChunkAt(pos)||!player.mayInteract(player.level(),pos))throw new IllegalArgumentException("The work area contains unavailable blocks.");
                        var state=player.level().getBlockState(pos);
                        if(state.getDestroySpeed(player.level(),pos)<0||state.hasBlockEntity())throw new IllegalArgumentException("The work area contains protected blocks or containers.");
                    }
                    var kind=request.action()==TribeControlPayload.Action.TREE?KoboldTribes.JobKind.TREE:KoboldTribes.JobKind.MINE;
                    BlockPos origin=kind==KoboldTribes.JobKind.TREE?blocks.stream().min(java.util.Comparator.comparingInt(BlockPos::getY)).orElse(request.target()):request.target();
                    if(!service.addJob(tribe.id(),new KoboldTribes.WorkJob(UUID.randomUUID(),kind,origin,request.direction(),blocks)))throw new IllegalArgumentException("This work area overlaps a job or the job limit is reached.");
                    status="Work assigned.";
                }
                case CANCEL -> {service.cancelJobAt(tribe.id(),request.target());status="Work at this location cancelled.";}
            }
            var updated=service.find(tribe.id()).orElseThrow();
            for(UUID member:updated.members())if(player.level().getEntity(member) instanceof KoboldNpc npc) {
                npc.setOwnerUUID(updated.owner().orElse(null)); npc.setMovementMode(updated.following()?MovementMode.FOLLOW:MovementMode.WANDER);
            }
        } catch(IllegalArgumentException invalid) {status=invalid.getMessage();}
        send(player,service.find(tribe.id()).orElse(tribe),request.target(),request.direction(),status);
    }
    private static void send(ServerPlayer player,KoboldTribes.Tribe tribe,BlockPos target,Direction direction,String status) {
        ServerPlayNetworking.send(player,new TribeScreenPayload(tribe.id(),tribe.name(),tribe.color(),tribe.owner().isPresent(),tribe.following(),
                tribe.members().size(),tribe.beds().size(),tribe.chests().size(),tribe.jobs().size(),target,direction,status));
    }
}
