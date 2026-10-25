package com.arn.goodmod.world;

import com.arn.goodmod.MinecraftTestBootstrap;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerFormDataTest {
    @BeforeAll static void bootstrap(){MinecraftTestBootstrap.initialize();}
    @Test void switchingFormsRetainsIndependentEquipmentAndSelectionAcrossSaveReload(){
        UUID player=UUID.randomUUID();var data=new PlayerFormData();
        data.select(player,"kobold");
        data.saveForm(player,new PlayerFormData.Form("kobold","$0-0-0-0-0-0-0-0-0-0-0-",List.of(new ItemStack(Items.DIAMOND_SWORD),ItemStack.EMPTY)));
        data.select(player,"bee");data.saveForm(player,new PlayerFormData.Form("bee","",List.of(ItemStack.EMPTY,new ItemStack(Items.BOW))));
        data.grantedFlight(player,true);
        var ops=RegistryOps.create(JsonOps.INSTANCE,RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var restored=PlayerFormData.CODEC.parse(ops,PlayerFormData.CODEC.encodeStart(ops,data).getOrThrow()).getOrThrow();
        assertEquals("bee",restored.profile(player).selected());assertTrue(restored.profile(player).grantedFlight());
        var kobold=restored.profile(player).forms().stream().filter(form->form.kind().equals("kobold")).findFirst().orElseThrow();
        assertTrue(kobold.equipment().getFirst().is(Items.DIAMOND_SWORD));assertTrue(kobold.equipment().get(1).isEmpty());
        assertEquals(2,restored.profile(player).forms().size());
    }
    @Test void snapshotCopiesEquipmentAndUpdatingOneFormDoesNotAppendDuplicateKinds(){
        UUID player=UUID.randomUUID();var data=new PlayerFormData();var stack=new ItemStack(Items.BOW);
        data.saveForm(player,new PlayerFormData.Form("bee","",List.of(stack)));stack.shrink(1);
        assertFalse(data.profile(player).forms().getFirst().equipment().getFirst().isEmpty());
        data.saveForm(player,new PlayerFormData.Form("bee","",List.of(ItemStack.EMPTY)));
        assertEquals(1,data.profile(player).forms().size());assertTrue(data.profile(player).forms().getFirst().equipment().getFirst().isEmpty());
        assertThrows(IllegalArgumentException.class,()->new PlayerFormData.Form("bee","",java.util.Collections.nCopies(8,ItemStack.EMPTY)));
    }
}
