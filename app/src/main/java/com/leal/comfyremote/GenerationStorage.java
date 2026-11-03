package com.leal.comfyremote;

import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Persistent deletion markers prevent stale history and recipe recovery from reviving media. */
final class GenerationStorage {
    private final File files;
    private final Set<String> deleted = new HashSet<>();
    private boolean pendingSanitize;
    GenerationStorage(File files) {
        this.files=files;
        File savedFile=new File(files,"deleted-generations.json");
        if(!savedFile.exists()&&!new File(savedFile+".bak").exists())return;
        try(InputStream in=VaultFiles.open(savedFile);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
            String text=out.toString("UTF-8");JSONArray ids;
            if(text.trim().startsWith("{")){JSONObject saved=new JSONObject(text);ids=saved.getJSONArray("ids");pendingSanitize=saved.optBoolean("sanitize");}else ids=new JSONArray(text);
            for(int i=0;i<ids.length();i++)if(valid(ids.optString(i)))deleted.add(ids.optString(i));
        }catch(Exception error){throw new IllegalStateException(UiLanguage.t("Registro de exclusões indisponível. Arquivos preservados."),error);}
    }
    static boolean valid(String id){return id!=null&&id.matches("[A-Za-z0-9_-]{1,128}");}
    synchronized boolean deleted(String id){return deleted.contains(id);}
    synchronized void markDeleted(Set<String> ids)throws Exception{
        markDeleted(ids,false);
    }
    synchronized void markDeleted(Set<String> ids,boolean sanitize)throws Exception{
        Set<String> next=new HashSet<>(deleted);for(String id:ids){if(!valid(id))throw new IllegalArgumentException(UiLanguage.t("ID inválido"));next.add(id);}
        persist(next,pendingSanitize||sanitize);
    }
    private void persist(Set<String> next,boolean sanitize)throws Exception{
        VaultFiles.write(new File(files,"deleted-generations.json"),new JSONObject().put("ids",new JSONArray(next)).put("sanitize",sanitize).toString().getBytes(StandardCharsets.UTF_8));
        deleted.clear();deleted.addAll(next);pendingSanitize=sanitize;
    }
    synchronized boolean recoverCleanup(){
        boolean ok=removeFiles(new HashSet<>(deleted));
        if(pendingSanitize){ok&=sanitizeFiles();if(ok)try{persist(new HashSet<>(deleted),false);}catch(Exception e){ok=false;}}
        return ok;
    }
    boolean removeFiles(Set<String> ids){
        boolean ok=true;
        for(String id:ids){if(!valid(id))continue;
            for(String ext:new String[]{".img",".jpg",".mp4",".mp4.part",".audio"}){
                File path=new File(new File(files,"images"),id+ext);VaultFiles.delete(path);
                ok&=!path.exists()&&!new File(path+".bak").exists()&&!new File(path+".new").exists();
            }
            GenerationRecipe.delete(files,id);
            ok&=!GenerationRecipe.file(files,id).exists();
        }
        return ok;
    }
    boolean sanitizeFiles(){
        boolean ok=true;
        // Only generation-owned directories. Workflows, reference presets and app settings are separate.
        for(String name:new String[]{"images","recipes"}){
            File directory=new File(files,name);File[] entries=directory.listFiles();if(entries==null)continue;
            for(File file:entries)try{if(file.isFile()&&file.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile()))ok&=remove(file);}catch(IOException e){ok=false;}
        }
        VaultFiles.delete(new File(files,"reuse-inputs.json"));
        return ok;
    }
    private static boolean remove(File file){return !file.exists()||VaultFiles.delete(file);}
    static Set<String> familyIds(List<? extends MediaFamilies.Record> items,Set<String> selected){
        Set<String> roots=new HashSet<>(),ids=new HashSet<>();
        for(MediaFamilies.Record item:items)if(selected.contains(item.id))roots.add(MediaFamilies.root(item,items));
        for(MediaFamilies.Record item:items)if(roots.contains(MediaFamilies.root(item,items)))ids.add(item.id);
        return ids;
    }
}
