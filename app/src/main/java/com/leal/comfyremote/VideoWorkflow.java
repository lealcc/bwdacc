package com.leal.comfyremote;

import org.json.*;
import java.util.*;

/** H3 text/image/keyframe modes share one graph; input roles are explicit. */
final class VideoWorkflow {
    static final String STANDARD="MiniMax H3", TURBO="MiniMax H3 Turbo";
    static boolean matches(JSONObject graph){return ReferenceSupport.type(graph,"8","MiniMaxH3ImageToVideo");}
    static boolean turbo(JSONObject graph){
        if(!matches(graph))return false;
        // Turbo is a model adapter, not a step count; reused/custom samplers may use other steps.
        JSONObject loader=graph.optJSONObject("21"),inputs=loader==null?null:loader.optJSONObject("inputs");
        if(inputs!=null)for(Iterator<String> keys=inputs.keys();keys.hasNext();){JSONObject value=inputs.optJSONObject(keys.next());if(value!=null&&value.optString("lora").contains("turbo"))return value.optBoolean("on");}
        return graph.optJSONObject("11")!=null&&graph.optJSONObject("11").optJSONObject("inputs").optInt("steps")==4;
    }
    static int frames(int seconds){int n=seconds*24;return n+(5-n%17+17)%17;}
    static String prompt(String raw,int images,boolean firstLast,int frames){
        String body=raw.trim().replaceAll("(?m)^For the target video, at [0-9.]+ seconds into the target video, <Picture [12]> \\(from \\[Shot 1\\]\\) is fully referenced\\.\\s*","");
        String alignment=images>0?"For the target video, at 0.00 seconds into the target video, <Picture 1> (from [Shot 1]) is fully referenced.\n\n":"";
        if(firstLast)alignment+=String.format(Locale.ROOT,"For the target video, at %.2f seconds into the target video, <Picture 2> (from [Shot 1]) is fully referenced.\n\n",(frames-1)/24.0);
        if(!body.contains("integrated_multimodal_description:"))body="integrated_multimodal_description: [Shot 1] "+body
            +(firstLast?"\nMake a continuous transition from the first picture to the final picture.":"")
            +"\nUnless explicitly requested otherwise, use one continuous shot. If speech is not requested, all mouths remain closed.";
        if(!body.contains("overall_soundscape:"))body+="\n\noverall_soundscape: Follow explicit sound and dialogue directions above. Otherwise, subtle sounds from the visible scene only. No unrequested spoken dialogue, vocalizations or voiceover.";
        if(!body.contains("non_diegetic_music:"))body+="\n\nnon_diegetic_music: N/A unless explicitly requested above.";
        return alignment+body;
    }
    static void prepare(JSONObject graph,List<String> images,boolean firstLast,String text,String ratio,double megapixels,int refWidth,int refHeight,int seconds,long seed)throws Exception {
        if(!matches(graph))throw new IllegalArgumentException(UiLanguage.t("Workflow de vídeo incompatível."));
        if(firstLast&&images.size()!=2)throw new IllegalArgumentException(UiLanguage.t("Adicione o quadro inicial e o quadro final."));
        if(!firstLast&&images.size()>1)throw new IllegalArgumentException(UiLanguage.t("Ative First/Last Frame para usar dois quadros."));
        int frameCount=frames(seconds);
        int[] size=GenerationOptions.videoDimensions(images.isEmpty()&&"Original".equals(ratio)?"16:9":ratio,megapixels,refWidth,refHeight);
        JSONObject input=graph.getJSONObject("8").getJSONObject("inputs");input.remove("first_frame");input.remove("last_frame");
        for(String id:new String[]{"1","2","3","momentos_last_frame"})graph.remove(id);
        input.put("width",size[0]).put("height",size[1]).put("length",frameCount).put("prompt",prompt(text,images.size(),firstLast,frameCount));
        if(!images.isEmpty()){
            graph.put("1",ReferenceSupport.node("LoadImage",new JSONObject().put("image",images.get(0))));
            graph.put("2",ReferenceSupport.node("ImageScaleToTotalPixels",new JSONObject().put("image",ReferenceSupport.link("1")).put("upscale_method","lanczos").put("megapixels",megapixels).put("resolution_steps",32)));
            input.put("first_frame",ReferenceSupport.link("2"));
        }
        if(firstLast){graph.put("momentos_last_frame",ReferenceSupport.node("LoadImage",new JSONObject().put("image",images.get(1))));input.put("last_frame",ReferenceSupport.link("momentos_last_frame"));}
        graph.getJSONObject("9").getJSONObject("inputs").put("noise_seed",seed&0x1FFFFFFFFFFFFFL);
    }
}
