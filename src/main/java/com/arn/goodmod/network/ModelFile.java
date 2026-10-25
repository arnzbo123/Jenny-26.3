package com.arn.goodmod.network;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Metadata for an explicitly shared addon; network input never contains arbitrary paths. */
public record ModelFile(String model,Kind kind,int length,String sha256) {
    public enum Kind {
        GEOMETRY(".geo.json",16*1024*1024),TEXTURE(".png",32*1024*1024),CONFIG(".cfg",65536);
        private final String extension;private final int limit;
        Kind(String extension,int limit){this.extension=extension;this.limit=limit;}
        public String extension(){return extension;}public int limit(){return limit;}
    }
    public static final int MAX_MODELS=64,MAX_TOTAL=256*1024*1024,CHUNK_SIZE=32768;
    public ModelFile {
        if(model==null||!model.matches("[A-Za-z0-9_.-]{1,128}")||model.endsWith(".")||model.equals("cross"))throw new IllegalArgumentException("Invalid model name");
        if(kind==null||length<1||length>kind.limit()||sha256==null||!sha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid model file metadata");
    }
    public String fileName(){return model+kind.extension();}
    public static String hash(Path file)throws IOException{
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(var stream=Files.newInputStream(file)){byte[] bytes=new byte[CHUNK_SIZE];for(int read;(read=stream.read(bytes))!=-1;)digest.update(bytes,0,read);}
            return HexFormat.of().formatHex(digest.digest());
        }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
