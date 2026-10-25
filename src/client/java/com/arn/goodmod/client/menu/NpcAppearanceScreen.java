package com.arn.goodmod.client.menu;

import com.arn.goodmod.entity.GoodcraftNpc;
import com.arn.goodmod.menu.NpcModelCode;
import com.arn.goodmod.network.NpcAppearancePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class NpcAppearanceScreen extends Screen {
    private final GoodcraftNpc npc;
    private EditBox code;
    private String error = "";
    public NpcAppearanceScreen(GoodcraftNpc npc) { super(Component.literal("NPC appearance")); this.npc = npc; }
    @Override protected void init() {
        int left = width / 2 - 150, top = height / 2 - 30;
        code = addRenderableWidget(new EditBox(font, left, top, 300, 20, Component.literal("Model code")));
        code.setMaxLength(NpcModelCode.MAX_LENGTH); code.setValue(npc.getModelCode());
        addRenderableWidget(Button.builder(Component.literal("Copy"), button -> minecraft.keyboardHandler.setClipboard(code.getValue())).bounds(left, top + 30, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Apply"), button -> {
            try {
                NpcModelCode parsed = NpcModelCode.parse(code.getValue(), npc.getNpcKind());
                ClientPlayNetworking.send(new NpcAppearancePayload(npc.getId(), npc.getUUID(), parsed.encode()));
                onClose();
            } catch (IllegalArgumentException invalid) { error = invalid.getMessage(); }
        }).bounds(left + 102, top + 30, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose()).bounds(left + 205, top + 30, 95, 20).build());
        setInitialFocus(code);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        super.extractRenderState(graphics,x,y,delta);
        graphics.text(font, title, width/2-150, height/2-65, 0xffffffff, true);
        String hint = NpcModelCode.bounds(npc.getNpcKind()).length==0?"Model names: Name#OtherName#":"Built-in code: $" + java.util.Arrays.stream(NpcModelCode.bounds(npc.getNpcKind())).mapToObj(bound -> "0–" + (bound - 1)).collect(java.util.stream.Collectors.joining("-"));
        graphics.text(font, Component.literal(hint), width/2-150, height/2-48, 0xffdddddd, false);
        if (!error.isEmpty()) graphics.text(font, Component.literal(error), width/2-150, height/2+62, 0xffff5555, false);
    }
    @Override public boolean isPauseScreen() { return false; }
}
