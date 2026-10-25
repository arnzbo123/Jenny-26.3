package com.arn.goodmod.world;

import com.arn.goodmod.GoodcraftMod;
import com.arn.goodmod.entity.NpcKind;
import com.arn.goodmod.menu.NpcModelCode;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import com.arn.goodmod.network.ModelFile;

/** br server availability metadata. Local addon files are never accepted from client packets. */
public final class CustomModelCatalog {
    public record Model(String name,Set<NpcKind> kinds,String anchor){public Model{kinds=Set.copyOf(kinds);}}
    private static Map<String,Model> models=Map.of();
    private static Path catalogRoot;
    private CustomModelCatalog(){}
    public static int reload(MinecraftServer server){
        Path root=FabricLoader.getInstance().getGameDir().resolve("sexmod/custom_models");
        if(!server.isDedicatedServer())root=root.resolve("singleplayer");
        catalogRoot=root.toAbsolutePath().normalize();models=scan(root);return models.size();
    }
    public static java.util.List<ModelFile> describe(String name)throws IOException{
        if(!models.containsKey(name))throw new IOException("Unknown installed custom model: "+name);
        var files=new java.util.ArrayList<ModelFile>();
        for(var kind:ModelFile.Kind.values()){
            Path file=resolveFile(name,kind);
            files.add(new ModelFile(name,kind,(int)Files.size(file),ModelFile.hash(file)));
        }
        return java.util.List.copyOf(files);
    }
    public static Path resolveFile(String name,ModelFile.Kind kind)throws IOException{
        if(catalogRoot==null||!models.containsKey(name))throw new IOException("Unknown installed model");
        Path root=catalogRoot.toRealPath(),folder=root.resolve(name).toRealPath();
        if(!folder.startsWith(root))throw new IOException("Model directory escapes catalog");
        return checked(folder,name+kind.extension(),kind.limit());
    }
    public static void validate(NpcModelCode code,NpcKind kind){
        for(String name:code.addons()){
            Model model=models.get(name);
            if(model==null)throw new IllegalArgumentException("Custom model is not installed on this server: "+name);
            if(!model.kinds().isEmpty()&&!model.kinds().contains(kind))throw new IllegalArgumentException("Custom model "+name+" does not support "+kind.id());
        }
    }
    public static Map<String,Model> scan(Path directory){
        Map<String,Model> result=new LinkedHashMap<>();
        if(!Files.isDirectory(directory))return Map.of();
        try(var paths=Files.list(directory)){
            Path root=directory.toRealPath();
            for(Path candidate:paths.filter(Files::isDirectory).sorted().limit(256).toList()){
                String name=candidate.getFileName().toString();
                if(!name.matches("[A-Za-z0-9_.-]{1,128}")||name.equals("cross")||name.equals(".")||name.equals(".."))continue;
                try{
                    Path model=candidate.toRealPath();if(!model.startsWith(root))continue;
                    Path config=checked(model,name+".cfg",65536),geometry=checked(model,name+".geo.json",16*1024*1024),texture=checked(model,name+".png",32*1024*1024);
                    Properties props=new Properties();try(var stream=Files.newInputStream(config)){props.load(stream);}
                    String anchor=props.getProperty("wear_type","").trim();
                    if(!Set.of("HEAD","HAND_L","HAND_R","FOOT_L","FOOT_R","CUSTOM_BONE").contains(anchor))throw new IOException("Invalid wear_type");
                    if(anchor.equals("CUSTOM_BONE")&&props.getProperty("custom_bone","").isBlank())throw new IOException("Missing custom_bone");
                    if(!Set.of("DEFAULT","SEXMOD","NONE").contains(props.getProperty("which_lighting","").trim()))throw new IOException("Invalid which_lighting");
                    EnumSet<NpcKind> kinds=EnumSet.noneOf(NpcKind.class);
                    for(String value:props.getProperty("which_girls","").split(","))if(!value.isBlank())kinds.add(NpcKind.byId(value.trim()).orElseThrow(()->new IllegalArgumentException("Invalid which_girls")));
                    var json=JsonParser.parseString(Files.readString(geometry));
                    if(!json.isJsonObject())throw new IOException("Geometry must be an object");
                    try(var stream=Files.newInputStream(texture)){
                        byte[] header=stream.readNBytes(24);byte[] signature={(byte)137,80,78,71,13,10,26,10};
                        if(header.length<24||!java.util.Arrays.equals(java.util.Arrays.copyOf(header,8),signature))throw new IOException("Invalid PNG");
                        var size=java.nio.ByteBuffer.wrap(header);int width=size.getInt(16),height=size.getInt(20);
                        if(width<1||height<1||width>4096||height>4096)throw new IOException("Unsupported texture dimensions");
                    }
                    result.put(name,new Model(name,kinds,anchor));
                }catch(Exception invalid){GoodcraftMod.LOGGER.warn("Cannot index custom model {}: {}",name,invalid.toString());}
            }
        }catch(IOException invalid){GoodcraftMod.LOGGER.warn("Cannot read server custom models",invalid);}
        return Map.copyOf(result);
    }
    private static Path checked(Path root,String name,long limit)throws IOException{
        Path file=root.resolve(name).toRealPath();
        if(!file.startsWith(root)||!Files.isRegularFile(file)||Files.size(file)>limit)throw new IOException("Invalid addon file "+name);
        return file;
    }
}
