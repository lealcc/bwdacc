package com.leal.comfyremote;
import org.json.*;
import java.nio.file.*;
public final class GenerationDetailsTest {
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception{
        JSONObject graph=new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8"));
        JSONObject details=GenerationDetails.snapshot(graph);
        check(details.optString("Seed").equals(graph.getJSONObject("29").getJSONObject("inputs").getString("value")),"linked seed resolved");
        check(details.optString("Passos").equals("8"),"actual steps captured");
        check(!details.optString("Modelo").isEmpty(),"model captured");
        check(Math.abs(GenerationAspect.requested(details)-4.0/3)<.0001,"pending Kromoss uses submitted ratio");
        check(Math.abs(GenerationAspect.requested(new JSONObject().put("Dimensões solicitadas","1344 × 768").put("Proporção","9:16"))-1.75)<.0001,"output dimensions take priority over generic ratio");
        check(GenerationAspect.requested(new JSONObject().put("Proporção","Original"))==0,"unknown aspect uses media fallback");
        check(GenerationAspect.requested(new JSONObject().put("Dimensões solicitadas","1024 × 0"))==0,"invalid dimensions rejected");
        JSONObject job=new JSONObject().put("prompt",new JSONArray().put(0).put("id").put(graph));
        JSONArray messages=new JSONArray().put(new JSONArray().put("execution_start").put(new JSONObject().put("timestamp",100000)))
            .put(new JSONArray().put("execution_cached").put(new JSONObject().put("timestamp",101000)))
            .put(new JSONArray().put("execution_success").put(new JSONObject().put("timestamp",165000)));
        job.put("status",new JSONObject().put("messages",messages));
        long[] times=GenerationDetails.executionTimes(job);
        check(times[0]==100000&&times[1]==165000,"server execution timestamps, excluding queue and download");
        check(GenerationDetails.duration(times[0],times[1]).equals("1 min 5 s"),"formatted duration");
        check(GenerationDetails.duration(0,0).equals("Não registrado"),"old history not fabricated");
        check(GenerationDetails.historySettings(job).toString().equals(details.toString()),"legacy history recovery");
        check(GenerationDetails.executionTimes(new JSONObject())[0]==0,"missing history tolerated");
        System.out.println("PASS: request settings, linked seed, server duration and legacy recovery");
    }
}
