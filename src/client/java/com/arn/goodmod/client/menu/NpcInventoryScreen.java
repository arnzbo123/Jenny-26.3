package com.arn.goodmod.client.menu;

import com.arn.goodmod.inventory.NpcEquipmentSlot;
import com.arn.goodmod.menu.NpcInventoryMenu;
import com.arn.goodmod.menu.NpcMenuView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** fb/az equipment screens on girlinventory.png, and ek's chest-style storage; vanilla click/drag/shift-click. */
public final class NpcInventoryScreen extends AbstractContainerScreen<NpcInventoryMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("sexmod", "textures/gui/girlinventory.png");
    private static final Identifier CHEST = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

    public NpcInventoryScreen(NpcInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        inventoryLabelY = 73;
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        if (menu.opening().view() == NpcMenuView.EQUIPMENT) {
            // fb draws the 176x114 sheet 52 px below the container top; az uses the variant with Luna's rod slot.
            boolean rod = menu.npcSlotCount() == 7;
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos + 52, rod ? 80 : 33, rod ? 142 : 16, 176, 114, 256, 256);
        } else {
            graphics.blit(RenderPipelines.GUI_TEXTURED, CHEST, leftPos, topPos, 0, 0, 176, 71, 256, 256);
            graphics.blit(RenderPipelines.GUI_TEXTURED, CHEST, leftPos, topPos + 70, 0, 126, 176, 96, 256, 256);
        }
    }

    /** The original equipment screens draw no title or inventory captions. */
    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (menu.opening().view() == NpcMenuView.STORAGE) super.extractLabels(graphics, mouseX, mouseY);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        extractTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot instanceof NpcEquipmentSlot slot && !slot.hasItem()) {
            graphics.setTooltipForNextFrame(Component.literal(slot.rule().name().toLowerCase(java.util.Locale.ROOT)), mouseX, mouseY);
        }
    }
}
