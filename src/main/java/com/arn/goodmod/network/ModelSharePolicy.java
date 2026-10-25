package com.arn.goodmod.network;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Off by default; exact-file approval is per model, per server session, by an owner-permission command. */
public final class ModelSharePolicy {
    private final Map<String,List<ModelFile>> approved=new LinkedHashMap<>();
    public void clear(){approved.clear();}
    public void approve(String name,List<ModelFile> files){
        if(files.size()!=3||files.stream().anyMatch(file->!file.model().equals(name)))throw new IllegalArgumentException("Approval must name one complete model");
        new ModelManifestPayload(files);
        var candidate=new LinkedHashMap<>(approved);candidate.put(name,List.copyOf(files));
        new ModelManifestPayload(candidate.values().stream().flatMap(List::stream).toList());
        approved.put(name,List.copyOf(files));
    }
    public boolean permits(ModelFile file){return approved.getOrDefault(file.model(),List.of()).contains(file);}
    public List<ModelFile> manifest(){return approved.values().stream().flatMap(List::stream).toList();}
}
