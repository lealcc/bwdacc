package com.leal.comfyremote;
import org.json.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;

public final class GenerationRecipeTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static JSONObject read(String path)throws Exception{return new JSONObject(new String(Files.readAllBytes(Paths.get(path)),"UTF-8"));}
    public static void main(String[] args)throws Exception{
        JSONObject klein=read(args[0]),graph=GenerationRecipe.copy(klein);
        ReferenceSupport.bind(graph,Arrays.asList("first.png","second.png","third.png"));
        graph.getJSONObject("12").getJSONObject("inputs").put("noise_seed",987654321L);
        graph.getJSONObject("14").getJSONObject("inputs").put("steps",13).put("width",896).put("height",1344);
        JSONObject options=new JSONObject().put("ratio","2:3").put("imageMp",1).put("quality",33).put("duration",1).put("batch",4);
        JSONArray refs=new JSONArray().put(new JSONObject().put("id","edit1")).put(new JSONObject().put("id","other")).put(new JSONObject().put("id","third"));
        JSONObject draft=new JSONObject().put("template",klein).put("options",options).put("operation","image_edit").put("sourceId","edit1").put("refs",refs).put("presets",new JSONArray());
        JSONObject recipe=GenerationRecipe.finish(draft,graph,Arrays.asList("first.png","second.png","third.png"));
        check(GenerationRecipe.source(recipe).equals("edit1"),"Branch must use actual edit parent, not root");
        check(GenerationRecipe.edit(recipe),"Image edit route");
        check(recipe.getJSONObject("options").getInt("batch")==4,"Batch saved");
        JSONObject restored=GenerationRecipe.configured(recipe);
        check(!restored.has("momentos_ref_1_load"),"Prepared references cannot leak into template");
        check(restored.getJSONObject("15").getJSONObject("inputs").getJSONArray("positive").getString(0).equals("9"),"Template links must survive");
        check(restored.getJSONObject("12").getJSONObject("inputs").getLong("noise_seed")==987654321L,"Actual seed restored");
        ReferenceSupport.bind(restored,Arrays.asList("first.png","second.png","third.png"));
        AdvancedGeneration.apply(restored,GenerationRecipe.advanced(graph));
        check(restored.getJSONObject("14").getJSONObject("inputs").getInt("steps")==13,"Steps preserved");
        check(restored.getJSONObject("12").getJSONObject("inputs").getLong("noise_seed")==987654321L,"Seed must not randomize on reuse");
        check(GenerationRecipe.inputFiles(graph,false).equals(Arrays.asList("first.png","second.png","third.png")),"Legacy reference order");
        JSONObject legacy=GenerationRecipe.legacyTemplate(graph);ReferenceSupport.bind(legacy,Arrays.asList("new.png","other.png"));
        check(!legacy.has("momentos_ref_2_load"),"Removed legacy references not retained");
        JSONObject moss=read(args[1]);moss.getJSONObject("28").getJSONObject("inputs").put("lora_1",new JSONObject().put("lora","moss/Test.safetensors").put("on",false).put("strength",1));JSONObject submitted=GenerationRecipe.copy(moss);
        submitted.getJSONObject("28").getJSONObject("inputs").getJSONObject("lora_1").put("on",true).put("strength",.45);
        JSONObject mr=new JSONObject().put("template",moss).put("graph",submitted);
        JSONObject mg=GenerationRecipe.configured(mr);mg.getJSONObject("28").getJSONObject("inputs").getJSONObject("lora_1").put("strength",.9);
        check(submitted.getJSONObject("28").getJSONObject("inputs").getJSONObject("lora_1").getDouble("strength")==.45,"Recipe LoRA snapshot must be immutable");
        check(GenerationRecipe.legacyOptions(moss,false).getString("ratio").equals("4:3"),"Legacy ratio recovered");
        JSONObject video=read(args[3]),prepared=GenerationRecipe.copy(video);
        VideoWorkflow.prepare(prepared,Arrays.asList("first.png","last.png"),true,"a slow turn","9:16",.66,720,1280,8,42);
        prepared.getJSONObject("11").getJSONObject("inputs").put("steps",7);
        JSONObject vr=new JSONObject().put("template",video).put("graph",prepared);JSONObject vt=GenerationRecipe.configured(vr);
        check(VideoWorkflow.turbo(vt),"Turbo identity must survive custom steps");
        check(GenerationRecipe.inputFiles(prepared,false).equals(Arrays.asList("first.png","last.png")),"First/last reference roles and order");
        VideoWorkflow.prepare(vt,Arrays.asList("first.png","last.png"),true,"a slow turn","9:16",.66,720,1280,8,999);
        AdvancedGeneration.apply(vt,GenerationRecipe.advanced(prepared));
        check(vt.getJSONObject("9").getJSONObject("inputs").getLong("noise_seed")==42,"Video seed restored after prepare");
        check(vt.getJSONObject("11").getJSONObject("inputs").getInt("steps")==7,"Custom video steps restored");
        JSONObject tagged=GenerationRecipe.copy(prepared);tagged.getJSONObject("8").put("class_type","MiniMaxH3ReferenceToVideo");tagged.getJSONObject("4").getJSONObject("inputs").put("unet_name","minimax_h3_ref2va.safetensors");tagged.getJSONObject("21").getJSONObject("inputs").getJSONObject("lora_1").put("lora","minimax_h3_ref2va_turbo.safetensors");
        tagged.getJSONObject("21").getJSONObject("inputs").put("lora_2",new JSONObject().put("lora","h3/identity.safetensors").put("on",true).put("strength",.6));
        JSONObject switched=GenerationRecipe.configured(new JSONObject().put("template",video).put("graph",tagged));
        check(switched.getJSONObject("4").getJSONObject("inputs").getString("unet_name").equals(video.getJSONObject("4").getJSONObject("inputs").getString("unet_name")),"Disabling visual tags must restore the FL2V model");
        check(switched.getJSONObject("21").getJSONObject("inputs").getJSONObject("lora_1").getString("lora").equals(video.getJSONObject("21").getJSONObject("inputs").getJSONObject("lora_1").getString("lora")),"Structural adapter must follow the original model");
        check(switched.getJSONObject("21").getJSONObject("inputs").getJSONObject("lora_2").getDouble("strength")==.6,"Manual H3 adapters survive automatic mode changes");
        JSONObject replay=GenerationRecipe.copy(draft).put("inherited",recipe),output=GenerationRecipe.copy(graph);
        output.getJSONObject("14").getJSONObject("inputs").put("width",1024);GenerationRecipe.preserveOutput(output,replay);
        check(output.getJSONObject("14").getJSONObject("inputs").getInt("width")==896,"Exact output size");
        replay.getJSONObject("options").put("ratio","1:1");output.getJSONObject("14").getJSONObject("inputs").put("width",1024);GenerationRecipe.preserveOutput(output,replay);
        check(output.getJSONObject("14").getJSONObject("inputs").getInt("width")==1024,"Changed ratio must win");
        JSONObject saved=GenerationRecipe.finish(replay,graph,Collections.emptyList());check(!saved.has("inherited"),"No recursive recipe growth");
        File folder=new File(args[2]);folder.mkdirs();GenerationRecipe.save(folder,"test-recipe",recipe);JSONObject disk=GenerationRecipe.load(folder,"test-recipe");
        check(disk.toString().equals(recipe.toString()),"Atomic storage roundtrip");GenerationRecipe.delete(folder,"test-recipe");check(!GenerationRecipe.file(folder,"test-recipe").exists(),"Recipe cleanup");
        System.out.println("GenerationRecipeTest PASS: branches, refs, seed, steps, LoRAs, ratio, batch, immutable storage");
    }
}
