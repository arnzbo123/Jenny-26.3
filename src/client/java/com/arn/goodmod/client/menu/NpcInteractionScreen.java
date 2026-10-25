package com.arn.goodmod.client.menu;

import com.arn.goodmod.entity.GoodcraftNpc;
import com.arn.goodmod.entity.MovementMode;
import com.arn.goodmod.menu.NpcMenuView;
import com.arn.goodmod.network.NpcActionPayload;
import com.arn.goodmod.network.NpcCommandPayload;
import com.arn.goodmod.network.OpenNpcInteractionPayload;
import com.arn.goodmod.network.OpenNpcMenuPayload;
import com.arn.goodmod.network.SceneActionPayload;
import com.arn.goodmod.scene.SceneMenus;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The original m overlay: equipment panel and expanding icon buttons on the left for companions, her actions
 * stacked from the bottom right with their price drawn as a tooltip box ("3x" and the item) beside each button.
 * Nothing pauses or darkens the world, as in the original.
 */
public final class NpcInteractionScreen extends Screen {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("sexmod", "textures/gui/girlinventory.png");
    /** m.a/j/b/e: label, icon U, label padding and hover growth; follow and stop-follow share a row. */
    private record Icon(String label, int u, int padding, int growth) {}
    private static final Icon FOLLOW = new Icon("action.names.followme", 64, 4, 50);
    private static final Icon STOP_FOLLOW = new Icon("action.names.stopfollowme", 80, 4, 90);
    private static final Icon GO_HOME = new Icon("action.names.gohome", 47, 5, 50);
    private static final Icon SET_HOME = new Icon("action.names.setnewhome", 32, 5, 80);
    private static final Icon EQUIPMENT = new Icon("action.names.equipment", 96, 4, 60);
    private static final int[] EQUIPMENT_Y = {68, 87, 109, 127, 146, 166};

    private final OpenNpcInteractionPayload opening;
    private final List<Icon> rows = new ArrayList<>();
    private final List<Button> iconButtons = new ArrayList<>();
    private final List<Button> actionButtons = new ArrayList<>();
    private float[] grown = new float[0];
    private final long openedAt = net.minecraft.util.Util.getMillis();
    private long lastFrame = openedAt;
    private float slide, expand;

    public NpcInteractionScreen(OpenNpcInteractionPayload opening) {
        super(Component.empty());
        this.opening = opening;
    }

    private GoodcraftNpc npc() {
        return minecraft.level != null && minecraft.level.getEntity(opening.entityId()) instanceof GoodcraftNpc npc
                && npc.getUUID().equals(opening.entityUuid()) && npc.isAlive() ? npc : null;
    }

    private Icon icon(int row) {
        Icon icon = rows.get(row);
        if (icon != FOLLOW) return icon;
        GoodcraftNpc npc = npc();
        return npc != null && npc.getMovementMode() == MovementMode.FOLLOW ? STOP_FOLLOW : FOLLOW;
    }

    @Override protected void init() {
        rows.clear(); iconButtons.clear(); actionButtons.clear();
        if (opening.icons()) rows.addAll(List.of(FOLLOW, GO_HOME, SET_HOME, EQUIPMENT));
        else if (opening.storage()) rows.add(EQUIPMENT);
        grown = new float[rows.size()];
        for (int row = 0; row < rows.size(); row++) {
            int index = row;
            iconButtons.add(addRenderableWidget(Button.builder(Component.empty(), button -> pressIcon(index)).bounds(36, 70 + row * 30, 0, 20).build()));
        }
        for (int i = 0; i < opening.actions().size(); i++) {
            SceneMenus.Action action = opening.actions().get(i);
            actionButtons.add(addRenderableWidget(Button.builder(Component.translatable(action.label()), button -> choose(action))
                    .bounds(width + 30, height - 70 - i * 30, 100, 20).build()));
        }
        layout();
    }

    private void pressIcon(int row) {
        Icon icon = rows.get(row);
        if (icon == EQUIPMENT) {
            NpcMenuView view = opening.icons() ? NpcMenuView.EQUIPMENT : NpcMenuView.STORAGE;
            ClientPlayNetworking.send(new OpenNpcMenuPayload(opening.entityId(), opening.entityUuid(), view));
            return;
        }
        NpcActionPayload.Action action = icon == FOLLOW ? (icon(row) == STOP_FOLLOW ? NpcActionPayload.Action.WANDER : NpcActionPayload.Action.FOLLOW)
                : icon == GO_HOME ? NpcActionPayload.Action.GO_HOME : NpcActionPayload.Action.SET_HOME;
        ClientPlayNetworking.send(new NpcCommandPayload(opening.entityId(), opening.entityUuid(), action));
        onClose();
    }

    /** m.a(GuiButton): the server checks the price and takes it, as packet t did. */
    private void choose(SceneMenus.Action action) {
        ClientPlayNetworking.send(new SceneActionPayload(opening.entityId(), opening.entityUuid(), action.key()));
        onClose();
    }

    /** m.drawScreen: the panel slides in over five ticks, then the icon column and prices open over five more. */
    private void layout() {
        long now = net.minecraft.util.Util.getMillis();
        float frameTicks = (now - lastFrame) / 50f;
        lastFrame = now;
        slide = Mth.clamp((now - openedAt) / 250f, 0, 1);
        expand = Mth.clamp((now - openedAt - 250) / 250f, 0, 1);
        int base = (int)Mth.lerp(expand, 0, 23);
        for (int row = 0; row < iconButtons.size(); row++) {
            Button button = iconButtons.get(row);
            Icon icon = icon(row);
            // Original grows/shrinks 7 px per frame; scaled here to 60 frames per second.
            float step = 7 * frameTicks * 3;
            grown[row] = button.isHovered() ? Math.min(icon.growth(), grown[row] + step) : Math.max(0, grown[row] - step);
            button.visible = expand > 0;
            button.setWidth(Math.max(1, base + (int)grown[row]));
            button.setMessage(grown[row] <= 14 ? Component.empty() : Component.translatable(icon.label()).append(" ".repeat(icon.padding())));
        }
        int right = (int)Mth.lerp(slide, -30, 120);
        for (Button button : actionButtons) button.setX(width - right);
    }

    @Override public void tick() {
        if (npc() == null) onClose();
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {}

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        layout();
        if (opening.icons()) {
            int panel = (int)Mth.lerp(slide, -30, 120);
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, panel - 113, 60, 0, 0, 32, 130, 256, 256);
            for (int i = 0; i < Math.min(6, opening.equipment().size()); i++) graphics.item(opening.equipment().get(i), panel - 105, EQUIPMENT_Y[i]);
        }
        drawPrices(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (expand <= 0) return;
        int base = (int)Mth.lerp(expand, 0, 23);
        for (int row = 0; row < iconButtons.size(); row++) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, (int)grown[row] + 35 - 18 + base, 72 + row * 30, icon(row).u(), 0, 16, 16, 256, 256);
        }
    }

    /** m.a(List, x, y, FontRenderer): a vanilla-tooltip box with "Nx" whose padding holds the item icon. */
    private void drawPrices(GuiGraphicsExtractor graphics) {
        if (expand <= 0) return;
        int textX = width - (int)Mth.lerp(expand, 115, 161) + 12, itemX = width - (int)Mth.lerp(expand, 91, 137);
        for (int i = 0; i < opening.actions().size(); i++) {
            var cost = opening.actions().get(i).cost();
            if (cost.isEmpty()) continue;
            String text = cost.getCount() + "x    ";
            int textY = height - 52 - 30 * i - 12, textWidth = font.width(text);
            int left = textX - 3, top = textY - 4, right = textX + textWidth + 3, bottom = textY + 8 + 4;
            graphics.fill(left, top + 1, right, bottom - 1, 0xF0100010);
            graphics.fill(left + 1, top, right - 1, top + 1, 0xF0100010);
            graphics.fill(left + 1, bottom - 1, right - 1, bottom, 0xF0100010);
            graphics.fillGradient(left + 1, top + 1, left + 2, bottom - 1, 0x505000FF, 0x5028007F);
            graphics.fillGradient(right - 2, top + 1, right - 1, bottom - 1, 0x505000FF, 0x5028007F);
            graphics.fill(left + 1, top + 1, right - 1, top + 2, 0x505000FF);
            graphics.fill(left + 1, bottom - 2, right - 1, bottom - 1, 0x5028007F);
            graphics.text(font, text, textX, textY, 0xFFFFFFFF, true);
            graphics.item(cost, itemX, height - 68 - 30 * i);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
