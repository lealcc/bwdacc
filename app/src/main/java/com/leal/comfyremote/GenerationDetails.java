package com.leal.comfyremote;

import org.json.*;
import java.util.*;

/** Immutable request summary and server execution timestamps, never download/queue duration. */
final class GenerationDetails {
    static JSONObject snapshot(JSONObject graph) {
        JSONObject result=new JSONObject();if(graph==null)return result;
        LinkedHashMap<String,LinkedHashSet<String>> values=new LinkedHashMap<>();
        try {
            Iterator<String> ids=graph.keys();while(ids.hasNext()){
                JSONObject node=graph.optJSONObject(ids.next());if(node==null)continue;
                JSONObject in=node.optJSONObject("inputs");if(in==null)continue;
                String type=node.optString("class_type");
                JSONObject meta=node.optJSONObject("_meta");if(meta!=null){add(values,"Referências",meta.optString("momentos_references"));if(meta.optBoolean("momentos_agent"))add(values,"Agente",PromptAgent.MODEL+(meta.optString("profile").equals("NSFW")?" Heretic":"")+" · "+meta.optString("profile"));}
                if(type.equals("MiniMaxH3RefModsLoader"))for(int slot=1;slot<=8;slot++){String mod=in.optString("mod_"+slot);if(!mod.isEmpty()&&!mod.equals("(none)"))add(values,"RefMods",mod+" · "+in.optDouble("strength_"+slot,1));}
                if(meta!=null&&meta.has("momentos_memory_profile"))add(values,"Perfil de memória",MemoryProfiles.label(meta.optString("momentos_memory_profile")));
                if(meta!=null&&meta.optJSONObject("momentos_layout")!=null){JSONArray boxes=meta.getJSONObject("momentos_layout").optJSONArray("boxes");if(boxes!=null&&meta.getJSONObject("momentos_layout").optBoolean("enabled"))add(values,"Composição",boxes.length()+" boxes");}
                if(type.equals("MomentosVideoRange"))add(values,"Trecho de origem",String.format(Locale.ROOT,"%.1fs – %.1fs",in.optLong("start_ms")/1000.0,in.optLong("end_ms")/1000.0));
                if(meta!=null&&meta.optBoolean("momentos_agent"))continue;
                for(String key:new String[]{"unet_name","ckpt_name","model_name","diffusion_model_name","bg_removal_name"})add(values,"Modelo",scalar(graph,in.opt(key),0));
                for(String[] field:new String[][]{{"seed","Seed"},{"noise_seed","Seed"},{"steps","Passos"},{"cfg","CFG"},{"guidance","Guidance"},{"sampler_name","Sampler"},{"scheduler","Scheduler"},{"denoise","Denoise"},{"frame_rate","FPS"},{"fps","FPS"}})
                    add(values,field[1],scalar(graph,in.opt(field[0]),0));
                if("ResolutionSelector".equals(type)){
                    add(values,"Proporção",scalar(graph,in.opt("aspect_ratio"),0));
                    Object mp=scalar(graph,in.opt("megapixels"),0);if(mp!=null)add(values,"Resolução solicitada",mp+" MP");
                }
                if(GenerationOptions.imageLatent(type)||type.startsWith("MiniMaxH3")){
                    Object w=scalar(graph,in.opt("width"),0),h=scalar(graph,in.opt("height"),0);
                    if(w!=null&&h!=null)add(values,"Dimensões solicitadas",w+" × "+h);
                    add(values,"Quadros",scalar(graph,in.opt("length"),0));
                }
                if(in.has("lora_name")&&in.optDouble("strength_model",1)!=0)add(values,"LoRAs",in.optString("lora_name")+" · "+in.optDouble("strength_model",1));
                Iterator<String> keys=in.keys();while(keys.hasNext()){
                    JSONObject lora=in.optJSONObject(keys.next());
                    if(lora!=null&&lora.optBoolean("on")&&lora.has("lora"))add(values,"LoRAs",lora.optString("lora")+" · "+lora.optDouble("strength",1));
                }
            }
            for(Map.Entry<String,LinkedHashSet<String>> entry:values.entrySet())result.put(entry.getKey(),String.join("\n",entry.getValue()));
        }catch(Exception ignored){}
        return result;
    }
    private static void add(Map<String,LinkedHashSet<String>> values,String key,Object value){
        if(value==null||JSONObject.NULL.equals(value)||String.valueOf(value).isEmpty())return;
        if(!values.containsKey(key))values.put(key,new LinkedHashSet<>());values.get(key).add(String.valueOf(value));
    }
    private static Object scalar(JSONObject graph,Object value,int depth){
        if(value instanceof Number||value instanceof String||value instanceof Boolean)return value;
        if(!(value instanceof JSONArray)||depth>6)return null;
        JSONArray link=(JSONArray)value;JSONObject node=graph.optJSONObject(link.optString(0));
        if(node==null)return null;JSONObject in=node.optJSONObject("inputs");if(in==null)return null;
        if(node.optString("class_type").startsWith("Primitive"))return scalar(graph,in.opt("value"),depth+1);
        return null;
    }
    static JSONObject historySettings(JSONObject job){JSONArray prompt=job.optJSONArray("prompt");return prompt==null?new JSONObject():snapshot(prompt.optJSONObject(2));}
    static long[] executionTimes(JSONObject job){
        long start=0,end=0;JSONObject status=job.optJSONObject("status");JSONArray messages=status==null?null:status.optJSONArray("messages");
        if(messages!=null)for(int i=0;i<messages.length();i++){
            JSONArray event=messages.optJSONArray(i);if(event==null)continue;JSONObject data=event.optJSONObject(1);if(data==null)continue;
            long time=data.optLong("timestamp",0);String name=event.optString(0);
            if("execution_start".equals(name)&&time>0)start=start==0?time:Math.min(start,time);
            if(("execution_success".equals(name)||"execution_error".equals(name)||"execution_interrupted".equals(name))&&time>0)end=Math.max(end,time);
        }
        return new long[]{start,end>=start?end:0};
    }
    static String duration(long start,long end){
        if(start<=0||end<start)return UiLanguage.t("Não registrado");
        long seconds=Math.max(0,Math.round((end-start)/1000.0));
        return seconds<60?seconds+UiLanguage.t(" s"):seconds<3600?(seconds/60)+UiLanguage.t(" min ")+(seconds%60)+UiLanguage.t(" s"):(seconds/3600)+UiLanguage.t(" h ")+((seconds%3600)/60)+UiLanguage.t(" min");
    }
}
