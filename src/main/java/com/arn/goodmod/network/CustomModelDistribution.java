package com.arn.goodmod.network;

import com.arn.goodmod.world.CustomModelCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/** No files are shared until an owner explicitly confirms an individual model. */
public final class CustomModelDistribution {
    private static final ModelSharePolicy policy=new ModelSharePolicy();
    private static final Map<UUID,Transfer> transfers=new HashMap<>();
    private static final class Transfer implements AutoCloseable {
        final ServerPlayer player;final UUID id;final ModelFile file;final InputStream stream;int offset;
        Transfer(ServerPlayer player,UUID id,ModelFile file,InputStream stream){this.player=player;this.id=id;this.file=file;this.stream=stream;}
        @Override public void close(){try{stream.close();}catch(IOException ignored){}}
    }
    private CustomModelDistribution(){}
    public static void initialize(){
        PayloadTypeRegistry.clientboundPlay().register(ModelManifestPayload.TYPE,ModelManifestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ModelChunkPayload.TYPE,ModelChunkPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ModelRequestPayload.TYPE,ModelRequestPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ModelRequestPayload.TYPE,(payload,context)->context.server().execute(()->request(context.player(),payload)));
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->publish(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{var transfer=transfers.remove(handler.player.getUUID());if(transfer!=null)transfer.close();});
        ServerTickEvents.END_SERVER_TICK.register(server->tick());
        ServerLifecycleEvents.SERVER_STOPPING.register(server->reset(server));
    }
    public static void reset(MinecraftServer server){
        policy.clear();transfers.values().forEach(Transfer::close);transfers.clear();
        for(ServerPlayer player:server.getPlayerList().getPlayers())publish(player);
    }
    public static int approve(CommandSourceStack source,String name,boolean confirm){
        if(!source.permissions().hasPermission(Permissions.COMMANDS_OWNER))return 0;
        try{
            var files=CustomModelCatalog.describe(name);
            if(!confirm){
                long size=files.stream().mapToLong(ModelFile::length).sum();
                source.sendSuccess(()->Component.literal("Sharing "+name+" exports its .geo.json, .png and .cfg files ("+size+" bytes) to clients that trust this server. Confirm with /sharecustommodel "+name+" confirm. Sharing resets on reload or restart."),false);return 0;
            }
            policy.approve(name,files);
            transfers.values().forEach(Transfer::close);transfers.clear();
            for(ServerPlayer player:source.getServer().getPlayerList().getPlayers())publish(player);
            source.sendSuccess(()->Component.literal("Model sharing enabled for "+name+" for this server session."),true);return 1;
        }catch(IOException|IllegalArgumentException invalid){source.sendFailure(Component.literal(invalid.getMessage()));return 0;}
    }
    private static void publish(ServerPlayer player){ServerPlayNetworking.send(player,new ModelManifestPayload(policy.manifest()));}
    private static void request(ServerPlayer player,ModelRequestPayload payload){
        if(!policy.permits(payload.file())||transfers.containsKey(player.getUUID()))return;
        try{
            var file=CustomModelCatalog.resolveFile(payload.file().model(),payload.file().kind());
            if(Files.size(file)!=payload.file().length()||!ModelFile.hash(file).equals(payload.file().sha256()))throw new IOException("Shared file changed; owner must approve it again");
            transfers.put(player.getUUID(),new Transfer(player,payload.request(),payload.file(),Files.newInputStream(file)));
        }catch(IOException invalid){ServerPlayNetworking.send(player,new ModelChunkPayload(payload.request(),0,new byte[0],true));}
    }
    private static void tick(){
        var iterator=transfers.entrySet().iterator();
        while(iterator.hasNext()){
            var transfer=iterator.next().getValue();boolean finished=false;
            try{
                for(int i=0;i<2&&!finished;i++){
                    byte[] bytes=transfer.stream.readNBytes(Math.min(ModelFile.CHUNK_SIZE,transfer.file.length()-transfer.offset));
                    finished=bytes.length==0||transfer.offset+bytes.length==transfer.file.length();
                    ServerPlayNetworking.send(transfer.player,new ModelChunkPayload(transfer.id,transfer.offset,bytes,finished));transfer.offset+=bytes.length;
                }
            }catch(IOException invalid){ServerPlayNetworking.send(transfer.player,new ModelChunkPayload(transfer.id,transfer.offset,new byte[0],true));finished=true;}
            if(finished){transfer.close();iterator.remove();}
        }
    }
}
