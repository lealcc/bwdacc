package com.leal.comfyremote;

import org.json.*;
import java.util.*;

/** Reference-conditioned video revisions. The source is a clip, never its thumbnail. */
final class VideoEditWorkflow {
    static String name(boolean turbo){return turbo?"MiniMax H3 Ref2VA Turbo":"MiniMax H3 Ref2VA";}
    static int frames(long sourceMillis,int seconds){
        if(sourceMillis<2000||sourceMillis>15100)throw new IllegalArgumentException(UiLanguage.t("Use um clipe entre 2 e 15 segundos para editar."));
        int raw=seconds==0?(int)Math.round(sourceMillis*24.0/1000.0):seconds*24;
        return Math.min(362,raw+(5-raw%17+17)%17);
    }
    static String prompt(String instruction,boolean audio,int pictures){
        StringBuilder references=new StringBuilder("<Video 1> is the source clip to edit: its subjects, scene, action, camera motion and timing.\n");
        if(audio)references.append("<Audio 1> is the soundtrack of <Video 1>; use its timing, voices and soundscape as reference.\n");
        for(int i=1;i<=pictures;i++)references.append("<Picture ").append(i).append("> is an additional visual reference for the requested changes.\n");
        return references+"\nsubject_definitions:\nUse the subjects in <Video 1>, unless the edit requests a replacement."
            +"\n\nsummary:\nEdit the source clip according to this instruction: "+instruction.trim()
            +"\n\nretention_analysis:\nKeep the source composition, identities, action, camera motion and timing unless the requested edit changes them."
            +"\n\ndetailed_description:\n[Shot 1] Apply the requested changes consistently throughout the clip. Preserve everything else from <Video 1>."
            +"\n\noverall_soundscape:\nFollow the requested audio changes. Otherwise, "
            +(audio?"recreate the source soundscape using <Audio 1>. Do not add new dialogue, voiceover or vocalizations.":"use subtle sounds from the visible scene only. No unrequested speech, voiceover or vocalizations.")
            +"\n\nnon_diegetic_music:\n"+(audio?"Keep music only if present in <Audio 1> or requested by the edit.":"N/A unless requested by the edit.");
    }
    static void prepare(JSONObject graph,String video,List<String> images,boolean audio,boolean turbo,String instruction,
                        String ratio,double megapixels,int width,int height,long millis,int seconds,long seed)throws Exception{
        if(video==null||video.isEmpty())throw new IllegalArgumentException(UiLanguage.t("Clipe de origem ausente."));
        if(images.size()>2)throw new IllegalArgumentException(UiLanguage.t("Use até duas imagens junto ao clipe de origem."));
        if(instruction.trim().isEmpty())throw new IllegalArgumentException(UiLanguage.t("Descreva a alteração no vídeo."));
        int count=frames(millis,seconds);
        int[] output=GenerationOptions.videoDimensions(ratio,megapixels,width,height);
        int[] reference=GenerationOptions.videoDimensions("Original",Math.min(megapixels,.33),width,height);
        JSONObject input=graph.getJSONObject("8").getJSONObject("inputs");
        List<String> keys=new ArrayList<>();for(Iterator<String> it=input.keys();it.hasNext();)keys.add(it.next());
        for(String key:keys)if(key.startsWith("ref_images.")||key.startsWith("ref_videos.")||key.startsWith("ref_video_audios."))input.remove(key);
        input.put("width",output[0]).put("height",output[1]).put("length",count).put("prompt",prompt(instruction,audio,images.size()));
        graph.put("source_video",ReferenceSupport.node("VHS_LoadVideo",new JSONObject().put("video",video).put("force_rate",24)
            .put("custom_width",reference[0]).put("custom_height",reference[1]).put("frame_load_cap",count)
            .put("skip_first_frames",0).put("select_every_nth",1).put("format","None")));
        input.put("ref_videos.ref_video_0",ReferenceSupport.link("source_video"));
        if(audio)input.put("ref_video_audios.ref_video_audio_0",new JSONArray().put("source_video").put(2));
        for(int i=0;i<2;i++)graph.remove("edit_reference_"+i);
        for(int i=0;i<images.size();i++){String id="edit_reference_"+i;graph.put(id,ReferenceSupport.node("LoadImage",new JSONObject().put("image",images.get(i))));input.put("ref_images.ref_image_"+i,ReferenceSupport.link(id));}
        graph.getJSONObject("11").getJSONObject("inputs").put("steps",turbo?4:20);
        graph.getJSONObject("21").getJSONObject("inputs").getJSONObject("lora_1").put("on",turbo);
        graph.getJSONObject("9").getJSONObject("inputs").put("noise_seed",seed&0x1FFFFFFFFFFFFFL);
    }
}
