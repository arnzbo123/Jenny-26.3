package com.arn.goodmod.network;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bounded, hash-verified staging. Only three fixed filenames per model may be installed. */
public final class ModelDownloadStaging implements AutoCloseable {
    private final Path root,stage;
    private final List<ModelFile> files;
    private final Set<ModelFile> complete=new HashSet<>();
    private ModelFile current;
    private OutputStream output;
    private int offset;
    public ModelDownloadStaging(Path destination,List<ModelFile> files)throws IOException{
        this.files=new ModelManifestPayload(files).files();
        root=destination.toAbsolutePath().normalize();
        Path ancestor=root;while(!Files.exists(ancestor,LinkOption.NOFOLLOW_LINKS))ancestor=ancestor.getParent();
        if(!ancestor.toRealPath().equals(ancestor)||Files.isSymbolicLink(ancestor))throw new IOException("Cache path contains a link");
        Files.createDirectories(root);if(!root.toRealPath().equals(root))throw new IOException("Cache directory redirects outside its path");
        stage=Files.createTempDirectory(root,".model-download-");
        try{
            for(ModelFile file:this.files){
                Path target=stage.resolve(file.model()).resolve(file.fileName());Files.createDirectories(target.getParent());
                Path existing=root.resolve(file.model()).resolve(file.fileName());
                if(Files.isRegularFile(existing,LinkOption.NOFOLLOW_LINKS)&&existing.toRealPath().startsWith(root)
                        &&!Files.isSymbolicLink(existing.getParent())&&Files.size(existing)==file.length()&&ModelFile.hash(existing).equals(file.sha256())){
                    Files.copy(existing,target);complete.add(file);
                }
            }
        }catch(IOException invalid){close();throw invalid;}
    }
    public List<ModelFile> pending(){return files.stream().filter(file->!complete.contains(file)).toList();}
    public void accept(ModelFile file,int receivedOffset,byte[] bytes,boolean last)throws IOException{
        if(!files.contains(file)||complete.contains(file)||bytes.length>ModelFile.CHUNK_SIZE)throw new IOException("Unexpected model file");
        if(current==null){
            if(receivedOffset!=0)throw new IOException("Model transfer started at an invalid offset");
            current=file;offset=0;output=Files.newOutputStream(stage.resolve(file.model()).resolve(file.fileName()),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        }
        if(!current.equals(file)||receivedOffset!=offset||bytes.length==0||offset+bytes.length>file.length())throw new IOException("Invalid model chunk order or length");
        output.write(bytes);offset+=bytes.length;
        if(last){
            output.close();output=null;
            if(offset!=file.length()||!ModelFile.hash(stage.resolve(file.model()).resolve(file.fileName())).equals(file.sha256()))throw new IOException("Custom model checksum mismatch");
            complete.add(file);current=null;offset=0;
        }else if(offset==file.length())throw new IOException("Missing final model chunk marker");
    }
    public void commit()throws IOException{
        if(output!=null||complete.size()!=files.size())throw new IOException("Model download is incomplete");
        Path backup=Files.createTempDirectory(root,".model-backup-");
        for(String model:files.stream().map(ModelFile::model).distinct().toList()){
            Path destination=root.resolve(model),previous=backup.resolve(model),replacement=stage.resolve(model);
            boolean existed=Files.exists(destination,LinkOption.NOFOLLOW_LINKS);
            if(existed&&(!Files.isDirectory(destination,LinkOption.NOFOLLOW_LINKS)||!destination.toRealPath().startsWith(root)))throw new IOException("Model destination is not a safe directory");
            if(existed)move(destination,previous);
            try{move(replacement,destination);}catch(IOException invalid){if(existed)move(previous,destination);throw invalid;}
            if(existed){
                // Preserve unexpected local files in the backup; remove only the known old triplet.
                Set<String> expected=new HashSet<>();for(ModelFile.Kind kind:ModelFile.Kind.values())expected.add(model+kind.extension());
                List<Path> old;
                try(var children=Files.list(previous)){old=children.toList();}
                if(old.size()==3&&old.stream().allMatch(path->expected.contains(path.getFileName().toString())&&Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))){
                    for(Path path:old)Files.delete(path);Files.delete(previous);
                }
            }
        }
        boolean empty;try(var remaining=Files.list(backup)){empty=remaining.findAny().isEmpty();}
        if(empty)Files.delete(backup);
    }
    private static void move(Path from,Path to)throws IOException{
        try{Files.move(from,to,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException unavailable){Files.move(from,to);}
    }
    @Override public void close()throws IOException{
        if(output!=null){output.close();output=null;}
        if(!stage.normalize().startsWith(root)||stage.equals(root))throw new IOException("Unsafe staging directory");
        if(Files.exists(stage,LinkOption.NOFOLLOW_LINKS))try(var paths=Files.walk(stage)){
            for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
        }
    }
}
