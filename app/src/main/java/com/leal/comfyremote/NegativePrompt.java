package com.leal.comfyremote;

import org.json.*;
import java.util.*;

/** Negative conditioning when available; textual exclusions for distilled/positive-only models. */
final class NegativePrompt {
    static final String KEY="negativePrompt",MARK="bwdacc_negative",PREFIX="bwdacc_negative_";
    private static List<String> ids(JSONObject graph){List<String> result=new ArrayList<>();for(Iterator<String> it=graph.keys();it.hasNext();)result.add(it.next());Collections.sort(result);return result;}
    static String value(JSONObject graph){if(graph!=null)for(String id:ids(graph)){JSONObject n=graph.optJSONObject(id),meta=n==null?null:n.optJSONObject("_meta");if(meta!=null&&meta.has(KEY))return meta.optString(KEY);}return "";}
    static boolean stored(JSONObject graph){if(graph!=null)for(String id:ids(graph)){JSONObject n=graph.optJSONObject(id),meta=n==null?null:n.optJSONObject("_meta");if(meta!=null&&meta.has(KEY))return true;}return false;}
    private static boolean branch(JSONObject graph,String id,Set<String> seen){
        if(!seen.add(id))return false;JSONObject n=graph.optJSONObject(id),in=n==null?null:n.optJSONObject("inputs");if(in==null)return false;
        if("CLIPTextEncode".equals(n.optString("class_type")))return in.has("clip");
        for(String key:ids(in))if(key.startsWith("conditioning")){JSONArray link=in.optJSONArray(key);if(link!=null&&branch(graph,link.optString(0),seen))return true;}return false;
    }
    private static List<String> consumers(JSONObject graph){
        List<String> result=new ArrayList<>();if(graph==null)return result;
        for(String id:ids(graph)){JSONObject n=graph.optJSONObject(id),in=n==null?null:n.optJSONObject("inputs");if(in==null)continue;
            JSONArray negative=in.optJSONArray("negative");
            if(negative!=null&&in.optDouble("cfg",0)>1&&branch(graph,negative.optString(0),new HashSet<>()))result.add(id);
        }return result;
    }
    static String explanation(JSONObject graph){
        if(!consumers(graph).isEmpty())return UiLanguage.t("Aplicado ao condicionamento negativo. Com CFG 1, funciona como instrução no prompt.");
        if(PromptAgent.target(graph)!=null)return UiLanguage.t("Este modelo não usa negative prompt nativo. Os termos entram como instrução para evitar, sem garantia de exclusão.");
        return UiLanguage.t("Salvo neste workflow. Esta operação não tem entrada de texto compatível.");
    }
    static void strip(JSONObject graph)throws JSONException{
        List<String> remove=new ArrayList<>();
        for(String id:ids(graph)){JSONObject n=graph.optJSONObject(id),meta=n==null?null:n.optJSONObject("_meta");if(meta==null)continue;
            if(meta.optBoolean(MARK)){remove.add(id);continue;}
            if(meta.has("bwdacc_original_negative")){n.getJSONObject("inputs").put("negative",meta.remove("bwdacc_original_negative"));}
            if(meta.has("bwdacc_exclusion_base")){n.getJSONObject("inputs").put(meta.optString("bwdacc_exclusion_key"),meta.remove("bwdacc_exclusion_base"));meta.remove("bwdacc_exclusion_key");}
            meta.remove(KEY);meta.remove("bwdacc_negative_native");
        }
        for(String id:remove)graph.remove(id);
    }
    static void apply(JSONObject graph,String text)throws Exception{
        strip(graph);text=text==null?"":text.trim();
        List<String> targets=consumers(graph);boolean nativeMode=!targets.isEmpty();
        for(String consumer:targets){JSONObject in=graph.getJSONObject(consumer).getJSONObject("inputs");JSONArray original=in.getJSONArray("negative");
            String cloned=cloneBranch(graph,original.getString(0),text,new HashMap<>());
            meta(graph.getJSONObject(consumer)).put("bwdacc_original_negative",new JSONArray(original.toString()));in.put("negative",new JSONArray().put(cloned).put(original.get(1)));
        }
        // Metadata survives recipe storage without adding invalid nodes to the API graph.
        for(String id:ids(graph))if(!id.startsWith(PREFIX)){meta(graph.getJSONObject(id)).put(KEY,text).put("bwdacc_negative_native",nativeMode);break;}
    }
    private static JSONObject meta(JSONObject node)throws JSONException{JSONObject meta=node.optJSONObject("_meta");if(meta==null){meta=new JSONObject();node.put("_meta",meta);}return meta;}
    private static String cloneBranch(JSONObject graph,String source,String text,Map<String,String> copies)throws Exception{
        if(copies.containsKey(source))return copies.get(source);
        JSONObject original=graph.getJSONObject(source),copy=new JSONObject(original.toString()),in=copy.getJSONObject("inputs");
        String id=PREFIX+source;int suffix=0;while(graph.has(id))id=PREFIX+source+"_"+(++suffix);copies.put(source,id);
        if("ConditioningZeroOut".equals(copy.optString("class_type"))){String resolved=cloneBranch(graph,in.getJSONArray("conditioning").getString(0),text,copies);copies.put(source,resolved);return resolved;}
        if("CLIPTextEncode".equals(copy.optString("class_type")))in.put("text",text);
        else for(String key:ids(in))if(key.startsWith("conditioning")&&in.optJSONArray(key)!=null){JSONArray link=in.getJSONArray(key);in.put(key,new JSONArray().put(cloneBranch(graph,link.getString(0),text,copies)).put(link.get(1)));}
        copy.put("_meta",new JSONObject().put(MARK,true).put("title","Negative prompt"));graph.put(id,copy);return id;
    }
    static String request(JSONObject graph,String positive){
        String text=value(graph);if(text.isEmpty())return positive;
        for(String id:ids(graph)){JSONObject n=graph.optJSONObject(id),meta=n==null?null:n.optJSONObject("_meta");if(meta!=null&&meta.optBoolean("bwdacc_negative_native"))return positive;}
        return positive+"\n\nExclusions: Avoid the following elements in the result: "+text;
    }
    static void direct(JSONObject graph)throws JSONException{
        String[] target=PromptAgent.target(graph);if(target==null)return;
        if(PromptAgent.moss(graph))target=new String[]{"18:17","value"};
        JSONObject node=graph.getJSONObject(target[0]),in=node.getJSONObject("inputs"),meta=meta(node);
        // Keep the prepared H3 timeline, references and soundscape intact.
        Object current=in.opt(target[1]);if(!(current instanceof String))return;
        String base=meta.optString("bwdacc_exclusion_base",(String)current),result=request(graph,base);
        if(!result.equals(base)){meta.put("bwdacc_exclusion_base",base).put("bwdacc_exclusion_key",target[1]);in.put(target[1],result);}
    }
}
