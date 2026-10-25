package com.arn.goodmod.network;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ModelManifestPayload(List<ModelFile> files) implements CustomPacketPayload {
    public static final Type<ModelManifestPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("sexmod","model_manifest"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ModelManifestPayload> CODEC=StreamCodec.of(
            (buf,p)->{buf.writeVarInt(p.files().size());for(ModelFile file:p.files()){buf.writeUtf(file.model(),128);buf.writeEnum(file.kind());buf.writeVarInt(file.length());buf.writeUtf(file.sha256(),64);}},
            buf->{int count=buf.readVarInt();if(count<0||count>ModelFile.MAX_MODELS*3)throw new IllegalArgumentException("Too many model files");
                var files=new ArrayList<ModelFile>(count);for(int i=0;i<count;i++)files.add(new ModelFile(buf.readUtf(128),buf.readEnum(ModelFile.Kind.class),buf.readVarInt(),buf.readUtf(64)));return new ModelManifestPayload(files);});
    public ModelManifestPayload {
        files=List.copyOf(files);if(files.size()>ModelFile.MAX_MODELS*3)throw new IllegalArgumentException("Too many model files");
        long total=0;var unique=new HashSet<String>();var models=new HashSet<String>();
        for(ModelFile file:files){total+=file.length();models.add(file.model());if(!unique.add((file.model()+"/"+file.fileName()).toLowerCase(java.util.Locale.ROOT)))throw new IllegalArgumentException("Duplicate model file");}
        if(total>ModelFile.MAX_TOTAL||models.size()>ModelFile.MAX_MODELS||files.size()!=models.size()*3)throw new IllegalArgumentException("Invalid model manifest");
    }
    @Override public Type<ModelManifestPayload> type(){return TYPE;}
}
