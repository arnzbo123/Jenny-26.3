package com.arn.goodmod.client.menu;

import com.arn.goodmod.network.TribeControlPayload;
import com.arn.goodmod.network.TribeScreenPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TribeCommandScreen extends Screen {
    private final TribeScreenPayload state;
    private EditBox name;
    public TribeCommandScreen(TribeScreenPayload state){super(Component.literal(state.name().isBlank()?"Form a tribe":state.name()+" tribe"));this.state=state;}
    @Override protected void init(){
        int left=width/2-140,top=height/2-50;
        if(!state.owned()) {
            name=addRenderableWidget(new EditBox(font,left,top,280,20,Component.literal("Tribe name")));name.setMaxLength(48);name.setValue(state.name());
            addRenderableWidget(Button.builder(Component.literal("Claim tribe"),b->send(TribeControlPayload.Action.CLAIM)).bounds(left,top+28,280,20).build());
        } else {
            TribeControlPayload.Action[] actions={state.following()?TribeControlPayload.Action.WAIT:TribeControlPayload.Action.FOLLOW,
                    TribeControlPayload.Action.ASSIGN,TribeControlPayload.Action.UNASSIGN,TribeControlPayload.Action.TREE,TribeControlPayload.Action.MINE,TribeControlPayload.Action.CANCEL};
            String[] labels={state.following()?"Work at home":"Follow me","Assign bed / chest","Unassign bed / chest","Harvest tree","Mine tunnel","Cancel work here"};
            for(int i=0;i<actions.length;i++) {
                var action=actions[i];
                addRenderableWidget(Button.builder(Component.literal(labels[i]),b->send(action)).bounds(left+(i%2)*144,top+(i/2)*24,136,20).build());
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Close"),b->onClose()).bounds(left,top+85,280,20).build());
    }
    private void send(TribeControlPayload.Action action){ClientPlayNetworking.send(new TribeControlPayload(state.tribe(),action,state.target(),state.direction(),name==null?state.name():name.getValue()));}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float delta){
        super.extractRenderState(graphics,x,y,delta);
        int left=width/2-140,top=height/2-85;
        graphics.text(font,title,left,top,0xffffffff,true);
        graphics.text(font,Component.literal(state.members()+" members · "+state.beds()+" beds · "+state.chests()+" chests · "+state.jobs()+" jobs"),left,top+15,0xffdddddd,false);
        if(!state.status().isEmpty())graphics.text(font,Component.literal(state.status()),left,height/2+70,0xffffff55,false);
    }
    @Override public boolean isPauseScreen(){return false;}
}
