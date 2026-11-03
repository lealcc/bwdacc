package com.leal.comfyremote;

import org.json.*;
import java.util.*;

/** Live node progress and explicitly approximate timing. Queue time is never execution time. */
final class GenerationProgress {
    static final class Display {
        final String title,detail;
        final float fraction;
        Display(String title,String detail,float fraction){this.title=title;this.detail=detail;this.fraction=fraction;}
    }
    interface Source { Display read(); }
    JSONObject nodes=new JSONObject();
    long observedStart,serverSeenAt;
    int queuePosition;
    boolean streamLive,downloading;
    String currentNode="";
    double value,max;

    static JSONObject nodeTypes(JSONObject graph){
        JSONObject result=new JSONObject();if(graph==null)return result;
        try{Iterator<String> ids=graph.keys();while(ids.hasNext()){String id=ids.next();JSONObject node=graph.optJSONObject(id);if(node!=null)result.put(id,node.optString("class_type"));}}catch(JSONException ignored){}
        return result;
    }
    void event(String type,JSONObject data,long now){
        serverSeenAt=now;
        if("execution_start".equals(type)){observedStart=now;currentNode="";value=max=0;downloading=false;}
        else if("executing".equals(type)){
            if(data.isNull("node")){downloading=true;return;}
            select(data.optString("node"));
        }else if("progress".equals(type)){
            String node=data.optString("node",currentNode);if(node.isEmpty()||"null".equals(node))return;
            select(node);steps(data.optDouble("value",0),data.optDouble("max",0));
        }else if("progress_state".equals(type)){
            JSONObject states=data.optJSONObject("nodes");if(states==null)return;
            JSONObject chosen=states.optJSONObject(currentNode);String id=currentNode;
            if(chosen==null||!"running".equals(chosen.optString("state"))){chosen=null;Iterator<String> ids=states.keys();while(ids.hasNext()){String key=ids.next();JSONObject state=states.optJSONObject(key);if(state!=null&&"running".equals(state.optString("state"))){id=key;chosen=state;break;}}}
            if(chosen!=null){select(id);if(!nodes.has(id)){String real=chosen.optString("real_node_id");try{if(nodes.has(real))nodes.put(id,nodes.optString(real));}catch(JSONException ignored){}}
                // A freshly started node uses max=1; it is not a measured step count.
                if(chosen.optDouble("max",0)>1||chosen.optDouble("value",0)>0)steps(chosen.optDouble("value",0),chosen.optDouble("max",0));
            }else if(states.has(currentNode)){value=max=0;currentNode="";}
        }else if("execution_success".equals(type))downloading=true;
    }
    private void select(String id){if(!id.equals(currentNode)){currentNode=id;value=max=0;}}
    private void steps(double completed,double total){if(!Double.isFinite(completed)||!Double.isFinite(total)||total<=0)return;max=total;value=Math.min(total,Math.max(0,completed));}
    String stage(){
        String type=nodes.optString(currentNode).toLowerCase(Locale.ROOT);
        if(currentNode.equals(PromptAgent.LLM))return UiLanguage.t("Agente preparando prompt");
        if(type.contains("sampler")&&!type.contains("select"))return UiLanguage.t("Amostragem");
        if(type.contains("decodeaudio"))return UiLanguage.t("Preparando áudio");
        if(type.contains("decode"))return UiLanguage.t("Preparando resultado");
        if(type.contains("save")||type.contains("createvideo")||type.contains("combine"))return UiLanguage.t("Finalizando");
        if(type.contains("loader")||type.contains("lora"))return UiLanguage.t("Carregando modelo");
        if(type.contains("text")||type.contains("minimax"))return UiLanguage.t("Preparando prompt");
        if(type.contains("encode")||type.contains("loadimage")||type.contains("scale"))return UiLanguage.t("Preparando referência");
        return UiLanguage.t("Gerando");
    }
    Display display(String state,long now,long expectedMillis,boolean online){
        long elapsed=observedStart>0?Math.max(0,now-observedStart):0;
        if(!online)return new Display(UiLanguage.t("Reconectando"), UiLanguage.t("Aguardando conexão com servidor"),-1);
        if("queued".equals(state))return new Display(queuePosition>0?UiLanguage.t("Na fila · ")+queuePosition+"º":UiLanguage.t("Na fila"),UiLanguage.t("Aguardando sua vez"),-1);
        if(downloading)return new Display(UiLanguage.t("Salvando resultado"),UiLanguage.t("Geração concluída no servidor"),-1);
        String timing=observedStart==0?UiLanguage.t("Acompanhando geração"):shortTime(elapsed)+UiLanguage.t(" decorridos");
        if(expectedMillis>0&&observedStart>0){
            if(elapsed<expectedMillis)timing="≈ "+shortTime(expectedMillis-elapsed)+UiLanguage.t(" restante");
            else timing=UiLanguage.t("Além da estimativa · ")+shortTime(elapsed);
        }
        if(streamLive&&max>0){
            int percent=(int)Math.floor(value/max*100);
            String count=String.format(Locale.ROOT,"%.0f / %.0f",value,max)+(UiLanguage.original(stage()).equals("Amostragem")?UiLanguage.t(" passos"):UiLanguage.t(" da etapa"));
            return new Display(percent>=100?UiLanguage.t("Finalizando etapa"):stage()+" · "+percent+"%",count+" · "+timing,(float)(value/max));
        }
        // A duration estimate is not a measured percentage. Do not replace the node's
        // percentage with an unrelated elapsed-time percentage when the next node starts.
        return new Display(streamLive?stage():UiLanguage.t("Gerando"),timing,-1);
    }
    static String shortTime(long millis){long seconds=Math.max(1,(millis+999)/1000);return seconds<60?seconds+" s":seconds<3600?((seconds+59)/60)+" min":String.format(Locale.ROOT,"%.1f h",seconds/3600.0);}
    static int queuePosition(JSONArray queue,String id){
        if(queue==null)return 0;
        for(int i=0;i<queue.length();i++){
            JSONArray item=queue.optJSONArray(i);if(item==null||!id.equals(item.optString(1)))continue;
            double priority=item.optDouble(0,Double.NaN);if(!Double.isFinite(priority))return i+1;
            int position=1;for(int j=0;j<queue.length();j++){JSONArray other=queue.optJSONArray(j);if(other==null||i==j)continue;double order=other.optDouble(0,Double.NaN);if(order<priority||(order==priority&&j<i))position++;}return position;
        }
        return 0;
    }
    static boolean comparable(JSONObject a,JSONObject b){
        if(a==null||b==null||a.optString("Modelo").isEmpty()||!a.optString("Modelo").equals(b.optString("Modelo")))return false;
        for(String key:new String[]{"Agente","Passos","Sampler","Scheduler","Denoise","CFG","Guidance","Dimensões solicitadas","Proporção","Resolução solicitada","Quadros","FPS","LoRAs"})if(!a.optString(key).equals(b.optString(key)))return false;
        return true;
    }
    static long median(List<Long> samples){if(samples.isEmpty())return 0;List<Long> copy=new ArrayList<>(samples);Collections.sort(copy);int n=copy.size();return n%2==0?(copy.get(n/2-1)+copy.get(n/2))/2:copy.get(n/2);}
}
