package com.leal.comfyremote;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public final class WorkflowProfilesTest {
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    static JSONObject read(String path)throws Exception{return new JSONObject(new String(Files.readAllBytes(Paths.get(path)),"UTF-8"));}
    static void save(JSONObject graph,String path)throws Exception{Files.write(Paths.get(path),graph.toString(2).getBytes("UTF-8"));}
    static void visit(JSONObject g,String id,Set<String> stack,Set<String> done)throws Exception{
        check(g.has(id),"Dangling node "+id);if(done.contains(id))return;check(stack.add(id),"Cycle "+id);
        JSONObject in=g.getJSONObject(id).getJSONObject("inputs");for(Iterator<String> it=in.keys();it.hasNext();){JSONArray link=in.optJSONArray(it.next());if(link!=null&&link.length()==2&&link.opt(0) instanceof String&&link.opt(1) instanceof Number)visit(g,link.getString(0),stack,done);}
        stack.remove(id);done.add(id);
    }
    static void valid(JSONObject g)throws Exception{Set<String> done=new HashSet<>();for(Iterator<String> it=g.keys();it.hasNext();)visit(g,it.next(),new HashSet<>(),done);}
    public static void main(String[] args)throws Exception{
        String root=args[0],out=args[1];JSONArray entries=new JSONArray();int exported=0;
        String[] files={"ideogram4_t2i_api","ideogram4_turbotime_api","moss_t2i_api","kromoss_t2i_api","minimax_h3_i2v_api","minimax_h3_turbo_api","krea2_raw_api","krea2_turbo_api","krea2_turbo_4step_api"};
        JSONObject layout=new JSONObject().put("enabled",true).put("boxes",new JSONArray().put(new JSONObject().put("x",.1).put("y",.2).put("w",.3).put("h",.4).put("desc","A small blue paper boat").put("type","obj")));
        for(String file:files)for(String profile:MemoryProfiles.IDS){JSONObject g=read(root+"/"+file+".json");String blocked=MemoryProfiles.unavailable(g,profile);JSONObject entry=new JSONObject().put("workflow",file).put("profile",profile).put("available",blocked.isEmpty()).put("reason",blocked);entries.put(entry);if(!blocked.isEmpty()){try{MemoryProfiles.apply(g,profile);throw new AssertionError("Unsupported profile accepted");}catch(IllegalArgumentException expected){}continue;}
            if(VideoWorkflow.matches(g))VideoWorkflow.prepare(g,Collections.emptyList(),false,"A paper boat drifts slowly on a quiet pond. Static camera. No music or dialogue.","16:9",profile.equals("8")?.33:.66,0,0,5,12345);
            else{GenerationOptions.imageOutput(g,"1:1",1,0,0);if(MemoryProfiles.family(g).equals("ideogram"))BoxLayout.apply(g,null,"A paper boat on a quiet pond");}
            MemoryProfiles.apply(g,profile);valid(g);int count=g.length();MemoryProfiles.apply(g,profile);valid(g);check(count==g.length(),"Profile idempotency "+file+profile);
            for(Iterator<String> ids=g.keys();ids.hasNext();){JSONObject node=g.getJSONObject(ids.next());if(node.optString("class_type").equals("Ideogram4OptimizationsKJ"))check(!node.getJSONObject("inputs").getBoolean("bf16_rope"),"Keep native Ideogram matrix RoPE");}
            String path=file+"_"+profile+".json";save(g,out+"/"+path);entry.put("graph",path);exported++;
            MemoryProfiles.apply(g,"24plus");valid(g);for(Iterator<String> it=g.keys();it.hasNext();){JSONObject n=g.getJSONObject(it.next());check(!n.optString("class_type").equals("VAEDecodeTiled"),"Decode restored after changing profile");}
        }
        for(String name:Arrays.asList("ideogram4_t2i_api","ideogram4_turbotime_api","moss_t2i_api","kromoss_t2i_api")){JSONObject g=read(root+"/"+name+".json");GenerationOptions.imageOutput(g,"9:16",2,0,0);BoxLayout.apply(g,layout,"Minimal paper boat poster");valid(g);check(g.has("momentos_layout"),"Layout node");JSONObject in=g.getJSONObject("momentos_layout").getJSONObject("inputs");
            if(name.startsWith("ideogram")){check(in.getString("coord_mode").equals("normalized"),"Kijai coordinates");check(in.getInt("width")==g.getJSONObject("9").getJSONObject("inputs").getInt("width"),"Scheduler dimensions");}
            else{check(!g.has("momentos_layout_elements"),"No custom bridge required");JSONArray elements=in.getJSONObject("element").getJSONArray("__value__");check(elements.getJSONObject(0).getJSONArray("bbox").toString().equals("[200,100,600,400]"),"MOSS normalized yxyx");}
            save(g,out+"/"+name+"_boxes.json");
            BoxLayout.apply(g,new JSONObject().put("enabled",false),"A plain paper boat");valid(g);if(!name.startsWith("ideogram"))check(!g.has("momentos_layout"),"Disabling boxes removes inherited composition");
        }
        JSONObject extension=read(root+"/minimax_h3_turbo_api.json"),range=VideoExtendWorkflow.range(1000,3500);
        VideoExtendWorkflow.prepare(extension,"Momentos/Tests/extension-source.mp4","The boat keeps moving forward.",range,720,1280,4000,.33,5,42);valid(extension);
        JSONObject condition=extension.getJSONObject("8").getJSONObject("inputs");check(condition.getInt("height")>condition.getInt("width"),"Portrait extension preserves source ratio");check(condition.getInt("length")%17==5,"H3 frame alignment");check(extension.getJSONObject("12").getJSONObject("inputs").getJSONArray("conditioning").getString(0).equals("momentos_extend_guide"),"Motion guide reaches sampler");check(extension.getJSONObject("17").getJSONObject("inputs").getJSONArray("video").getString(0).equals("momentos_extend_merge"),"Saves joined output");
        check(PromptAgent.instructions(extension,"video_extend").contains("22-frame"),"Agent preserves extension context");check(GenerationRecipe.edit(new JSONObject().put("operation","video_extend")),"Reuse restores original clip");
        check(GenerationRecipe.inputFiles(extension,true).get(0).equals("Momentos/Tests/extension-source.mp4"),"Legacy extension recovers input clip");check(GenerationRecipe.legacyOptions(extension,true).getJSONObject("videoRange").getLong("start")==1000,"Legacy extension restores trim");
        save(extension,out+"/h3_extension_test.json");
        for(boolean turbo:new boolean[]{false,true})for(String profile:new String[]{"24plus","24","16"}){
            JSONObject g=read(root+"/"+(turbo?"minimax_h3_turbo_api":"minimax_h3_i2v_api")+".json");
            VideoExtendWorkflow.prepare(g,"Momentos/extension-source.mp4","The paper boat continues drifting forward.",VideoExtendWorkflow.range(0,5000),1280,720,5000,.33,5,12345);
            MemoryProfiles.apply(g,profile);valid(g);save(g,out+"/minimax_h3_extend_"+(turbo?"turbo_":"normal_")+profile+".json");
        }
        try{VideoExtendWorkflow.validate(VideoExtendWorkflow.range(2000,2200),4000);throw new AssertionError("Subsecond accepted");}catch(IllegalArgumentException expected){}
        try{VideoExtendWorkflow.validate(VideoExtendWorkflow.range(0,5000),4000);throw new AssertionError("Outside source accepted");}catch(IllegalArgumentException expected){}
        JSONObject bad=new JSONObject(layout.toString());bad.getJSONArray("boxes").getJSONObject(0).put("w",2);try{BoxLayout.regions(bad);throw new AssertionError("Box overflow");}catch(IllegalArgumentException expected){}
        save(new JSONObject().put("profiles",entries),out+"/profile-matrix.json");
        if(args.length>2){JSONObject schema=read(args[2]),g=read(root+"/ideogram4_t2i_api.json");WorkflowDependencies.verify(g,schema);g.getJSONObject("1").getJSONObject("inputs").put("unet_name","not-installed-model.safetensors");try{WorkflowDependencies.verify(g,schema);throw new AssertionError("Missing dependency accepted");}catch(IllegalArgumentException expected){check(expected.getMessage().contains("not-installed-model"),"Actionable dependency error");}}
        System.out.println("PASS WorkflowProfilesTest: "+exported+" portable graphs, profile switching, boxes, trim/extension, reuse");
    }
}
