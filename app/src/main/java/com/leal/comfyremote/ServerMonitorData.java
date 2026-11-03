package com.leal.comfyremote;

import org.json.*;
import java.util.*;
import java.util.regex.Pattern;

/** Read-only server snapshots. Queue summaries never include prompts or media. */
final class ServerMonitorData {
    static final class Job {
        final String id, title;final double order;final boolean running;
        Job(String id,String title,double order,boolean running){this.id=id;this.title=title;this.order=order;this.running=running;}
    }
    static List<Job> jobs(JSONObject snapshot){
        List<Job> result=new ArrayList<>();
        for(String key:new String[]{"queue_running","queue_pending"}){
            JSONArray rows=snapshot.optJSONArray(key);if(rows==null)continue;
            List<Job> group=new ArrayList<>();
            for(int i=0;i<rows.length();i++){
                JSONArray row=rows.optJSONArray(i);if(row==null||row.length()<3||row.optString(1).isEmpty())continue;
                group.add(new Job(row.optString(1),title(row.optJSONObject(2)),row.optDouble(0,0),key.equals("queue_running")));
            }
            group.sort((a,b)->Double.compare(a.order,b.order));result.addAll(group);
        }return result;
    }
    private static String title(JSONObject graph){
        if(graph==null)return UiLanguage.t("Workflow do ComfyUI");
        if(graph.has("momentos_extend_source"))return UiLanguage.t("Extensão de vídeo");
        String family=MemoryProfiles.family(graph);
        if(family.equals("ideogram")){
            for(Iterator<String> it=graph.keys();it.hasNext();){JSONObject n=graph.optJSONObject(it.next()),in=n==null?null:n.optJSONObject("inputs");if(in!=null&&in.optString("lora_name").contains("turbotime"))return UiLanguage.t("Ideogram 4 TurboTime");}
            return UiLanguage.t("Ideogram 4");
        }
        if(family.equals("kromoss"))return UiLanguage.t("Kromoss");
        if(family.equals("moss"))return UiLanguage.t("MOSS Image");
        if(family.equals("h3"))return ReferenceBindings.ref2va(graph)?UiLanguage.t("Edição de vídeo H3"):VideoWorkflow.turbo(graph)?UiLanguage.t("MiniMax H3 Turbo"):UiLanguage.t("MiniMax H3");
        if(ReferenceSupport.klein(graph))return UiLanguage.t("Edição de imagem");
        for(Iterator<String> it=graph.keys();it.hasNext();){JSONObject n=graph.optJSONObject(it.next());if(n!=null&&n.optString("class_type").equals("LlamaServerVisionCaption"))return UiLanguage.t("Agente · preparando referência");}
        return UiLanguage.t("Workflow do ComfyUI");
    }
    private static final Pattern ANSI=Pattern.compile("\u001B(?:\\[[0-?]*[ -/]*[@-~]|\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\))");
    static String logs(JSONObject snapshot){
        JSONArray entries=snapshot.optJSONArray("entries");if(entries==null)return "";
        StringBuilder text=new StringBuilder();
        for(int i=Math.max(0,entries.length()-400);i<entries.length();i++){
            JSONObject entry=entries.optJSONObject(i);if(entry==null)continue;
            String message=ANSI.matcher(entry.optString("m")).replaceAll("").replace("\r\n","\n").replace('\r','\n');
            message=message.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]","");
            if(message.trim().isEmpty())continue;
            String time=entry.optString("t");if(time.length()>=19)text.append(time.substring(11,19)).append("  ");
            text.append(message);if(!message.endsWith("\n"))text.append('\n');
            if(text.length()>100000)text.delete(0,text.length()-80000);
        }
        String[] lines=text.toString().split("\n");StringBuilder tail=new StringBuilder();
        for(int i=Math.max(0,lines.length-400);i<lines.length;i++)tail.append(lines[i]).append('\n');
        return tail.length()>80000?tail.substring(tail.length()-80000):tail.toString();
    }
}
