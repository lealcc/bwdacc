package com.leal.comfyremote;
import org.json.*;
import java.nio.file.*;
import java.util.*;
import android.content.Intent;
import android.provider.MediaStore;

public final class ReferencePermissionsTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static JSONObject read(String file)throws Exception{return new JSONObject(new String(Files.readAllBytes(Paths.get(file)),"UTF-8"));}
    public static void main(String[] args)throws Exception{
        JSONObject schema=read(args[0]),h3=read(args[1]),klein=read(args[2]);
        ReferencePreset p=new ReferencePreset();p.name="Nora";p.text="manual description";p.ghost="ghost description";
        p.images.add(new ReferencePreset.Picture("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg","source",false));p.refmod="Momentos/test-permission";p.refmodServer="local";p.refmodSource=p.signature();
        p.loras.put(new JSONObject().put("family","h3").put("lora","h3/identity.safetensors").put("strength",.6));
        schema.getJSONObject("MiniMaxH3RefModsLoader").getJSONObject("input").getJSONObject("required").getJSONArray("mod_1").getJSONArray(0).put(p.refmod);
        for(String kind:new String[]{"character","style","location"}){
            p.kind=kind;p.useText=false;p.useImages=false;p.useRefmod=true;p.useLoras=false;ReferencePreset round=ReferencePreset.parse(p.json());
            check(!round.useText&&!round.useImages&&round.useRefmod&&!round.useLoras,"Independent persisted permissions for "+kind);
            Map<String,String> tags=new HashMap<>();tags.put("@nora",round.promptText());check(PromptTags.expand("@nora walking",tags).trim().equals("walking"),"Disabled text removes manual text, ghost and name fallback");
            check(round.description().contains("manual")&&round.description().contains("ghost"),"Disabled content remains editable");
            check(ReferenceBindings.pictures(Arrays.asList(round),true,"local").isEmpty(),"Images off even with a valid RefMod");
            check(ReferenceBindings.hasVisual(Arrays.asList(round),"local"),"RefMod alone is visual");
            check(!ReferenceBindings.hasVisual(Arrays.asList(round),"other"),"Foreign RefMod cannot switch the model");
        }
        p.useText=true;p.useImages=true;p.useLoras=true;
        check(p.promptText().contains("ghost"),"Text on restores description");
        ReferenceBindings.bindH3Pictures(h3,Arrays.asList("portrait.png"));ReferenceBindings.applyMods(h3,Arrays.asList(p),"local",schema);
        check(h3.has("momentos_refmods")&&h3.getJSONObject("8").getJSONObject("inputs").has("ref_images.ref_image_0"),"Images and RefMod coexist");
        ReferenceBindings.applyLoras(h3,Arrays.asList(p),Arrays.asList("h3/identity.safetensors"));check(h3.has("momentos_preset_lora_4_0"),"Allowed compatible LoRA");
        p.useRefmod=false;JSONObject noMod=read(args[1]);ReferenceBindings.applyMods(noMod,Arrays.asList(p),"local",schema);check(!noMod.has("momentos_refmods"),"RefMod off never injected");
        p.useLoras=false;ReferenceBindings.applyLoras(noMod,Arrays.asList(p),Collections.emptyList());check(!noMod.has("momentos_preset_lora_4_0"),"LoRA off skips loading/validation");
        p.useRefmod=true;String before=klein.toString();ReferenceBindings.applyMods(klein,Arrays.asList(p),"local",schema);ReferenceBindings.applyLoras(klein,Arrays.asList(p),Collections.emptyList());check(before.equals(klein.toString()),"Incompatible model never gets RefMod or H3 LoRA");
        p.useImages=false;p.useRefmod=false;check(!ReferenceBindings.hasVisual(Arrays.asList(p),"local"),"All visuals off must preserve T2V workflow");
        ReferencePreset frozen=p.copy(),current=p.copy();current.text="new description";current.useText=false;frozen.usePermissions(current);check(frozen.promptText().isEmpty()&&frozen.text.equals(p.text),"Current permission overrides reused content without changing the snapshot text");
        JSONObject old=p.json();for(String key:new String[]{"usageVersion","useText","useImages","useLoras","useRefmod"})old.remove(key);ReferencePreset migrated=ReferencePreset.parse(old);check(migrated.useText&&migrated.useImages&&migrated.useRefmod&&migrated.useLoras&&migrated.usageVersion==0,"Legacy defaults remain enabled");
        Intent one=PhotoPicker.photos(1,150),three=PhotoPicker.photos(3,150),two=PhotoPicker.photos(2,150);
        check(one.getAction().equals(MediaStore.ACTION_PICK_IMAGES)&&!one.hasExtra(MediaStore.EXTRA_PICK_IMAGES_MAX),"One remaining slot uses single-photo picker");
        check(three.getIntExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,0)==3&&two.getIntExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,0)==2,"Picker respects remaining slots");
        Intent fallback=PhotoPicker.gallery(3);check(fallback.getAction().equals(Intent.ACTION_PICK)&&fallback.getData().equals(MediaStore.Images.Media.EXTERNAL_CONTENT_URI),"Fallback opens media gallery, not Documents/Drive");
        System.out.println("ReferencePermissionsTest PASS: permissions, model compatibility, combinations, snapshot override, persistence and photo picker");
    }
}
