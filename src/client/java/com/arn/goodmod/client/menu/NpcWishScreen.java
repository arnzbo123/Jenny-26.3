package com.arn.goodmod.client.menu;

import com.arn.goodmod.network.NpcWishPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class NpcWishScreen extends Screen {
    private final NpcWishPayload opening;
    public NpcWishScreen(NpcWishPayload opening){super(Component.literal("Choose a wish"));this.opening=opening;}
    @Override protected void init(){
        addRenderableWidget(Button.builder(Component.literal("Wealth"),b->send(NpcWishPayload.Action.WEALTH)).bounds(width/2-100,height/2-15,200,20).build());
        addRenderableWidget(Button.builder(Component.literal("Dismiss"),b->send(NpcWishPayload.Action.DISMISS)).bounds(width/2-100,height/2+10,200,20).build());
    }
    private void send(NpcWishPayload.Action action){ClientPlayNetworking.send(new NpcWishPayload(opening.entityId(),opening.entityUuid(),action));onClose();}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float delta){
        super.extractRenderState(graphics,x,y,delta);graphics.text(font,title,width/2-100,height/2-40,0xffffffff,true);
    }
    @Override public boolean isPauseScreen(){return false;}
}
