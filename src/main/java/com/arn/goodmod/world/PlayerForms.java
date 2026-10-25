package com.arn.goodmod.world;

import com.arn.goodmod.GoodcraftMod;
import com.arn.goodmod.entity.GoodcraftNpc;
import com.arn.goodmod.entity.NpcKind;
import com.arn.goodmod.network.PlayerFormPayload;
import com.arn.goodmod.registry.GoodcraftEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class PlayerForms {
    private record Selection(UUID token,long expires){}
    private static final Map<UUID,Selection> selections=new HashMap<>();
    private static final Map<UUID,GoodcraftNpc> active=new HashMap<>();
    private PlayerForms(){}
    public static void initialize(){
        PayloadTypeRegistry.serverboundPlay().register(PlayerFormPayload.TYPE,PlayerFormPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PlayerFormPayload.TYPE,PlayerFormPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PlayerFormPayload.TYPE,(payload,context)->context.server().execute(()->select(context.player(),payload)));
        ServerTickEvents.END_SERVER_TICK.register(server->{for(ServerPlayer player:server.getPlayerList().getPlayers())tick(player);});
        ServerPlayerEvents.LEAVE.register(player->{capture(player);discard(player);updateFlight(player,null);selections.remove(player.getUUID());});
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer,newPlayer,alive)->{capture(oldPlayer);discard(oldPlayer);selections.remove(oldPlayer.getUUID());});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{for(ServerPlayer player:server.getPlayerList().getPlayers()){capture(player);updateFlight(player,null);}active.values().forEach(GoodcraftNpc::discard);active.clear();selections.clear();});
        ServerLifecycleEvents.BEFORE_SAVE.register((server,flush,force)->{for(ServerPlayer player:server.getPlayerList().getPlayers())capture(player);});
    }
    public static Optional<GoodcraftNpc> get(ServerPlayer player){return Optional.ofNullable(active.get(player.getUUID())).filter(npc->!npc.isRemoved());}
    public static void openSelection(ServerPlayer player){
        if(!player.isAlive()||player.isSpectator())return;
        UUID token=UUID.randomUUID();selections.put(player.getUUID(),new Selection(token,player.level().getServer().getTickCount()+1200L));
        ServerPlayNetworking.send(player,new PlayerFormPayload(token,PlayerFormData.get(player.level().getServer()).profile(player.getUUID()).selected()));
    }
    private static void select(ServerPlayer player,PlayerFormPayload request){
        Selection selection=selections.get(player.getUUID());
        if(selection==null||!selection.token().equals(request.token())||selection.expires()<player.level().getServer().getTickCount()||!player.isAlive()||player.isSpectator())return;
        Optional<NpcKind> kind=NpcKind.byId(request.kind());
        if(!request.kind().equals("player")&&(kind.isEmpty()||!kind.get().hasPlayerForm()))return;
        selections.remove(player.getUUID());capture(player);player.closeContainer();discard(player);
        PlayerFormData.get(player.level().getServer()).select(player.getUUID(),request.kind());tick(player);
    }
    private static void tick(ServerPlayer player){
        var data=PlayerFormData.get(player.level().getServer());var profile=data.profile(player.getUUID());
        NpcKind kind=NpcKind.byId(profile.selected()).filter(NpcKind::hasPlayerForm).orElse(null);
        if(!player.isAlive()||player.isSpectator()||kind==null){capture(player);discard(player);updateFlight(player,null);return;}
        GoodcraftNpc proxy=active.get(player.getUUID());
        if(proxy!=null&&(proxy.isRemoved()||proxy.level()!=player.level()||proxy.getNpcKind()!=kind)){capture(player);discard(player);proxy=null;}
        if(proxy==null){
            profile=data.profile(player.getUUID());
            proxy=GoodcraftEntities.createPlayerForm(kind,player.level(),player.getUUID());if(proxy==null)return;
            proxy.getEquipmentInventory().clearContent();
            var saved=profile.forms().stream().filter(form->form.kind().equals(kind.id())).findFirst();
            if(saved.isPresent()){
                proxy.setModelCode(saved.get().model());
                for(int i=0;i<saved.get().equipment().size();i++)proxy.getEquipmentInventory().setItem(i,saved.get().equipment().get(i).copy());
            }
            proxy.setPos(player.position());proxy.setPermanentlyInvulnerable(true);proxy.setPersistenceRequired();
            if(!player.level().addFreshEntity(proxy))return;
            active.put(player.getUUID(),proxy);
        }
        proxy.setPos(player.position());proxy.setYRot(player.getYRot());proxy.setXRot(player.getXRot());
        proxy.setDeltaMovement(player.getDeltaMovement());proxy.setSprinting(player.isSprinting());proxy.setShiftKeyDown(player.isShiftKeyDown());
        if(player.tickCount%20==0)capture(player);
        updateFlight(player,kind);
    }
    private static void capture(ServerPlayer player){
        GoodcraftNpc proxy=active.get(player.getUUID());if(proxy==null)return;
        var equipment=new ArrayList<ItemStack>();for(int i=0;i<7;i++)equipment.add(proxy.getEquipmentInventory().getItem(i).copy());
        PlayerFormData.get(player.level().getServer()).saveForm(player.getUUID(),new PlayerFormData.Form(proxy.getNpcKind().id(),proxy.getModelCode(),equipment));
    }
    private static void discard(ServerPlayer player){GoodcraftNpc proxy=active.remove(player.getUUID());if(proxy!=null){proxy.getEquipmentInventory().clearContent();proxy.discard();}}
    private static void updateFlight(ServerPlayer player,NpcKind kind){
        var data=PlayerFormData.get(player.level().getServer());var profile=data.profile(player.getUUID());
        boolean wants=GoodcraftMod.config().allowFlying()&&(kind==NpcKind.BEE||kind==NpcKind.ALLIE||kind==NpcKind.GALATH);
        if(wants&&!player.getAbilities().mayfly){player.getAbilities().mayfly=true;data.grantedFlight(player.getUUID(),true);player.onUpdateAbilities();}
        if(!wants&&profile.grantedFlight()){
            if(!player.isCreative()&&!player.isSpectator()){player.getAbilities().mayfly=false;player.getAbilities().flying=false;player.onUpdateAbilities();}
            data.grantedFlight(player.getUUID(),false);
        }
    }
}
