package com.leal.comfyremote;

import org.json.*;
import java.nio.file.*;
import java.util.*;

public final class PrivateConditioningTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static JSONObject read(String path)throws Exception{return new JSONObject(new String(Files.readAllBytes(Paths.get(path)),"UTF-8"));}
    static JSONObject inputs(JSONObject graph,String id)throws Exception{return graph.getJSONObject(id).getJSONObject("inputs");}
    static int count(JSONObject graph){int n=0;for(Iterator<String> it=graph.keys();it.hasNext();)if(it.next().startsWith("bwdacc_private_conditioning_"))n++;return n;}
    public static void main(String[] args)throws Exception{
        String root=args[0],out=args[2];JSONObject schema=read(args[1]);Files.createDirectories(Paths.get(out));int cases=0;
        String prompt="Product photograph of a blue ceramic teapot beside a lemon on a wooden table, warm daylight, realistic photography.";
        for(String file:new String[]{"ideogram4_t2i_api","ideogram4_turbotime_api","krea2_raw_api","krea2_turbo_api","krea2_turbo_4step_api"}){
            JSONObject base=read(root+"/"+file+".json");String original=base.toString();boolean ideogram=file.startsWith("ideogram");
            String consumer=ideogram?"10":"7",key=ideogram?"model":"positive";
            int adapters=file.equals("ideogram4_t2i_api")?2:1;
            for(boolean agent:new boolean[]{false,true})for(String profile:new String[]{"24","24plus"}){
                JSONObject graph=GenerationRecipe.copy(base);inputs(graph,ideogram?"5":"4").put("text",prompt);
                PromptAgent.apply(graph,agent,true,prompt,"image",schema);
                if(ideogram){JSONObject boxes=new JSONObject().put("enabled",true).put("boxes",new JSONArray().put(new JSONObject()
                    .put("x",.1).put("y",.15).put("w",.55).put("h",.65).put("type","obj").put("desc","A blue ceramic teapot")));
                    BoxLayout.apply(graph,boxes,prompt);}
                MemoryProfiles.apply(graph,profile);WorkflowDependencies.verify(graph,schema);JSONObject before=GenerationRecipe.copy(graph);
                PrivateConditioning.apply(graph,false);check(graph.toString().equals(before.toString()),"SFW unchanged: "+file);
                PrivateConditioning.apply(graph,true);WorkflowProfilesTest.valid(graph);WorkflowDependencies.verify(graph,schema);
                check(count(graph)==adapters,"Correct private branch count: "+file);
                String adapter=inputs(graph,consumer).getJSONArray(key).getString(0);JSONObject params=inputs(graph,adapter);
                if(ideogram){
                    check(LoraSupport.normalized(params.getString("lora_name")).equals(LoraSupport.normalized(PrivateConditioning.IDEOGRAM_LORA)),"Tested Snofs adapter");
                    check(params.getDouble("strength_model")==1,"Tested private LoRA weight");
                    if(adapters==2){JSONObject negative=inputs(graph,inputs(graph,consumer).getJSONArray("model_negative").getString(0));
                        check(negative.getJSONArray("model").toString().equals(inputs(before,consumer).getJSONArray("model_negative").toString()),"Unconditional model patched without replacing its source");
                        check(negative.getDouble("strength_model")==1,"Unconditional branch receives the same adapter");}
                    check(inputs(graph,consumer).getJSONArray(file.contains("turbotime")?"conditioning":"positive").toString()
                        .equals(inputs(before,consumer).getJSONArray(file.contains("turbotime")?"conditioning":"positive").toString()),"Ideogram text conditioning untouched");
                }else{
                    check(params.getString("per_layer_weights").split(",").length==12,"Krea encoder tap layout");
                    check(params.getDouble("multiplier")==1,"Layer adjustment without the destabilizing 4x global gain");
                }
                check(params.getJSONArray(ideogram?"model":"conditioning").toString().equals(inputs(before,consumer).getJSONArray(key).toString()),"Original edge is the source");
                for(Iterator<String> it=before.keys();it.hasNext();){String id=it.next();if(id.equals(consumer))continue;
                    check(graph.getJSONObject(id).toString().equals(before.getJSONObject(id).toString()),"Model/LoRA/negative/boxes/agent preserved: "+id);}
                if(!ideogram)check(inputs(graph,consumer).getJSONArray("negative").toString().equals(inputs(before,consumer).getJSONArray("negative").toString()),"Negative is not rebalanced");
                JSONObject once=GenerationRecipe.copy(graph);PrivateConditioning.apply(graph,true);
                check(graph.similar(once),"Repeated prepare/POST is idempotent, including server-bound path spelling");
                SfwPromptFilter.apply(graph,false);check(count(graph)==adapters,"Private prompt path retains adapters");
                JSONObject restored=GenerationRecipe.configured(new JSONObject().put("template",base).put("graph",graph));
                PrivateConditioning.apply(restored,false);check(count(restored)==0,"Reuse under SFW does not retain private adapter");
                JSONObject legacy=GenerationRecipe.legacyTemplate(graph);check(count(legacy)==0,"Legacy import strips owned adapter");WorkflowProfilesTest.valid(legacy);
                PrivateConditioning.apply(graph,false);WorkflowProfilesTest.valid(graph);
                // verify() normalizes file separators, so compare normalized pre-transform graph.
                WorkflowDependencies.verify(before,schema);
                if(!graph.similar(before)){
                    Files.write(Paths.get(out,"before.json"),before.toString(2).getBytes("UTF-8"));
                    Files.write(Paths.get(out,"after.json"),graph.toString(2).getBytes("UTF-8"));
                    throw new AssertionError("Turning private mode off fully restores graph: "+file+" agent="+agent+" profile="+profile);
                }cases++;
            }
            JSONObject live=GenerationRecipe.copy(base);inputs(live,ideogram?"5":"4").put("text",prompt);
            inputs(live,"7").put(ideogram?"noise_seed":"seed",882211L);
            // The app always builds structured Ideogram prompts, even without boxes.
            if(ideogram)BoxLayout.apply(live,new JSONObject(),prompt);
            MemoryProfiles.apply(live,"24");PrivateConditioning.apply(live,true);WorkflowDependencies.verify(live,schema);
            inputs(live,ideogram?"13":"10").put("filename_prefix","bwdacc_QA/private_conditioning/"+file);
            Files.write(Paths.get(out,file+".json"),live.toString(2).getBytes("UTF-8"));
            check(base.toString().equals(original),"Catalog template remains immutable");
        }
        JSONObject other=read(root+"/minimax_h3_i2v_api.json");String saved=other.toString();PrivateConditioning.apply(other,true);check(saved.equals(other.toString()),"Other families unaffected");
        JSONObject collision=read(root+"/krea2_raw_api.json");
        collision.put("bwdacc_private_conditioning_7_positive",new JSONObject().put("class_type",PrivateConditioning.NODE).put("inputs",new JSONObject()
            .put("conditioning",new JSONArray().put("4").put(0)).put("multiplier",1).put("per_layer_weights","")));
        String imported=collision.toString();PrivateConditioning.apply(collision,true);PrivateConditioning.apply(collision,false);
        check(collision.toString().equals(imported),"Unowned node and ID collision preserved");
        JSONObject manual=read(root+"/ideogram4_t2i_api.json");
        manual.put("user_lora",new JSONObject().put("class_type","LoraLoaderModelOnly").put("inputs",new JSONObject()
            .put("model",inputs(manual,"10").getJSONArray("model")).put("lora_name",PrivateConditioning.IDEOGRAM_LORA.replace('/','\\')).put("strength_model",.35)));
        inputs(manual,"10").put("model",new JSONArray().put("user_lora").put(0));JSONObject manualBefore=GenerationRecipe.copy(manual);
        PrivateConditioning.apply(manual,true);check(count(manual)==1,"User-selected Snofs is not stacked twice; missing unconditional branch still receives it");
        check(inputs(manual,"user_lora").getDouble("strength_model")==.35,"User-selected LoRA weight preserved");
        PrivateConditioning.apply(manual,false);check(manual.similar(manualBefore),"Public mode removes owned LoRA only");
        LoraPrivacy.load(read(root+"/../lora-privacy.json"));LoraPrivacy.enforce(manual);
        check(inputs(manual,"user_lora").getDouble("strength_model")==0,"Final public-mode privacy enforcement also disables manual Snofs");
        System.out.println("PASS PrivateConditioningTest: "+cases+" profile/agent cases; five variants; reversible mode, reuse, negative branches, adapters, graph validity and server dependencies");
    }
}
