package com.leal.comfyremote;

import org.json.*;
import java.util.*;

/** Continue from a real trailing clip, then remove the duplicated context when joining. */
final class VideoExtendWorkflow {
    static String name(boolean turbo){return turbo?"MiniMax H3 · Estender Turbo":"MiniMax H3 · Estender";}
    static JSONObject range(long start,long end)throws Exception{return new JSONObject().put("start",start).put("end",end).put("x",0).put("y",0).put("w",1).put("h",1);}
    static void validate(JSONObject range,long duration)throws Exception{
        long start=range.optLong("start"),end=range.optLong("end",duration);if(end==0)end=duration;
        if(start<0||end>duration+100||end-start<1000||end-start>120000)throw new IllegalArgumentException(UiLanguage.t("Selecione um trecho de 1 a 120 segundos."));
        double x=range.optDouble("x",0),y=range.optDouble("y",0),w=range.optDouble("w",1),h=range.optDouble("h",1);
        if(!Double.isFinite(x+y+w+h)||x<0||y<0||w<=0||h<=0||x+w>1.00001||y+h>1.00001)throw new IllegalArgumentException(UiLanguage.t("Recorte fora do vídeo."));
    }
    static String summary(JSONObject range){long start=range.optLong("start"),end=range.optLong("end");return String.format(Locale.ROOT,"%.1fs – %.1fs",start/1000.0,end/1000.0);}
    static void prepare(JSONObject graph,String uploaded,String raw,JSONObject range,int width,int height,long duration,double megapixels,int seconds,long seed)throws Exception{
        validate(range,duration);if(raw.trim().isEmpty())throw new IllegalArgumentException(UiLanguage.t("Descreva como continuar o vídeo."));
        if(seconds<3||seconds>10)throw new IllegalArgumentException(UiLanguage.t("Escolha de 3 a 10 segundos de continuação."));
        int cw=Math.max(1,(int)Math.round(width*range.optDouble("w",1))),ch=Math.max(1,(int)Math.round(height*range.optDouble("h",1)));
        VideoWorkflow.prepare(graph,Collections.emptyList(),false,raw,cw+":"+ch,megapixels,cw,ch,seconds+1,seed);
        JSONObject in=graph.getJSONObject("8").getJSONObject("inputs");
        in.put("prompt",VideoWorkflow.prompt("Continue the source shot naturally after the opening motion and audio guide. Preserve identity, setting, camera trajectory and ongoing motion. The first 22 frames reproduce the end of the selected source clip; new action starts after this context. Do not restart the action or introduce a cut. User continuation: "+raw,0,false,in.getInt("length")));
        graph.put("momentos_extend_source",ReferenceSupport.node("MomentosVideoRange",new JSONObject().put("video",uploaded).put("start_ms",range.optLong("start")).put("end_ms",range.optLong("end",duration))
            .put("width",in.getInt("width")).put("height",in.getInt("height")).put("crop_x",range.optDouble("x",0)).put("crop_y",range.optDouble("y",0)).put("crop_w",range.optDouble("w",1)).put("crop_h",range.optDouble("h",1))));
        graph.put("momentos_extend_guide",ReferenceSupport.node("MiniMaxH3AddGuide",new JSONObject().put("positive",ReferenceSupport.link("8")).put("latent",new JSONArray().put("8").put(1))
            .put("frame_idx",0).put("vae",ReferenceSupport.link("6")).put("audio_vae",ReferenceSupport.link("7")).put("image",ReferenceSupport.link("momentos_extend_source")).put("audio",new JSONArray().put("momentos_extend_source").put(1))));
        graph.getJSONObject("12").getJSONObject("inputs").put("conditioning",ReferenceSupport.link("momentos_extend_guide"));
        graph.put("momentos_extend_merge",ReferenceSupport.node("MomentosVideoExtensionMerge",new JSONObject().put("video",ReferenceSupport.link("16")).put("trimmed_source",new JSONArray().put("momentos_extend_source").put(2))));
        graph.getJSONObject("17").getJSONObject("inputs").put("video",ReferenceSupport.link("momentos_extend_merge")).put("filename_prefix","Momentos/Extensions");
    }
}
