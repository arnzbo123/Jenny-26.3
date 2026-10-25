package com.arn.goodmod.animation;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class AddonGeometryLimitsTest {
    private static String model(String bones) { return "{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[{\"bones\":["+bones+"]}]}"; }
    @Test void acceptsValidHierarchyAndRejectsAmbiguousOrCyclicParents() throws Exception {
        assertNotNull(AddonGeometryLimits.parse(model("{\"name\":\"child\",\"parent\":\"root\"},{\"name\":\"root\"}")));
        assertThrows(IOException.class,()->AddonGeometryLimits.parse(model("{\"name\":\"a\",\"parent\":\"b\"},{\"name\":\"b\",\"parent\":\"a\"}")));
        assertThrows(IOException.class,()->AddonGeometryLimits.parse(model("{\"name\":\"a\",\"parent\":\"missing\"}")));
        assertThrows(IOException.class,()->AddonGeometryLimits.parse(model("{\"name\":\"a\"},{\"name\":\"a\"}")));
    }
    @Test void boundsJsonNestingBeforeBuildingGeometryObjects() {
        String input="[".repeat(65)+"0"+"]".repeat(65);
        assertThrows(IOException.class,()->AddonGeometryLimits.parse(input));
        assertThrows(IOException.class,()->AddonGeometryLimits.parse(model("{\"name\":\"a\",\"pivot\":[1e999,0,0]}")));
    }
    @Test void boundsGeometryAllocationsBeforeBaking() {
        String cubes=String.join(",",java.util.Collections.nCopies(8193,"{}"));
        assertThrows(IOException.class,()->AddonGeometryLimits.parse(model("{\"name\":\"a\",\"cubes\":["+cubes+"]}")));
    }
    @Test void validatesPngHeaderAndDimensionsBeforeDecode() throws Exception {
        byte[] png=new byte[24];byte[] signature={(byte)137,80,78,71,13,10,26,10};System.arraycopy(signature,0,png,0,8);
        var data=ByteBuffer.wrap(png);data.putInt(8,13);data.putInt(12,0x49484452);data.putInt(16,4096);data.putInt(20,4096);
        assertEquals(4096*4096,AddonGeometryLimits.pngPixels(png));
        data.putInt(16,4097);assertThrows(IOException.class,()->AddonGeometryLimits.pngPixels(png));
        data.putInt(16,16);data.putInt(20,-1);assertThrows(IOException.class,()->AddonGeometryLimits.pngPixels(png));
        data.putInt(20,16);png[12]='X';assertThrows(IOException.class,()->AddonGeometryLimits.pngPixels(png));
    }
}
