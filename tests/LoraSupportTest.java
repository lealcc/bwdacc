package com.leal.comfyremote;
import org.json.*;
import java.nio.file.*;
import java.util.*;
public final class LoraSupportTest {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        JSONObject graph=new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8"));
        List<String> ids=LoraSupport.loaders(graph);
        check(ids.size()==2,"both active loaders discovered");
        String target=ids.get(0);
        JSONObject structural=new JSONObject(graph.getJSONObject("18:26").getJSONObject("inputs").getJSONObject("lora_1").toString());
        JSONObject before=new JSONObject(graph.toString());
        JSONObject entry=new JSONObject().put("on",true).put("lora","moss\\test.safetensors").put("strength","-0.35").put("strengthTwo","0.5");
        JSONObject choices=new JSONObject().put(target,new JSONArray().put(entry));
        LoraSupport.apply(graph,choices,Arrays.asList("moss\\test.safetensors"));
        check(graph.getJSONObject("18:26").getJSONObject("inputs").getJSONObject("lora_1").toString().equals(structural.toString()),"structural DMD preserved");
        JSONObject inputs=graph.getJSONObject(target).getJSONObject("inputs"); boolean found=false;
        Iterator<String> keys=inputs.keys();while(keys.hasNext()){String key=keys.next();JSONObject v=inputs.optJSONObject(key);
            if(v!=null&&v.optString("lora").equals("moss\\test.safetensors")){found=true;check(v.get("strength") instanceof Number,"numeric model strength");check(v.get("strengthTwo") instanceof Number,"numeric CLIP strength");}}
        check(found,"selected LoRA present");
        check(inputs.getJSONArray("model").toString().equals(before.getJSONObject(target).getJSONObject("inputs").getJSONArray("model").toString()),"model links preserved");
        for(String path:Arrays.asList("h3\\bad.safetensors","moss\\missing.safetensors")){
            entry.put("lora",path);boolean rejected=false;
            try{LoraSupport.apply(new JSONObject(before.toString()),choices,Collections.emptyList());}catch(IllegalArgumentException e){rejected=true;}
            check(rejected,"reject incompatible or missing file");
        }
        entry.put("lora","moss\\test.safetensors").put("strength","NaN");boolean invalid=false;
        try{LoraSupport.apply(new JSONObject(before.toString()),choices,Arrays.asList("moss\\test.safetensors"));}catch(IllegalArgumentException e){invalid=true;}
        check(invalid,"reject non-finite strength");
        check(!LoraSupport.matches("mossy/test.safetensors","moss"),"exact folder boundary");
        check(LoraSupport.matches("MOSS/test.safetensors","moss"),"separator/case normalization");
        System.out.println("PASS LoRA graph/filter/strength/structural-preservation tests");
    }
}
