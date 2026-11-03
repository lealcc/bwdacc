package com.leal.comfyremote;

import org.json.*;
import java.util.*;
import java.nio.file.*;

public final class ReferenceBindingsTest {
    static void check(boolean test,String why){if(!test)throw new AssertionError(why);}
    static JSONObject read(String file)throws Exception{return new JSONObject(new String(Files.readAllBytes(Paths.get(file)),"UTF-8"));}
    public static void main(String[] args)throws Exception{
        JSONObject schema=read(args[0]),klein=read(args[1]),h3=read(args[2]);
        ReferencePreset p=new ReferencePreset();p.name="Nora";p.text="User description $literal";p.ghost="Visual description @literal";
        p.images.add(new ReferencePreset.Picture("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg","origin",true));p.images.add(new ReferencePreset.Picture("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.jpg","other",false));p.cover=p.images.get(1).file;
        p.loras.put(new JSONObject().put("family","klein9b").put("lora","klein/nine.safetensors").put("strength",.7));
        p.loras.put(new JSONObject().put("family","h3").put("lora","h3/identity.safetensors").put("strength",.8));
        ReferencePreset restored=ReferencePreset.parse(p.json());check(restored.images.size()==2&&restored.hidden(),"Round trip retains private references");
        check(restored.text.equals(p.text)&&restored.ghost.equals(p.ghost),"User text and ghost remain separate");
        Map<String,String> descriptions=new HashMap<>();descriptions.put("@nora",p.description());descriptions.put("$literal","MUST NOT EXPAND");
        String expanded=PromptTags.expand("@nora at home",descriptions);check(expanded.contains("$literal")&&!expanded.contains("MUST NOT"),"Expansion is one pass");
        check(PromptTags.tokens("@nora @Nora \\@escaped user@nora @@nested").size()==1,"Only explicit deduplicated tags bind assets");
        check(ReferenceBindings.pictures(Arrays.asList(p,p),false,"local").size()==2,"Repeated tags do not duplicate references");
        check(ReferenceBindings.pictures(Arrays.asList(p),false,"local").get(0).file.equals(p.cover),"Chosen cover is first reference");
        String untouched=klein.toString();ReferenceBindings.applyLoras(klein,Arrays.asList(p),Arrays.asList("klein/nine.safetensors","h3/identity.safetensors"));check(untouched.equals(klein.toString()),"Klein 9B adapters never reach 4B model");
        ReferenceBindings.applyLoras(h3,Arrays.asList(p,p),Arrays.asList("h3/identity.safetensors"));check(h3.has("momentos_preset_lora_4_0")&&!h3.has("momentos_preset_lora_4_1"),"Duplicate adapter is applied once");
        check(h3.getJSONObject("21").getJSONObject("inputs").getJSONObject("lora_1").optBoolean("on"),"Structural Turbo adapter survives");
        check(h3.getJSONObject("19").getJSONObject("inputs").getJSONArray("model").getString(0).equals("momentos_preset_lora_4_0"),"Preset adapter reaches model chain");
        ReferencePreset conflict=p.copy();conflict.loras.getJSONObject(1).put("strength",1.3);boolean rejected=false;try{ReferenceBindings.applyLoras(read(args[2]),Arrays.asList(p,conflict),Arrays.asList("h3/identity.safetensors"));}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Conflicting weights cannot silently override");
        p.refmod="Momentos/test";p.refmodServer="local";p.refmodSource=p.signature();check(ReferenceBindings.pictures(Arrays.asList(p),true,"local").size()==2,"Explicit permissions combine RefMod and raw refs");check(!ReferenceBindings.pictures(Arrays.asList(p),true,"other").isEmpty(),"RefMod never crosses servers");
        ReferencePreset legacy=p.copy();legacy.usageVersion=0;check(ReferenceBindings.pictures(Arrays.asList(legacy),true,"local").isEmpty(),"Untouched legacy recipes retain their visual fallback policy");
        schema.getJSONObject("MiniMaxH3RefModsLoader").getJSONObject("input").getJSONObject("required").getJSONArray("mod_1").getJSONArray(0).put(p.refmod);
        ReferenceBindings.applyMods(h3,Arrays.asList(p),"local",schema);check(h3.getJSONObject("12").getJSONObject("inputs").getJSONArray("conditioning").getString(0).equals("momentos_refmod_apply"),"RefMod reaches conditioning");check(h3.getJSONObject("13").getJSONObject("inputs").getJSONArray("latent_image").getString(0).equals("8"),"Latent output stays on reference node");
        p.images.remove(0);check(!ReferenceBindings.validMod(p,"local"),"Changing images invalidates stale RefMod");
        JSONObject caption=ReferenceGraphs.caption(schema,"qa_reference_studio.png","character");check(caption.getJSONObject("caption").getJSONObject("inputs").getInt("max_tokens")==384,"Caption output bounded");
        JSONObject mod=ReferenceGraphs.refmod(schema,Arrays.asList("qa_reference_studio.png"),"momentos_qa_reference_studio","character","A notebook on a table.");check(mod.getJSONObject("result").getJSONObject("inputs").has("refs_image.ref_image_0"),"Actual v3 autogrow schema");
        check(ReferenceGraphs.outputText(new JSONObject("{\"outputs\":{\"result\":{\"text\":[\"visual description\"]}}}")).equals("visual description"),"History output parser");
        if(args.length>3){Files.write(Paths.get(args[3]+"/caption-graph.json"),caption.toString().getBytes("UTF-8"));Files.write(Paths.get(args[3]+"/refmod-graph.json"),mod.toString().getBytes("UTF-8"));}
        System.out.println("Reference bindings, privacy, graph and migration-format tests passed");
    }
}
