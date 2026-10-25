package com.arn.goodmod.worldgen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Converts the supplied legacy template fixtures using Mojang's real data fixer. */
class LegacyStructureConversionTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @ParameterizedTest
    @ValueSource(strings = {"alex", "bia", "ellie", "goblin", "jenny", "luna", "ssa"})
    void convertsAndLoadsOriginalTemplateWithoutLosingBlocks(String name) throws Exception {
        Path source = Path.of("..", "goodcraft 1.12.2 v1.1.jar");
        assumeTrue(Files.isRegularFile(source), "Original migration fixture must be supplied");
        CompoundTag original;
        try (ZipFile archive = new ZipFile(source.toFile());
             var stream = archive.getInputStream(archive.getEntry("assets/sexmod/structures/" + name + ".nbt"))) {
            original = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
        }
        int oldVersion = original.getIntOr("DataVersion", 1343);
        CompoundTag converted = DataFixTypes.STRUCTURE.updateToCurrentVersion(
                DataFixers.getDataFixer(), original.copy(), oldVersion);
        repairLegacySkulls(converted);
        // Unknown mod IDs are opaque to Mojang's entity schema. Convert the base
        // living-entity data through a known schema, then retain the original ID
        // and explicitly migrate the mod's nested equipment inventory.
        var oldEntities = original.getListOrEmpty("entities");
        var newEntities = converted.getListOrEmpty("entities");
        for (int i = 0; i < oldEntities.size(); i++) {
            CompoundTag oldNbt = oldEntities.getCompoundOrEmpty(i).getCompoundOrEmpty("nbt");
            String id = oldNbt.getStringOr("id", "");
            if (!id.startsWith("sexmod:")) continue;
            CompoundTag base = oldNbt.copy(); base.putString("id", "minecraft:zombie");
            base = (CompoundTag)DataFixers.getDataFixer().update(References.ENTITY,
                    new Dynamic<>(NbtOps.INSTANCE, base), oldVersion, SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
            base.putString("id", id); base.putBoolean("homeInitialized", false);
            if (oldNbt.contains("inventory")) {
                ListTag equipment = new ListTag();
                for (var entry : oldNbt.getCompoundOrEmpty("inventory").getListOrEmpty("Items")) {
                    CompoundTag oldStack = (CompoundTag)entry;
                    CompoundTag stack = (CompoundTag)DataFixers.getDataFixer().update(References.ITEM_STACK,
                            new Dynamic<>(NbtOps.INSTANCE, oldStack.copy()), oldVersion, SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
                    stack.putByte("Slot", (byte)oldStack.getIntOr("Slot", 0)); equipment.add(stack);
                    assertTrue(stack.contains("count"), "Legacy stack count must become a modern item stack");
                }
                CompoundTag inventory = new CompoundTag(); inventory.put("Items", equipment);
                base.put("npcEquipment", inventory); base.remove("inventory");
            }
            newEntities.getCompoundOrEmpty(i).put("nbt", base);
        }
        for (var entity : newEntities) {
            CompoundTag nbt = ((CompoundTag)entity).getCompoundOrEmpty("nbt");
            ListTag attributes = nbt.getListOrEmpty("attributes");
            // Forge injected its default swim-speed attribute into every saved
            // living entity. It has no registry entry on Fabric; retaining its
            // invalid legacy identifier makes the whole attribute list fail.
            attributes.removeIf(value -> ((CompoundTag)value).getStringOr("id", "").equals("forge.swimSpeed"));
            for (var attribute : attributes) {
                String id = ((CompoundTag)attribute).getStringOr("id", "");
                assertTrue(BuiltInRegistries.ATTRIBUTE.containsKey(Identifier.parse(id)), "Unknown entity attribute " + id);
            }
        }
        converted.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        for (var entry : converted.getListOrEmpty("palette")) {
            String id = ((CompoundTag)entry).getStringOr("id", "");
            assertTrue(BuiltInRegistries.BLOCK.containsKey(Identifier.parse(id)), "Unknown converted block " + id);
        }
        StructureTemplate template = new StructureTemplate();
        template.load(BuiltInRegistries.BLOCK, converted);
        assertTrue(template.getSize().getX() > 0 && template.getSize().getY() > 0 && template.getSize().getZ() > 0);
        CompoundTag roundTrip = template.save(new CompoundTag());
        assertEquals(original.getListOrEmpty("blocks").size(), roundTrip.getListOrEmpty("blocks").size());
        assertEquals(original.getListOrEmpty("entities").size(), roundTrip.getListOrEmpty("entities").size());
        assertEquals(original.getListOrEmpty("size"), roundTrip.getListOrEmpty("size"));
        long expectedAir = converted.getListOrEmpty("palette").stream().filter(tag -> ((CompoundTag)tag).getStringOr("id", "").equals("minecraft:air")).count();
        long actualAir = roundTrip.getListOrEmpty("palette").stream().filter(tag -> ((CompoundTag)tag).getStringOr("id", "").equals("minecraft:air")).count();
        assertEquals(expectedAir, actualAir, "Loading cannot silently substitute unknown blocks with air");
        Path output = Path.of("build", "converted-structures", name + ".nbt");
        Files.createDirectories(output.getParent());
        NbtIo.writeCompressed(converted, output);
    }

    private static void repairLegacySkulls(CompoundTag template) {
        // Mojang's flattening fix deliberately emits FILTER_ME for old skull
        // palette entries: their type/rotation lived in each block entity. The
        // chunk fixer repairs these, but the structure fixer does not.
        ListTag palette = template.getListOrEmpty("palette");
        java.util.Set<Integer> legacy = new java.util.HashSet<>(), replaced = new java.util.HashSet<>();
        for (int i=0;i<palette.size();i++) if (palette.getCompoundOrEmpty(i).getStringOr("id", "").equals("%%FILTER_ME%%")) legacy.add(i);
        var original = new java.util.HashMap<Integer, CompoundTag>();
        legacy.forEach(index -> original.put(index, palette.getCompoundOrEmpty(index).copy()));
        String[] standing = {"skeleton_skull", "wither_skeleton_skull", "zombie_head", "player_head", "creeper_head", "dragon_head"};
        String[] wall = {"skeleton_wall_skull", "wither_skeleton_wall_skull", "zombie_wall_head", "player_wall_head", "creeper_wall_head", "dragon_wall_head"};
        for (var value : template.getListOrEmpty("blocks")) {
            CompoundTag block = (CompoundTag)value; int index = block.getIntOr("state", -1);
            if (!legacy.contains(index)) continue;
            CompoundTag nbt = block.getCompoundOrEmpty("nbt"); int type = nbt.getIntOr("SkullType", 0);
            assertTrue(type >= 0 && type < standing.length);
            String facing = original.get(index).getCompoundOrEmpty("properties").getStringOr("facing", "up");
            boolean onWall = !facing.equals("up") && !facing.equals("down");
            CompoundTag state = new CompoundTag(), properties = new CompoundTag();
            state.putString("id", "minecraft:" + (onWall ? wall[type] : standing[type]));
            if (onWall) properties.putString("facing", facing);
            else properties.putString("rotation", Integer.toString(nbt.getIntOr("Rot", 0) & 15));
            state.put("properties", properties);
            if (replaced.add(index)) palette.set(index, state);
            else if (!palette.getCompoundOrEmpty(index).equals(state)) { block.putInt("state", palette.size()); palette.add(state); }
            nbt.remove("SkullType"); nbt.remove("Rot");
        }
        assertEquals(legacy, replaced, "Every legacy skull state should have a block entity");
    }
}
