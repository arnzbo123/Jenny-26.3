package com.arn.goodmod.network;

import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ModelSharingTest {
    @TempDir Path directory;
    private static final Map<ModelFile.Kind,byte[]> CONTENT=Map.of(ModelFile.Kind.GEOMETRY,"geometry".getBytes(),ModelFile.Kind.TEXTURE,"texture".getBytes(),ModelFile.Kind.CONFIG,"configuration".getBytes());
    private static List<ModelFile> files(String model)throws Exception{
        var result=new ArrayList<ModelFile>();
        for(var kind:ModelFile.Kind.values()){byte[] bytes=CONTENT.get(kind);result.add(new ModelFile(model,kind,bytes.length,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))));}
        return result;
    }
    @Test void sharingDefaultsOffAndApprovalIsForExactFilesUntilCleared()throws Exception{
        var files=files("Hat");var policy=new ModelSharePolicy();
        assertTrue(policy.manifest().isEmpty());assertFalse(policy.permits(files.getFirst()));
        policy.approve("Hat",files);assertTrue(policy.permits(files.getFirst()));
        var old=files.getFirst();assertFalse(policy.permits(new ModelFile(old.model(),old.kind(),old.length(),"0".repeat(64))));
        assertThrows(IllegalArgumentException.class,()->policy.approve("Other",files));
        policy.clear();assertFalse(policy.permits(files.getFirst()));
    }
    @Test void metadataRejectsPathsIncompleteTripletsCollisionsAndOversizedPayloads()throws Exception{
        assertThrows(IllegalArgumentException.class,()->new ModelFile("../private",ModelFile.Kind.CONFIG,1,"0".repeat(64)));
        assertThrows(IllegalArgumentException.class,()->new ModelFile("Hat.",ModelFile.Kind.CONFIG,1,"0".repeat(64)));
        assertThrows(IllegalArgumentException.class,()->new ModelFile("Hat",ModelFile.Kind.CONFIG,65537,"0".repeat(64)));
        var files=files("Hat");assertThrows(IllegalArgumentException.class,()->new ModelManifestPayload(files.subList(0,2)));
        var duplicate=new ArrayList<>(files);duplicate.addAll(files("hat"));assertThrows(IllegalArgumentException.class,()->new ModelManifestPayload(duplicate));
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try{buffer.writeVarInt(193);assertThrows(IllegalArgumentException.class,()->ModelManifestPayload.CODEC.decode(buffer));}finally{buffer.release();}
    }
    @Test void stagedChunksOnlyInstallAfterAllHashesMatchAndUseWholeModelReplacement()throws Exception{
        Path root=directory.resolve("trusted-server");var files=files("Hat");
        try(var staging=new ModelDownloadStaging(root,files)){
            for(ModelFile file:files){byte[] bytes=CONTENT.get(file.kind());staging.accept(file,0,new byte[]{bytes[0]},false);staging.accept(file,1,java.util.Arrays.copyOfRange(bytes,1,bytes.length),true);}
            assertFalse(Files.exists(root.resolve("Hat")));staging.commit();
        }
        for(ModelFile file:files)assertArrayEquals(CONTENT.get(file.kind()),Files.readAllBytes(root.resolve("Hat").resolve(file.fileName())));
        try(var cached=new ModelDownloadStaging(root,files)){assertTrue(cached.pending().isEmpty());cached.commit();}
    }
    @Test void badChecksumOrOffsetCannotReplaceInstalledFiles()throws Exception{
        Path root=directory.resolve("trusted-server"),old=Files.createDirectories(root.resolve("Hat"));
        Files.writeString(old.resolve("Hat.cfg"),"keep existing config");var files=files("Hat");
        try(var staging=new ModelDownloadStaging(root,files)){
            ModelFile first=files.getFirst();assertThrows(java.io.IOException.class,()->staging.accept(first,1,new byte[]{1},false));
        }
        try(var staging=new ModelDownloadStaging(root,files)){
            ModelFile first=files.getFirst();assertThrows(java.io.IOException.class,()->staging.accept(first,0,new byte[first.length()],true));
            assertThrows(java.io.IOException.class,staging::commit);
        }
        assertEquals("keep existing config",Files.readString(old.resolve("Hat.cfg")));
    }
    @Test void metadataCodecRoundTripsOnlyBoundedFixedTypes()throws Exception{
        var expected=new ModelManifestPayload(files("Hat"));var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try{ModelManifestPayload.CODEC.encode(buffer,expected);assertEquals(expected,ModelManifestPayload.CODEC.decode(buffer));assertEquals(0,buffer.readableBytes());}finally{buffer.release();}
    }
}
