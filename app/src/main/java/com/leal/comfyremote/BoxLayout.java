package com.leal.comfyremote;

import org.json.*;
import java.util.*;

/** Normalized editor coordinates are converted only after the final output size is known. */
final class BoxLayout {
    static boolean supported(JSONObject graph){String family=MemoryProfiles.family(graph);return family.equals("ideogram")||family.equals("moss")||family.equals("kromoss");}
    static JSONObject empty(){return new JSONObject();}
    static JSONArray regions(JSONObject layout)throws Exception{
        JSONArray result=new JSONArray();if(layout==null||!layout.optBoolean("enabled"))return result;
        JSONArray boxes=layout.optJSONArray("boxes");if(boxes==null)return result;
        if(boxes.length()>12)throw new IllegalArgumentException(UiLanguage.t("Use até 12 boxes por composição."));
        for(int i=0;i<boxes.length();i++){
            JSONObject box=GenerationRecipe.copy(boxes.getJSONObject(i));double x=box.getDouble("x"),y=box.getDouble("y"),w=box.getDouble("w"),h=box.getDouble("h");
            if(!Double.isFinite(x+y+w+h)||x<0||y<0||w<=0||h<=0||x+w>1.00001||y+h>1.00001)throw new IllegalArgumentException(UiLanguage.t("Box fora da área da imagem."));
            if(box.optString("desc").trim().isEmpty()&&box.optString("text").trim().isEmpty())throw new IllegalArgumentException(UiLanguage.t("Descreva o conteúdo do box ")+(i+1)+".");
            result.put(box);
        }return result;
    }
    static void apply(JSONObject graph,JSONObject layout,String raw)throws Exception{
        strip(graph,raw);
        JSONArray boxes=regions(layout);if(boxes.length()==0&&!MemoryProfiles.family(graph).equals("ideogram"))return;
        if(!supported(graph))throw new IllegalArgumentException(UiLanguage.t("Este workflow não oferece geração com boxes."));
        String target="";int width=0,height=0;
        for(Iterator<String> it=graph.keys();it.hasNext();){String id=it.next();JSONObject n=graph.getJSONObject(id),in=n.optJSONObject("inputs");if(in==null)continue;
            if(GenerationOptions.imageLatent(n.optString("class_type"))&&in.opt("width") instanceof Number&&in.opt("height") instanceof Number){width=in.optInt("width");height=in.optInt("height");}
            if(n.optString("class_type").equals("CLIPTextEncode")){JSONObject meta=n.optJSONObject("_meta");if(meta!=null&&(meta.has("momentos_prompt_input")||meta.optString("title").toLowerCase(Locale.ROOT).contains("positive")))target=id;}
            if(n.optString("class_type").equals("ResolutionSelector")){
                String ratio=in.optString("aspect_ratio",in.optString("ratio","1:1"));java.util.regex.Matcher m=java.util.regex.Pattern.compile("(\\d+):(\\d+)").matcher(ratio);if(!m.find())throw new IllegalArgumentException(UiLanguage.t("Proporção dos boxes indisponível."));int[] size=GenerationOptions.dimensions(m.group(1)+":"+m.group(2),in.optDouble("megapixels",1),16);width=size[0];height=size[1];
            }
        }
        if(graph.has("18:4"))target="18:4";
        if(target.isEmpty()||width<=0||height<=0)throw new IllegalArgumentException(UiLanguage.t("Não foi possível localizar a composição deste workflow."));
        JSONObject encode=graph.getJSONObject(target).getJSONObject("inputs");Object global=graph.has("momentos_agent_guard")?ReferenceSupport.link("momentos_agent_guard"):raw;
        if(MemoryProfiles.family(graph).equals("ideogram")){
            JSONObject inputs=new JSONObject().put("width",width).put("height",height).put("high_level_description",global).put("background",global)
                .put("style","none").put("aesthetics","").put("lighting","").put("medium","").put("style_palette_data","[]").put("elements_data",boxes.toString())
                .put("bg_brightness",25).put("import_mode","when empty").put("output_format","compact").put("coord_mode","normalized").put("bbox_order","yx");
            graph.put("momentos_layout",ReferenceSupport.node("Ideogram4PromptBuilderKJ",inputs));
        }else{
            JSONArray elements=new JSONArray();
            for(int i=0;i<boxes.length();i++){JSONObject b=boxes.getJSONObject(i);elements.put(new JSONObject().put("type",b.optString("type","obj")).put("desc",b.optString("desc")).put("text",b.optString("text"))
                .put("bbox",new JSONArray().put(Math.round(b.getDouble("y")*1000)).put(Math.round(b.getDouble("x")*1000)).put(Math.round((b.getDouble("y")+b.getDouble("h"))*1000)).put(Math.round((b.getDouble("x")+b.getDouble("w"))*1000))));}
            // The API's literal-value wrapper disambiguates arrays from node links.
            // Use the native layout node directly; boxes need no server add-on/restart.
            graph.put("momentos_layout",ReferenceSupport.node("MOSSImageLayoutPrompt",new JSONObject().put("prompt",global).put("element",new JSONObject().put("__value__",elements)).put("width",width).put("height",height).put("character_boxes","")));
        }
        encode.put("text",ReferenceSupport.link("momentos_layout"));
        graph.getJSONObject("momentos_layout").put("_meta",new JSONObject().put("title","Composição por boxes").put("momentos_layout",layout==null?new JSONObject():layout));
    }
    static void strip(JSONObject graph,String raw)throws JSONException{
        for(Iterator<String> ids=graph.keys();ids.hasNext();){JSONObject node=graph.getJSONObject(ids.next());if(!node.optString("class_type").equals("CLIPTextEncode"))continue;JSONObject in=node.getJSONObject("inputs");JSONArray link=in.optJSONArray("text");if(link!=null&&link.optString(0).equals("momentos_layout"))in.put("text",raw);}
        graph.remove("momentos_layout");graph.remove("momentos_layout_elements");
    }
}
