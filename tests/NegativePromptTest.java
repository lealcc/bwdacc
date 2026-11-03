package com.leal.comfyremote;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public final class NegativePromptTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static JSONObject read(Path p)throws Exception{return new JSONObject(new String(Files.readAllBytes(p),"UTF-8"));}
    static void links(JSONObject g)throws Exception{for(Iterator<String> it=g.keys();it.hasNext();){JSONObject in=g.getJSONObject(it.next()).getJSONObject("inputs");for(Iterator<String> k=in.keys();k.hasNext();){JSONArray link=in.optJSONArray(k.next());if(link!=null&&link.length()==2&&link.opt(1) instanceof Number)check(g.has(link.getString(0)),"dangling negative link: "+link);}}}
    public static void main(String[] args)throws Exception{
        int count=0;
        try(java.util.stream.Stream<Path> files=Files.walk(Paths.get(args[0]))){
            for(Path file:(Iterable<Path>)files.filter(p->p.toString().endsWith(".json"))::iterator){
                JSONObject g=read(file);if(g.length()==0)continue;String first=g.keys().next();if(g.optJSONObject(first)==null||!g.getJSONObject(first).has("class_type"))continue;
                JSONObject original=new JSONObject(g.toString());
                AdvancedGeneration.apply(g,new JSONObject().put("randomSeed",false).put(NegativePrompt.KEY,"blur, watermark"));links(g);
                check(NegativePrompt.value(g).equals("blur, watermark"),"negative persisted: "+file);
                check(GenerationRecipe.advanced(g).getString(NegativePrompt.KEY).equals("blur, watermark"),"reuse captures negative: "+file);
                int length=g.length();NegativePrompt.apply(g,"blur, watermark");check(g.length()==length,"idempotent nodes: "+file);links(g);
                NegativePrompt.apply(g,"");check(NegativePrompt.request(g,"A blue teapot").equals("A blue teapot"),"clear removes textual exclusion");links(g);
                NegativePrompt.strip(g);
                for(Iterator<String> it=original.keys();it.hasNext();){String id=it.next();check(original.getJSONObject(id).getJSONObject("inputs").similar(g.getJSONObject(id).getJSONObject("inputs")),"original edges restored: "+file+"/"+id);}
                count++;
            }
        }
        JSONObject raw=read(Paths.get(args[0],"krea2_raw_api.json"));String positive=raw.getJSONObject("4").getJSONObject("inputs").toString();NegativePrompt.apply(raw,"blur");check(NegativePrompt.request(raw,"Scene").equals("Scene"),"RAW uses native conditioning");check(positive.equals(raw.getJSONObject("4").getJSONObject("inputs").toString()),"shared positive encoder unchanged");
        JSONObject turbo=read(Paths.get(args[0],"krea2_turbo_api.json"));NegativePrompt.apply(turbo,"blur");check(NegativePrompt.request(turbo,"Scene").contains("Exclusions:"),"CFG 1 uses textual instruction without changing CFG");check(turbo.getJSONObject("7").getJSONObject("inputs").getDouble("cfg")==1,"distilled CFG preserved");
        JSONObject video=read(Paths.get(args[0],"minimax_h3_i2v_api.json"));NegativePrompt.apply(video,"camera shake");String request=NegativePrompt.request(video,"The camera follows the boat.");PromptAgent.apply(video,false,false,request,"video",new JSONObject());String prepared=video.getJSONObject("8").getJSONObject("inputs").getString("prompt");NegativePrompt.direct(video);check(video.getJSONObject("8").getJSONObject("inputs").getString("prompt").startsWith(prepared),"H3 structured timeline preserved");NegativePrompt.direct(video);check(video.getJSONObject("8").getJSONObject("inputs").getString("prompt").indexOf("Exclusions:")==video.getJSONObject("8").getJSONObject("inputs").getString("prompt").lastIndexOf("Exclusions:"),"textual exclusions are idempotent");check(video.getJSONObject("8").getJSONObject("inputs").getString("prompt").endsWith("camera shake"),"video receives exclusions without an agent");
        System.out.println("PASS: "+count+" graphs; native/fallback, clear, reuse, idempotence, links and distilled CFG");
    }
}
