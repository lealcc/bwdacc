package com.leal.comfyremote;

import org.json.*;

/** Upscales are revisions of the same media family, with their own reproducible recipe. */
final class UpscaleWorkflow {
    static final String IMAGE="4x-UltraSharp", COMPACT="Open-H3 · Kijai compacto", FULL="Open-H3 Regenerate · completo";
    static String asset(boolean video,boolean compact){return video?(compact?"open_h3_upscale_compact_api.json":"open_h3_upscale_api.json"):"image_upscale_api.json";}
    static String name(boolean video,boolean compact){return video?(compact?COMPACT:FULL):IMAGE;}
    static boolean matches(JSONObject graph){return graph!=null&&(graph.has("momentos_upscale_source")||graph.has("momentos_upscale_image"));}
    static String unavailable(boolean video,String profile){return video&&!MemoryProfiles.normalize(profile).startsWith("24")?UiLanguage.t("Upscale H3 indisponível neste perfil: ainda sem workflow validado para ")+profile+UiLanguage.t(" GB."):"";}
    static int[] dimensions(int width,int height,int longEdge){
        if(width<1||height<1)throw new IllegalArgumentException(UiLanguage.t("Dimensões da mídia indisponíveis."));
        double scale=longEdge/(double)Math.max(width,height);
        return new int[]{Math.max(64,(int)Math.round(width*scale/2)*2),Math.max(64,(int)Math.round(height*scale/2)*2)};
    }
    static int aligned(int value){return Math.max(64,(value+31)/32*32);}
    static void prepare(JSONObject graph,String file,String prompt,int width,int height,long millis,int size,long seed)throws Exception{
        if(file==null||file.isEmpty())throw new IllegalArgumentException(UiLanguage.t("Mídia de origem ausente."));
        if(width<1||height<1)throw new IllegalArgumentException(UiLanguage.t("Dimensões da mídia indisponíveis."));
        if(graph.has("momentos_upscale_image")){
            if(size!=2&&size!=4)throw new IllegalArgumentException(UiLanguage.t("Escolha 2× ou 4×."));
            if((long)width*height*size*size>64_000_000L)throw new IllegalArgumentException(UiLanguage.t("Upscale excede limite de 64 MP."));
            graph.getJSONObject("momentos_upscale_image").getJSONObject("inputs").put("image",file);
            graph.getJSONObject("size").getJSONObject("inputs").put("width",width*size).put("height",height*size);
        }else{
            if(size!=1920&&size!=2560)throw new IllegalArgumentException(UiLanguage.t("Escolha 1920 ou 2560 px no lado maior."));
            if(millis<200||millis>15100)throw new IllegalArgumentException(UiLanguage.t("Upscale H3 aceita clipes de até 15 segundos; corte o vídeo antes."));
            double aspect=Math.max(width,height)/(double)Math.min(width,height);
            int inputEdge=Math.min(Math.max(width,height),Math.min(1344,(int)Math.sqrt(1_000_000*aspect)));
            int[] out=dimensions(width,height,size),draft=dimensions(width,height,inputEdge);
            if(Math.max(width,height)>=size)throw new IllegalArgumentException(UiLanguage.t("Vídeo já tem essa resolução. Escolha tamanho maior."));
            graph.getJSONObject("momentos_upscale_source").getJSONObject("inputs").put("video",file).put("width",aligned(draft[0])).put("height",aligned(draft[1]));
            graph.getJSONObject("upscale").getJSONObject("inputs").put("mode.width",aligned(out[0])).put("mode.height",aligned(out[1]));
            graph.getJSONObject("finish").getJSONObject("inputs").put("video",file).put("width",out[0]).put("height",out[1]);
            graph.getJSONObject("prompt").getJSONObject("inputs").put("text",prompt.trim().isEmpty()?"Preserve the subjects, composition, motion and lighting of the source video. Refine natural detail.":prompt);
            graph.getJSONObject("noise").getJSONObject("inputs").put("noise_seed",seed&Long.MAX_VALUE);
        }
    }
}
