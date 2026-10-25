package com.arn.goodmod.world;

import com.arn.goodmod.entity.NpcKind;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Player-form choices and equipment survive proxy replacement, logout and world reload. */
public final class PlayerFormData extends SavedData {
    public record Form(String kind,String model,List<ItemStack> equipment) {
        public static final Codec<Form> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("kind").forGetter(Form::kind),
                Codec.STRING.optionalFieldOf("model","").forGetter(Form::model),ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("equipment",List.of()).forGetter(Form::equipment)).apply(i,Form::new));
        public Form {if(model.length()>4096||equipment.size()>7)throw new IllegalArgumentException("Invalid saved form");equipment=equipment.stream().map(ItemStack::copy).toList();}
    }
    public record Profile(UUID player,String selected,List<Form> forms,boolean grantedFlight) {
        public static final Codec<Profile> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.STRING_CODEC.fieldOf("player").forGetter(Profile::player),
                Codec.STRING.optionalFieldOf("selected","player").forGetter(Profile::selected),Form.CODEC.listOf().optionalFieldOf("forms",List.of()).forGetter(Profile::forms),
                Codec.BOOL.optionalFieldOf("grantedFlight",false).forGetter(Profile::grantedFlight)).apply(i,Profile::new));
        public Profile {forms=List.copyOf(forms);if(forms.size()>NpcKind.values().length)throw new IllegalArgumentException("Too many saved forms");}
    }
    public static final Codec<PlayerFormData> CODEC=Profile.CODEC.listOf().xmap(PlayerFormData::new,PlayerFormData::snapshot);
    public static final SavedDataType<PlayerFormData> TYPE=new SavedDataType<>(Identifier.fromNamespaceAndPath("sexmod","player_forms"),PlayerFormData::new,CODEC,null);
    private final Map<UUID,Profile> profiles=new LinkedHashMap<>();
    public PlayerFormData(){}
    private PlayerFormData(List<Profile> loaded){loaded.forEach(profile->profiles.put(profile.player(),profile));}
    public static PlayerFormData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(TYPE);}
    public List<Profile> snapshot(){return List.copyOf(profiles.values());}
    public Profile profile(UUID player){return profiles.getOrDefault(player,new Profile(player,"player",List.of(),false));}
    public void select(UUID player,String kind){var p=profile(player);put(new Profile(player,kind,p.forms(),p.grantedFlight()));}
    public void saveForm(UUID player,Form form){var p=profile(player);var forms=new java.util.ArrayList<>(p.forms());forms.removeIf(old->old.kind().equals(form.kind()));forms.add(form);put(new Profile(player,p.selected(),forms,p.grantedFlight()));}
    public void grantedFlight(UUID player,boolean value){var p=profile(player);if(p.grantedFlight()!=value)put(new Profile(player,p.selected(),p.forms(),value));}
    private void put(Profile profile){profiles.put(profile.player(),profile);setDirty();}
}
