package com.leal.comfyremote;

import org.json.*;
import java.util.*;

public final class ServerMonitorDataTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static JSONArray job(int priority,String id,JSONObject graph)throws Exception{return new JSONArray().put(priority).put(id).put(graph).put(new JSONObject().put("prompt","PRIVATE PROMPT")).put(new JSONArray());}
    public static void main(String[] args)throws Exception{
        JSONObject graph=new JSONObject().put("1",new JSONObject().put("class_type","UNETLoader").put("inputs",new JSONObject().put("unet_name","ideogram4_fp8_scaled.safetensors").put("text","PRIVATE PROMPT")));
        graph.put("14",new JSONObject().put("class_type","LoraLoaderModelOnly").put("inputs",new JSONObject().put("lora_name","ideogram4/ideogram_4_turbotime_v1.safetensors")));
        JSONObject queue=new JSONObject().put("queue_running",new JSONArray().put(job(9,"running-id",graph)))
            .put("queue_pending",new JSONArray().put(job(12,"later-id",graph)).put("bad row").put(new JSONArray().put(1)).put(job(3,"next-id",new JSONObject())));
        List<ServerMonitorData.Job> jobs=ServerMonitorData.jobs(queue);
        check(jobs.size()==3&&jobs.get(0).running&&jobs.get(1).id.equals("next-id")&&jobs.get(2).id.equals("later-id"),"Running first, pending sorted by queue priority, malformed rows ignored");
        check(jobs.get(0).title.equals("Ideogram 4 TurboTime"),"Recognize the actual workflow");
        for(ServerMonitorData.Job job:jobs)check(!job.title.contains("PRIVATE"),"Queue summaries do not expose prompts");
        check(ServerMonitorData.jobs(new JSONObject()).isEmpty(),"Empty queue");
        JSONArray entries=new JSONArray().put(new JSONObject().put("t","2026-10-30T17:00:00").put("m","\u001b[32mReady\u001b[0m\rProgress\n\u001b]0;private title\u0007Done"));
        String log=ServerMonitorData.logs(new JSONObject().put("entries",entries));
        check(log.contains("17:00:00")&&log.contains("Ready")&&log.contains("Progress")&&log.contains("Done")&&!log.contains("\u001b")&&!log.contains("private title"),"Strip terminal controls while preserving messages");
        JSONArray many=new JSONArray();for(int i=0;i<900;i++)many.put(new JSONObject().put("m","line-"+i+"\n"));
        log=ServerMonitorData.logs(new JSONObject().put("entries",many));check(!log.contains("line-0\n")&&log.contains("line-899")&&log.split("\n").length<=400,"Only retain the recent log window");
        char[] huge=new char[150000];Arrays.fill(huge,'x');log=ServerMonitorData.logs(new JSONObject().put("entries",new JSONArray().put(new JSONObject().put("m",new String(huge)))));
        check(log.length()<=80000,"Bound large log entries");check(ServerMonitorData.logs(new JSONObject()).isEmpty(),"Unsupported log schema");
        System.out.println("PASS ServerMonitorDataTest: queue order, privacy, malformed data, ANSI and bounded logs");
    }
}
