package com.arn.goodmod.client.command;

import com.arn.goodmod.GoodcraftMod;
import com.arn.goodmod.client.render.GoodcraftCustomModels;
import com.arn.goodmod.network.*;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Requests files only from an explicitly trusted endpoint; server sharing also defaults off. */
public final class ClientModelDownloads {
    private static List<ModelFile> manifest=List.of();
    private static final ArrayDeque<ModelFile> queue=new ArrayDeque<>();
    private static ModelDownloadStaging staging;
    private static UUID request;
    private static String endpoint;
    private static long lastChunkNanos;
    private ClientModelDownloads(){}
    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(ModelManifestPayload.TYPE,(payload,context)->context.client().execute(()->{cancel();manifest=payload.files();startTrustedDownloads();}));
        ClientPlayNetworking.registerGlobalReceiver(ModelChunkPayload.TYPE,(payload,context)->context.client().execute(()->receive(payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{cancel();manifest=List.of();});
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(staging!=null&&(System.nanoTime()-lastChunkNanos>60_000_000_000L||!trustedCurrentEndpoint()))fail("Custom model download stopped or timed out.");});
    }
    public static void startTrustedDownloads(){
        Minecraft client=Minecraft.getInstance();
        if(client.getCurrentServer()==null||manifest.isEmpty()||GoodcraftCustomModels.currentRoot().isEmpty())return;
        cancel();endpoint=currentEndpoint();
        try{
            staging=new ModelDownloadStaging(GoodcraftCustomModels.currentRoot().orElseThrow(),manifest);
            queue.addAll(staging.pending());lastChunkNanos=System.nanoTime();next();
        }catch(IOException|RuntimeException invalid){fail("Cannot prepare custom model download: "+invalid.getMessage());}
    }
    private static String currentEndpoint(){var server=Minecraft.getInstance().getCurrentServer();return server==null?"":server.ip.trim().toLowerCase(java.util.Locale.ROOT);}
    private static boolean trustedCurrentEndpoint(){return endpoint!=null&&endpoint.equals(currentEndpoint())&&GoodcraftCustomModels.currentRoot().isPresent();}
    private static void next()throws IOException{
        if(!trustedCurrentEndpoint())throw new IOException("Server trust changed");
        if(queue.isEmpty()){
            staging.commit();staging.close();staging=null;request=null;
            int loaded=GoodcraftCustomModels.reload();message("Custom models updated ("+loaded+" loaded).");return;
        }
        request=UUID.randomUUID();lastChunkNanos=System.nanoTime();ClientPlayNetworking.send(new ModelRequestPayload(request,queue.peekFirst()));
    }
    private static void receive(ModelChunkPayload payload){
        if(staging==null||request==null||!request.equals(payload.request()))return;
        if(!trustedCurrentEndpoint()){fail("Server trust changed; model download cancelled.");return;}
        try{
            staging.accept(queue.peekFirst(),payload.offset(),payload.bytes(),payload.last());lastChunkNanos=System.nanoTime();
            if(payload.last()){queue.removeFirst();request=null;next();}
        }catch(IOException|RuntimeException invalid){fail("Custom model download rejected: "+invalid.getMessage());}
    }
    private static void message(String text){var player=Minecraft.getInstance().player;if(player!=null)player.sendSystemMessage(Component.literal(text));}
    private static void fail(String error){GoodcraftMod.LOGGER.warn(error);message(error);cancel();}
    private static void cancel(){
        if(staging!=null)try{staging.close();}catch(IOException error){GoodcraftMod.LOGGER.warn("Cannot remove incomplete model download",error);}
        staging=null;queue.clear();request=null;endpoint=null;
    }
}
