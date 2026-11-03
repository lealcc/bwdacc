package com.leal.comfyremote;

import org.json.*;
import java.util.*;
import java.nio.file.*;

public final class VideoEditWorkflowTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static JSONObject read(String path)throws Exception{return new JSONObject(new String(Files.readAllBytes(Paths.get(path)),"UTF-8"));}
    static MediaFamilies.Record record(String id,String kind){MediaFamilies.Record r=new MediaFamilies.Record();r.id=id;r.mediaKind=kind;return r;}
    public static void main(String[] args)throws Exception{
        JSONObject graph=read(args[0]);
        for(String ratio:new String[]{"4:3","1:1","3:4"}){
            JSONObject g=read(args[0]);VideoEditWorkflow.prepare(g,"clip.mp4",Collections.emptyList(),true,true,"Change the sphere color",ratio,.66,768,416,3042,0,42);
            JSONObject size=g.getJSONObject("8").getJSONObject("inputs"),source=g.getJSONObject("source_video").getJSONObject("inputs");
            int w=size.getInt("width"),h=size.getInt("height");String[] parts=ratio.split(":");double expected=Double.parseDouble(parts[0])/Double.parseDouble(parts[1]);
            check(Math.abs((w/(double)h)/expected-1)<.07&&w%32==0&&h%32==0,"Edit output uses selected ratio with model alignment");
            check(source.getInt("custom_width")>source.getInt("custom_height"),"Output ratio does not distort source reference");
        }
        VideoEditWorkflow.prepare(graph,"Momentos/Tests/h3-source-edit.mp4",Collections.emptyList(),true,true,"Change the blue sphere to orange. Keep its movement and the lake.","Original",.33,768,416,3042,0,12345);
        JSONObject input=graph.getJSONObject("8").getJSONObject("inputs");
        check(graph.getJSONObject("8").getString("class_type").equals("MiniMaxH3ReferenceToVideo"),"Ref2VA node, not first frame animation");
        check(input.getJSONArray("ref_videos.ref_video_0").getString(0).equals("source_video"),"Actual video link");
        check(input.getJSONArray("ref_video_audios.ref_video_audio_0").getInt(1)==2,"Paired source audio");
        check(input.getInt("length")==73,"Original duration preserved");
        check(graph.getJSONObject("source_video").getJSONObject("inputs").getInt("force_rate")==24,"Reference resampled to 24 fps");
        check(!input.has("first_frame")&&!input.has("last_frame")&&!input.getString("prompt").contains("<Picture"),"No invented still frames");
        check(input.getString("prompt").contains("<Video 1>")&&input.getString("prompt").contains("<Audio 1>"),"Prompt describes connected references");
        Files.write(Paths.get(args[1]),graph.toString().getBytes("UTF-8"));
        VideoEditWorkflow.prepare(graph,"silent.mp4",Arrays.asList("appearance.png","style.png"),false,false,"Change the background","9:16",.66,768,416,3042,5,99);
        input=graph.getJSONObject("8").getJSONObject("inputs");
        check(!input.has("ref_video_audios.ref_video_audio_0")&&!input.getString("prompt").contains("<Audio"),"Silent clip removes stale audio");
        check(input.has("ref_images.ref_image_1")&&input.getString("prompt").contains("<Picture 2>"),"Additional image references");
        check(graph.getJSONObject("11").getJSONObject("inputs").getInt("steps")==20&&!graph.getJSONObject("21").getJSONObject("inputs").getJSONObject("lora_1").getBoolean("on"),"Standard profile disables turbo LoRA");
        VideoEditWorkflow.prepare(graph,"silent.mp4",Collections.emptyList(),false,true,"Keep the action","Original",.33,768,416,3042,0,7);
        check(!graph.has("edit_reference_0")&&!input.has("ref_images.ref_image_0"),"Old image references removed");
        boolean rejected=false;try{VideoEditWorkflow.frames(16000,0);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Unsupported duration is explicit");
        MediaFamilies.Record image=record("image","image"),video=record("video","video");video.sourceId=image.id;video.rootId=video.id;
        List<MediaFamilies.Record> items=new ArrayList<>(Arrays.asList(image,video));
        MediaFamilies.Record edit=record("edit","video");MediaFamilies.videoEdit(edit,video,items);items.add(edit);
        MediaFamilies.Record branch=record("branch","video");MediaFamilies.videoEdit(branch,edit,items);items.add(branch);
        check(edit.rootId.equals(video.id)&&edit.parentId.equals(video.id)&&edit.editNumber==1,"Video edit belongs to source video set");
        check(branch.rootId.equals(video.id)&&branch.parentId.equals(edit.id)&&branch.editNumber==2,"Edit from edit preserves lineage");
        check(!MediaFamilies.migrate(items)&&branch.rootId.equals(video.id),"Existing migration preserves video edits");
        items.remove(video);check(MediaFamilies.root(branch,items).equals("video"),"Deleting original keeps descendant set");
        rejected=false;try{MediaFamilies.videoEdit(record("invalid","video"),image,items);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Image and video families cannot mix");
        System.out.println("VideoEditWorkflowTest passed: clip/audio input, profiles, duration, references and edit lineage.");
    }
}
