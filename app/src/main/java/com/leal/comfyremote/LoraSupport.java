package com.leal.comfyremote;

import org.json.*;
import java.util.*;

/** Conservative folder filtering; original model/CLIP links and structural LoRAs stay intact. */
final class LoraSupport {
    /** Add a selectable model-only slot to older H3 graphs; existing chains are preserved. */
    static JSONObject videoLoader(JSONObject graph) throws Exception {
        if (!loaders(graph).isEmpty()) return graph;
        List<String> sources = new ArrayList<>();
        for (Iterator<String> ids=graph.keys();ids.hasNext();) {
            String id=ids.next(); JSONObject node=graph.optJSONObject(id);
            if(node==null||!node.optString("class_type").startsWith("UNETLoader"))continue;
            JSONObject in=node.optJSONObject("inputs");
            if(in!=null&&"h3".equals(modelFamily(in.optString("unet_name"))))sources.add(id);
        }
        for(String source:sources){
            // Bundled H3 modes share slot 21, including automatic Ref2VA conversion.
            String slot=sources.size()==1&&!graph.has("21")?"21":"bwdacc_video_loras_"+source;
            if(graph.has(slot))continue;
            boolean connected=false;
            for(Iterator<String> ids=graph.keys();ids.hasNext();){
                JSONObject node=graph.optJSONObject(ids.next());if(node==null)continue;
                JSONObject in=node.optJSONObject("inputs");if(in==null)continue;
                for(Iterator<String> keys=in.keys();keys.hasNext();){String key=keys.next();JSONArray link=in.optJSONArray(key);
                    if(link!=null&&link.length()==2&&source.equals(link.optString(0))&&link.optInt(1,-1)==0){in.put(key,new JSONArray().put(slot).put(0));connected=true;}
                }
            }
            if(connected)graph.put(slot,new JSONObject().put("class_type","Power Lora Loader (rgthree)").put("inputs",new JSONObject().put("model",new JSONArray().put(source).put(0))));
        }
        return graph;
    }
    static String normalized(String name) { return name.replace('\\', '/').toLowerCase(Locale.ROOT); }
    static String modelFamily(String name) {
        String n = normalized(name);
        if (n.contains("krea2") || n.contains("krea_2")) return "krea2";
        if (n.contains("moss")) return "moss";
        if (n.contains("minimax_h3")) return "h3";
        // Klein variants and Wan noise stages need finer folder mappings before enabling them.
        return "";
    }
    static String family(JSONObject graph, String id, Set<String> seen) {
        if (!seen.add(id)) return "";
        JSONObject node = graph.optJSONObject(id);
        if (node == null) return "";
        JSONObject in = node.optJSONObject("inputs");
        if (in == null) return "";
        String direct = in.optString("unet_name", in.optString("ckpt_name", ""));
        if (!direct.isEmpty()) return modelFamily(direct);
        JSONArray source = in.optJSONArray("model");
        if (source == null) source = in.optJSONArray("on_true");
        if ("ComfySwitchNode".equals(node.optString("class_type")))
            source = in.optJSONArray(in.optBoolean("switch") ? "on_true" : "on_false");
        return source == null ? "" : family(graph, source.optString(0), seen);
    }
    static void ancestors(JSONObject graph, String id, Set<String> out) {
        if (!out.add(id)) return;
        JSONObject node = graph.optJSONObject(id);
        if (node == null) return;
        JSONObject in = node.optJSONObject("inputs");
        if (in == null) return;
        Iterator<String> keys = in.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if ("ComfySwitchNode".equals(node.optString("class_type")) && key.startsWith("on_")
                    && !key.equals(in.optBoolean("switch") ? "on_true" : "on_false")) continue;
            JSONArray link = in.optJSONArray(key);
            if (link != null && link.length() == 2) ancestors(graph, link.optString(0), out);
        }
    }
    static List<String> loaders(JSONObject graph) {
        Set<String> live = new HashSet<>();
        Iterator<String> ids = graph.keys();
        while (ids.hasNext()) {
            String id = ids.next(); String type = graph.optJSONObject(id).optString("class_type");
            if (type.startsWith("Save") || type.equals("PreviewImage")) ancestors(graph, id, live);
        }
        List<String> result = new ArrayList<>();
        for (String id : live) if ("Power Lora Loader (rgthree)".equals(graph.optJSONObject(id).optString("class_type"))
                && !family(graph,id,new HashSet<>()).isEmpty()) result.add(id);
        Collections.sort(result); return result;
    }
    static boolean matches(String path, String family) {
        return !family.isEmpty() && normalized(path).startsWith(family + "/");
    }
    static boolean structural(JSONObject graph,String path) {
        for(Iterator<String> it=graph.keys();it.hasNext();){JSONObject n=graph.optJSONObject(it.next());if(n==null)continue;JSONObject in=n.optJSONObject("inputs");
            if(n.optString("class_type").startsWith("LoraLoader")&&in!=null&&normalized(path).equals(normalized(in.optString("lora_name")))&&in.optDouble("strength_model",0)!=0)return true;}
        return false;
    }
    static JSONObject defaults(JSONObject graph, List<String> ids) throws Exception {
        JSONObject all = new JSONObject();
        for (String id : ids) {
            JSONArray entries = new JSONArray(); JSONObject in = graph.getJSONObject(id).getJSONObject("inputs");
            String family = family(graph,id,new HashSet<>());
            List<String> keys = new ArrayList<>(); Iterator<String> it=in.keys(); while(it.hasNext()) keys.add(it.next());
            Collections.sort(keys);
            for(String key:keys) {
                JSONObject value=in.optJSONObject(key);
                if(key.startsWith("lora_") && value!=null && matches(value.optString("lora"),family)) entries.put(new JSONObject(value.toString()));
            }
            all.put(id,entries);
        }
        return all;
    }
    static void apply(JSONObject graph, JSONObject choices, List<String> installed) throws Exception {
        List<String> ids=loaders(graph);
        Iterator<String> selected=choices.keys();
        while(selected.hasNext()) {
            String id=selected.next();
            if(!ids.contains(id)) throw new IllegalArgumentException(UiLanguage.t("Workflow mudou. Revise os LoRAs."));
            String family=family(graph,id,new HashSet<>());
            JSONObject in=graph.getJSONObject(id).getJSONObject("inputs");
            List<String> remove=new ArrayList<>();Iterator<String> keys=in.keys();
            while(keys.hasNext()) {String k=keys.next();JSONObject v=in.optJSONObject(k);
                if(k.startsWith("lora_") && v!=null && matches(v.optString("lora"),family))remove.add(k);}
            for(String k:remove)in.remove(k);
            JSONArray entries=choices.getJSONArray(id);int index=1;
            for(int j=0;j<entries.length();j++) {
                JSONObject value=new JSONObject(entries.getJSONObject(j).toString());String path=value.getString("lora");
                if(!matches(path,family))throw new IllegalArgumentException(UiLanguage.t("LoRA fora da pasta ") + family);
                double strength=value.getDouble("strength");
                if(Double.isNaN(strength)||Double.isInfinite(strength)||Math.abs(strength)>20)throw new IllegalArgumentException(UiLanguage.t("Intensidade deve ficar entre -20 e 20."));
                value.put("strength",strength);
                if(value.has("strengthTwo")) {
                    double clip=value.getDouble("strengthTwo");
                    if(Double.isNaN(clip)||Double.isInfinite(clip)||Math.abs(clip)>20)throw new IllegalArgumentException(UiLanguage.t("Intensidade CLIP deve ficar entre -20 e 20."));
                    value.put("strengthTwo",clip);
                }
                if(value.optBoolean("on") && !installed.contains(path))throw new IllegalArgumentException(UiLanguage.t("LoRA não instalado: ") + path);
                while(in.has("lora_"+index))index++;
                in.put("lora_"+index++,value);
            }
        }
    }
}
