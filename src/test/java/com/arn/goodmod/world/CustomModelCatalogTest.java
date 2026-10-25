package com.arn.goodmod.world;

import com.arn.goodmod.entity.NpcKind;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CustomModelCatalogTest {
    @TempDir Path directory;
    private Path model(String name,String config)throws Exception{
        Path folder=Files.createDirectory(directory.resolve(name));
        Files.writeString(folder.resolve(name+".cfg"),config);
        Files.writeString(folder.resolve(name+".geo.json"),"{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[]}");
        byte[] header=new byte[24];byte[] signature={(byte)137,80,78,71,13,10,26,10};System.arraycopy(signature,0,header,0,8);
        ByteBuffer.wrap(header).putInt(16,16).putInt(20,16);Files.write(folder.resolve(name+".png"),header);
        return folder;
    }
    @Test void metadataIndexRespectsSpeciesAndRequiredAnchorConfiguration()throws Exception{
        model("Hat","wear_type=HEAD\nwhich_girls=JENNY, ELLIE\nwhich_lighting=DEFAULT\n");
        model("Broken","wear_type=CUSTOM_BONE\nwhich_girls=JENNY\nwhich_lighting=NONE\n");
        var models=CustomModelCatalog.scan(directory);
        assertEquals(1,models.size());assertEquals(java.util.Set.of(NpcKind.JENNY,NpcKind.ELLIE),models.get("Hat").kinds());
    }
    @Test void corruptTextureAndUnknownSpeciesDoNotEnterTheIndex()throws Exception{
        Path bad=model("BadTexture","wear_type=HEAD\nwhich_girls=JENNY\nwhich_lighting=DEFAULT\n");
        Files.write(bad.resolve("BadTexture.png"),new byte[24]);
        model("UnknownSpecies","wear_type=HEAD\nwhich_girls=OTHER\nwhich_lighting=DEFAULT\n");
        assertTrue(CustomModelCatalog.scan(directory).isEmpty());
    }
}
