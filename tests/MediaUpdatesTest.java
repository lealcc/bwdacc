package com.leal.comfyremote;

import org.json.*;
import java.nio.file.*;
import java.util.*;

public final class MediaUpdatesTest {
    static JSONObject read(String path)throws Exception{return new JSONObject(new String(Files.readAllBytes(Paths.get(path)),"UTF-8"));}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static JSONObject node(String type,JSONObject input)throws Exception{return ReferenceSupport.node(type,input);}
    public static void main(String[] args)throws Exception{
        String root=args[0];JSONObject schema=read(args[1]);LoraPrivacy.load(read(args[2]));
        JSONObject snofs=read(root+"/snofs_klein_edit_api.json");
        check(ReferenceSupport.klein(snofs)&&ReferenceSupport.capacity(snofs)==3,"Snofs accepts all three references despite mixed-case checkpoint filename");
        check(ImageEditWorkflow.snofs(snofs)&&snofs.getJSONObject("3").getJSONObject("inputs").getString("clip_name").equals("qwen_3_8b_fp8mixed.safetensors"),"9B uses Qwen 8B, not 4B encoder");
        check(snofs.getJSONObject("14").getJSONObject("inputs").getInt("steps")==4,"Distilled Klein uses four steps");
        check(!MemoryProfiles.unavailable(snofs,"16").isEmpty()&&MemoryProfiles.unavailable(snofs,"24").isEmpty(),"Only supported Snofs tiers enabled");
        WorkflowDependencies.verify(snofs,schema);ReferenceSupport.bind(snofs,Arrays.asList("a.png","b.png","c.png"));WorkflowProfilesTest.valid(snofs);
        JSONObject recipe=new JSONObject().put("template",read(root+"/snofs_klein_edit_api.json")).put("graph",snofs);
        check(ImageEditWorkflow.snofs(GenerationRecipe.configured(recipe)),"Reuse retains Snofs model");
        for(boolean compact:new boolean[]{true,false})for(int[] shape:new int[][]{{1280,720},{720,1280},{1008,1008}}){
            JSONObject graph=read(root+"/"+UpscaleWorkflow.asset(true,compact));UpscaleWorkflow.prepare(graph,"source.mp4","Original prompt",shape[0],shape[1],3000,2560,123);
            WorkflowProfilesTest.valid(graph);JSONObject finish=graph.getJSONObject("finish").getJSONObject("inputs"),source=graph.getJSONObject("momentos_upscale_source").getJSONObject("inputs");
            check(Math.abs(finish.getDouble("width")/finish.getDouble("height")-shape[0]/(double)shape[1])<.005,"Aspect survives video upscale");
            check(source.getInt("width")*source.getInt("height")<=1_500_000,"Square source fits encoder input budget");
            check(graph.getJSONObject("sigma").getJSONObject("inputs").getString("sigmas").startsWith("0.909375"),"Reference three-step sigma schedule");
            check(!UpscaleWorkflow.unavailable(true,"16").isEmpty(),"Unvalidated low VRAM upscale remains unavailable");
        }
        JSONObject image=read(root+"/image_upscale_api.json");UpscaleWorkflow.prepare(image,"a.png","",800,1200,0,4,0);
        check(image.getJSONObject("size").getJSONObject("inputs").getInt("height")==4800,"4x image keeps aspect");WorkflowDependencies.verify(image,schema);
        check(MemoryProfiles.unavailable(image,"8").isEmpty(),"Small tiled ESRGAN allowed on 8GB");
        check(LoraPrivacy.restricted("h3/style/2d_nsfw_5s.safetensors"),"Adult content in style folder blocked");
        check(LoraPrivacy.restricted("new/unknown.safetensors"),"Unclassified adapter restricted");
        String safe="ideogram4/ideogram_4_turbotime_v1.safetensors",adult="h3/sex/example.safetensors";
        JSONObject choices=new JSONObject().put("21",new JSONArray().put(new JSONObject().put("lora",adult).put("on",true)).put(new JSONObject().put("lora",safe).put("on",true)));
        check(LoraPrivacy.disableChoices(choices)==1&&!choices.getJSONArray("21").getJSONObject(0).getBoolean("on"),"Disabling private mode clears saved active selection");
        JSONObject graph=new JSONObject().put("adult",node("LoraLoaderModelOnly",new JSONObject().put("lora_name",adult).put("strength_model",1)))
            .put("safe",node("LoraLoaderModelOnly",new JSONObject().put("lora_name",safe).put("strength_model",1)))
            .put("rgthree",node("Power Lora Loader (rgthree)",new JSONObject().put("lora_1",new JSONObject().put("lora",adult).put("on",true).put("strength",.8))));
        graph.getJSONObject("adult").put("_meta",new JSONObject().put("momentos_structural_lora",true));LoraPrivacy.enforce(graph);
        check(graph.getJSONObject("adult").getJSONObject("inputs").getDouble("strength_model")==0,"Reused and character adapters disabled; annotation cannot bypass privacy");
        check(graph.getJSONObject("safe").getJSONObject("inputs").getDouble("strength_model")==1,"Technical adapter remains active");
        check(!graph.getJSONObject("rgthree").getJSONObject("inputs").getJSONObject("lora_1").getBoolean("on"),"rgthree adapter disabled");
        ReferencePreset preset=new ReferencePreset();preset.loras=new JSONArray().put(new JSONObject().put("lora",adult)).put(new JSONObject().put("lora",safe));LoraPrivacy.filterPresets(Collections.singletonList(preset));check(preset.loras.length()==1,"Reference bindings filtered without losing safe adapter");
        LoraPrivacy.refresh("http://test",url->new JSONObject().put("data",new JSONArray().put(new JSONObject().put("file",safe).put("name","Readable Film Name").put("adult",true))));
        check(LoraPrivacy.display(safe).equals("Readable Film Name")&&LoraPrivacy.restricted(safe),"New adult metadata overrides prior SFW classification");
        check(LoraPrivacy.display("unknown\\File_Name.safetensors").equals("File Name"),"Clean filename fallback");
        LoraPrivacy.refresh("http://generic",url->new JSONObject().put("data",new JSONArray().put(new JSONObject().put("file","unknown/File_Name.safetensors").put("name","aitk_lora"))));
        check(LoraPrivacy.display("unknown/File_Name.safetensors").equals("File Name"),"Generic training metadata does not replace a useful filename");
        System.out.println("MediaUpdatesTest passed");
    }
}
