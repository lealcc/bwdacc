package com.leal.comfyremote;

import org.json.*;
import java.util.*;

/** Resolves only explicit tags. Text, visual references and adapters retain separate roles. */
final class ReferenceBindings {
    static String family(String model){
        String n=model.toLowerCase(Locale.ROOT);
        if(n.contains("klein"))return n.contains("9b")?"klein9b":n.contains("4b")?"klein4b":"";
        if(n.contains("moss"))return "moss";if(n.contains("minimax_h3"))return "h3";
        if(n.contains("krea2")||n.contains("krea_2"))return "krea2";if(n.contains("ideogram"))return "ideogram4";return "";
    }
    static String loraFamily(String path){String n=LoraSupport.normalized(path);String folder=n.contains("/")?n.substring(0,n.indexOf('/')):"";
        if(folder.equals("klein"))return n.contains("4b")?"klein4b":"klein9b";
        return Arrays.asList("moss","h3","krea2","ideogram4").contains(folder)?folder:"";
    }
    static String familyLabel(String key){switch(key){case "h3":return "MiniMax H3";case "klein4b":return "Klein 4B";case "klein9b":return "Klein 9B";case "moss":return "Moss";case "krea2":return "Krea 2";case "ideogram4":return "Ideogram 4";default:return key;}}
    static boolean ref2va(JSONObject graph){return ReferenceSupport.type(graph,"8","MiniMaxH3ReferenceToVideo");}
    static List<ReferencePreset.Picture> pictures(List<ReferencePreset> presets,boolean useMods,String address){
        List<ReferencePreset.Picture> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(ReferencePreset p:presets){if(p.control()||!p.useImages||p.usageVersion==0&&useMods&&validMod(p,address))continue;ReferencePreset.Picture cover=p.coverPicture();if(cover!=null&&seen.add(cover.file))result.add(cover);}
        for(ReferencePreset p:presets){if(p.control()||!p.useImages||p.usageVersion==0&&useMods&&validMod(p,address))continue;for(ReferencePreset.Picture image:p.images)if(seen.add(image.file))result.add(image);}
        return result;
    }
    static boolean validMod(ReferencePreset p,String address){return !p.control()&&p.useRefmod&&!p.refmod.isEmpty()&&p.refmodServer.equals(address)&&p.refmodSource.equals(p.signature());}
    static boolean hasVisual(List<ReferencePreset> presets,String address){for(ReferencePreset p:presets)if(!p.control()&&(p.useImages&&!p.images.isEmpty()||validMod(p,address)))return true;return false;}
    static boolean hidden(List<ReferencePreset> presets){for(ReferencePreset p:presets)if(p.hidden())return true;return false;}
    static void applyLoras(JSONObject graph,List<ReferencePreset> presets,List<String> installed)throws Exception{
        Set<String> live=new HashSet<>();for(Iterator<String> it=graph.keys();it.hasNext();){String id=it.next();String type=graph.getJSONObject(id).optString("class_type");if(type.startsWith("Save")||type.equals("PreviewImage"))LoraSupport.ancestors(graph,id,live);}
        Set<String> activeFamilies=new HashSet<>();for(String id:live){JSONObject input=graph.getJSONObject(id).optJSONObject("inputs");if(input!=null)activeFamilies.add(family(input.optString("unet_name",input.optString("ckpt_name"))));}
        Map<String,LinkedHashMap<String,JSONObject>> groups=new LinkedHashMap<>();
        for(ReferencePreset p:presets)if(!p.control()&&p.useLoras)for(int i=0;i<p.loras.length();i++){
            JSONObject value=p.loras.getJSONObject(i);String f=value.getString("family"),path=value.getString("lora");double weight=value.getDouble("strength");if(!activeFamilies.contains(f))continue;
            if(Double.isNaN(weight)||Double.isInfinite(weight)||Math.abs(weight)>20)throw new IllegalArgumentException(UiLanguage.t("Peso de LoRA inválido em ")+p.name);
            LinkedHashMap<String,JSONObject> list=groups.get(f);if(list==null){list=new LinkedHashMap<>();groups.put(f,list);}
            JSONObject previous=list.get(path);if(previous!=null&&Math.abs(previous.getDouble("strength")-weight)>.0001)throw new IllegalArgumentException(UiLanguage.t("Referências usam pesos diferentes para ")+path+UiLanguage.t(". Ajuste os vínculos."));
            list.put(path,new JSONObject().put("lora",path).put("strength",weight).put("on",true));
        }
        for(String id:new ArrayList<>(live)){
            JSONObject node=graph.optJSONObject(id),in=node.optJSONObject("inputs");if(in==null)continue;
            if(!Arrays.asList("UNETLoader","CheckpointLoaderSimple").contains(node.optString("class_type")))continue;
            String f=family(in.optString("unet_name",in.optString("ckpt_name")));Map<String,JSONObject> chosen=groups.get(f);if(chosen==null||chosen.isEmpty())continue;
            // Keep the workflow's structural adapters and manual choices. Explicit manual weights win on duplicates.
            Set<String> present=new HashSet<>();for(String other:live){JSONObject ni=graph.getJSONObject(other).optJSONObject("inputs");if(ni==null)continue;
                for(Iterator<String> ks=ni.keys();ks.hasNext();){JSONObject v=ni.optJSONObject(ks.next());if(v!=null&&v.optBoolean("on"))present.add(v.optString("lora"));}}
            String last=id;int index=0;List<String> added=new ArrayList<>();
            for(JSONObject v:chosen.values()){
                String path=v.getString("lora");if(present.contains(path)||v.getDouble("strength")==0)continue;
                if(!installed.contains(path))throw new IllegalArgumentException(UiLanguage.t("LoRA da referência não instalado: ")+path);
                String next="momentos_preset_lora_"+id+"_"+(index++);if(graph.has(next))throw new IllegalArgumentException(UiLanguage.t("ID de LoRA duplicado."));
                graph.put(next,ReferenceSupport.node("LoraLoaderModelOnly",new JSONObject().put("model",ReferenceSupport.link(last)).put("lora_name",path).put("strength_model",v.getDouble("strength"))));added.add(next);last=next;
            }
            if(last.equals(id))continue;
            for(String consumer:new ArrayList<>(live)){if(consumer.equals(id)||added.contains(consumer))continue;JSONObject inputs=graph.getJSONObject(consumer).optJSONObject("inputs");if(inputs==null)continue;
                for(Iterator<String> keys=inputs.keys();keys.hasNext();){String key=keys.next();JSONArray link=inputs.optJSONArray(key);if(link!=null&&link.length()==2&&id.equals(link.optString(0))&&link.optInt(1,-1)==0)inputs.put(key,ReferenceSupport.link(last));}}
        }
    }
    static void bindH3Pictures(JSONObject graph,List<String> paths)throws Exception{
        JSONObject input=graph.getJSONObject("8").getJSONObject("inputs");int start=0;while(input.has("ref_images.ref_image_"+start))start++;
        if(start+paths.size()>3)throw new IllegalArgumentException(UiLanguage.t("Até 3 imagens de referência no total. Reduza as imagens vinculadas às tags."));
        for(int i=0;i<paths.size();i++){String id="momentos_preset_image_"+i;graph.put(id,ReferenceSupport.node("LoadImage",new JSONObject().put("image",paths.get(i))));input.put("ref_images.ref_image_"+(start+i),ReferenceSupport.link(id));}
    }
    static void applyMods(JSONObject graph,List<ReferencePreset> presets,String address,JSONObject schema)throws Exception{
        if(!ref2va(graph))return;List<String> mods=new ArrayList<>();for(ReferencePreset p:presets)if(validMod(p,address)&&!mods.contains(p.refmod))mods.add(p.refmod);
        if(mods.isEmpty())return;if(mods.size()>8)throw new IllegalArgumentException(UiLanguage.t("Até 8 RefMods por geração."));
        JSONObject loader=ReferenceGraphs.defaults(schema,"MiniMaxH3RefModsLoader");
        JSONArray available=schema.getJSONObject("MiniMaxH3RefModsLoader").getJSONObject("input").getJSONObject("required").getJSONArray("mod_1").getJSONArray(0);
        Set<String> names=new HashSet<>();for(int i=0;i<available.length();i++)names.add(available.getString(i));
        for(int i=0;i<8;i++){String mod=i<mods.size()?mods.get(i):"(none)";if(!names.contains(mod))throw new IllegalArgumentException(UiLanguage.t("RefMod indisponível no servidor: ")+mod);loader.put("mod_"+(i+1),mod).put("strength_"+(i+1),1).put("copies_"+(i+1),1);}
        graph.put("momentos_refmods",ReferenceSupport.node("MiniMaxH3RefModsLoader",loader));
        JSONObject apply=ReferenceGraphs.defaults(schema,"MiniMaxH3RefModApply");apply.put("conditioning",ReferenceSupport.link("8")).put("mods",ReferenceSupport.link("momentos_refmods"));
        graph.put("momentos_refmod_apply",ReferenceSupport.node("MiniMaxH3RefModApply",apply));
        for(Iterator<String> ids=graph.keys();ids.hasNext();){String id=ids.next();if(id.equals("momentos_refmod_apply"))continue;JSONObject inputs=graph.getJSONObject(id).optJSONObject("inputs");if(inputs==null)continue;
            for(String key:new String[]{"conditioning","positive"}){JSONArray link=inputs.optJSONArray(key);if(link!=null&&"8".equals(link.optString(0))&&link.optInt(1,-1)==0)inputs.put(key,ReferenceSupport.link("momentos_refmod_apply"));}}
    }
}
