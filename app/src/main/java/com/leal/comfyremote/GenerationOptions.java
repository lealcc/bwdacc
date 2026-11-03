package com.leal.comfyremote;
import org.json.*;
import java.util.*;
final class GenerationOptions {
 static int count(int value){return Math.max(1,Math.min(4,value));}
 static void varySeed(JSONObject graph,int index)throws Exception {
  if(index==0)return;
  Random random=new Random(); Iterator<String> ids=graph.keys();
  while(ids.hasNext()) {String id=ids.next();if(id.startsWith("momentos_agent_"))continue;JSONObject in=graph.getJSONObject(id).optJSONObject("inputs");if(in==null)continue;
   Iterator<String> keys=in.keys();while(keys.hasNext()){String key=keys.next();if(!key.equals("seed")&&!key.equals("noise_seed"))continue;
    long seed=random.nextLong()&0x1FFFFFFFFFFFFFL;
    if(in.opt(key) instanceof Number)in.put(key,seed);
    else {JSONArray link=in.optJSONArray(key);if(link!=null){JSONObject node=graph.optJSONObject(link.optString(0));
     if(node!=null&&"PrimitiveInt".equals(node.optString("class_type")))node.getJSONObject("inputs").put("value",seed);}}
   }
  }
 }
 static int[] dimensions(String ratio,double megapixels,int multiple){
  String[] parts=ratio.split(":");double r=Double.parseDouble(parts[0])/Double.parseDouble(parts[1]);
  return new int[]{Math.max(multiple,(int)Math.round(Math.sqrt(megapixels*1000000*r)/multiple)*multiple),Math.max(multiple,(int)Math.round(Math.sqrt(megapixels*1000000/r)/multiple)*multiple)};
 }
 static int imageMegapixels(int value){return value==4?4:value==2?2:1;}
 static boolean imageLatent(String type){return "EmptyLatentImage".equals(type)||"EmptySD3LatentImage".equals(type)||"EmptyFlux2LatentImage".equals(type);}
 static boolean supportsImageResolution(JSONObject graph){
  if(graph==null)return false;
  if(ReferenceSupport.klein(graph))return true;
  Iterator<String> ids=graph.keys();while(ids.hasNext()){
   JSONObject node=graph.optJSONObject(ids.next());if(node==null)continue;
   if("ResolutionSelector".equals(node.optString("class_type")))return true;
   JSONObject in=node.optJSONObject("inputs");if(imageLatent(node.optString("class_type"))&&in!=null&&in.opt("width") instanceof Number&&in.opt("height") instanceof Number)return true;
  }return false;
 }
 static void imageOutput(JSONObject graph,String ratio,int megapixels,int referenceWidth,int referenceHeight)throws Exception {
  int mp=imageMegapixels(megapixels);
  if(ReferenceSupport.klein(graph)){
   if("Original".equals(ratio)){
    if(referenceWidth<=0||referenceHeight<=0)throw new IllegalArgumentException(UiLanguage.t("Não foi possível ler a proporção da referência."));
    ratio=referenceWidth+":"+referenceHeight;
   }
   int[] size=dimensions(ratio,mp,16);
   for(String id:new String[]{"14","16"})graph.getJSONObject(id).getJSONObject("inputs").put("width",size[0]).put("height",size[1]);
   return; // Reference encoding keeps its own 1 MP budget.
  }
  Iterator<String> ids=graph.keys();while(ids.hasNext()){
   JSONObject node=graph.optJSONObject(ids.next());if(node==null)continue;JSONObject in=node.optJSONObject("inputs");if(in==null)continue;
   String type=node.optString("class_type");
   if("ResolutionSelector".equals(type))in.put("megapixels",mp);
   else if((imageLatent(type)||type.equals("Ideogram4Scheduler")||type.equals("ModelSamplingFlux"))&&in.opt("width") instanceof Number&&in.opt("height") instanceof Number){
    String selected="Original".equals(ratio)?in.getInt("width")+":"+in.getInt("height"):ratio;
    int[] size=dimensions(selected,mp,16);in.put("width",size[0]).put("height",size[1]);
   }
  }
 }
 static void editRatio(JSONObject graph,String ratio,boolean video)throws Exception {
  if("Original".equals(ratio))return;
  int[] size=dimensions(ratio,video?.4:1,video?32:16);
  if(video)graph.getJSONObject("8").getJSONObject("inputs").put("width",size[0]).put("height",size[1]);
  else for(String id:new String[]{"14","16"})graph.getJSONObject(id).getJSONObject("inputs").put("width",size[0]).put("height",size[1]);
 }
 static double videoMegapixels(int quality) {return quality==66?.66:quality==98?.98:.33;}
 static int[] videoDimensions(String ratio,double megapixels,int referenceWidth,int referenceHeight) {
  if("Original".equals(ratio)) {
   if(referenceWidth<=0||referenceHeight<=0)throw new IllegalArgumentException(UiLanguage.t("Não foi possível ler a proporção da referência."));
   ratio=referenceWidth+":"+referenceHeight;
  }
  int[] size=dimensions(ratio,megapixels,32);
  if(size[0]>16384||size[1]>16384)throw new IllegalArgumentException(UiLanguage.t("Proporção de vídeo fora do limite do workflow."));
  return size;
 }
 static void videoOutput(JSONObject graph,String ratio,double megapixels,int referenceWidth,int referenceHeight)throws Exception {
  int[] size=videoDimensions(ratio,megapixels,referenceWidth,referenceHeight);
  graph.getJSONObject("8").getJSONObject("inputs").put("width",size[0]).put("height",size[1]);
  // Keep the reference at the selected pixel budget before the model's own alignment.
  graph.getJSONObject("2").getJSONObject("inputs").put("megapixels",megapixels);
 }
}
