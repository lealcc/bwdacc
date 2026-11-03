package com.leal.comfyremote;

import org.json.*;
import java.util.*;

/** Reversible, per-request conditioning profiles for the local image models. */
final class PrivateConditioning {
    static final String NODE="ConditioningKrea2Rebalance";
    static final String IDEOGRAM_LORA="ideogram4/ideogramSnofsV1.safetensors";
    private static final String MARK="bwdacc_private_conditioning", PREFIX=MARK+"_";
    // Author's Krea Rebalance-Pack preset.
    // Explicit weights also keep behavior stable when a server updates the pack.
    // Keep global gain at 1: the upstream 4x default damages ordinary images with
    // our existing CFG settings. The per-layer ratios supply the adjustment.
    static final String KREA="1.0,1.0,1.0,1.0,1.0,1.0,1.0,2.5,5.0,1.1,4.0,1.0";

    static void apply(JSONObject graph,boolean privateMode)throws Exception{
        // Dependency verification binds portable names to the server's exact enum.
        // Reapplying at POST must retain that spelling (not reintroduce '/' on Windows).
        String ideogramLora=IDEOGRAM_LORA;
        for(String id:ids(graph)){JSONObject n=graph.optJSONObject(id),meta=n==null?null:n.optJSONObject("_meta"),in=n==null?null:n.optJSONObject("inputs");
            if(id.startsWith(PREFIX)&&meta!=null&&meta.optBoolean(MARK)&&in!=null&&sameLora(in.optString("lora_name")))ideogramLora=in.optString("lora_name");
        }
        strip(graph);
        if(!privateMode)return;
        String family=MemoryProfiles.family(graph);
        if(!family.equals("ideogram")&&!family.equals("krea2"))return;
        List<String> ids=ids(graph);
        for(String id:ids){
            JSONObject node=graph.getJSONObject(id),in=node.optJSONObject("inputs");
            if(in==null)continue;
            String type=node.optString("class_type"),key;
            if(type.equals("BasicGuider"))key="conditioning";
            else if(Arrays.asList("DualModelGuider","CFGGuider","KSampler","KSamplerAdvanced","SamplerCustom").contains(type))key="positive";
            else continue;
            if(family.equals("ideogram")){
                // Standard Ideogram has two separately loaded branches. Patch both;
                // TurboTime has only the conditional branch and keeps its speed adapter.
                for(String modelKey:new String[]{"model","model_negative"}){
                    JSONArray source=in.optJSONArray(modelKey);
                    if(source==null||hasIdeogramLora(graph,source,new HashSet<>()))continue;
                    wrap(graph,id,modelKey,"LoraLoaderModelOnly","model",new JSONObject()
                        .put("lora_name",ideogramLora).put("strength_model",1.0),"Ideogram 4 · private adapter");
                }
                continue;
            }
            JSONArray source=in.optJSONArray(key);
            if(source==null||source.length()!=2)continue;
            // Wrap only the positive consumer edge. Shared text encodes, zero/negative
            // branches, box builders, reference metadata and user LoRAs stay intact.
            wrap(graph,id,key,NODE,"conditioning",new JSONObject().put("multiplier",1.0).put("per_layer_weights",KREA),"Krea 2 · private conditioning");
        }
    }

    private static void wrap(JSONObject graph,String id,String key,String type,String sourceKey,JSONObject settings,String title)throws JSONException{
        JSONObject in=graph.getJSONObject(id).getJSONObject("inputs");
        String slot=PREFIX+id+"_"+key;int suffix=0;
        while(graph.has(slot))slot=PREFIX+id+"_"+key+"_"+(++suffix);
        settings.put(sourceKey,new JSONArray(in.getJSONArray(key).toString()));
        graph.put(slot,new JSONObject().put("class_type",type).put("inputs",settings)
            .put("_meta",new JSONObject().put(MARK,true).put("title",title)));
        in.put(key,new JSONArray().put(slot).put(0));
    }

    private static boolean hasIdeogramLora(JSONObject graph,JSONArray link,Set<String> seen){
        String id=link.optString(0);if(!seen.add(id))return false;
        JSONObject node=graph.optJSONObject(id),in=node==null?null:node.optJSONObject("inputs");if(in==null)return false;
        String type=node.optString("class_type");
        if(type.startsWith("LoraLoader")&&sameLora(in.optString("lora_name"))&&in.optDouble("strength_model",0)!=0)return true;
        if(type.equals("Power Lora Loader (rgthree)"))for(Iterator<String> keys=in.keys();keys.hasNext();){JSONObject lora=in.optJSONObject(keys.next());
            if(lora!=null&&lora.optBoolean("on")&&sameLora(lora.optString("lora"))&&lora.optDouble("strength",0)!=0)return true;
        }
        JSONArray source=in.optJSONArray("model");return source!=null&&hasIdeogramLora(graph,source,seen);
    }
    private static boolean sameLora(String name){return LoraSupport.normalized(name).equals(LoraSupport.normalized(IDEOGRAM_LORA));}

    static void strip(JSONObject graph)throws JSONException{
        Set<String> remove=new HashSet<>();
        for(String id:ids(graph)){
            JSONObject n=graph.optJSONObject(id),meta=n==null?null:n.optJSONObject("_meta");
            if(id.startsWith(PREFIX)&&n!=null&&(n.optString("class_type").equals(NODE)||n.optString("class_type").equals("LoraLoaderModelOnly"))&&meta!=null&&meta.optBoolean(MARK))remove.add(id);
        }
        for(String id:ids(graph)){
            JSONObject in=graph.getJSONObject(id).optJSONObject("inputs");if(in==null)continue;
            for(Iterator<String> keys=in.keys();keys.hasNext();){String key=keys.next();JSONArray link=in.optJSONArray(key);Set<String> seen=new HashSet<>();
                while(link!=null&&link.optInt(1,-1)==0&&remove.contains(link.optString(0))&&seen.add(link.optString(0))){
                    JSONObject wrapped=graph.getJSONObject(link.getString(0));
                    JSONArray original=wrapped.getJSONObject("inputs").optJSONArray(wrapped.optString("class_type").equals(NODE)?"conditioning":"model");
                    if(original==null)throw new JSONException(UiLanguage.t("Invalid private conditioning source"));
                    link=new JSONArray(original.toString());in.put(key,link);
                }
            }
        }
        for(String id:remove)graph.remove(id);
    }
    private static List<String> ids(JSONObject graph){List<String> ids=new ArrayList<>();for(Iterator<String> it=graph.keys();it.hasNext();)ids.add(it.next());Collections.sort(ids);return ids;}
}
