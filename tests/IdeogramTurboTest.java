package com.leal.comfyremote;

import org.json.*;
import java.nio.file.*;
import java.util.*;

public final class IdeogramTurboTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static JSONObject read(String path)throws Exception{return new JSONObject(new String(Files.readAllBytes(Paths.get(path)),"UTF-8"));}
    public static void main(String[] args)throws Exception{
        JSONObject base=read(args[0]),schema=read(args[1]);String original=base.toString();
        check(WorkflowPicker.description(base).contains("8 passos"),"Picker must show the actual TurboTime steps");
        check(base.getJSONObject("10").getString("class_type").equals("BasicGuider")&&!base.has("2"),"TurboTime has one conditional model and no CFG branch");
        check(base.getJSONObject("14").getJSONObject("inputs").getString("lora_name").equals("ideogram4/ideogram_4_turbotime_v1.safetensors"),"Use installed folder organization");
        WorkflowDependencies.verify(GenerationRecipe.copy(base),schema);
        check(WorkflowDependencies.installedName(new JSONArray().put("ideogram4\\adapter.safetensors"),"ideogram4/adapter.safetensors").equals("ideogram4\\adapter.safetensors"),"Portable paths must bind to the Windows enum spelling");
        check(WorkflowDependencies.installedName(new JSONArray().put("ideogram4/adapter.safetensors"),"ideogram4\\adapter.safetensors").equals("ideogram4/adapter.safetensors"),"Windows recipes must bind to a Linux server");
        JSONObject layout=new JSONObject().put("enabled",true).put("boxes",new JSONArray()
            .put(new JSONObject().put("x",.08).put("y",.2).put("w",.34).put("h",.65).put("type","obj").put("desc","A red ceramic mug"))
            .put(new JSONObject().put("x",.58).put("y",.15).put("w",.34).put("h",.7).put("type","obj").put("desc","A blue ceramic vase")));
        String raw="Studio product photograph of a red ceramic mug on the left and a blue ceramic vase on the right. Both objects on a clean white table, soft studio light, white background.";
        for(int steps:new int[]{4,8}){
            JSONObject graph=GenerationRecipe.copy(base);GenerationOptions.imageOutput(graph,"1:1",1,0,0);
            graph.getJSONObject("9").getJSONObject("inputs").put("steps",steps);
            graph.getJSONObject("7").getJSONObject("inputs").put("noise_seed",12345L);
            BoxLayout.apply(graph,layout,raw);MemoryProfiles.apply(graph,"24");WorkflowProfilesTest.valid(graph);WorkflowDependencies.verify(graph,schema);
            graph.getJSONObject("13").getJSONObject("inputs").put("filename_prefix","Momentos/QA/IdeogramTurbo/steps"+steps);
            Files.write(Paths.get(args[2],"turbo-"+steps+".json"),graph.toString(2).getBytes("UTF-8"));
            JSONObject recipe=new JSONObject().put("template",base).put("graph",graph),restored=GenerationRecipe.configured(recipe);
            check(restored.getJSONObject("9").getJSONObject("inputs").getInt("steps")==steps,"Reuse must preserve chosen steps");
            MemoryProfiles.apply(restored,"24plus");check(restored.getJSONObject("14").getJSONObject("inputs").getDouble("strength_model")==1,"Memory changes preserve structural adapter");
            check(restored.getJSONObject("10").getJSONObject("inputs").getJSONArray("model").getString(0).equals("14"),"Guider stays downstream from adapter");
        }
        for(boolean nsfw:new boolean[]{false,true}){
            JSONObject graph=GenerationRecipe.copy(base);PromptAgent.apply(graph,true,nsfw,raw,"image",schema);BoxLayout.apply(graph,layout,raw);MemoryProfiles.apply(graph,"24");WorkflowProfilesTest.valid(graph);
            check(graph.getJSONObject("momentos_layout").getJSONObject("inputs").getJSONArray("high_level_description").getString(0).equals("momentos_agent_guard"),"Agent output reaches the native boxes builder");
            check(graph.getJSONObject("14").getJSONObject("inputs").getDouble("strength_model")==1,"Agent preserves TurboTime adapter");
        }
        check(base.toString().equals(original),"Base template remains immutable");
        System.out.println("PASS IdeogramTurboTest: adapter wiring, installed dependencies, 4/8 steps, boxes, profiles, reuse and both agent routes");
    }
}
