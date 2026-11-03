package com.leal.comfyremote;

import org.json.*;
import java.util.*;

/** Verify readiness and bind portable model paths to the server's actual path spelling. */
final class WorkflowDependencies {
    static void verify(JSONObject graph,JSONObject schema)throws Exception{
        LinkedHashSet<String> missing=new LinkedHashSet<>();
        for(Iterator<String> ids=graph.keys();ids.hasNext();){JSONObject n=graph.getJSONObject(ids.next()),in=n.getJSONObject("inputs");String type=n.getString("class_type");
            if(!schema.has(type)){missing.add("node "+type);continue;}
            for(String key:new String[]{"unet_name","clip_name","vae_name","lora_name","model_name","bg_removal_name","curve_name","curve_table"})if(in.opt(key) instanceof String&&!in.optString(key).isEmpty()){
                JSONArray choices=choices(schema.optJSONObject(type),key);if(choices!=null){String actual=installedName(choices,in.getString(key));if(actual==null)missing.add(in.getString(key));else in.put(key,actual);}
            }
            if(type.equals("Power Lora Loader (rgthree)"))for(Iterator<String> keys=in.keys();keys.hasNext();){JSONObject lora=in.optJSONObject(keys.next());if(lora!=null&&lora.optBoolean("on")&&lora.has("lora")){
                JSONArray available=choices(schema.optJSONObject("LoraLoader"),"lora_name");if(available!=null){String actual=installedName(available,lora.optString("lora"));if(actual==null)missing.add(lora.optString("lora"));else lora.put("lora",actual);}
            }}
        }
        if(!missing.isEmpty())throw new IllegalArgumentException(UiLanguage.t("Workflow incluído, mas faltam dependências no servidor: ")+String.join(", ",missing)+UiLanguage.t(". Consulte o catálogo do pacote."));
    }
    static JSONArray choices(JSONObject node,String key){if(node==null)return null;JSONObject inputs=node.optJSONObject("input");if(inputs==null)return null;for(String section:new String[]{"required","optional"}){JSONObject group=inputs.optJSONObject(section);JSONArray spec=group==null?null:group.optJSONArray(key);if(spec==null)continue;if(spec.optJSONArray(0)!=null)return spec.optJSONArray(0);JSONObject info=spec.optJSONObject(1);if(info!=null)return info.optJSONArray("options");}return null;}
    static String installedName(JSONArray values,String name){for(int i=0;i<values.length();i++)if(name.replace('\\','/').equals(values.optString(i).replace('\\','/')))return values.optString(i);return null;}
    static boolean contains(JSONArray values,String name){return installedName(values,name)!=null;}
    static String[] types(JSONObject graph)throws Exception{LinkedHashSet<String> types=new LinkedHashSet<>();for(Iterator<String> it=graph.keys();it.hasNext();)types.add(graph.getJSONObject(it.next()).getString("class_type"));types.add("LoraLoader");return types.toArray(new String[0]);}
}
