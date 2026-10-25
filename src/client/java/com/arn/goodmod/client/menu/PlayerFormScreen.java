package com.arn.goodmod.client.menu;

import com.arn.goodmod.entity.NpcKind;
import com.arn.goodmod.network.PlayerFormPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PlayerFormScreen extends Screen {
    private final PlayerFormPayload opening;
    public PlayerFormScreen(PlayerFormPayload opening){super(Component.literal("Choose your form"));this.opening=opening;}
    @Override protected void init(){
        int row=0,left=width/2-150,top=height/2-78;
        for(NpcKind kind:NpcKind.values())if(kind.hasPlayerForm()){
            addRenderableWidget(Button.builder(Component.translatable("entity.sexmod."+kind.id()),b->select(kind.id())).bounds(left+(row%2)*154,top+(row/2)*24,146,20).build());row++;
        }
        addRenderableWidget(Button.builder(Component.literal("Player"),b->select("player")).bounds(left,top+125,300,20).build());
    }
    private void select(String kind){ClientPlayNetworking.send(new PlayerFormPayload(opening.token(),kind));onClose();}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float delta){super.extractRenderState(graphics,x,y,delta);graphics.text(font,title,width/2-150,height/2-100,0xffffffff,true);}
    @Override public boolean isPauseScreen(){return false;}
}
