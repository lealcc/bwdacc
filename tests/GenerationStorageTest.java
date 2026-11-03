package com.leal.comfyremote;

import java.io.*;
import java.util.*;
import org.json.*;

public final class GenerationStorageTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static MediaFamilies.Record item(String id,String root,String kind){MediaFamilies.Record r=new MediaFamilies.Record();r.id=id;r.rootId=root;r.mediaKind=kind;return r;}
    static void put(File root,String name)throws Exception{File file=new File(root,name);file.getParentFile().mkdirs();VaultFiles.write(file,new byte[]{1});}
    public static void main(String[] args)throws Exception{
        List<MediaFamilies.Record> records=Arrays.asList(item("original","","image"),item("hidden-edit","original","image"),item("trial-edit","original","image"),item("animation","animation","video"),item("other","","image"));
        Set<String> family=GenerationStorage.familyIds(records,Collections.singleton("hidden-edit"));
        check(family.equals(new HashSet<>(Arrays.asList("original","hidden-edit","trial-edit"))),"deleting any cover selects full family, independent of visibility or saved state");
        check(!family.contains("animation"),"image deletion does not cascade into independent animation set");
        File root=new File(args[0],"storage-"+UUID.randomUUID());check(root.mkdirs(),"test storage created");
        GenerationStorage store=new GenerationStorage(root);store.markDeleted(family);
        check(new GenerationStorage(root).deleted("hidden-edit"),"deletion survives process restart");
        boolean rejected=false;try{store.markDeleted(Collections.singleton("../escape"));}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected&&!store.deleted("../escape"),"invalid IDs cannot target external paths");
        for(String ext:new String[]{".img",".jpg",".mp4",".mp4.part"})put(root,"images/original"+ext);
        put(root,"images/original.img.bak");put(root,"images/original.mp4.new");
        GenerationRecipe.save(root,"original",new JSONObject().put("test",true));
        put(root,"images/other.img");put(root,"references/character.jpg");put(root,"workflows/workflow.json");put(root,"project-covers/custom.jpg");
        check(store.removeFiles(family),"selected files removed");
        for(String ext:new String[]{".img",".jpg",".mp4",".mp4.part"})check(!new File(root,"images/original"+ext).exists(),"remove "+ext);
        check(!new File(root,"images/original.img.bak").exists()&&!new File(root,"images/original.mp4.new").exists(),"remove atomic media remnants");
        check(!GenerationRecipe.file(root,"original").exists(),"recipe removed");check(new File(root,"images/other.img").exists(),"other generation preserved");
        put(root,"images/orphan.mp4.part");put(root,"recipes/orphan.json.bak");put(root,"reuse-inputs.json");
        check(store.sanitizeFiles(),"sanitize succeeds");
        check(new File(root,"images").list().length==0&&new File(root,"recipes").list().length==0,"sanitize includes orphan media and recipe backups");
        check(!new File(root,"reuse-inputs.json").exists(),"recovered references removed");
        for(String keep:new String[]{"references/character.jpg","workflows/workflow.json","project-covers/custom.jpg","deleted-generations.json"})check(new File(root,keep).isFile(),"preserve "+keep);
        put(root,"images/crash-orphan.img");store.markDeleted(Collections.singleton("other"),true);
        GenerationStorage restarted=new GenerationStorage(root);check(restarted.recoverCleanup(),"resume interrupted sanitize after restart");
        check(new File(root,"images").list().length==0,"interrupted sanitize also removes orphan files");
        put(root,"images/new-generation.img");check(new GenerationStorage(root).recoverCleanup(),"next launch cleanup");
        check(new File(root,"images/new-generation.img").exists(),"completed sanitize does not delete future generations");
        System.out.println("PASS: whole families, durable deletions, path validation, all media formats, orphan cleanup, configured references preserved");
    }
}
