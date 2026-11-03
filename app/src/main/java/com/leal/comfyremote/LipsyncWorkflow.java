package com.leal.comfyremote;

import org.json.*;

final class LipsyncWorkflow {
    static int size(int value){return value==512||value==768||value==1024||value==1280?value:1024;}
    static int batch(String profile){return "8".equals(profile)?1:2;}
    static JSONObject graph(boolean video,String source,String audio,int size,int batch,int seed)throws Exception{
        JSONObject inputs=new JSONObject().put("source",source).put("audio",audio).put("kind",video?"video":"image").put("max_side",size(size)).put("batch_size",Math.max(1,Math.min(4,batch))).put("seed",seed&Integer.MAX_VALUE);
        return new JSONObject().put("lipsync",ReferenceSupport.node("BwdaccMuseTalk",inputs))
            .put("save",ReferenceSupport.node("SaveVideo",new JSONObject().put("video",ReferenceSupport.link("lipsync")).put("filename_prefix","bwdacc/Lipsync").put("format","mp4").put("codec","h264")));
    }
}
