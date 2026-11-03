package com.leal.comfyremote;

import org.json.*;
import java.util.*;

/** Unknown adapters require private mode until reviewed. A graph annotation cannot bypass this policy. */
final class LoraPrivacy {
    private static volatile JSONObject catalog=new JSONObject();
    private static volatile JSONObject metadata=new JSONObject();
    private static volatile JSONObject names=new JSONObject();
    static final String NAMES_KEY="lora_display_names";
    private static String metadataServer="";
    private static long metadataAt;
    interface Fetch {JSONObject get(String url)throws Exception;}
    static void refresh(String address,Fetch fetch){
        synchronized(LoraPrivacy.class){if(address.equals(metadataServer)&&System.currentTimeMillis()-metadataAt<300_000)return;}
        JSONObject data=null;try{data=fetch.get(address+"/momentos/loras/metadata");}catch(Exception unavailable){try{data=fetch.get(address+"/rgthree/api/loras/info?light=1");}catch(Exception ignored){}}
        JSONObject next=new JSONObject();JSONArray items=data==null?null:data.optJSONArray("data");
        if(items!=null)for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item!=null&&!item.optString("file").isEmpty())try{next.put(LoraSupport.normalized(item.optString("file")),item);}catch(Exception ignored){}}
        synchronized(LoraPrivacy.class){metadata=next;metadataServer=address;metadataAt=System.currentTimeMillis();}
    }
    static void loadNames(JSONObject value)throws JSONException{names=new JSONObject(value.toString());}
    static String display(String path){
        String name=cleanName(names.optString(LoraSupport.normalized(path)));
        return name.isEmpty()?automaticName(path):name;
    }
    static String automaticName(String path){
        JSONObject remote=metadata.optJSONObject(LoraSupport.normalized(path)),local=catalog.optJSONObject(LoraSupport.normalized(path));
        String name=remote==null?"":usefulName(remote.optString("name",""));if(name.trim().isEmpty()&&local!=null)name=usefulName(local.optString("name",""));
        if(name.trim().isEmpty())name=filename(path).replace('_',' ');
        return cleanName(name);
    }
    static String filename(String path){String file=path.replace('\\','/');return withoutExtension(file.substring(file.lastIndexOf('/')+1));}
    private static String withoutExtension(String value){return value.replaceFirst("(?i)\\.(safetensors|pt|pth|ckpt)$","");}
    static String cleanName(String name){name=withoutExtension(name.replaceAll("[\\p{Cntrl}]"," ").trim()).trim();return name.length()>180?name.substring(0,180):name;}
    static boolean matchesSearch(String path,String query){return searchText(path+" "+display(path)+" "+automaticName(path)).contains(searchText(query).trim());}
    private static String searchText(String value){return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);}
    private static String usefulName(String name){return Arrays.asList("aitk lora","lora","model","default","untitled","output").contains(name.trim().toLowerCase(Locale.ROOT).replaceAll("[ _.-]+"," "))?"":name;}
    static boolean nameLooksAdult(String name){return name.toLowerCase(Locale.ROOT).matches(".*(nsfw|snofs|nude|nudity|porn|hentai|cumshot|pussy|penis|blowjob|handjob|footjob|dildo|gangbang|deepthroat|sex art|sexual|sex lora).*");}
    static void load(JSONObject value){catalog=value.optJSONObject("entries")==null?new JSONObject():value.optJSONObject("entries");}
    static String status(String path){
        JSONObject remote=metadata.optJSONObject(LoraSupport.normalized(path));
        if(remote!=null&&(remote.optBoolean("adult")||nameLooksAdult(remote.optString("name"))))return "nsfw";
        JSONObject item=catalog.optJSONObject(LoraSupport.normalized(path));
        if(item!=null)return "sfw".equals(item.optString("status"))?"sfw":"nsfw";
        String name=LoraSupport.normalized(path);name=name.substring(name.lastIndexOf('/')+1);
        // Exact published infrastructure adapters, including models not installed yet.
        if(Arrays.asList("minimax_h3_open_regenerate_2k_refiner_rank_16_bf16.safetensors","minimax_h3_open_regenerate_2k_refiner_rank_384_bf16.safetensors",
            "open_h3_regenerate_2k_lora.safetensors","sharp_curve_branches.safetensors","ideogram_4_turbotime_v1.safetensors",
            "minimax_h3_turbo_v4_step600_comfyui_t8-convert.safetensors","krea2_turbo_4step_rank_64_lora_comfyui.safetensors").contains(name))return "sfw";
        return "nsfw";
    }
    static boolean restricted(String path){return !"sfw".equals(status(path));}
    static String label(String path){return "sfw".equals(status(path))?"SFW":"NSFW";}
    static int disableChoices(JSONObject choices)throws JSONException{
        int count=0;for(Iterator<String> it=choices.keys();it.hasNext();){JSONArray entries=choices.optJSONArray(it.next());if(entries==null)continue;
            for(int i=0;i<entries.length();i++){JSONObject entry=entries.optJSONObject(i);if(entry!=null&&restricted(entry.optString("lora"))&&entry.optBoolean("on")){entry.put("on",false);count++;}}}
        return count;
    }
    static void filterPresets(List<ReferencePreset> presets)throws JSONException{
        for(ReferencePreset preset:presets){JSONArray allowed=new JSONArray();for(int i=0;i<preset.loras.length();i++){JSONObject value=preset.loras.getJSONObject(i);if(!restricted(value.optString("lora")))allowed.put(value);}preset.loras=allowed;}
    }
    static int enforce(JSONObject graph)throws JSONException{
        int count=0;for(Iterator<String> it=graph.keys();it.hasNext();){JSONObject node=graph.optJSONObject(it.next());if(node==null)continue;JSONObject input=node.optJSONObject("inputs");if(input==null)continue;
            String type=node.optString("class_type");
            if(input.opt("lora_name") instanceof String&&restricted(input.optString("lora_name"))){
                boolean weights=false;for(String key:new String[]{"strength_model","strength_clip","strength"})if(input.has(key)){input.put(key,0);weights=true;}
                if(!weights)throw new IllegalArgumentException(UiLanguage.t("LoRA restrito em node sem controle de peso: ")+type);
                count++;
            }
            for(Iterator<String> keys=input.keys();keys.hasNext();){String key=keys.next();JSONObject value=input.optJSONObject(key);
                if(value!=null&&value.has("lora")&&restricted(value.optString("lora"))){if(value.optBoolean("on")){value.put("on",false);count++;}}
                else if(type.toLowerCase(Locale.ROOT).contains("lora")&&key.startsWith("lora_")&&input.opt(key) instanceof String&&!key.equals("lora_name")&&!key.equals("lora_dtype")&&!input.optString(key).isEmpty()&&restricted(input.optString(key)))
                    throw new IllegalArgumentException(UiLanguage.t("Revise o LoRA restrito neste workflow importado: ")+type);
            }
        }return count;
    }
}
