package com.leal.comfyremote;

import org.json.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import android.util.AtomicFile;

/** Immutable submission recipes, separate from the small gallery index. */
final class GenerationRecipe {
    static JSONObject copy(JSONObject value)throws JSONException{return new JSONObject(value.toString());}
    static JSONObject advanced(JSONObject graph)throws JSONException{
        JSONObject values=new JSONObject().put("randomSeed",false);
        for(AdvancedGeneration.Control c:AdvancedGeneration.controls(graph))values.put(c.id(),c.value);
        if(NegativePrompt.stored(graph))values.put(NegativePrompt.KEY,NegativePrompt.value(graph));
        return values;
    }
    // Restore scalar values without substituting prepared reference/LoRA links into a template.
    static JSONObject configured(JSONObject recipe)throws JSONException{
        JSONObject template=copy(recipe.getJSONObject("template")),graph=copy(recipe.getJSONObject("graph"));
        boolean changedVideoMode=VideoWorkflow.matches(template)&&ReferenceBindings.ref2va(graph);
        for(Iterator<String> ids=template.keys();ids.hasNext();){String id=ids.next();JSONObject node=template.getJSONObject(id),submitted=graph.optJSONObject(id);
            if(submitted==null||!node.optString("class_type").equals(submitted.optString("class_type")))continue;
            JSONObject to=node.getJSONObject("inputs"),from=submitted.getJSONObject("inputs");
            // The Ref2VA model/adapter was selected by visual tags. Restore the original
            // video model first so switching those permissions off can return to T2V/I2V.
            if(changedVideoMode&&Arrays.asList("4","19","20").contains(id))continue;
            if(changedVideoMode&&id.equals("21")){
                List<String> remove=new ArrayList<>();for(Iterator<String> keys=to.keys();keys.hasNext();){String key=keys.next();JSONObject v=to.optJSONObject(key);if(v!=null&&LoraSupport.matches(v.optString("lora"),"h3"))remove.add(key);}for(String key:remove)to.remove(key);
                int index=1;for(Iterator<String> keys=from.keys();keys.hasNext();){JSONObject v=from.optJSONObject(keys.next());if(v==null||!LoraSupport.matches(v.optString("lora"),"h3"))continue;while(to.has("lora_"+index))index++;to.put("lora_"+index++,v);}continue;
            }
            List<String> remove=new ArrayList<>();for(Iterator<String> keys=to.keys();keys.hasNext();){String key=keys.next();if(key.startsWith("lora_")&&!from.has(key))remove.add(key);}for(String key:remove)to.remove(key);
            for(Iterator<String> keys=from.keys();keys.hasNext();){String key=keys.next();Object v=from.opt(key);if(!(v instanceof JSONArray)&&!(to.opt(key) instanceof JSONArray))to.put(key,v);}
        }
        String id=recipe.optString("promptNode"),key=recipe.optString("promptKey");
        if(template.has(id)&&!key.isEmpty()){JSONObject node=template.getJSONObject(id),meta=node.optJSONObject("_meta");if(meta==null)meta=new JSONObject();node.put("_meta",meta.put("momentos_prompt_input",key));}
        return template;
    }
    static File file(File files,String id){if(!id.matches("[A-Za-z0-9_-]{1,128}"))throw new IllegalArgumentException(UiLanguage.t("ID inválido"));return new File(new File(files,"recipes"),id+".json");}
    static void save(File files,String id,JSONObject recipe)throws Exception{
        VaultFiles.write(file(files,id),recipe.toString().getBytes(StandardCharsets.UTF_8));
    }
    static JSONObject load(File files,String id)throws Exception{
        try(InputStream in=VaultFiles.open(file(files,id));ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){bytes.write(buffer,0,n);if(bytes.size()>8_000_000)throw new IOException(UiLanguage.t("Configuração muito grande"));}return new JSONObject(bytes.toString("UTF-8"));
        }
    }
    static void delete(File files,String id){VaultFiles.delete(file(files,id));}
    static Set<String> presetFiles(File files){
        Set<String> used=new HashSet<>();File[] saved=new File(files,"recipes").listFiles();if(saved==null)return used;
        for(File file:saved)if(file.getName().endsWith(".json"))try{JSONObject recipe=load(files,file.getName().replace(".json",""));JSONArray refs=recipe.optJSONArray("presets");if(refs==null)continue;
            for(int i=0;i<refs.length();i++){JSONArray pictures=refs.getJSONObject(i).getJSONObject("preset").optJSONArray("images");if(pictures!=null)for(int j=0;j<pictures.length();j++)used.add(pictures.getJSONObject(j).optString("file"));}
        }catch(Exception ignored){}return used;
    }
    static JSONObject finish(JSONObject draft,JSONObject graph,List<String> uploaded)throws JSONException{
        JSONObject result=copy(draft).put("graph",copy(graph));result.remove("inherited");JSONArray refs=result.getJSONArray("refs");
        for(int i=0;i<refs.length()&&i<uploaded.size();i++)refs.getJSONObject(i).put("input",uploaded.get(i));return result;
    }
    // Rounding, exact clip lengths and custom dimensions survive reuse. A changed option
    // or changed source releases the corresponding old value back to normal preparation.
    static void preserveOutput(JSONObject graph,JSONObject draft)throws JSONException{
        JSONObject old=draft.optJSONObject("inherited");if(old==null)return;
        JSONObject before=old.getJSONObject("options"),after=draft.getJSONObject("options");
        boolean source=referenceIds(old).equals(referenceIds(draft))&&old.getJSONArray("presets").toString().equals(draft.getJSONArray("presets").toString());
        boolean size=before.optString("ratio").equals(after.optString("ratio"))&&before.optInt("imageMp")==after.optInt("imageMp")&&before.optInt("quality")==after.optInt("quality")&&(!"Original".equals(before.optString("ratio"))||source);
        boolean length=before.optInt("duration")==after.optInt("duration")&&(before.optInt("duration")>=0||source);
        JSONObject original=old.getJSONObject("graph");for(Iterator<String> ids=graph.keys();ids.hasNext();){String id=ids.next();JSONObject n=graph.getJSONObject(id),previous=original.optJSONObject(id);if(previous==null||!previous.optString("class_type").equals(n.optString("class_type")))continue;
            String type=n.optString("class_type");boolean output=GenerationOptions.imageLatent(type)||type.equals("Flux2Scheduler")||type.startsWith("MiniMaxH3");if(!output)continue;
            JSONObject to=n.getJSONObject("inputs"),from=previous.getJSONObject("inputs");
            for(String key:new String[]{"width","height","length"})if((key.equals("length")?length:size)&&from.opt(key) instanceof Number)to.put(key,from.opt(key));
        }
    }
    static String source(JSONObject recipe){return recipe.optString("sourceId");}
    static boolean edit(JSONObject recipe){return (recipe.optString("operation").endsWith("_edit")||recipe.optString("operation").endsWith("_upscale")||recipe.optString("operation").endsWith("_lipsync"))||recipe.optString("operation").equals("video_extend")||ImageToolsWorkflow.operation(recipe.optString("operation"));}
    static List<String> referenceIds(JSONObject recipe)throws JSONException{
        List<String> ids=new ArrayList<>();JSONArray refs=recipe.getJSONArray("refs");for(int i=0;i<refs.length();i++)ids.add(refs.getJSONObject(i).getString("id"));return ids;
    }
    static JSONObject legacyTemplate(JSONObject graph)throws Exception{
        JSONObject out=copy(graph);PrivateConditioning.strip(out);NegativePrompt.strip(out);SfwPromptFilter.strip(out);PromptAgent.strip(out);MemoryProfiles.strip(out);BoxLayout.strip(out,"");
        if(ReferenceSupport.klein(out)){
            List<String> remove=new ArrayList<>();for(Iterator<String> ids=out.keys();ids.hasNext();){String id=ids.next();if(id.startsWith("momentos_ref_"))remove.add(id);}for(String id:remove)out.remove(id);
            out.getJSONObject("15").getJSONObject("inputs").put("positive",ReferenceSupport.link("9")).put("negative",ReferenceSupport.link("10"));
        }
        return out;
    }
    static List<String> inputFiles(JSONObject graph,boolean videoEdit)throws JSONException{
        List<String> result=new ArrayList<>();
        if(graph.has("momentos_extend_source"))result.add(graph.getJSONObject("momentos_extend_source").getJSONObject("inputs").getString("video"));
        if(videoEdit&&graph.has("source_video"))result.add(graph.getJSONObject("source_video").getJSONObject("inputs").getString("video"));
        if(VideoWorkflow.matches(graph)){
            if(graph.has("1"))result.add(graph.getJSONObject("1").getJSONObject("inputs").getString("image"));
            if(graph.has("momentos_last_frame"))result.add(graph.getJSONObject("momentos_last_frame").getJSONObject("inputs").getString("image"));
        }else for(String id:ReferenceSupport.inputs(graph))result.add(graph.getJSONObject(id).getJSONObject("inputs").getString("image"));
        return result;
    }
    static JSONObject legacyOptions(JSONObject graph,boolean video)throws JSONException{
        int width=0,height=0,mp=1,frames=0;String ratio="Original";
        for(Iterator<String> ids=graph.keys();ids.hasNext();){JSONObject node=graph.getJSONObject(ids.next()),in=node.getJSONObject("inputs");String type=node.optString("class_type");
            if(type.equals("ResolutionSelector")){mp=GenerationOptions.imageMegapixels((int)Math.round(in.optDouble("megapixels",1)));ratio=in.optString("aspect_ratio",in.optString("ratio","Original")).split(" ")[0];}
            if(GenerationOptions.imageLatent(type)||type.startsWith("MiniMaxH3")&&in.has("width")){width=in.optInt("width",0);height=in.optInt("height",0);frames=in.optInt("length",0);}
        }
        double area=width* (double)height/1000000;
        if(width>0&&height>0){ratio=width+":"+height;for(String r:new String[]{"16:9","4:3","1:1","3:4","9:16"}){String[] p=r.split(":");if(Math.abs(Math.log((width/(double)height)/(Double.parseDouble(p[0])/Double.parseDouble(p[1]))))<.04){ratio=r;break;}}if(!video)mp=area>3?4:area>1.5?2:1;}
        int duration=1;int[] seconds={3,5,8,10};int best=Integer.MAX_VALUE;for(int i=0;i<seconds.length;i++){int d=Math.abs(VideoWorkflow.frames(seconds[i])-frames);if(d<best){best=d;duration=i;}}
        boolean turbo=graph.optJSONObject("11")!=null&&graph.getJSONObject("11").getJSONObject("inputs").optInt("steps")==4;
        JSONObject options=new JSONObject().put("ratio",ratio).put("imageMp",mp).put("quality",area>.82?98:area>.49?66:33).put("duration",duration).put("batch",1).put("firstLast",graph.has("momentos_last_frame")).put("turbo",turbo);
        String profile=MemoryProfiles.saved(graph);if(!profile.isEmpty())options.put("memoryProfile",profile);
        JSONObject layout=graph.optJSONObject("momentos_layout");if(layout!=null&&layout.optJSONObject("_meta")!=null)options.put("layout",layout.getJSONObject("_meta").optJSONObject("momentos_layout"));
        JSONObject source=graph.optJSONObject("momentos_extend_source");if(source!=null){JSONObject in=source.getJSONObject("inputs");options.put("ratio","Original").put("videoRange",new JSONObject().put("start",in.optLong("start_ms")).put("end",in.optLong("end_ms")).put("x",in.optDouble("crop_x",0)).put("y",in.optDouble("crop_y",0)).put("w",in.optDouble("crop_w",1)).put("h",in.optDouble("crop_h",1)));}
        return options;
    }
}
