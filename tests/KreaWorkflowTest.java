package com.leal.comfyremote;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public final class KreaWorkflowTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static JSONObject read(String file)throws Exception{return new JSONObject(new String(Files.readAllBytes(Paths.get(file)),"UTF-8"));}
    public static void main(String[] args)throws Exception{
        JSONObject schema=read(args[1]);LoraPrivacy.load(read(args[2]));
        for(String variant:new String[]{"raw","turbo","turbo_4step"}){
            JSONObject graph=read(args[0]+"/krea2_"+variant+"_api.json");
            check(MemoryProfiles.family(graph).equals("krea2"),"Krea family");
            check(LoraSupport.loaders(graph).equals(Arrays.asList("3")),"Live Krea manual loader");
            check(Arrays.equals(PromptAgent.target(graph),new String[]{"4","text"}),"Agent uses positive prompt only");
            check(!BoxLayout.supported(graph),"No unsupported boxes");
            check(!MemoryProfiles.unavailable(graph,"8").isEmpty()&&!MemoryProfiles.unavailable(graph,"16").isEmpty(),"Unverified VRAM tiers blocked");
            WorkflowDependencies.verify(graph,schema);
            for(int mp:new int[]{1,2,4}){GenerationOptions.imageOutput(graph,"9:16",mp,0,0);int[] size=GenerationOptions.dimensions("9:16",mp,16);JSONObject latent=graph.getJSONObject("6").getJSONObject("inputs");check(latent.getInt("width")==size[0]&&latent.getInt("height")==size[1],"Aspect/MP applied");
                if(variant.equals("raw"))check(graph.getJSONObject("11").getJSONObject("inputs").getInt("height")==size[1],"RAW shift follows resolution");}
            String structural=variant.equals("turbo_4step")?graph.getJSONObject("11").toString():"";
            String path="krea2\\test-unknown.safetensors";
            JSONObject choices=new JSONObject().put("3",new JSONArray().put(new JSONObject().put("lora",path).put("on",true).put("strength",.65)));
            LoraSupport.apply(graph,choices,Arrays.asList(path));check(LoraPrivacy.enforce(graph)==1,"Unknown manual LoRA disabled in public mode");
            if(!structural.isEmpty()){check(graph.getJSONObject("11").toString().equals(structural),"4-step adapter survives privacy and manual selections");check(LoraSupport.structural(graph,graph.getJSONObject("11").getJSONObject("inputs").getString("lora_name")),"Fixed adapter not listed twice");}
            choices.getJSONArray("3").getJSONObject(0).put("lora","moss\\wrong.safetensors");try{LoraSupport.apply(graph,choices,Arrays.asList("moss\\wrong.safetensors"));throw new AssertionError("Wrong family accepted");}catch(IllegalArgumentException expected){}
            MemoryProfiles.apply(graph,"24");WorkflowProfilesTest.valid(graph);check(graph.getJSONObject("9").getString("class_type").equals("VAEDecodeTiled"),"24 GB tiled decode");
            GenerationOptions.varySeed(graph,1);check(graph.getJSONObject("7").getJSONObject("inputs").getLong("seed")!=0,"Batch seeds vary");
        }
        check(!LoraSupport.matches("krea20/test.safetensors","krea2"),"Folder boundary");
        System.out.println("PASS KreaWorkflowTest: dependencies, profiles, prompt, ratio/MP, seeds, manual/structural LoRAs and privacy");
    }
}
