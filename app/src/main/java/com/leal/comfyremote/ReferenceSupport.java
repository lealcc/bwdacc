package com.leal.comfyremote;

import org.json.*;
import java.util.*;

/** Only bind live image inputs. Extra references never become an implicit collage. */
final class ReferenceSupport {
    static boolean type(JSONObject graph,String id,String type) {
        return graph!=null&&graph.optJSONObject(id)!=null&&type.equals(graph.optJSONObject(id).optString("class_type"));
    }
    static boolean klein(JSONObject graph) {
        return type(graph,"2","UNETLoader")&&graph.optJSONObject("2").optJSONObject("inputs").optString("unet_name").toLowerCase(Locale.ROOT).contains("klein")
            &&type(graph,"9","ReferenceLatent")&&type(graph,"10","ReferenceLatent")&&type(graph,"15","CFGGuider")&&type(graph,"1","LoadImage")
            &&type(graph,"4","VAELoader")&&type(graph,"5","ImageScaleToTotalPixels")&&type(graph,"8","VAEEncode")
            &&type(graph,"11","GetImageSize")&&type(graph,"14","Flux2Scheduler")&&type(graph,"16","EmptyFlux2LatentImage");
    }
    static boolean h3(JSONObject graph) {return type(graph,"8","MiniMaxH3ImageToVideo")&&type(graph,"1","LoadImage");}
    static List<String> inputs(JSONObject graph) {
        Set<String> live=new HashSet<>();List<String> result=new ArrayList<>();
        if(graph==null)return result;
        Iterator<String> keys=graph.keys();while(keys.hasNext()){String id=keys.next();JSONObject node=graph.optJSONObject(id);if(node==null)continue;
            String type=node.optString("class_type");if(type.startsWith("Save")||type.equals("PreviewImage"))LoraSupport.ancestors(graph,id,live);}
        for(String id:live)if(type(graph,id,"LoadImage"))result.add(id);
        Collections.sort(result,(a,b)->{try{return Integer.compare(Integer.parseInt(a),Integer.parseInt(b));}catch(Exception e){return a.compareTo(b);}});
        return result;
    }
    static int capacity(JSONObject graph) {return klein(graph)?3:h3(graph)?2:Math.min(3,inputs(graph).size());}
    static JSONArray link(String id) {return new JSONArray().put(id).put(0);}
    static JSONObject node(String type,JSONObject inputs) throws Exception {return new JSONObject().put("class_type",type).put("inputs",inputs);}
    static void bind(JSONObject graph,List<String> files) throws Exception {
        if(files.isEmpty())return;
        if(files.size()>capacity(graph))throw new IllegalArgumentException(UiLanguage.t("Este workflow não aceita essa quantidade de referências."));
        if(klein(graph)) {
            graph.getJSONObject("1").getJSONObject("inputs").put("image",files.get(0));
            String positive="9",negative="10";
            for(int i=1;i<files.size();i++) {
                String prefix="momentos_ref_"+i+"_";
                for(String part:new String[]{"load","scale","encode","positive","negative"})if(graph.has(prefix+part))throw new IllegalArgumentException(UiLanguage.t("IDs de referência já usados no workflow."));
                graph.put(prefix+"load",node("LoadImage",new JSONObject().put("image",files.get(i))));
                JSONObject scale=new JSONObject(graph.getJSONObject("5").getJSONObject("inputs").toString());scale.put("image",link(prefix+"load"));
                graph.put(prefix+"scale",node("ImageScaleToTotalPixels",scale));
                graph.put(prefix+"encode",node("VAEEncode",new JSONObject().put("pixels",link(prefix+"scale")).put("vae",link("4"))));
                graph.put(prefix+"positive",node("ReferenceLatent",new JSONObject().put("conditioning",link(positive)).put("latent",link(prefix+"encode"))));
                graph.put(prefix+"negative",node("ReferenceLatent",new JSONObject().put("conditioning",link(negative)).put("latent",link(prefix+"encode"))));
                positive=prefix+"positive";negative=prefix+"negative";
            }
            graph.getJSONObject("15").getJSONObject("inputs").put("positive",link(positive)).put("negative",link(negative));
        } else if(h3(graph)) {
            graph.getJSONObject("1").getJSONObject("inputs").put("image",files.get(0));
            if(files.size()==2) {
                graph.put("momentos_last_frame",node("LoadImage",new JSONObject().put("image",files.get(1))));
                graph.getJSONObject("8").getJSONObject("inputs").put("last_frame",link("momentos_last_frame"));
            }
        } else {
            List<String> nodes=inputs(graph);
            for(int i=0;i<files.size();i++)graph.getJSONObject(nodes.get(i)).getJSONObject("inputs").put("image",files.get(i));
        }
    }
}
