package com.leal.comfyremote;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import org.json.*;

/** Typed control maps. Storage and extraction are independent from generation-model support. */
final class ControlFragments {
    static final String[] TYPES={"depth","pose","lineart","canny","softedge","normal","scribble","segmentation"};
    static final String[] LABELS={"Depth","Pose","Lineart","Canny","Soft edge","Normais","Scribble","Segmentação"};
    static final String[][] NODES={{"DepthAnythingV2Preprocessor","DepthAnythingPreprocessor","MiDaS-DepthMapPreprocessor"},{"DWPreprocessor","OpenposePreprocessor"},{"LineArtPreprocessor","LineartStandardPreprocessor"},{"Canny","CannyEdgePreprocessor"},{"HEDPreprocessor","PiDiNetPreprocessor"},{"BAE-NormalMapPreprocessor"},{"ScribblePreprocessor"},{"OneFormer-ADE20K-SemSegPreprocessor"}};
    static int index(String type){return Arrays.asList(TYPES).indexOf(type);}
    static String label(String type){int i=index(type);return i<0?type:UiLanguage.t(LABELS[i]);}
    static String icon(String type){switch(type){case "depth":return "depth";case "pose":return "pose";case "normal":return "object";case "segmentation":return "grid";case "lineart":case "scribble":return "edit";default:return "edges";}}
    static String summary(ReferencePreset p){List<String> labels=new ArrayList<>();for(String type:TYPES)if(p.controls.has(type))labels.add(label(type));return String.join(" · ",labels);}
    static void validate(ReferencePreset p)throws JSONException{
        Set<String> files=new HashSet<>();for(ReferencePreset.Picture picture:p.images)files.add(picture.file);
        for(Iterator<String> keys=p.controls.keys();keys.hasNext();){String type=keys.next();if(index(type)<0)throw new JSONException(UiLanguage.t("Unknown control map type"));JSONObject map=p.controls.getJSONObject(type);
            for(String field:new String[]{"file","source"}){String name=map.optString(field);if(!name.isEmpty()&&(!ReferencePreset.validFile(name)||!files.contains(name)))throw new JSONException(UiLanguage.t("Control map image missing"));}
            if(map.optString("file").isEmpty())throw new JSONException(UiLanguage.t("Control map image missing"));
        }
    }
    static void put(ReferencePreset p,String type,ReferencePreset.Picture map,ReferencePreset.Picture source,String processor)throws JSONException{
        if(index(type)<0)throw new JSONException(UiLanguage.t("Unknown control map type"));p.kind="control";p.useText=p.useImages=p.useLoras=p.useRefmod=false;
        for(ReferencePreset.Picture image:Arrays.asList(map,source))if(image!=null){boolean present=false;for(ReferencePreset.Picture old:p.images)if(old.file.equals(image.file))present=true;if(!present)p.images.add(image);p.privateCover|=image.hidden;}
        p.controls.put(type,new JSONObject().put("file",map.file).put("source",source==null?"":source.file).put("processor",processor).put("created",System.currentTimeMillis()));p.cover=map.file;prune(p);validate(p);
    }
    static void remove(ReferencePreset p,String type){p.controls.remove(type);prune(p);}
    private static void prune(ReferencePreset p){Set<String> used=new HashSet<>();for(String type:TYPES){JSONObject map=p.controls.optJSONObject(type);if(map!=null){used.add(map.optString("file"));used.add(map.optString("source"));}}p.images.removeIf(image->!used.contains(image.file));if(!used.contains(p.cover))p.cover=p.images.isEmpty()?"":p.images.get(0).file;}
    static ReferencePreset.Picture picture(ReferencePreset p,String file){for(ReferencePreset.Picture image:p.images)if(image.file.equals(file))return image;return null;}
    static String processor(JSONObject schema,String type)throws Exception{
        int at=index(type);if(at<0)throw new IllegalArgumentException(UiLanguage.t("Unknown control type"));
        for(String node:NODES[at])if(schema.has(node)){
            JSONArray outputs=schema.getJSONObject(node).optJSONArray("output");if(outputs!=null&&"IMAGE".equals(outputs.optString(0)))return node;
        }
        throw new IllegalStateException(UiLanguage.t("Preprocessador ausente no ComfyUI. Importe um mapa pronto ou instale: ")+NODES[at][0]);
    }
    static JSONObject graph(JSONObject schema,String node,String image)throws Exception{
        JSONObject input=ReferenceGraphs.defaults(schema,node,true);input.put("image",ReferenceSupport.link("source"));
        JSONObject required=ReferenceGraphs.inputFields(schema.getJSONObject(node));
        if(required!=null&&required.has("resolution"))input.put("resolution",512);
        if(node.equals("DepthAnythingV2Preprocessor")){
            JSONArray names=required.getJSONArray("ckpt_name").getJSONArray(0);for(int i=0;i<names.length();i++)if("depth_anything_v2_vits.pth".equals(names.optString(i)))input.put("ckpt_name",names.getString(i));
        }
        JSONObject graph=new JSONObject();graph.put("source",ReferenceSupport.node("LoadImage",new JSONObject().put("image",image)));
        graph.put("map",ReferenceSupport.node(node,input));graph.put("result",ReferenceSupport.node("PreviewImage",new JSONObject().put("images",ReferenceSupport.link("map"))));return graph;
    }
    static final class Result {final byte[] image;final String processor;Result(byte[] image,String processor){this.image=image;this.processor=processor;}}
    static Result extract(ReferenceJobs.Server server,String address,File source,String type,Callable<Boolean> cancelled,Consumer<String> progress)throws Exception{
        if(address==null||address.isEmpty())throw new IllegalStateException(UiLanguage.t("Conecte ao servidor em Ajustes."));
        progress.accept(UiLanguage.t("Verificando preprocessadores…"));JSONObject schema=server.json("GET",address+"/object_info",null);String processor=processor(schema,type);
        if(cancelled.call())throw new InterruptedException();progress.accept(UiLanguage.t("Enviando imagem…"));String uploaded=server.upload(address,source);
        JSONObject graph=graph(schema,processor,uploaded);if(cancelled.call())throw new InterruptedException();String id;
        try{id=server.json("POST",address+"/prompt",new JSONObject().put("prompt",graph).put("client_id",UUID.randomUUID().toString())).getString("prompt_id");}
        catch(Exception error){throw new IllegalStateException(UiLanguage.t("Envio não confirmado. Confira a fila antes de tentar novamente."),error);}
        if(!id.matches("[a-zA-Z0-9_-]{1,128}"))throw new IllegalStateException(UiLanguage.t("Servidor não retornou ID."));
        progress.accept(UiLanguage.t("Gerando mapa no ComfyUI…"));long deadline=android.os.SystemClock.elapsedRealtime()+300_000;
        while(!cancelled.call()&&android.os.SystemClock.elapsedRealtime()<deadline){
            JSONObject h=server.json("GET",address+"/history/"+id,null).optJSONObject(id);if(h!=null){JSONObject status=h.optJSONObject("status");
                if(status!=null&&"error".equals(status.optString("status_str")))throw new IllegalStateException(UiLanguage.t("Falha na extração. Confira o preprocessador e seus modelos no ComfyUI."));
                if(status!=null&&status.optBoolean("completed")){JSONObject result=h.getJSONObject("outputs").getJSONObject("result");JSONArray images=result.getJSONArray("images");if(images.length()!=1)throw new IllegalStateException(UiLanguage.t("Resultado de análise inválido."));return new Result(server.download(address,images.getJSONObject(0)),processor);}
            }Thread.sleep(750);
        }
        if(cancelled.call())throw new InterruptedException();throw new IllegalStateException(UiLanguage.t("Análise demorou demais. Confira a fila antes de tentar novamente."));
    }
}
