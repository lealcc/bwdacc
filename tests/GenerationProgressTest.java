package com.leal.comfyremote;
import org.json.*;
import java.util.*;

public final class GenerationProgressTest {
    static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception{
        GenerationProgress p=new GenerationProgress();p.nodes.put("1","KSampler").put("2","VAEDecode");
        p.queuePosition=3;
        JSONArray queue=new JSONArray("[[1,'first'],[4,'fourth'],[2,'second'],[5,'fifth'],[6,'sixth'],[3,'third']]");
        check(GenerationProgress.queuePosition(queue,"third")==3,"heap-backed queue ranks by priority, not storage index");
        check(GenerationProgress.queuePosition(queue,"missing")==0,"unknown queue position is not fabricated");
        check(p.display("queued",10000,60000,true).title.equals("Na fila · 3º"),"queue position and no elapsed estimate while waiting");
        check(p.display("running",10000,60000,true).fraction<0,"unknown start never invents elapsed time");
        p.event("execution_start",new JSONObject(),10000);p.streamLive=true;
        p.event("progress",new JSONObject().put("node","1").put("value",4).put("max",20),25000);
        GenerationProgress.Display d=p.display("running",25000,60000,true);
        check(d.title.equals("Amostragem · 20%")&&Math.abs(d.fraction-.2)<.001,"live node fraction takes priority over historical estimate");
        check(d.detail.contains("4 / 20 passos")&&d.detail.contains("45 s restante"),"steps plus approximate remaining duration");
        p.event("progress",new JSONObject().put("node","1").put("value",24).put("max",20),26000);
        check(!p.display("running",26000,60000,true).title.contains("100%"),"sampler completion is not whole generation completion");
        p.event("executing",new JSONObject().put("node","2"),27000);
        check(p.max==0&&p.stage().equals("Preparando resultado"),"new stage clears sampler count");
        JSONObject nodes=new JSONObject().put("1",new JSONObject().put("state","finished").put("value",20).put("max",20))
            .put("2:0",new JSONObject().put("state","running").put("value",2).put("max",4).put("real_node_id","2"));
        p.event("progress_state",new JSONObject().put("nodes",nodes),28000);
        check(p.currentNode.equals("2:0")&&p.stage().equals("Preparando resultado")&&p.value==2,"dynamic node snapshot uses real node type");
        p.streamLive=false;
        check(p.display("running",40000,60000,true).title.equals("Gerando")&&p.display("running",40000,60000,true).fraction<0,"disconnected socket does not present stale or time-based percentages");
        check(p.display("running",80000,60000,true).detail.startsWith("Além da estimativa"),"overrun does not stick at zero seconds remaining");
        check(p.display("running",80000,60000,false).title.equals("Reconectando"),"network outage explicit");
        p.event("execution_success",new JSONObject(),85000);
        check(p.display("running",85000,60000,true).title.equals("Salvando resultado"),"server completion still awaits download");
        JSONObject a=new JSONObject().put("Modelo","m").put("Passos","20").put("Seed","1"),b=new JSONObject(a.toString()).put("Seed","2");
        check(GenerationProgress.comparable(a,b),"seed changes do not discard relevant timing history");
        check(!GenerationProgress.comparable(a,new JSONObject(b.toString()).put("Passos","40")),"different workloads do not share estimate");
        check(!GenerationProgress.comparable(new JSONObject(),new JSONObject()),"missing model cannot fabricate compatibility");
        check(GenerationProgress.median(Arrays.asList(60000L,59000L,900000L))==60000L,"median resists slow outlier");
        System.out.println("PASS: queue, live progress, stage changes, reconnect, historical estimates, overrun and download states");
    }
}
