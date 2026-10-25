package com.arn.goodmod.world;

import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KoboldTribesTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    private static KoboldTribes.Tribe tribe(UUID id,UUID member){return new KoboldTribes.Tribe(id,"GREEN",member,List.of(member),Optional.empty(),BlockPos.ZERO,"",false,List.of(),List.of(),List.of());}
    private static KoboldTribes load(KoboldTribes.Tribe... tribes){
        var json=KoboldTribes.Tribe.CODEC.listOf().encodeStart(JsonOps.INSTANCE,List.of(tribes)).getOrThrow();
        return KoboldTribes.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
    }
    @Test void claimUsesOneOwnerAndOneTribePerPlayer(){
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),owner=UUID.randomUUID();
        var data=load(tribe(a,UUID.randomUUID()),tribe(b,UUID.randomUUID()));
        assertTrue(data.claim(a,owner,"First tribe"));
        assertFalse(data.claim(a,UUID.randomUUID(),"Stolen"));
        assertFalse(data.claim(b,owner,"Second tribe"));
        assertFalse(data.claim(b,UUID.randomUUID(),"\n"));
        assertEquals("First tribe",data.find(a).orElseThrow().name());
    }
    @Test void jobsRequireBedsAndCannotBeReservedOrCompletedByUnrelatedMembers(){
        UUID id=UUID.randomUUID(),worker=UUID.randomUUID();var data=load(tribe(id,worker));
        data.claim(id,UUID.randomUUID(),"Workers");
        BlockPos target=new BlockPos(5,64,5);
        assertTrue(data.addJob(id,new KoboldTribes.WorkJob(UUID.randomUUID(),KoboldTribes.JobKind.MINE,target,Direction.NORTH,List.of(target,target.above()))));
        assertTrue(data.reserveWork(id,worker,BlockPos.ZERO).isEmpty());
        data.setAssignedBlock(id,new BlockPos(1,64,1),true,true);
        assertTrue(data.reserveWork(id,UUID.randomUUID(),BlockPos.ZERO).isEmpty());
        var reserved=data.reserveWork(id,worker,target).orElseThrow();assertEquals(target,reserved.block());
        data.completeWork(id,UUID.randomUUID(),target);assertEquals(2,data.find(id).orElseThrow().jobs().getFirst().blocks().size());
        data.completeWork(id,worker,target);assertEquals(List.of(target.above()),data.find(id).orElseThrow().jobs().getFirst().blocks());
        data.setFollowing(id,true);assertTrue(data.reserveWork(id,worker,target).isEmpty());
        data.cancelJobAt(id,target);assertTrue(data.find(id).orElseThrow().jobs().isEmpty());
    }
    @Test void blocksCannotBeAssignedToTwoTribesAndStateSurvivesCodecRoundTrip(){
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();var data=load(tribe(a,UUID.randomUUID()),tribe(b,UUID.randomUUID()));
        assertTrue(data.setAssignedBlock(a,BlockPos.ZERO,true,true));
        assertFalse(data.setAssignedBlock(b,BlockPos.ZERO,true,true));
        data.setAssignedBlock(a,BlockPos.ZERO,true,false);
        assertTrue(data.setAssignedBlock(b,BlockPos.ZERO,true,true));
        var restored=KoboldTribes.CODEC.parse(JsonOps.INSTANCE,KoboldTribes.CODEC.encodeStart(JsonOps.INSTANCE,data).getOrThrow()).getOrThrow();
        assertEquals(data.snapshot(),restored.snapshot());
    }
    @Test void miningFootprintMatchesLegacyThreeByThreeByThirtyAndRejectsVerticalDirections(){
        var blocks=TribeWorkPlanner.mine(new BlockPos(10,50,10),Direction.NORTH);
        assertEquals(270,blocks.size());assertEquals(270,blocks.stream().distinct().count());
        assertTrue(blocks.contains(new BlockPos(9,50,-19)));assertTrue(blocks.contains(new BlockPos(11,52,10)));
        assertFalse(blocks.contains(new BlockPos(10,53,10)));
        assertThrows(IllegalArgumentException.class,()->TribeWorkPlanner.mine(BlockPos.ZERO,Direction.UP));
    }
    @Test void treeHasOneWorkerAndDeathReleasesAssignmentsAndLastOwnership(){
        UUID id=UUID.randomUUID(),first=UUID.randomUUID(),second=UUID.randomUUID(),owner=UUID.randomUUID();
        var data=load(tribe(id,first));data.addMember(id,second);data.claim(id,owner,"Workers");
        data.setAssignedBlock(id,BlockPos.ZERO,true,true);data.setAssignedBlock(id,BlockPos.ZERO.east(),true,true);
        BlockPos log=new BlockPos(5,64,5);
        data.addJob(id,new KoboldTribes.WorkJob(UUID.randomUUID(),KoboldTribes.JobKind.TREE,log,Direction.NORTH,List.of(log,log.above())));
        assertTrue(data.reserveWork(id,first,log).isPresent());assertTrue(data.reserveWork(id,second,log).isEmpty());
        data.removeMember(id,first);assertEquals(second,data.find(id).orElseThrow().leader());
        assertTrue(data.reserveWork(id,second,log).isPresent());
        data.removeMember(id,second);assertTrue(data.find(id).isEmpty());assertTrue(data.ownedBy(owner).isEmpty());
    }
}
